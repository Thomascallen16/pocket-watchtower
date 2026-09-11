package com.thomascallen.pocketwatchtower

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root is not an Android runtime permission. This class therefore models it as
 * an explicit owner consent + observable capability, never as a hidden grant.
 *
 * No exploit, bypass, persistence mechanism, or privilege escalation is used.
 * If a root manager is present, the normal `su` authorization flow remains in
 * control of the device owner.
 */
enum class RootConsentState {
    NOT_ACCEPTED,
    ACCEPTED,
    REVOKED
}

enum class RootCapabilityState {
    AVAILABLE,
    NOT_AVAILABLE,
    DENIED,
    UNKNOWN
}

data class RootAccessStatus(
    val consent: RootConsentState,
    val capability: RootCapabilityState,
    val detail: String
)

class RootAccessController(context: Context) {
    private val prefs = context.getSharedPreferences("watchtower_root", Context.MODE_PRIVATE)

    fun status(): RootAccessStatus {
        val consent = when (prefs.getString(KEY_CONSENT, null)) {
            "accepted" -> RootConsentState.ACCEPTED
            "revoked" -> RootConsentState.REVOKED
            else -> RootConsentState.NOT_ACCEPTED
        }
        if (consent != RootConsentState.ACCEPTED) {
            return RootAccessStatus(consent, RootCapabilityState.UNKNOWN, "Root access has not been requested by the owner.")
        }
        return probe()
    }

    fun acceptOwnerConsent() {
        prefs.edit().putString(KEY_CONSENT, "accepted").apply()
    }

    fun revokeOwnerConsent() {
        prefs.edit().putString(KEY_CONSENT, "revoked").apply()
    }

    /**
     * Performs a minimal identity probe only after explicit owner consent.
     * The command is intentionally limited to `id`; it does not modify the
     * device or attempt to bypass a root manager's authorization decision.
     */
    fun probe(): RootAccessStatus {
        if (prefs.getString(KEY_CONSENT, null) != "accepted") {
            return RootAccessStatus(RootConsentState.NOT_ACCEPTED, RootCapabilityState.UNKNOWN, "Owner consent is required before a root probe.")
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }.trim()
            val exit = process.waitFor()
            when {
                exit == 0 && output.contains("uid=0") -> RootAccessStatus(
                    RootConsentState.ACCEPTED,
                    RootCapabilityState.AVAILABLE,
                    "Root authorization succeeded for the identity probe. No device changes were performed."
                )
                exit != 0 -> RootAccessStatus(
                    RootConsentState.ACCEPTED,
                    RootCapabilityState.DENIED,
                    if (error.isBlank()) "The root authorization request was not granted." else "The root authorization request was not granted: $error"
                )
                else -> RootAccessStatus(
                    RootConsentState.ACCEPTED,
                    RootCapabilityState.UNKNOWN,
                    "A privileged response was returned, but Watchtower could not verify uid=0."
                )
            }
        } catch (_: Exception) {
            RootAccessStatus(
                RootConsentState.ACCEPTED,
                RootCapabilityState.NOT_AVAILABLE,
                "No usable root authorization interface was available."
            )
        }
    }

    companion object {
        private const val KEY_CONSENT = "root_consent"
    }
}
