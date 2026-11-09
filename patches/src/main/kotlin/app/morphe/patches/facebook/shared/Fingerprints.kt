package app.morphe.patches.facebook.shared

import app.morphe.patcher.Fingerprint

// FbMainTabActivity inherits onCreate from this base activity and does not
// declare it itself, so the fingerprint must target the base class (the
// activity base De-Vanced itself uses for its Facebook patches).
internal const val FACEBOOK_BASE_ACTIVITY_CLASS_TYPE = "Lcom/facebook/base/activity/FbFragmentActivity;"

internal object FacebookActivityOnCreateFingerprint : Fingerprint(
    definingClass = FACEBOOK_BASE_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)
