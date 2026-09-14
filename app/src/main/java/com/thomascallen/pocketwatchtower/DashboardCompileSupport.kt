package com.thomascallen.pocketwatchtower

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll as foundationVerticalScroll
import androidx.compose.ui.Modifier
import java.security.MessageDigest

/** Compose scrolling support for the dashboard. */
fun Modifier.verticalScroll(state: ScrollState): Modifier =
    foundationVerticalScroll(state)

internal fun dashboardSha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
