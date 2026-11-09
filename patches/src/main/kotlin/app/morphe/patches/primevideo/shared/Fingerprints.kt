package app.morphe.patches.primevideo.shared

import app.morphe.patcher.Fingerprint

internal const val PRIME_VIDEO_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/amazon/avod/client/activity/SplashScreenActivity;"

internal object PrimeVideoActivityOnCreateFingerprint : Fingerprint(
    definingClass = PRIME_VIDEO_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
