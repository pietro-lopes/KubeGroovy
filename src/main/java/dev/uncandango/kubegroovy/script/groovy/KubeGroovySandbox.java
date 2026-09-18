package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.script.TypeWrapperRegistry;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
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
				if (exp instanceof CastExpression cast && wrappers != null && wrappers.hasWrapper((importNode = source.getAST().getImportType(cast.getType().getName())) == null ? cast.getType().getName() : importNode.getName())) {
					var value = transform(cast.getExpression());
					var rewritten = new StaticMethodCallExpression(ClassHelper.make(TypeWrapperRegistry.class), "coerce",
						new ArgumentListExpression(value, new ClassExpression(cast.getType())));
					rewritten.setSourcePosition(exp);
					return rewritten;
				}
				if (exp instanceof MethodCallExpression call) {
					String method = call.getMethodAsString();
					Expression object = call.getObjectExpression();
					if ("asType".equals(method) && wrappers != null && call.getArguments() instanceof TupleExpression args
						&& args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClassExpression target
						&& wrappers.hasWrapper((importNode = source.getAST().getImportType(target.getType().getName())) == null ? target.getType().getName() : importNode.getName())) {
						var rewritten = new StaticMethodCallExpression(ClassHelper.make(TypeWrapperRegistry.class), "coerce",
							new ArgumentListExpression(transform(object), new ClassExpression(target.getType())));
						rewritten.setSourcePosition(exp);
						return rewritten;
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
}
