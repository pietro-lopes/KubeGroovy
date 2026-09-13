package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.script.ScriptManager;
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
		super(parent, makeConfig(manager));
		this.manager = manager;
	}

	private static CompilerConfiguration makeConfig(ScriptManager manager) {
		var config = new CompilerConfiguration();
		config.setScriptBaseClass(KubeGroovyScript.class.getName());

		var imports = new ImportCustomizer();
		// Common static helpers available without qualification in scripts
		// (kept minimal for now; bindings provide console/events/etc.)
		config.addCompilationCustomizers(imports);

		ClassFilter filter = manager == null ? null : manager.getClassFilter();
		for (CompilationCustomizer c : KubeGroovySandbox.customizers(filter)) {
			config.addCompilationCustomizers(c);
		}

		return config;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (manager != null && !manager.isClassAllowed(name)) {
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
		if (name != null && manager != null && !manager.isClassAllowed(name)) {
			// Allow core JDK + our own API; filter decides
			throw new ClassNotFoundException("Class not allowed by class filter: " + name);
		}
		return super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
	}
}
