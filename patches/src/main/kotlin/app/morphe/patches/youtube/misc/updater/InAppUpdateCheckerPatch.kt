package app.morphe.patches.youtube.misc.updater

import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.YouTubeActivityOnCreateFingerprint

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = YouTubeActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer YouTube releases on GitHub and silently pre-downloads them for instant installation.",
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)
}
