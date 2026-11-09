package app.morphe.patches.facebook.misc.extension

import app.morphe.patches.facebook.shared.FACEBOOK_MAIN_ACTIVITY_CLASS_TYPE
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

/**
 * Extends Facebook with the shared extension, so that the in-app update checker
 * (GitHubReleaseChecker) is available and receives the application context
 * at the start of the main activity's onCreate.
 */
val sharedExtensionPatch = sharedExtensionPatch(
    activityOnCreateExtensionHook(FACEBOOK_MAIN_ACTIVITY_CLASS_TYPE, true)
)
