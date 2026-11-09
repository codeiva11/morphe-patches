package app.morphe.patches.protonvpn.misc.updater

import app.morphe.patches.protonvpn.misc.extension.sharedExtensionPatch
import app.morphe.patches.protonvpn.shared.ProtonVpnActivityOnCreateFingerprint
import app.morphe.patches.shared.misc.updater.inAppUpdateCheckerPatch

@Suppress("unused")
val inAppUpdateCheckerPatch = inAppUpdateCheckerPatch(
    mainActivityOnCreateFingerprint = ProtonVpnActivityOnCreateFingerprint,
    extensionPatch = sharedExtensionPatch,
    name = "Enable in-app update checker",
    description = "Checks for newer Proton VPN releases on GitHub and silently pre-downloads them for instant installation.",
    default = false,
)
