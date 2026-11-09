package app.morphe.patches.instagram.shared

import app.morphe.patcher.Fingerprint

internal const val INSTAGRAM_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/instagram/mainactivity/InstagramMainActivity;"

internal object InstagramActivityOnCreateFingerprint : Fingerprint(
    definingClass = INSTAGRAM_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
