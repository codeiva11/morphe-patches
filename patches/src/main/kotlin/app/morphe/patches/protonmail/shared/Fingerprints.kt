package app.morphe.patches.protonmail.shared

import app.morphe.patcher.Fingerprint

internal const val PROTON_MAIL_MAIN_ACTIVITY_CLASS_TYPE = "Lch/protonmail/android/MainActivity;"

internal object ProtonMailActivityOnCreateFingerprint : Fingerprint(
    definingClass = PROTON_MAIL_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
