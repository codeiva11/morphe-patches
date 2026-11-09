package app.morphe.patches.instagram.misc.updater

import app.morphe.patches.instagram.misc.extension.sharedExtensionPatch
import app.morphe.patches.instagram.shared.InstagramActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = InstagramActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer Instagram releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
