package app.morphe.patches.protonvpn.shared

import app.morphe.patcher.Fingerprint

internal const val PROTON_VPN_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/protonvpn/android/redesign/app/ui/MainActivity;"

internal object ProtonVpnActivityOnCreateFingerprint : Fingerprint(
    definingClass = PROTON_VPN_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
