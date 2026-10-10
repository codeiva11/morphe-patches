package app.morphe.patches.shared.misc.updater

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption

internal fun inAppUpdateCheckerResourcePatch() = resourcePatch {
    execute {
        val manifestFile = get("AndroidManifest.xml")
        val manifestContent = manifestFile.readText()
        val permTag = "<uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\"/>"
        if (!manifestContent.contains(permTag)) {
            manifestFile.writeText(
                manifestContent.replace("<application", "$permTag\n    <application")
            )
        }
    }
}

fun inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint: Fingerprint,
    extensionPatch: Patch<*>,
    name: String = "Enable in-app update checker",
    description: String = "Checks for newer releases on GitHub and silently pre-downloads them for instant installation.",
    block: BytecodePatchBuilder.() -> Unit = {},
) = bytecodePatch(
    name = name,
    description = description,
    default = true,
) {
    block()

    dependsOn(inAppUpdateCheckerResourcePatch())
    dependsOn(extensionPatch)

    val releaseApiUrl = stringOption(
        key = "releaseApiUrl",
        default = "https://api.github.com/repos/codeiva11/Morphe-AutoBuilds/releases/tags/latest",
        title = "GitHub Release API URL",
        description = "Endpoint to check for the latest patched APK release.",
        required = true,
    )

    execute {
        val targetUrl = releaseApiUrl.value ?: releaseApiUrl.default ?: "https://api.github.com/repos/codeiva11/Morphe-AutoBuilds/releases/tags/latest"
        mainActivityOnCreateFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "$targetUrl"
                invoke-static {p0, v0}, Lapp/morphe/extension/shared/updater/GitHubReleaseChecker;->checkUpdateOnStartup(Landroid/content/Context;Ljava/lang/String;)V
            """.trimIndent()
        )
    }
}
