package com.thomascallen.pocketwatchtower

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * Owner-controlled remediation helpers.
 *
 * Watchtower never silently destroys data or pretends that Android granted it
 * more authority than it actually has. Where Android permits background
 * process termination, Watchtower exposes that as an explicit owner action.
 * Full force-stop remains Android system UI unless Watchtower has a management
 * role that legitimately permits stronger controls.
 */
internal data class OwnerActionResult(
    val title: String,
    val detail: String,
    val completed: Boolean
)

internal class OwnerActions(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun appDetailsIntent(packageName: String): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName")
    )

    fun endBackgroundProcesses(packageName: String): OwnerActionResult = runCatching {
        if (packageName == context.packageName) {
            return OwnerActionResult(
                "Watchtower cannot end itself",
                "Use Android's Force stop control for Watchtower itself. This action is reserved so the observatory cannot accidentally terminate its own evidence UI mid-operation.",
                false
            )
        }
        activityManager.killBackgroundProcesses(packageName)
        OwnerActionResult(
            "Background process stop requested",
            "Android accepted Watchtower's request to stop background processes for $packageName. This is not the same as a guaranteed full force-stop; Android may restart components according to its lifecycle rules.",
            true
        )
    }.getOrElse {
        OwnerActionResult(
            "Process stop unavailable",
            "Android rejected the background-process stop request: ${it.message ?: "unknown error"}",
            false
        )
    }

    fun forceStopGuidance(packageName: String): OwnerActionResult = OwnerActionResult(
        "Force-stop control",
        "Android does not give an ordinary app a general-purpose silent force-stop API. Watchtower can open this app's system controls so the owner can review and use Force stop there.",
        false
    )

    fun uninstallGuidance(packageName: String): OwnerActionResult = OwnerActionResult(
        "Uninstall control",
        "Watchtower will hand the package to Android's app controls. The owner confirms the uninstall in system UI; Watchtower does not silently remove applications.",
        false
    )

    fun suspendIfDeviceOwner(packageName: String): OwnerActionResult {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return OwnerActionResult(
                "Suspension unavailable",
                "Watchtower is not the device owner. Android reserves package suspension for device/profile-owner management or an authorized delegate.",
                false
            )
        }
        return runCatching {
            val failed = dpm.setPackagesSuspended(null, arrayOf(packageName), true)
            if (failed.isNullOrEmpty()) {
                OwnerActionResult("Package suspended", "$packageName was suspended by Android device policy.", true)
            } else {
                OwnerActionResult("Package not suspended", "Android declined suspension for: ${failed.joinToString()}", false)
            }
        }.getOrElse {
            OwnerActionResult("Package not suspended", "Android rejected the operation: ${it.message ?: "unknown error"}", false)
        }
    }

    fun unsuspendIfDeviceOwner(packageName: String): OwnerActionResult {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return OwnerActionResult("Unsuspend unavailable", "Only a device/profile owner or authorized delegate can reverse a policy suspension.", false)
        }
        return runCatching {
            val failed = dpm.setPackagesSuspended(null, arrayOf(packageName), false)
            if (failed.isNullOrEmpty()) {
                OwnerActionResult("Package resumed", "$packageName is no longer suspended by Watchtower policy.", true)
            } else {
                OwnerActionResult("Package remains suspended", "Android declined resume for: ${failed.joinToString()}", false)
            }
        }.getOrElse {
            OwnerActionResult("Package remains suspended", "Android rejected the operation: ${it.message ?: "unknown error"}", false)
        }
    }

    fun deleteSelectedDocument(uri: Uri): OwnerActionResult = runCatching {
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (deleted) {
            OwnerActionResult("File deleted", "The selected document provider accepted the delete request.", true)
        } else {
            OwnerActionResult("File not deleted", "The document provider did not confirm deletion.", false)
        }
    }.getOrElse {
        OwnerActionResult("File not deleted", "Android/document provider rejected the request: ${it.message ?: "unknown error"}", false)
    }

    fun clearWatchtowerCache(): OwnerActionResult = runCatching {
        val cache: File = context.cacheDir
        val count = cache.walkTopDown().filter { it.isFile }.count()
        cache.deleteRecursively()
        cache.mkdirs()
        OwnerActionResult("Watchtower cache cleaned", "Removed $count Watchtower-owned cache file(s). Evidence history is stored separately and was not targeted.", true)
    }.getOrElse {
        OwnerActionResult("Cache cleanup incomplete", "Android/filesystem rejected the cleanup: ${it.message ?: "unknown error"}", false)
    }

    fun storageSettingsIntent(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    fun actionPolicyText(): String = if (dpm.isDeviceOwnerApp(context.packageName)) {
        "DEVICE OWNER: Watchtower may use Android device-policy controls such as package suspension. Every destructive action remains owner-visible."
    } else {
        "STANDARD APP: Watchtower can end background processes where Android permits it, and can hand full force-stop/uninstall actions to Android system UI."
    }
}

/** System UI entry points used by the Owner Action Center. */
internal object OwnerActionIntents {
    fun openDocumentPicker(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    fun uninstall(packageName: String): Intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:$packageName")
    }
}
