package com.thomascallen.pocketwatchtower

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings

internal data class ObservatoryItem(val section: String, val name: String, val value: String, val status: String = "OBSERVED")

internal class DeviceObservatory(private val context: Context) {
    private val pm = context.packageManager

    fun collect(): List<ObservatoryItem> = buildList {
        add(ObservatoryItem("Device", "Manufacturer / model", "${Build.MANUFACTURER} ${Build.MODEL}"))
        add(ObservatoryItem("Device", "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        add(ObservatoryItem("Security", "Security patch", Build.VERSION.SECURITY_PATCH.ifBlank { "Not exposed" }))
        add(ObservatoryItem("Device", "Architecture", Build.SUPPORTED_ABIS.joinToString()))
        add(ObservatoryItem("Device", "Build fingerprint", Build.FINGERPRINT, "KNOWN"))
        addAll(memory())
        addAll(storage())
        addAll(battery())
        addAll(network())
        addAll(sensors())
        addAll(access())
        addAll(apps())
    }

    private fun memory(): List<ObservatoryItem> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return listOf(
            ObservatoryItem("Memory", "Total RAM", formatBytes(info.totalMem)),
            ObservatoryItem("Memory", "Available RAM", formatBytes(info.availMem)),
            ObservatoryItem("Memory", "Low-memory state", info.lowMemory.toString())
        )
    }

    private fun storage(): List<ObservatoryItem> {
        val stat = StatFs(Environment.getDataDirectory().path)
        return listOf(
            ObservatoryItem("Storage", "Total", formatBytes(stat.totalBytes)),
            ObservatoryItem("Storage", "Available", formatBytes(stat.availableBytes)),
            ObservatoryItem("Storage", "Used", formatBytes(stat.totalBytes - stat.availableBytes))
        )
    }

    private fun battery(): List<ObservatoryItem> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return listOf(
            ObservatoryItem("Battery", "Charge", if (pct >= 0) "$pct%" else "Not exposed"),
            ObservatoryItem("Battery", "Current", if (current != Int.MIN_VALUE) "$current µA" else "Not exposed")
        )
    }

    private fun network(): List<ObservatoryItem> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities)
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val transport = when {
            vpn -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline / unknown"
        }
        return listOf(
            ObservatoryItem("Network", "Active transport", transport),
            ObservatoryItem("Network", "VPN transport", vpn.toString())
        )
    }

    private fun sensors(): List<ObservatoryItem> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        return listOf(
            ObservatoryItem("Sensors", "Exposed sensors", sensors.size.toString()),
            ObservatoryItem("Sensors", "Sensor names", sensors.take(20).joinToString("; ") { it.name })
        )
    }

    private fun access(): List<ObservatoryItem> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val usage = runCatching {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val notification = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val overlay = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else false
        val admins = runCatching { dpm.activeAdmins?.size ?: 0 }.getOrDefault(0)
        val thisAppOwner = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        return listOf(
            ObservatoryItem("Access", "Device administrators visible", admins.toString(), "OBSERVED"),
            ObservatoryItem("Access", "This app is device owner", thisAppOwner.toString()),
            ObservatoryItem("Access", "Accessibility services enabled", if (accessibility.isBlank()) "None observed" else accessibility, "OBSERVED"),
            ObservatoryItem("Access", "Notification listeners enabled", if (notification.isBlank()) "None observed" else notification, "OBSERVED"),
            ObservatoryItem("Access", "Overlay permission for Watchtower", overlay.toString(), "USER-GRANTED"),
            ObservatoryItem("Access", "Usage access for Watchtower", usage.toString(), if (usage) "USER-GRANTED" else "REQUIRES SPECIAL ACCESS")
        )
    }

    private fun apps(): List<ObservatoryItem> {
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val userApps = packages.filter { (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
        val privileged = userApps.count { pkg ->
            pkg.requestedPermissions?.any { it.contains("ACCESS_FINE_LOCATION") || it.contains("CAMERA") || it.contains("RECORD_AUDIO") } == true
        }
        return listOf(
            ObservatoryItem("Apps", "Visible installed packages", packages.size.toString()),
            ObservatoryItem("Apps", "Visible non-system apps", userApps.size.toString()),
            ObservatoryItem("Apps", "Visible apps requesting location/camera/microphone", privileged.toString()),
            ObservatoryItem("Visibility", "App inventory", "Android may restrict package visibility; this is not a guaranteed complete inventory.", "RESTRICTED BY ANDROID")
        )
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var n = value.toDouble()
        var i = -1
        while (n >= 1024 && i < units.lastIndex) { n /= 1024; i++ }
        return "%.1f %s".format(n, units[i])
    }
}
