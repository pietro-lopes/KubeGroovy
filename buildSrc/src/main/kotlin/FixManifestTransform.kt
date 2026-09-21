import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

@CacheableTransform
abstract class FixManifestTransform : TransformAction<FixManifestTransform.Parameters> {

	interface Parameters : TransformParameters {
		@get:Input
		val manifestEntries: MapProperty<String, String>
	}

	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	@get:InputArtifact
	abstract val inputArtifact: Provider<FileSystemLocation>

	override fun transform(outputs: TransformOutputs) {
		val input = inputArtifact.get().asFile
		val output = outputs.file(input.name)

		JarFile(input).use { jar ->
			val manifest = Manifest()
			manifest.mainAttributes.putValue("Manifest-Version", "1.0")
			parameters.manifestEntries.get().forEach { (k, v) ->
				manifest.mainAttributes.putValue(k, v)
			}

			JarOutputStream(output.outputStream(), manifest).use { jos ->
				jar.entries().asSequence()
					.filter { it.name != "META-INF/MANIFEST.MF" }
					.forEach { entry ->
						jos.putNextEntry(JarEntry(entry.name))
						jar.getInputStream(entry).copyTo(jos)
						jos.closeEntry()
					}
			}
		}
	}
}