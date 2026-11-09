package app.morphe.patches.protonmail.misc.updater

import app.morphe.patches.protonmail.misc.extension.sharedExtensionPatch
import app.morphe.patches.protonmail.shared.ProtonMailActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = ProtonMailActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer Proton Mail releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
