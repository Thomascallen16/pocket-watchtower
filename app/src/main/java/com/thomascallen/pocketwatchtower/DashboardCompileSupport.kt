package com.thomascallen.pocketwatchtower

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import java.security.MessageDigest

internal fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

fun Modifier.verticalScroll(state: ScrollState): Modifier =
    androidx.compose.foundation.verticalScroll(this, state)
