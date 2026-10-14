package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.ResolveVisitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.syntax.Types;

import java.util.ArrayList;
import java.util.Set;

/// Instruments authored code while leaving compiler linkage calls private to the engine.
final class SandboxCompilationCustomizer extends CompilationCustomizer {
	private static final String CHECKED = SandboxCompilationCustomizer.class.getName();
	private final ClassFilter filter;
	SandboxCompilationCustomizer(ClassFilter filter) {
		super(CompilePhase.INSTRUCTION_SELECTION);
		this.filter = filter;
	}

	private static final Set<String> BLOCKED_ANNOTATIONS = Set.of(
		"groovy.transform.ASTTest", "groovy.transform.CompileStatic", "groovy.transform.TypeChecked",
		"groovy.transform.AnnotationCollector", "groovy.transform.BaseScript",
		"groovy.lang.Grab", "groovy.lang.Grapes", "groovy.lang.GrabConfig", "groovy.lang.GrabExclude", "groovy.lang.GrabResolver",
		"org.codehaus.groovy.transform.GroovyASTTransformationClass", "org.codehaus.groovy.transform.GroovyASTTransformation"
	);

	static CompilationCustomizer annotations(ClassFilter filter) {
		return new CompilationCustomizer(CompilePhase.CONVERSION) {
			@Override
			public void call(SourceUnit source, GeneratorContext context, ClassNode node) {
				new ClassCodeVisitorSupport() {
					@Override
					protected SourceUnit getSourceUnit() { return source; }
					@Override
					public void visitAnnotations(AnnotatedNode annotated) {
						for (var annotation : annotated.getAnnotations()) {
							if (annotation.getLineNumber() < 0) continue; // Engine-added BaseScript and generated metadata.
							String name = annotationName(source, annotation.getClassNode());
							if (BLOCKED_ANNOTATIONS.contains(name) || !filter.isAllowed(name)) throw SandboxRuntime.blocked("annotation " + name);
						}
					}
				}.visitClass(node);
			}
		};
	}

	private static String annotationName(SourceUnit source, ClassNode type) {
		String name = KubeGroovySandbox.resolveFullName(source, type);
		if (name.contains(".")) return name;
		var candidates = new ArrayList<String>();
		if (source.getAST().getPackageName() != null) candidates.add(source.getAST().getPackageName() + name);
		for (var star : source.getAST().getStarImports()) candidates.add(star.getPackageName() + name);
		for (var pkg : ResolveVisitor.DEFAULT_IMPORTS) candidates.add(pkg + name);
		for (var candidate : candidates) {
			if (source.getAST().getClasses().stream().anyMatch(c -> c.getName().equals(candidate))) return candidate;
			try {
				if (Class.forName(candidate, false, SandboxCompilationCustomizer.class.getClassLoader()).isAnnotation()) return candidate;
			} catch (ClassNotFoundException ignored) {}
		}
		return name; // Groovy reports unresolved source annotations during normal resolution.
	}

