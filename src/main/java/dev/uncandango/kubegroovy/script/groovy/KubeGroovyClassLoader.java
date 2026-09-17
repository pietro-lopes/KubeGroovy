package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

/**
 * GroovyClassLoader with KubeGroovy sandbox customizers and class-filter enforcement.
 */
public class KubeGroovyClassLoader extends GroovyClassLoader {
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
		// The bridge relies on Groovy's registry MetaClass dispatch for dynamic
		// properties and methods.  Indy call sites cache the vanilla Java path and
		// bypass that registry after injected interfaces are installed.
		config.getOptimizationOptions().put("indy", false);

		var imports = new ImportCustomizer();
		// Common static helpers available without qualification in scripts
		// (kept minimal for now; bindings provide console/events/etc.)
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
		// Enforce class filter for reflective loads as well
		if (name != null && manager != null && (name.startsWith("java.lang.reflect.") || name.startsWith("java.lang.invoke."))) {
			// Allow the packages themselves but sandbox transformer blocks setAccessible etc.
		}
		if (name != null && manager != null && !isEngineClass(name) && !manager.isClassAllowed(name)) {
			// Allow core JDK + our own API; filter decides
			throw new ClassNotFoundException("Class not allowed by class filter: " + name);
		}
		return super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
	}

	private static boolean isEngineClass(String name) {
		return name.equals(KubeGroovyScript.class.getName())
			|| name.equals("dev.uncandango.kubegroovy.script.TypeWrapperRegistry")
			// Groovy's MetaClass six-argument dispatch signature references Class.
			// This is an engine linkage type; script-facing Class access remains
			// blocked by the class filter and AST checks.
			|| name.equals("java.lang.Class")
			|| name.equals("java.lang.ref.SoftReference");
	}
}
