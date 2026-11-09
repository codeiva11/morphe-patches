package app.morphe.patches.facebook.misc.updater

import app.morphe.patches.facebook.misc.extension.sharedExtensionPatch
import app.morphe.patches.facebook.shared.FacebookActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = FacebookActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer Facebook releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
