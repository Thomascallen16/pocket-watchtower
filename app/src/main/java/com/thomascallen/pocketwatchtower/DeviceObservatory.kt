package com.thomascallen.pocketwatchtower

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import java.io.File
import java.util.concurrent.TimeUnit

internal data class ObservatoryItem(
    val section: String,
    val name: String,
    val value: String,
    val status: String = "OBSERVED"
)

internal class DeviceObservatory(private val context: Context) {
    private val pm = context.packageManager

    fun collect(): List<ObservatoryItem> = buildList {
        add(ObservatoryItem("Device", "Manufacturer / model", "${Build.MANUFACTURER} ${Build.MODEL}"))
        add(ObservatoryItem("Device", "Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        add(ObservatoryItem("Security", "Security patch", Build.VERSION.SECURITY_PATCH.ifBlank { "Not exposed" }))
        add(ObservatoryItem("Device", "Architecture", Build.SUPPORTED_ABIS.joinToString()))
        add(ObservatoryItem("Device", "CPU cores", Runtime.getRuntime().availableProcessors().toString()))
        add(ObservatoryItem("Device", "CPU architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "Not exposed"))
        add(ObservatoryItem("Device", "Build fingerprint", Build.FINGERPRINT, "KNOWN"))
        addAll(cpu())
        addAll(systemState())
        addAll(memory())
        addAll(storage())
        addAll(battery())
        addAll(display())
        addAll(network())
        addAll(radio())
        addAll(sensors())
        addAll(access())
        addAll(apps())
    }

    private fun cpu(): List<ObservatoryItem> {
        val stat = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull()
        val cpuLine = stat?.takeIf { it.startsWith("cpu ") }
        val fields = cpuLine?.trim()?.split(Regex("\\s+"))
        val user = fields?.getOrNull(1)?.toLongOrNull() ?: return listOf(
            ObservatoryItem("CPU", "Utilization", "Not exposed", "NOT_EXPOSED_BY_ANDROID")
        )
        val nice = fields.getOrNull(2)?.toLongOrNull() ?: 0L
        val system = fields.getOrNull(3)?.toLongOrNull() ?: 0L
        val idle = fields.getOrNull(4)?.toLongOrNull() ?: 0L
        val total = user + nice + system + idle + (fields.getOrNull(5)?.toLongOrNull() ?: 0L) +
            (fields.getOrNull(6)?.toLongOrNull() ?: 0L) + (fields.getOrNull(7)?.toLongOrNull() ?: 0L)
        val busy = total - idle
        val pct = if (total > 0) busy * 100.0 / total else 0.0
        return listOf(
            ObservatoryItem("CPU", "Kernel-visible utilization sample", "%.1f%%".format(pct), "OBSERVED"),
            ObservatoryItem("CPU", "Frequency", readCpuFrequency())
        )
    }

    private fun readCpuFrequency(): String {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq"
        )
        for (path in paths) {
            val value = runCatching { File(path).readText().trim().toLong() }.getOrNull()
            if (value != null && value > 0) return "${value / 1000} MHz"
        }
        return "Not exposed"
    }

    private fun systemState(): List<ObservatoryItem> {
        val uptime = TimeUnit.MILLISECONDS.toMinutes(SystemClock.elapsedRealtime())
        val developerOptions = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val mockLocation = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION) == "1"
        }.getOrDefault(false)
        return listOf(
            ObservatoryItem("System", "Uptime", "$uptime minutes"),
            ObservatoryItem("Security", "Developer options enabled", developerOptions.toString(), "OBSERVED"),
            ObservatoryItem("Security", "USB debugging / ADB enabled", adb.toString(), "OBSERVED"),
            ObservatoryItem("Security", "Mock-location setting", mockLocation.toString(), "OBSERVED")
        )
    }

    private fun memory(): List<ObservatoryItem> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
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
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not plugged / unknown"
        }
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            else -> "Unknown"
        }
        return listOf(
            ObservatoryItem("Battery", "Charge", if (pct >= 0) "$pct%" else "Not exposed"),
            ObservatoryItem("Battery", "State", charging),
            ObservatoryItem("Battery", "Power source", source),
            ObservatoryItem("Battery", "Health", healthText),
            ObservatoryItem("Battery", "Temperature", if (temperature != Int.MIN_VALUE) "${temperature / 10.0} °C" else "Not exposed"),
            ObservatoryItem("Battery", "Voltage", if (voltage != Int.MIN_VALUE) "$voltage mV" else "Not exposed"),
            ObservatoryItem("Battery", "Current", if (current != Int.MIN_VALUE) "$current µA" else "Not exposed")
        )
    }

    private fun display(): List<ObservatoryItem> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context.resources.displayMetrics
        val display = wm.defaultDisplay
        val refresh = runCatching { display.refreshRate }.getOrDefault(0f)
        return listOf(
            ObservatoryItem("Display", "Resolution", "${metrics.widthPixels} × ${metrics.heightPixels}"),
            ObservatoryItem("Display", "Density", "${metrics.densityDpi} dpi"),
            ObservatoryItem("Display", "Refresh rate", if (refresh > 0) "%.1f Hz".format(refresh) else "Not exposed")
        )
    }

    private fun network(): List<ObservatoryItem> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metered = cm.isActiveNetworkMetered
        val transport = when {
            vpn -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Offline / unknown"
        }
        val link = caps?.let {
            listOfNotNull(
                if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "not metered" else null,
                if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) "not VPN transport" else null
            ).joinToString(", ")
        }.orEmpty()
        return listOf(
            ObservatoryItem("Network", "Active transport", transport),
            ObservatoryItem("Network", "VPN transport", vpn.toString()),
            ObservatoryItem("Network", "Internet validated", validated.toString()),
            ObservatoryItem("Network", "Active network metered", metered.toString()),
            ObservatoryItem("Network", "Capability summary", link.ifBlank { "Not exposed" })
        )
    }

    private fun radio(): List<ObservatoryItem> {
        val bluetooth = if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
            when {
                adapter == null -> "Present, adapter not exposed"
                adapter.isEnabled -> "Enabled"
                else -> "Disabled"
            }
        } else "Not supported"
        val nfc = if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
            when {
                adapter == null -> "Present, adapter not exposed"
                adapter.isEnabled -> "Enabled"
                else -> "Disabled"
            }
        } else "Not supported"
        return listOf(
            ObservatoryItem("Radio", "Bluetooth", bluetooth),
            ObservatoryItem("Radio", "NFC", nfc)
        )
    }

    private fun sensors(): List<ObservatoryItem> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
        val names = sensors.take(20).joinToString("; ") { it.name }
        return listOf(
            ObservatoryItem("Sensors", "Exposed sensors", sensors.size.toString()),
            ObservatoryItem("Sensors", "Sensor names", names.ifBlank { "None exposed" })
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
            pkg.requestedPermissions?.any {
                it == android.Manifest.permission.ACCESS_FINE_LOCATION ||
                    it == android.Manifest.permission.ACCESS_COARSE_LOCATION ||
                    it == android.Manifest.permission.CAMERA ||
                    it == android.Manifest.permission.RECORD_AUDIO
            } == true
        }
        val updatedRecently = userApps.count { pkg ->
            System.currentTimeMillis() - pkg.lastUpdateTime < TimeUnit.DAYS.toMillis(7)
        }
        return listOf(
            ObservatoryItem("Apps", "Visible installed packages", packages.size.toString()),
            ObservatoryItem("Apps", "Visible non-system apps", userApps.size.toString()),
            ObservatoryItem("Apps", "Visible apps requesting location/camera/microphone", privileged.toString()),
            ObservatoryItem("Apps", "Visible non-system apps updated in last 7 days", updatedRecently.toString()),
            ObservatoryItem("Visibility", "App inventory", "Android may restrict package visibility; this is not a guaranteed complete inventory.", "RESTRICTED BY ANDROID")
        )
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var n = value.toDouble()
        var i = -1
        while (n >= 1024 && i < units.lastIndex) {
            n /= 1024
            i++
        }
        return "%.1f %s".format(n, units[i])
    }
}
