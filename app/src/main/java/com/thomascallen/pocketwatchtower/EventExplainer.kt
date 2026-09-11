package com.thomascallen.pocketwatchtower

/**
 * Plain-English explanations for observable event types.
 * Explanations describe the observation, not an actor or cause.
 */
internal data class EventExplanation(
    val title: String,
    val whatItIs: String,
    val whatItMeans: String,
    val whyItMatters: String,
    val doesNotMean: String,
    val visibility: String
)

internal fun explainEvent(event: Event): EventExplanation {
    val key = event.key.lowercase()
    val category = event.category.lowercase()

    return when {
        key.contains("available ram") -> EventExplanation(
            "Available RAM",
            "The amount of working memory Android currently reports as available for apps and the system.",
            "A change means Android's memory picture changed between two observations. RAM naturally moves up and down as apps start, stop, cache data, and the system manages memory.",
            "Large or repeated changes can help establish a baseline for normal device activity and identify when the phone's memory state is unusual compared with its own history.",
            "It does not identify which app caused the change, and it does not by itself indicate malware, spying, or compromise.",
            "Observed through Android's memory information APIs. Android does not provide a complete causal history for every RAM change."
        )
        key.contains("battery") || key.contains("current") -> EventExplanation(
            "Battery current",
            "An electrical-current reading reported by the device's battery subsystem.",
            "The number can move quickly because charging, screen use, radios, CPU activity, temperature, and background work affect power draw. Negative and positive signs depend on the device/API convention.",
            "A history of readings helps show how the phone's power state changes over time and establish what is ordinary for this device.",
            "It does not reveal which app or person caused a particular current change.",
            "Android exposes battery properties, but the precision and availability of individual readings vary by device."
        )
        key.contains("uptime") -> EventExplanation(
            "System uptime",
            "How long Android reports the device has been running since its last boot, measured using the system's elapsed clock.",
            "An increase is normal while the phone remains running. A reset to a much smaller value can indicate a reboot or another clock discontinuity.",
            "Uptime gives Watchtower a stable system timeline for interpreting other observations.",
            "It does not tell us why a reboot occurred or who initiated it.",
            "Android exposes elapsed uptime; it does not provide a complete explanation for every reboot or shutdown."
        )
        key.contains("total ram") -> simple(
            "Total RAM",
            "The amount of physical memory Android reports for the device.",
            "This normally remains stable unless the device reports it differently after a system or hardware change.",
            "It provides context for available-memory readings.",
            "It does not describe current app activity by itself.",
            "Android exposes the device-level memory figure."
        )
        key.contains("low-memory") -> simple(
            "Low-memory state",
            "Android's current indication that the system considers available memory to be low.",
            "A true value means Android considers memory pressure significant at that moment.",
            "It helps distinguish ordinary RAM movement from a period of system memory pressure.",
            "It does not identify a specific app as the cause.",
            "This is an Android system-level signal."
        )
        key.contains("charge") -> simple(
            "Battery charge",
            "The battery percentage currently reported by Android.",
            "It represents the device's reported state of charge and changes as the battery charges or discharges.",
            "It provides context for other battery observations.",
            "It does not prove what caused charging or discharging.",
            "The value comes from Android's BatteryManager."
        )
        key.contains("state") && category == "battery" -> simple(
            "Battery state",
            "Whether Android reports the battery as charging, discharging, full, or another state.",
            "The state describes the electrical/charging condition reported by the device.",
            "It helps interpret current and power-source changes.",
            "It does not identify the application responsible for power use.",
            "Android supplies this through the battery status broadcast."
        )
        key.contains("power source") -> simple(
            "Power source",
            "The type of external power connection Android reports, such as USB, AC, or wireless.",
            "It tells us how Android believes the phone is receiving power at that moment.",
            "It helps explain battery-current and charging-state changes.",
            "It does not reveal who connected the charger or why.",
            "The exact information available depends on the device and Android version."
        )
        key.contains("temperature") && category == "battery" -> simple(
            "Battery temperature",
            "The battery temperature reported by Android, where the device exposes it.",
            "Temperature naturally changes with charging, workload, environment, and battery use.",
            "It provides context for battery behavior and unusual thermal conditions.",
            "It does not identify the cause of a temperature change by itself.",
            "Availability and precision depend on the device."
        )
        key.contains("voltage") -> simple(
            "Battery voltage",
            "The electrical voltage reported for the battery.",
            "Voltage changes with battery state and electrical load.",
            "It can help characterize battery behavior over time.",
            "It is not a direct measure of app activity or compromise.",
            "Android may expose voltage through the battery status information."
        )
        key.contains("health") && category == "battery" -> simple(
            "Battery health",
            "Android's reported battery-health condition, when available.",
            "It is a broad device-reported condition such as good, overheat, or another status.",
            "It can help identify hardware or thermal conditions worth watching.",
            "It is not a forensic diagnosis of a battery problem.",
            "The available health states are defined by Android and device hardware."
        )
        key.contains("active transport") -> simple(
            "Active network transport",
            "The network path Android currently identifies as active, such as Wi-Fi, mobile data, Ethernet, or VPN transport.",
            "It describes the connection type Android sees at the time of the scan.",
            "It gives context for network-state changes.",
            "It does not tell us what a connection is being used for or who is communicating with the phone.",
            "Android limits detailed network visibility for ordinary applications."
        )
        key.contains("vpn") -> simple(
            "VPN transport",
            "Whether Android reports the active network as using VPN transport.",
            "A true value means Android currently reports VPN transport. VPNs can be used for privacy, work, security, or many ordinary purposes.",
            "It is useful when tracking changes to the phone's network path.",
            "It does not identify the VPN operator, purpose, or destination traffic.",
            "Android exposes network capabilities, not a complete record of external network activity."
        )
        key.contains("internet validated") -> simple(
            "Internet validation",
            "Android's indication that the active network has been validated as having internet connectivity.",
            "A true value means Android currently considers the connection usable for internet access.",
            "It helps distinguish a connected local network from a connection Android believes reaches the internet.",
            "It does not prove that every service or website is reachable.",
            "This is an Android network-capability signal."
        )
        key.contains("bluetooth") -> simple(
            "Bluetooth state",
            "Whether Android reports Bluetooth as supported and currently enabled.",
            "It describes the radio's reported state, not what devices are communicating over it.",
            "It helps track changes to a major local wireless interface.",
            "It does not show all Bluetooth activity or identify a remote person.",
            "Android restricts detailed radio and peer information depending on version and permissions."
        )
        key.contains("nfc") -> simple(
            "NFC state",
            "Whether Android reports NFC support and whether the NFC radio is enabled.",
            "It describes the radio's current state.",
            "It helps establish the phone's local wireless capabilities.",
            "It does not prove that an NFC transaction occurred.",
            "Android does not expose every past NFC interaction to an ordinary app."
        )
        key.contains("developer options") -> simple(
            "Developer options",
            "Whether Android reports that its developer-options configuration is enabled.",
            "Enabled means developer-oriented settings are available in Android Settings.",
            "It matters because some debugging and development capabilities depend on these settings.",
            "It does not mean the device is compromised or that debugging is currently being used.",
            "Android exposes the setting state but not a complete history of who changed it."
        )
        key.contains("usb debugging") || key.contains("adb") -> simple(
            "USB debugging / ADB",
            "Whether Android reports that USB debugging through Android Debug Bridge is enabled.",
            "Enabled means Android permits the debugging pathway according to its current security state and authorization rules.",
            "It is an important security configuration worth understanding and monitoring.",
            "Enabled does not prove that a computer is connected or that someone accessed the phone.",
            "Android does not expose a complete historical record of every ADB connection through this observation alone."
        )
        key.contains("mock-location") -> simple(
            "Mock-location setting",
            "A device setting associated with allowing mock location behavior on Android versions that expose it.",
            "A change means the reported setting changed; modern Android location controls can be more nuanced than this single flag.",
            "It can matter when evaluating location-related configuration.",
            "It does not prove that a fake location was actually used.",
            "Location security behavior varies by Android version and device."
        )
        key.contains("accessibility services") -> simple(
            "Accessibility services",
            "The accessibility services Android currently exposes as enabled.",
            "Accessibility services can legitimately help users with disabilities and can also have powerful interaction capabilities.",
            "Because these services can interact with parts of the interface, changes are worth understanding.",
            "An enabled accessibility service is not proof of spying or malicious behavior.",
            "Android controls which service information is exposed and what Watchtower can inspect."
        )
        key.contains("notification listeners") -> simple(
            "Notification listeners",
            "The notification-listener services Android currently exposes as enabled.",
            "A notification listener can receive notification events according to Android's notification-access framework.",
            "This is a meaningful special access because notifications can contain sensitive information.",
            "Access does not prove that a service misused the information.",
            "Watchtower can report exposed listener state but cannot infer intent or inspect every provider-side action."
        )
        key.contains("device administrators") -> simple(
            "Device administrators",
            "The number of active device administrators Android exposes to Watchtower.",
            "Device administrators can have specific device-management capabilities under Android's administration framework.",
            "A change can indicate that device-management authority changed.",
            "It does not identify a human actor or prove misuse.",
            "Android determines what administrator information an ordinary app can inspect."
        )
        key.contains("device owner") -> simple(
            "Device owner status",
            "Whether Android reports that Pocket Watchtower itself is the device owner.",
            "A device owner is a special Android management role with elevated management capabilities.",
            "It helps distinguish ordinary app status from formal Android device-management status.",
            "This observation only concerns the Watchtower app's own device-owner status.",
            "Android exposes device-owner status through DevicePolicyManager."
        )
        key.contains("overlay") -> simple(
            "Overlay access",
            "Whether Watchtower itself currently has Android's permission to draw over other apps.",
            "Overlay access lets an app display certain UI elements above other applications.",
            "It is a security-sensitive special access worth keeping visible.",
            "It does not mean the app is currently displaying an overlay or that another app has access.",
            "This value describes Watchtower's own permission state."
        )
        key.contains("usage access") -> simple(
            "Usage access",
            "Whether Watchtower has Android's special permission to read certain application-usage information.",
            "This access can expose usage statistics that ordinary applications cannot read by default.",
            "It matters because it is a sensitive capability and requires explicit user action.",
            "Having usage access does not prove that an app is monitoring another person.",
            "Android controls this through special-access settings."
        )
        key.contains("visible installed packages") || key.contains("visible non-system") -> simple(
            "Visible applications",
            "The number of installed application packages that Android currently lets Watchtower see.",
            "The count is an observable inventory, not necessarily a complete inventory of everything installed on the phone.",
            "It provides a baseline for application changes that are visible to the app.",
            "It does not prove that an unseen application does or does not exist.",
            "Android package-visibility rules can restrict what ordinary apps can discover."
        )
        key.contains("updated in last 7 days") -> simple(
            "Recently updated applications",
            "The number of visible non-system applications whose package update time falls within the last seven days.",
            "It indicates recent package-update timestamps among apps Watchtower can see.",
            "It helps focus attention on recent application changes.",
            "A recent update does not imply malicious behavior.",
            "The list is subject to Android package visibility and package metadata availability."
        )
        key.contains("app inventory") -> simple(
            "Application inventory visibility",
            "Android's boundary around which installed packages an ordinary application is allowed to discover.",
            "A restricted result means Watchtower cannot guarantee a complete inventory.",
            "Making this limitation visible prevents false confidence about what is installed.",
            "Not visible does not mean not installed.",
            "This is an Android platform privacy/security restriction."
        )
        key.contains("kernel-visible utilization") || key == "utilization" -> simple(
            "CPU utilization sample",
            "A point-in-time estimate derived from CPU statistics visible to the application.",
            "It describes system CPU activity around the observation, not a permanent condition.",
            "Repeated samples can establish a baseline and show periods of heavier or lighter system activity.",
            "It does not identify the process responsible for the load from this number alone.",
            "The exact CPU statistics available to ordinary Android applications vary by device and Android version."
        )
        key.contains("frequency") -> simple(
            "CPU frequency",
            "The current CPU frequency value exposed by the device's CPU frequency interface, when available.",
            "CPU frequency can change dynamically to balance performance, temperature, and power use.",
            "It provides useful context for CPU and battery observations.",
            "A frequency change does not identify a cause or indicate compromise.",
            "Many devices restrict or omit direct CPU-frequency information."
        )
        key.contains("sensor") -> simple(
            "Sensor information",
            "Information about physical sensors that Android makes available to the application.",
            "Sensors can include motion, environmental, and other hardware capabilities depending on the device.",
            "The inventory helps the owner understand what sensor hardware Android exposes.",
            "The presence of a sensor does not mean an app is actively using it.",
            "Android permissions and hardware support determine what can be observed."
        )
        else -> EventExplanation(
            "${event.category} / ${event.key}",
            "A device observation recorded by Pocket Watchtower.",
            "Watchtower detected that this value changed from the previous recorded value to the current value.",
            "The event contributes to the phone's local baseline and chronological history.",
            "An observed change does not by itself establish a cause, actor, intent, compromise, or wrongdoing.",
            "The exact visibility depends on the Android API that supplied the observation."
        )
    }
}

private fun simple(
    title: String,
    whatItIs: String,
    whatItMeans: String,
    whyItMatters: String,
    doesNotMean: String,
    visibility: String
) = EventExplanation(title, whatItIs, whatItMeans, whyItMatters, doesNotMean, visibility)
