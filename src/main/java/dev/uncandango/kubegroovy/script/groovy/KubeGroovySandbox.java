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
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
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
		var secure = new SecureASTCustomizer();
		secure.setClosuresAllowed(true);
		secure.setMethodDefinitionAllowed(true); // scripts are run as script bodies, no new methods needed for minimal
		secure.setPackageAllowed(true);
		// Leave import whitelists at Groovy's permissive default; the class filter and
		// class-loader checks enforce the actual script import policy.
		// Block direct receiver types that are almost always abuse vectors
		secure.setReceiversBlackList(List.of(
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
				ClassNode importNode = null;
				if (exp instanceof CastExpression cast && wrappers != null && cast.isCoerce()) {
					var isArray = cast.getType().getComponentType() != null;
					//if (wrappers.hasWrapper(resolveFullName(source, isArray ? cast.getType().getComponentType() : cast.getType()))) {
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

						return gyToJavaCall; // GeneralUtils.tryCatchS(new ExpressionStatement(cast),new EmptyStatement(), new CatchStatement(new Parameter(ClassHelper.make(GroovyCastException.class), "e"),new ExpressionStatement(gyToJavaCall)));

//						//KubeGroovy.LOGGER.info(listExpression.getText());
//						var typeInfoCall = new StaticMethodCallExpression(ClassHelper.make(TypeInfo.class), "ofList",
//							new ArgumentListExpression(listExpression));
//						var rewritten = new StaticMethodCallExpression(ClassHelper.make(TypeWrapperRegistry.class), "coerce",
//								new ArgumentListExpression(value, typeInfoCall));
//						rewritten.setSourcePosition(exp);
//						return rewritten;
					//}
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

					// Block System.exit / halt
					if (object instanceof VariableExpression var && var.getName().equals("System")
						&& ("exit".equals(method) || "halt".equals(method))) {
						throw new SecurityException("System.exit/halt is blocked in KubeGroovy scripts");
					}
					if (object instanceof ClassExpression ce && ce.getType().getName().equals("java.lang.System")
						&& ("exit".equals(method) || "halt".equals(method) || "setSecurityManager".equals(method))) {
						throw new SecurityException("System." + method + " is blocked in KubeGroovy scripts");
					}
					// Block Runtime.exec
					if ("exec".equals(method) && object.getText() != null && object.getText().contains("Runtime")) {
						throw new SecurityException("Runtime.exec is blocked in KubeGroovy scripts");
					}
					// Block reflection setAccessible
					if ("setAccessible".equals(method)) {
						throw new SecurityException("Reflection setAccessible is blocked in KubeGroovy scripts");
					}
					// Block Class.forName on disallowed handled at classloader, but block defineClass/loadClass
					if (("defineClass".equals(method) || "loadClass".equals(method) || "findClass".equals(method))) {
						throw new SecurityException("ClassLoader." + method + " is blocked in KubeGroovy scripts");
					}
				}

				if (exp instanceof StaticMethodCallExpression smc) {
					String owner = smc.getOwnerType().getName();
					String method = smc.getMethod();
					if (owner.equals("java.lang.System") && ("exit".equals(method) || "halt".equals(method))) {
						throw new SecurityException("System." + method + " is blocked in KubeGroovy scripts");
					}
					if (owner.equals("java.lang.Runtime") && "getRuntime".equals(method)) {
						// Allow getRuntime but exec blocked above; keep tight: block entirely for minimal
						throw new SecurityException("Runtime access is blocked in KubeGroovy scripts");
					}
					if (owner.equals("java.lang.Class") && "forName".equals(method)) {
						// Check constant arg against filter when statically known
						var args = smc.getArguments();
						if (args instanceof ArgumentListExpression ale && !ale.getExpressions().isEmpty()
							&& ale.getExpressions().get(0) instanceof ConstantExpression ce
							&& ce.getValue() instanceof String s && filter != null && !filter.isAllowed(s)) {
							throw new SecurityException("Class.forName(" + s + ") blocked by class filter");
						}
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
			public void call(SourceUnit source, org.codehaus.groovy.classgen.GeneratorContext context, ClassNode classNode) {
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

		return List.of(secure, transformerCustomizer);
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
