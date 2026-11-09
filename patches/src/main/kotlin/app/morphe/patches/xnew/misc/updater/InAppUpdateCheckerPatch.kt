package app.morphe.patches.xnew.misc.updater

import app.morphe.patches.xnew.misc.extension.sharedExtensionPatch
import app.morphe.patches.xnew.shared.XActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = XActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer X releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
