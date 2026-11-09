package app.morphe.patches.instagram.shared

import app.morphe.patcher.Fingerprint

// InstagramMainActivity inherits onCreate from this base activity and does not
// declare it itself, so the fingerprint must target the base class (same
// pattern Piko uses for its own Instagram patches).
internal const val INSTAGRAM_BASE_ACTIVITY_CLASS_TYPE = "Lcom/instagram/base/activity/IgFragmentActivity;"

internal object InstagramActivityOnCreateFingerprint : Fingerprint(
    definingClass = INSTAGRAM_BASE_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
