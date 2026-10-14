package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.WithScriptContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * GroovyClassLoader with KubeGroovy sandbox customizers and class-filter enforcement.
 */
public class KubeGroovyClassLoader extends GroovyClassLoader implements WithScriptContext {
	private final ScriptManager manager;

	public KubeGroovyClassLoader(ClassLoader parent, ScriptManager manager) {
		this(parent, manager, makeConfig(manager, null));
	}

	public KubeGroovyClassLoader(ClassLoader parent, ScriptManager manager, CompilerConfiguration config) {
		super(parent, config);
		this.manager = manager;
	}

	public static CompilerConfiguration makeConfig(ScriptManager manager, TypeWrappers wrappers) {
		var config = new CompilerConfiguration();
		config.setScriptBaseClass(KubeGroovyScript.class.getName());
		// Remapped interface accessors are registered as MetaBeanProperties during
		// metaclass initialization, so invokedynamic can cache the bridge path too.
		config.getOptimizationOptions().put("indy", true);
		config.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation"));

		var imports = new ImportCustomizer();
		// Common static helpers available without qualification in scripts
		// (kept minimal for now; bindings provide console/events/etc.)
		for (var typeBinding : manager.getTypingBindings()) {
			if (typeBinding.classValue() && manager.scriptType == typeBinding.scriptType()) {
				imports.addImport(typeBinding.name(), typeBinding.typeName());
			}
		}
		config.addCompilationCustomizers(imports);

		ClassFilter filter = manager == null ? null : manager.getClassFilter();
		for (CompilationCustomizer c : KubeGroovySandbox.customizers(filter, wrappers)) {
			config.addCompilationCustomizers(c);
		}

		return config;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (manager != null && !isEngineClass(name) && !manager.isClassAllowed(name)) {
			throw new ClassNotFoundException("Class not allowed by class filter: " + name);
		}
		return super.findClass(name);
	}

	@Override
	public Class<?> loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve) throws ClassNotFoundException, CompilationFailedException {
		if (name != null && manager != null && !isEngineClass(name) && !manager.isClassAllowed(name)) {
			// Apply the shared script policy.
			throw new ClassNotFoundException("Class not allowed by class filter: " + name);
		}
		return super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
	}

	private static boolean isEngineClass(String name) {
		return name.equals(KubeGroovyScript.class.getName())
			|| name.equals(SandboxRuntime.class.getName())
			|| Set.of("org.codehaus.groovy.vmplugin.v8.IndyInterface", "org.codehaus.groovy.reflection.ClassInfo", "org.codehaus.groovy.runtime.InvokerHelper", "org.codehaus.groovy.runtime.ScriptBytecodeAdapter").contains(name)
			|| name.startsWith("org.codehaus.groovy.transform.") && compilerTransformLoad(name)
			|| name.equals("dev.uncandango.kubegroovy.script.TypeWrapperRegistry")
			// Groovy's MetaClass six-argument dispatch signature references Class.
			// This is an engine linkage type; script-facing Class access remains
			// checked by the member policy and AST checks.
			|| name.equals("java.lang.Class")
			|| name.equals("java.lang.ref.SoftReference");
	}

	private static boolean compilerTransformLoad(String name) {
		// These processors also stay blocked when reached through a collected/aliased annotation.
		if (Set.of("org.codehaus.groovy.transform.ASTTestTransformation",
			"org.codehaus.groovy.transform.StaticTypesTransformation", "org.codehaus.groovy.transform.sc.StaticCompileTransformation").contains(name)) return false;
		return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
			frame.getClassName().equals("org.codehaus.groovy.transform.ASTTransformationCollectorCodeVisitor")));
	}

	@Override
	public @Nullable Context cx() {
		return manager.context;
	}
}
