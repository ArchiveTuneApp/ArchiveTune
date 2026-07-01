import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

abstract class GenerateIconPackTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xmlDirectory: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val targetActivityClassName: Property<String>

    @get:OutputDirectory
    abstract val resourceOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val assetOutputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val metadata = parseMetadata()
        val resourcesDirectory = resourceOutputDirectory.get().asFile
        val assetsDirectory = assetOutputDirectory.get().asFile
        val manifestFile = manifestOutputFile.get().asFile

        resourcesDirectory.deleteRecursively()
        assetsDirectory.deleteRecursively()
        resourcesDirectory.mkdirs()
        assetsDirectory.mkdirs()
        manifestFile.parentFile.mkdirs()

        val seenIds = mutableSetOf<String>()
        val seenHashes = mutableSetOf<String>()
        val catalog =
            metadata.mapIndexed { index, rawEntry ->
                val entry = rawEntry as? Map<*, *>
                    ?: throw GradleException("IconPack metadata entry $index must be an object.")
                val id = entry.requiredString("Id", index)
                val name = entry.requiredString("Name", index)
                val author = entry.requiredString("Author", index)
                val source = entry.requiredString("Source", index)
                val link = entry.optionalString("Link")
                val githubAuthorUrl = entry.optionalString("GitHubAuthorUrl")

                if (id == DefaultIconId) {
                    throw GradleException("IconPack Id \"$DefaultIconId\" is reserved.")
                }
                if (!seenIds.add(id)) {
                    throw GradleException("IconPack metadata contains duplicate Id \"$id\".")
                }

                val hash = id.sha256Prefix()
                if (!seenHashes.add(hash)) {
                    throw GradleException("IconPack generated identifier collision for Id \"$id\".")
                }

                val sourceFile = resolveSource(source)
                val backgroundColor = validateVector(sourceFile)

                val drawableName = "icon_pack_$hash"
                val targetFile = File(resourcesDirectory, "drawable/$drawableName.xml")
                targetFile.parentFile.mkdirs()
                sourceFile.copyTo(target = targetFile, overwrite = true)

                mapOf(
                    "id" to id,
                    "name" to name,
                    "author" to author,
                    "link" to link,
                    "githubAuthorUrl" to githubAuthorUrl,
                    "source" to source,
                    "drawableResourceName" to drawableName,
                    "adaptiveIconResourceName" to drawableName,
                    "backgroundColor" to backgroundColor,
                    "aliasClassName" to "${applicationId.get()}.launcher.IconAlias$hash",
                )
            }

        writeAdaptiveIconResources(resourcesDirectory, catalog)
        File(assetsDirectory, "icon_pack/catalog.json").apply {
            parentFile.mkdirs()
            val runtimeCatalog =
                catalog.map { entry ->
                    entry - setOf("adaptiveIconResourceName", "backgroundColor")
                }
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(runtimeCatalog)) + System.lineSeparator())
        }
        manifestFile.writeText(buildManifest(catalog))
    }

    private fun parseMetadata(): List<*> {
        val parsed =
            try {
                JsonSlurper().parse(metadataFile.get().asFile)
            } catch (error: Exception) {
                throw GradleException("Unable to parse IconPack metadata.json.", error)
            }
        return parsed as? List<*>
            ?: throw GradleException("IconPack metadata.json must contain a JSON array.")
    }

    private fun resolveSource(source: String): File {
        if (!source.endsWith(".xml", ignoreCase = true) ||
            source.contains('/') ||
            source.contains('\\')
        ) {
            throw GradleException("IconPack Source \"$source\" must be an XML basename.")
        }

        val root = xmlDirectory.get().asFile.canonicalFile
        val sourceFile = File(root, source).canonicalFile
        if (sourceFile.parentFile != root || !sourceFile.isFile) {
            throw GradleException("IconPack Source \"$source\" is missing from ${root.path}.")
        }
        return sourceFile
    }

    private fun validateVector(sourceFile: File): String {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val document =
            try {
                factory.newDocumentBuilder().parse(sourceFile)
            } catch (error: Exception) {
                throw GradleException("IconPack Source \"${sourceFile.name}\" is not valid XML.", error)
            }
        if (document.documentElement.localName != "vector") {
            throw GradleException("IconPack Source \"${sourceFile.name}\" must have a <vector> root.")
        }

        val paths = document.getElementsByTagName("path")
        for (index in 0 until paths.length) {
            val fillColor =
                paths
                    .item(index)
                    .attributes
                    ?.getNamedItemNS(AndroidNamespace, "fillColor")
                    ?.nodeValue
                    ?.toOpaqueColor()
            if (fillColor != null) {
                return fillColor
            }
        }
        throw GradleException(
            "IconPack Source \"${sourceFile.name}\" must contain a path with a literal, non-transparent fillColor.",
        )
    }

    private fun writeAdaptiveIconResources(
        resourcesDirectory: File,
        catalog: List<Map<String, String>>,
    ) {
        val valuesFile = File(resourcesDirectory, "values/icon_pack_colors.xml")
        valuesFile.parentFile.mkdirs()
        val colors =
            catalog.joinToString(separator = System.lineSeparator()) { entry ->
                val resourceName = entry.getValue("adaptiveIconResourceName")
                val backgroundColor = entry.getValue("backgroundColor")
                "    <color name=\"${resourceName}_background\">$backgroundColor</color>"
            }
        valuesFile.writeText(
            """
<?xml version="1.0" encoding="utf-8"?>
<resources>
$colors
</resources>
            """.trimIndent() + System.lineSeparator(),
        )

        catalog.forEach { entry ->
            val resourceName = entry.getValue("adaptiveIconResourceName")
            val drawableName = entry.getValue("drawableResourceName")
            val adaptiveIconFile = File(resourcesDirectory, "mipmap-anydpi-v26/$resourceName.xml")
            adaptiveIconFile.parentFile.mkdirs()
            adaptiveIconFile.writeText(
                """
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/${resourceName}_background" />
    <foreground android:drawable="@drawable/$drawableName" />
</adaptive-icon>
                """.trimIndent() + System.lineSeparator(),
            )
        }
    }

    private fun buildManifest(catalog: List<Map<String, String>>): String {
        val packageName = applicationId.get()
        val aliases =
            catalog.joinToString(separator = System.lineSeparator()) { entry ->
                val aliasClassName = entry.getValue("aliasClassName")
                val adaptiveIconResourceName = entry.getValue("adaptiveIconResourceName")
                """
        <activity-alias
            android:name="$aliasClassName"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/$adaptiveIconResourceName"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/$adaptiveIconResourceName"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
                """.trimEnd()
            }

        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity-alias
            android:name="$packageName.launcher.DefaultIconAlias"
            android:enabled="true"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
${aliases.prependIndent("        ")}
    </application>
</manifest>
        """.trimIndent() + System.lineSeparator()
    }

    private fun Map<*, *>.requiredString(
        key: String,
        index: Int,
    ): String =
        this[key]
            ?.let { value ->
                when (value) {
                    is String,
                    is Number,
                    -> value.toString()

                    else -> null
                }
            }?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GradleException("IconPack metadata entry $index requires a non-empty $key.")

    private fun Map<*, *>.optionalString(key: String): String =
        (this[key] as? String)?.trim().orEmpty()

    private fun String.sha256Prefix(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.toOpaqueColor(): String? {
        val value = trim()
        val argb =
            when {
                value.matches(ShortRgbColor) -> "FF${value.substring(1).map { "$it$it" }.joinToString("")}"
                value.matches(ShortArgbColor) -> value.substring(1).map { "$it$it" }.joinToString("")
                value.matches(RgbColor) -> "FF${value.substring(1)}"
                value.matches(ArgbColor) -> value.substring(1)
                else -> return null
            }
        if (argb.substring(0, 2).equals("00", ignoreCase = true)) {
            return null
        }
        return "#FF${argb.substring(2).uppercase()}"
    }

    private companion object {
        const val AndroidNamespace = "http://schemas.android.com/apk/res/android"
        const val DefaultIconId = "default"
        val ShortRgbColor = Regex("^#[0-9A-Fa-f]{3}$")
        val ShortArgbColor = Regex("^#[0-9A-Fa-f]{4}$")
        val RgbColor = Regex("^#[0-9A-Fa-f]{6}$")
        val ArgbColor = Regex("^#[0-9A-Fa-f]{8}$")
    }
}
