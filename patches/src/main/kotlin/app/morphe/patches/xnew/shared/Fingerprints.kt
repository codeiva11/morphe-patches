package app.morphe.patches.xnew.shared

import app.morphe.patcher.Fingerprint

internal const val X_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/x/android/main/MainActivity;"

internal object XActivityOnCreateFingerprint : Fingerprint(
    definingClass = X_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
