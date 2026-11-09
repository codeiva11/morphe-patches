package app.morphe.patches.protonvpn.misc.extension

import app.morphe.patches.protonvpn.shared.PROTON_VPN_MAIN_ACTIVITY_CLASS_TYPE
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

/**
 * Extends Proton VPN with the shared extension, so that the in-app update checker
 * (GitHubReleaseChecker) is available and receives the application context
 * at the start of the main activity's onCreate.
 */
val sharedExtensionPatch = sharedExtensionPatch(
    activityOnCreateExtensionHook(PROTON_VPN_MAIN_ACTIVITY_CLASS_TYPE, true)
)
