package com.thomascallen.pocketwatchtower

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorPrivacyManager
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class ObservatoryItem(val section: String, val name: String, val value: String, val status: String = "OBSERVED")

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
        addAll(cpu()); addAll(systemState()); addAll(memory()); addAll(storage()); addAll(battery()); addAll(display()); addAll(network()); addAll(radio()); addAll(sensors()); addAll(access()); addAll(apps())
    }
    private fun cpu(): List<ObservatoryItem> {
        val stat = runCatching { File("/proc/stat").bufferedReader().use { it.readLine() } }.getOrNull()
        val fields = stat?.takeIf { it.startsWith("cpu ") }?.trim()?.split(Regex("\\s+"))
        val user = fields?.getOrNull(1)?.toLongOrNull() ?: return listOf(ObservatoryItem("CPU", "Utilization", "Not exposed", "NOT_EXPOSED_BY_ANDROID"))
        val nice = fields.getOrNull(2)?.toLongOrNull() ?: 0L; val system = fields.getOrNull(3)?.toLongOrNull() ?: 0L; val idle = fields.getOrNull(4)?.toLongOrNull() ?: 0L
        val total = user + nice + system + idle + (fields.getOrNull(5)?.toLongOrNull() ?: 0L) + (fields.getOrNull(6)?.toLongOrNull() ?: 0L) + (fields.getOrNull(7)?.toLongOrNull() ?: 0L)
        return listOf(ObservatoryItem("CPU", "Kernel-visible utilization sample", "%.1f%%".format(if (total > 0) (total - idle) * 100.0 / total else 0.0)), ObservatoryItem("CPU", "Frequency", readCpuFrequency()))
    }
    private fun readCpuFrequency(): String { for (path in listOf("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq")) { val value = runCatching { File(path).readText().trim().toLong() }.getOrNull(); if (value != null && value > 0) return "${value / 1000} MHz" }; return "Not exposed" }
    private fun systemState(): List<ObservatoryItem> {
        val uptime = TimeUnit.MILLISECONDS.toMinutes(SystemClock.elapsedRealtime())
        val developerOptions = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val wirelessAdb = runCatching { Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1 }.getOrDefault(false)
        val mockLocation = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION) == "1" }.getOrDefault(false)
        return listOf(ObservatoryItem("System", "Uptime", "$uptime minutes"), ObservatoryItem("Security", "Developer options enabled", developerOptions.toString()), ObservatoryItem("Security", "USB debugging / ADB enabled", adb.toString()), ObservatoryItem("Security", "Wireless debugging / ADB over Wi-Fi", wirelessAdb.toString()), ObservatoryItem("Security", "Mock-location setting", mockLocation.toString()))
    }
    private fun memory(): List<ObservatoryItem> { val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info); return listOf(ObservatoryItem("Memory", "Total RAM", formatBytes(info.totalMem)), ObservatoryItem("Memory", "Available RAM", formatBytes(info.availMem)), ObservatoryItem("Memory", "Low-memory state", info.lowMemory.toString())) }
    private fun storage(): List<ObservatoryItem> { val stat = StatFs(Environment.getDataDirectory().path); return listOf(ObservatoryItem("Storage", "Total", formatBytes(stat.totalBytes)), ObservatoryItem("Storage", "Available", formatBytes(stat.availableBytes)), ObservatoryItem("Storage", "Used", formatBytes(stat.totalBytes - stat.availableBytes))) }
    private fun battery(): List<ObservatoryItem> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)); val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1; val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE; val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE; val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val charging = when (status) { BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"; BatteryManager.BATTERY_STATUS_FULL -> "Full"; BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"; BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"; else -> "Unknown" }
        val source = when (plugged) { BatteryManager.BATTERY_PLUGGED_USB -> "USB"; BatteryManager.BATTERY_PLUGGED_AC -> "AC"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Not plugged / unknown" }
        val healthText = when (health) { BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"; BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"; BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"; else -> "Unknown" }
        return listOf(ObservatoryItem("Battery", "Charge", if (pct >= 0) "$pct%" else "Not exposed"), ObservatoryItem("Battery", "State", charging), ObservatoryItem("Battery", "Power source", source), ObservatoryItem("Battery", "Health", healthText), ObservatoryItem("Battery", "Temperature", if (temperature != Int.MIN_VALUE) "${temperature / 10.0} °C" else "Not exposed"), ObservatoryItem("Battery", "Voltage", if (voltage != Int.MIN_VALUE) "$voltage mV" else "Not exposed"), ObservatoryItem("Battery", "Current", if (current != Int.MIN_VALUE) "$current µA" else "Not exposed"))
    }
    private fun display(): List<ObservatoryItem> { val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager; val metrics = context.resources.displayMetrics; val refresh = runCatching { wm.defaultDisplay.refreshRate }.getOrDefault(0f); return listOf(ObservatoryItem("Display", "Resolution", "${metrics.widthPixels} × ${metrics.heightPixels}"), ObservatoryItem("Display", "Density", "${metrics.densityDpi} dpi"), ObservatoryItem("Display", "Refresh rate", if (refresh > 0) "%.1f Hz".format(refresh) else "Not exposed")) }
    private fun network(): List<ObservatoryItem> { val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities); val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true; val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true; val transport = when { vpn -> "VPN"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"; caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"; else -> "Offline / unknown" }; return listOf(ObservatoryItem("Network", "Active transport", transport), ObservatoryItem("Network", "VPN transport", vpn.toString()), ObservatoryItem("Network", "Internet validated", validated.toString()), ObservatoryItem("Network", "Active network metered", cm.isActiveNetworkMetered.toString())) }
    private fun radio(): List<ObservatoryItem> { val bluetooth = if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) { val a = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull(); when { a == null -> "Present, adapter not exposed"; a.isEnabled -> "Enabled"; else -> "Disabled" } } else "Not supported"; val nfc = if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) { val a = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull(); when { a == null -> "Present, adapter not exposed"; a.isEnabled -> "Enabled"; else -> "Disabled" } } else "Not supported"; return listOf(ObservatoryItem("Radio", "Bluetooth", bluetooth), ObservatoryItem("Radio", "NFC", nfc)) }
    private fun sensors(): List<ObservatoryItem> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager; val sensors = sm.getSensorList(Sensor.TYPE_ALL); val names = sensors.take(20).joinToString("; ") { it.name }; val privacy = runCatching { context.getSystemService(SensorPrivacyManager::class.java) }.getOrNull()
        val mic = if (Build.VERSION.SDK_INT >= 31 && privacy != null) runCatching { privacy.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE) }.getOrNull() else null; val camera = if (Build.VERSION.SDK_INT >= 31 && privacy != null) runCatching { privacy.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA) }.getOrNull() else null
        return listOf(ObservatoryItem("Sensors", "Exposed sensors", sensors.size.toString()), ObservatoryItem("Sensors", "Sensor names", names.ifBlank { "None exposed" }), ObservatoryItem("Privacy", "Microphone global access toggle", mic?.toString() ?: "Not exposed by Android", if (mic == null) "NOT_EXPOSED_BY_ANDROID" else "OBSERVED"), ObservatoryItem("Privacy", "Camera global access toggle", camera?.toString() ?: "Not exposed by Android", if (camera == null) "NOT_EXPOSED_BY_ANDROID" else "OBSERVED"), ObservatoryItem("Privacy", "Active mic/camera app identity", "Not exposed to ordinary third-party apps", "RESTRICTED BY ANDROID"))
    }
    private fun access(): List<ObservatoryItem> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; val usage = runCatching { val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED }.getOrDefault(false)
        val accessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty(); val notification = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty(); val overlay = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else false; val admins = runCatching { dpm.activeAdmins?.map { it.flattenToShortString() }.orEmpty() }.getOrDefault(emptyList()); val owner = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)
        return listOf(ObservatoryItem("Access", "Device administrators", if (admins.isEmpty()) "None observed" else admins.joinToString(", "), "OBSERVED"), ObservatoryItem("Access", "This app is device owner", owner.toString()), ObservatoryItem("Access", "Accessibility services enabled", if (accessibility.isBlank()) "None observed" else accessibility, "OBSERVED"), ObservatoryItem("Access", "Notification listeners enabled", if (notification.isBlank()) "None observed" else notification, "OBSERVED"), ObservatoryItem("Access", "Overlay permission for Watchtower", overlay.toString(), "USER-GRANTED"), ObservatoryItem("Access", "Usage access for Watchtower", usage.toString(), if (usage) "USER-GRANTED" else "REQUIRES SPECIAL ACCESS"))
    }
    private fun apps(): List<ObservatoryItem> {
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS); val userApps = packages.filter { (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
        val sensitive = setOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
        val grants = userApps.flatMap { pkg -> val requested = pkg.requestedPermissions ?: return@flatMap emptyList(); val flags = pkg.requestedPermissionsFlags ?: IntArray(requested.size); requested.mapIndexedNotNull { i, permission -> if (permission in sensitive && (flags.getOrNull(i) ?: 0) and PackageManager.REQUESTED_PERMISSION_GRANTED != 0) "${pkg.packageName}:$permission" else null } }.sorted()
        val inventory = packages.sortedBy { it.packageName }.joinToString("|") { "${it.packageName}@${runCatching { it.longVersionCode }.getOrDefault(0L)}@${it.lastUpdateTime}" }; val inventoryHash = sha256(inventory); val grantHash = sha256(grants.joinToString("|")); val recent = userApps.count { System.currentTimeMillis() - it.lastUpdateTime < TimeUnit.DAYS.toMillis(7) }
        return listOf(ObservatoryItem("Apps", "Visible installed packages", packages.size.toString()), ObservatoryItem("Apps", "Visible non-system apps", userApps.size.toString()), ObservatoryItem("Apps", "Visible apps with granted sensitive permissions", grants.size.toString()), ObservatoryItem("Apps", "Sensitive permission grant inventory SHA-256", grantHash), ObservatoryItem("Apps", "Package inventory SHA-256", inventoryHash), ObservatoryItem("Apps", "Visible non-system apps updated in last 7 days", recent.toString()), ObservatoryItem("Apps", "Sensitive permission inventory", grants.take(80).joinToString("; ").ifBlank { "None observed" }), ObservatoryItem("Apps", "Package inventory", packages.take(250).joinToString("; ") { it.packageName }.ifBlank { "None observed" }), ObservatoryItem("Visibility", "App inventory", "Android may restrict package visibility; this is not a guaranteed complete inventory.", "RESTRICTED BY ANDROID"))
    }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun formatBytes(value: Long): String { if (value < 1024) return "$value B"; val units = arrayOf("KB", "MB", "GB", "TB"); var n = value.toDouble(); var i = -1; while (n >= 1024 && i < units.lastIndex) { n /= 1024; i++ }; return "%.1f %s".format(n, units[i]) }
}
