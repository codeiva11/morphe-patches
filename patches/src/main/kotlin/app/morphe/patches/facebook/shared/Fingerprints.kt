package app.morphe.patches.facebook.shared

import app.morphe.patcher.Fingerprint

internal const val FACEBOOK_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/facebook/katana/activity/FbMainTabActivity;"

internal object FacebookActivityOnCreateFingerprint : Fingerprint(
    definingClass = FACEBOOK_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
