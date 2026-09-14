package com.thomascallen.pocketwatchtower

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll as foundationVerticalScroll
import androidx.compose.ui.Modifier

/** Compose scrolling support for the dashboard. */
fun Modifier.verticalScroll(state: ScrollState): Modifier =
    foundationVerticalScroll(state)
