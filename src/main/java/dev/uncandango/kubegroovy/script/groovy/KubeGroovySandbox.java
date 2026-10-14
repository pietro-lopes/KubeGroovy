package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.script.TypeWrapperRegistry;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.ParameterUtils;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Sandbox for KubeGroovy scripts.
 * Blocks dangerous calls (System.exit, Runtime.exec, reflection setAccessible, ClassLoader tricks)
 * and enforces ClassFilter on direct class references.
 */
public final class KubeGroovySandbox {
	private KubeGroovySandbox() {
	}

	public static List<CompilationCustomizer> customizers(@Nullable ClassFilter filter) {
		return customizers(filter, null);
	}

	public static String resolveFullName(SourceUnit source, ClassNode node){
		var isArray = node.getComponentType() != null;
		if (isArray) throw new IllegalArgumentException("Array types are not supported");
		var nodeName = node.getName();
		String result = null;
		String visited = null;
		for (var partialName : nodeName.split("\\.")) {
			if (result != null) {
				result += "." + partialName;
			}
			if (visited == null) {
				visited = partialName;
			} else {
				visited += "." + partialName;
			}
			var fromImport = source.getAST().getImportType(visited);
			if (fromImport != null) {
				result = fromImport.getName();
			}
		}
		return result != null ? result : nodeName;
	}

	public static List<CompilationCustomizer> customizers(@Nullable ClassFilter filter, @Nullable TypeWrappers wrappers) {
		var secure = new SecureASTCustomizer() {
			@Override
			protected GroovyCodeVisitor createGroovyCodeVisitor() {
				return new SecuringCodeVisitor() {
					@Override
					protected void assertExpressionAuthorized(Expression expression) {
						// Groovy emits this main entrypoint; authored InvokerHelper access remains prohibited.
						if (expression.getLineNumber() < 0 && expression instanceof StaticMethodCallExpression call
							&& call.getOwnerType().getName().equals("org.codehaus.groovy.runtime.InvokerHelper") && call.getMethod().equals("runScript")) return;
						super.assertExpressionAuthorized(expression);
					}
				};
			}
			@Override
			protected void assertImportIsAllowed(String name) {
				if (filter != null && !filter.isAllowed(name)) throw SandboxRuntime.blocked("import " + name);
			}
			@Override
			protected void assertStaticImportIsAllowed(String member, String owner) {
				assertImportIsAllowed(owner);
				if (filter != null && member != null && !filter.isGlobalMethodNameAllowed(member)) throw SandboxRuntime.blocked("static import " + member);
			}
		};
		if (filter != null) secure.addExpressionCheckers(expression -> {
			if (expression.getLineNumber() < 0) return true;
			ClassNode type = expression instanceof ClassExpression ce ? ce.getType()
				: expression instanceof ConstructorCallExpression cc ? cc.getType()
				: expression instanceof CastExpression cast ? cast.getType() : null;
			if (type != null) {
				while (type.isArray()) type = type.getComponentType();
				if (!filter.isAllowed(type.getName())) return false;
			}
			return !(expression instanceof MethodCallExpression call) || call.getMethodAsString() == null || filter.isGlobalMethodNameAllowed(call.getMethodAsString());
		});
		secure.setClosuresAllowed(true);
		secure.setMethodDefinitionAllowed(true);
		secure.setPackageAllowed(true);
		secure.setIndirectImportCheckEnabled(true);
		// Leave import whitelists at Groovy's permissive default; the class filter and
		// class-loader checks enforce the actual script import policy.
		// Block direct receiver types that are almost always abuse vectors
		secure.setDisallowedReceivers(List.of(
			"java.lang.System",
			"java.lang.Runtime",
			"java.lang.ProcessBuilder",
			"java.lang.ClassLoader",
			"java.lang.ThreadGroup"
		));



		var transformer = new ClassCodeExpressionTransformer() {
			private SourceUnit source;

			public void setSource(SourceUnit source) {
				this.source = source;
			}

			@Override
			protected SourceUnit getSourceUnit() {
				return source;
			}

			@Override
			public Expression transform(Expression exp) {
				// Groovy's ClassCodeExpressionTransformer intentionally does not descend
				// into closures. Script callbacks contain most of the public API calls,
				// so explicitly visit their body before handling the enclosing expression.
				if (exp instanceof ClosureExpression closure) {
					closure.visit(this);
					return exp;
				}
				if (exp instanceof CastExpression cast && wrappers != null && cast.isCoerce()) {
					var value = transform(cast.getExpression());

					var listExpression = new ListExpression();
					collectClassList(cast.getType(), listExpression);
					var typeInfoCall = new StaticMethodCallExpression(ClassHelper.make(TypeInfo.class), "ofList",
						new ArgumentListExpression(listExpression));
					var currentContextCall = new StaticMethodCallExpression(ClassHelper.make(Context.class), "from",
						new ArgumentListExpression(new VariableExpression("this")));
					var gyToJavaCall = new MethodCallExpression(currentContextCall, "gyToJava",
						new ArgumentListExpression(value, typeInfoCall));
					gyToJavaCall.setSourcePosition(exp);

					return gyToJavaCall;
				}
				if (exp instanceof MethodCallExpression call) {
					String method = call.getMethodAsString();
					Expression object = call.getObjectExpression();
					if ("asType".equals(method) && wrappers != null && call.getArguments() instanceof TupleExpression args
						&& args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClassExpression target) {
						var value = object;
						var listExpression = new ListExpression();
						collectClassList(target.getType(), listExpression);
						var typeInfoCall = new StaticMethodCallExpression(ClassHelper.make(TypeInfo.class), "ofList",
							new ArgumentListExpression(listExpression));
						var currentContextCall = new StaticMethodCallExpression(ClassHelper.make(Context.class), "getCurrent",
							new ArgumentListExpression());
						var gyToJavaCall = new MethodCallExpression(currentContextCall, "gyToJava",
								new ArgumentListExpression(value,typeInfoCall));
						gyToJavaCall.setSourcePosition(exp);
						return gyToJavaCall;
					}
				}

				if (exp instanceof ClassExpression ce && filter != null) {
					String name = ce.getType().getName();
					if (!filter.isAllowed(name)) {
						throw new SecurityException("Class blocked by class filter: " + name);
					}
				}

				return super.transform(exp);
			}
		};

		CompilationCustomizer transformerCustomizer = new CompilationCustomizer(CompilePhase.CONVERSION) {
			@Override
			public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
				transformer.setSource(source);
				// Script statements are still held by the module at this phase; visiting
				// the statement block directly ensures expressions in closures and script
				// initializers are transformed before Groovy emits the run method.
				if (source.getAST().getStatementBlock() != null) {
					source.getAST().getStatementBlock().visit(transformer);
				}
				transformer.visitClass(classNode);
			}
		};

		return filter == null ? List.of(secure, transformerCustomizer)
			: List.of(SandboxCompilationCustomizer.annotations(filter), secure, transformerCustomizer, new SandboxCompilationCustomizer(filter));
	}

	private static void collectClassList(ClassNode type, ListExpression listExpression) {
		// Class literals use the erased type; Groovy 6 rejects List<String>.class.
		// Keep the generic arguments in the following list for TypeInfo.ofList.
		listExpression.addExpression(new ClassExpression(type.getPlainNodeReference()));
		if (type.isUsingGenerics()) {
			var genericsList = new ListExpression();
			listExpression.addExpression(genericsList);
			for (var generics : type.getGenericsTypes()) {
				collectClassList(generics.getType().getName().equals("?") ? ClassHelper.OBJECT_TYPE : generics.getType(), genericsList);
			}
		}
	}
}
