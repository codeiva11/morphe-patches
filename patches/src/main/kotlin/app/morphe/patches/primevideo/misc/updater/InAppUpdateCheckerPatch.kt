package app.morphe.patches.primevideo.misc.updater

import app.morphe.patches.primevideo.misc.extension.sharedExtensionPatch
import app.morphe.patches.primevideo.shared.PrimeVideoActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = PrimeVideoActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer Prime Video releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