	@Override
	public void call(SourceUnit source, GeneratorContext context, ClassNode node) {
		var transformer = new ClassCodeExpressionTransformer() {
			@Override
			public void visitAnnotations(AnnotatedNode annotated) {
				// Annotation constants must retain their JVM representation; they are not runtime expressions.
				for (var annotation : annotated.getAnnotations()) {
					for (var value : annotation.getMembers().values()) value.visit(new CodeVisitorSupport() {
						@Override
						public void visitClassExpression(ClassExpression expression) {
							if (!filter.isAllowed(expression.getType().getName())) throw SandboxRuntime.blocked("annotation class " + expression.getType().getName());
						}
					});
				}
			}
			@Override
			public void visitMethod(MethodNode method) {
				if (method.getLineNumber() >= 0 && method.getCode() != null) method.setCode(checkedEntry(method.getCode(), method.getParameters(), false));
				super.visitMethod(method);
			}
			@Override
			protected SourceUnit getSourceUnit() { return source; }
			@Override
			public Expression transform(Expression expression) {
				if (expression == null || expression instanceof EmptyExpression || Boolean.TRUE.equals(expression.getNodeMetaData(CHECKED))) return expression;
				expression.putNodeMetaData(CHECKED, true);
				if (expression instanceof ClosureExpression closure) {
					closure.setCode(checkedEntry(closure.getCode(), closure.getParameters(), true));
					closure.getCode().visit(this);
					return closure;
				}
				// Assignment targets must remain assignable; protect their receiver, then guard the assigned value.
				if (expression instanceof BinaryExpression binary && Types.isAssignment(binary.getOperation().getType())) {
					var left = binary.getLeftExpression();
					if (left instanceof PropertyExpression property) binary.setLeftExpression(protectProperty(property));
					else if (left instanceof BinaryExpression index) {
						index.setLeftExpression(guard(transform(index.getLeftExpression())));
						index.setRightExpression(transform(index.getRightExpression()));
					}
					if (!(binary.getRightExpression() instanceof EmptyExpression)) binary.setRightExpression(guard(transform(binary.getRightExpression())));
					return binary;
				}
				if (expression instanceof PrefixExpression prefix) {
					if (prefix.getExpression() instanceof PropertyExpression property) prefix.setExpression(protectProperty(property));
					return guard(prefix);
				}
				if (expression instanceof PostfixExpression postfix) {
					if (postfix.getExpression() instanceof PropertyExpression property) postfix.setExpression(protectProperty(property));
					return guard(postfix);
				}
				if (expression instanceof MethodCallExpression method) {
					method.setObjectExpression(transform(method.getObjectExpression()));
					method.setMethod(transform(method.getMethod()));
					method.setArguments(transform(method.getArguments()));
					if (method.getLineNumber() < 0) return method;
					var name = method.getMethodAsString();
					if (name != null && !filter.isGlobalMethodNameAllowed(name)) throw SandboxRuntime.blocked("method " + name);
					if (name == null) method.setMethod(call("methodName", method.getMethod()));
					if (!method.isImplicitThis() && !(method.getObjectExpression() instanceof VariableExpression variable && variable.isSuperExpression())) {
						method.setObjectExpression(call("receiver", method.getObjectExpression(), new ConstantExpression(name == null ? "" : name), new ConstantExpression(method.isSpreadSafe())));
					}
					return guard(method);
				}
				if (expression instanceof PropertyExpression property) {
					var protectedProperty = protectProperty(property);
					return property.getLineNumber() < 0 ? protectedProperty : guard(protectedProperty);
				}
				if (expression instanceof MethodPointerExpression pointer) {
					var name = pointer.getMethodName();
					if (!(name instanceof ConstantExpression constant) || !filter.isGlobalMethodNameAllowed(String.valueOf(constant.getValue()))) throw SandboxRuntime.blocked("dynamic or prohibited method pointer");
					return new MethodPointerExpression(call("receiver", transform(pointer.getExpression()), name, ConstantExpression.FALSE), name);
				}
				var result = super.transform(expression);
				if (expression instanceof FieldExpression) return guard(result);
				if (expression instanceof ConstructorCallExpression constructor && !constructor.isSpecialCall()) {
					if (!filter.isAllowed(constructor.getType().getName())) throw SandboxRuntime.blocked("constructor " + constructor.getType().getName());
					return guard(result);
				}
				if (expression instanceof StaticMethodCallExpression staticCall && expression.getLineNumber() >= 0) {
					if (!filter.isAllowed(staticCall.getOwnerType().getName()) || !filter.isGlobalMethodNameAllowed(staticCall.getMethod())) throw SandboxRuntime.blocked("static method " + staticCall.getMethod());
					return guard(result);
				}
				if (expression instanceof CastExpression || expression instanceof BinaryExpression binary && binary.getOperation().getType() == Types.LEFT_SQUARE_BRACKET) return guard(result);
				return result;
			}

			private PropertyExpression protectProperty(PropertyExpression property) {
				property.setObjectExpression(transform(property.getObjectExpression()));
				if (property.getLineNumber() < 0) return property;
				String name = property.getPropertyAsString();
				if (name == null) {
					var checkedName = call("propertyName", transform(property.getProperty()));
					PropertyExpression dynamic = property instanceof AttributeExpression
						? new AttributeExpression(guard(property.getObjectExpression()), checkedName, property.isSafe())
						: new PropertyExpression(guard(property.getObjectExpression()), checkedName, property.isSafe());
					dynamic.setSpreadSafe(property.isSpreadSafe());
					dynamic.setImplicitThis(property.isImplicitThis());
					dynamic.setSourcePosition(property);
					return dynamic;
				}
				property.setObjectExpression(call("propertyReceiver", property.getObjectExpression(), new ConstantExpression(name), new ConstantExpression(property.isSpreadSafe())));
				return property;
			}
		};
		transformer.visitClass(node);
	}
	private static Statement checkedEntry(Statement code, Parameter[] parameters, boolean closure) {
		var block = new BlockStatement();
		if (parameters != null && parameters.length == 0 && closure) {
			block.addStatement(new ExpressionStatement(call("checkValue", new VariableExpression(new Parameter(ClassHelper.OBJECT_TYPE, "it")))));
		} else if (parameters != null) {
			for (var parameter : parameters) block.addStatement(new ExpressionStatement(call("checkValue", new VariableExpression(parameter))));
		}
		block.addStatement(code);
		return block;
	}

	private static Expression guard(Expression expression) {
		var result = call("checkValue", expression);
		result.setSourcePosition(expression);
		return result;
	}
	private static StaticMethodCallExpression call(String name, Expression... arguments) {
		var result = new StaticMethodCallExpression(ClassHelper.make(SandboxRuntime.class), name, new ArgumentListExpression(arguments));
		result.putNodeMetaData(CHECKED, true);
		return result;
	}
}
