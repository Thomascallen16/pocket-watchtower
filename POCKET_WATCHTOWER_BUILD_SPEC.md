# Pocket Watchtower — Build Specification

## Product

**Pocket Watchtower**

**Tagline:** Know Your Device.

Pocket Watchtower is the owner's local-first Android device observatory. It exposes as much legitimate device state as Android makes available, explains that state in plain English, records observable changes, and clearly marks the boundary between evidence and inference.

It is not generic antivirus, a spying tool, or a law-enforcement surveillance tool.

## Non-negotiable product rule

**Never fake data. Never turn an observation into an accusation. Never hide an Android limitation.**

Good:

> A new accessibility service is enabled.

Bad:

> Someone is spying on you.

Good:

> A VPN configuration is active.

Bad:

> The government is monitoring your phone.

## Truth statuses

Every collected item must carry an explicit status when applicable:

- `KNOWN`
- `OBSERVED`
- `USER-GRANTED`
- `REQUIRES_PERMISSION`
- `REQUIRES_SPECIAL_ACCESS`
- `RESTRICTED_BY_ANDROID`
- `NOT_EXPOSED_BY_ANDROID`
- `UNKNOWN`

The status is part of the evidence model, not merely UI decoration.

## User experience

The home screen should answer the user's most important questions in about five seconds.

### Home dashboard

**YOUR PHONE**

- Overall Device Status: Normal / Changes detected / Attention needed / Some information unavailable
- Device: CPU, RAM, Storage, Battery, Temperature, Network, OS, Security
- Access: Apps, Permissions, Special Access, Accessibility, Device Administrator, Device Owner, VPN, Notifications
- Changes: New, Changed, Removed, Unknown
- Visibility: what can be seen, what requires permission, what Android restricts, what cannot be determined

The UI must be calm, modern, readable and trustworthy. Avoid hacker-movie styling, alarmism and jargon.

Every technical item should support progressive disclosure:

1. **What it is**
2. **What it means**
3. **Why it matters**
4. **What I can do**
5. Technical details, when the user wants them

## Device Observatory

Collect what official Android APIs expose for:

- Processor / ABI / cores / manufacturer / model / CPU capabilities where available
- CPU utilization and frequency where legitimately available
- Total, available and used memory
- Storage volumes and capacity
- Battery percentage, charging state, health, temperature, voltage, current and capacity where available
- Display characteristics, resolution, density, refresh rate and brightness where accessible
- All Android-exposed sensors, including type, name, manufacturer, version, power, range, resolution and readings where appropriate
- Wi-Fi and network state
- Local interfaces and IP information where safe and available
- Cellular information where Android permits it
- Bluetooth
- NFC
- VPN state
- Network capabilities
- Operating system/build information
- Security state exposed by Android

If an API is unavailable or restricted, record the limitation instead of substituting a guess.

## Application Observatory

Show installed applications to the extent Android permits.

For each application, expose when available:

- Display name
- Package name
- Version
- Install/update information
- System vs user-installed classification
- Requested permissions
- Granted permissions
- Special access
- Accessibility capability
- Notification access
- Usage access
- Overlay access
- Device administrator status
- VPN capability/status
- Other elevated capabilities exposed by Android

Useful sorting/filtering:

- Newest
- Recently changed
- Highest access
- System apps
- User-installed
- Needs attention

The app should explain why a permission or capability exists without making unsupported claims about intent.

## Authority Map

Provide a clear answer to:

**WHO HAS ACCESS?**

Separate these concepts:

- Human owner
- Device administrator
- Device owner
- Accessibility services
- VPN
- Notification access
- Usage access
- Overlay access
- Other special access

Explain the difference between physical ownership and technical Android administration.

Settings shortcuts should open Android's own Settings UI. Pocket Watchtower must not bypass security controls.

## Permission Center

Group permissions by meaning:

- Location
- Camera
- Microphone
- Contacts
- Files/media
- Phone
- Notifications
- Other sensitive capabilities

For each application show:

- Current state
- Android permission name where useful
- Plain-English meaning
- Whether the user can change it
- Direct Android Settings path where available

Do not request every permission on first launch. Use guided, explain-before-prompt flows.

## Visibility Map

Make observability itself visible.

### 🟢 CAN SEE
Information available directly through normal Android APIs.

### 🟡 NEEDS YOUR PERMISSION
Information that becomes available only after the user grants a permission or special access.

### 🟠 ANDROID LIMITS THIS
Information that the Android security model restricts from ordinary applications.

### ⚪ CANNOT DETERMINE
Information that cannot be established from this device alone.

Examples:

- Installed applications → can see, subject to Android visibility rules
- Usage history → may require Usage Access
- Another application's private files → Android restricts access
- Whether an outside organization obtained carrier records → cannot determine from the phone

This section is a core feature, not a disclaimer buried in settings.

## Baseline

The owner can create a **DEVICE BASELINE**.

Store locally:

- Timestamp
- Device configuration
- Application inventory
- Permissions
- Special access
- Administrators
- Device owner state
- Accessibility services
- VPN state
- Security settings/state exposed by Android
- System information
- Other observable security/device state

The baseline belongs to the user. No upload is required.

## Change Detection

Compare later observations against the baseline and prior snapshots.

Detect, where Android exposes the relevant state:

- Application installed
- Application removed
- Application updated
- Permission changed
- Accessibility service changed
- Device administrator changed
- Device owner changed
- VPN changed
- Special access changed
- Security configuration changed
- Developer/debug configuration changed
- System update changed relevant state
- Other observable configuration changes

Each event should contain:

- Timestamp
- Event type
- Before state
- After state
- Source/API
- Truth status
- Plain-English explanation
- What Pocket Watchtower can and cannot conclude

## Local Audit History

Maintain a chronological local event history.

Existing tamper-evident/hash-chain work must be preserved and strengthened rather than replaced.

A useful event record should support:

- Timestamp
- Event identifier
- Previous-event hash
- Current-event hash
- Device/baseline reference
- Observed data
- Status
- Collector/source
- Explanation

The history should be exportable as JSON and as a human-readable report. Later releases may add plan/PDF export.

## Root and device management

### Root status

Use multiple non-invasive indicators to report apparent root status.

Possible outcomes:

- Root indicators detected
- No root indicators detected
- Cannot determine

Never claim that a device is impossible to access merely because root indicators are absent.

Root detection must not require root and must never exploit or bypass Android security.

### Device Administrator / Device Owner

Detect administrator and device-owner state where Android exposes it.

Explain:

- What the role is
- What capabilities it can have
- Whether it is active
- What Android reports
- What Pocket Watchtower cannot determine about the person or organization behind it

## External Access boundary

Provide a separate area:

**INFORMATION OUTSIDE YOUR PHONE**

Explain that some information may exist only with:

- Carriers
- Cloud providers
- Service providers
- Network operators
- Historical provider records
- Third-party systems
- External legal processes
- Remote systems

Mark this information clearly as **NOT VISIBLE FROM THIS DEVICE** when appropriate.

The absence of a local artifact must never be presented as proof that an external event did not occur.

## Privacy architecture

- Local-first
- No required account
- No telemetry upload by default
- No sale of device data
- No silent collection
- No hidden backend for core functionality
- Optional future sync only with explicit user control and understandable encryption
- Official Android APIs only
- No exploits
- No sandbox bypass
- No covert surveillance
- No privilege escalation

## Android implementation

Use:

- Kotlin
- Jetpack Compose
- AndroidX
- Modern Android architecture
- Local database/storage for baseline and history
- Repository/service separation between UI and collectors

Suggested collector modules:

- `DeviceInfoCollector`
- `CpuInfoCollector`
- `MemoryInfoCollector`
- `StorageInfoCollector`
- `BatteryInfoCollector`
- `SensorInfoCollector`
- `NetworkInfoCollector`
- `BluetoothInfoCollector`
- `ApplicationInfoCollector`
- `PermissionInfoCollector`
- `SpecialAccessCollector`
- `AccessibilityCollector`
- `DeviceAdminCollector`
- `DeviceOwnerCollector`
- `VpnCollector`
- `UsageInfoCollector`
- `NotificationAccessCollector`
- `RootStatusCollector`
- `SecurityInfoCollector`
- `BaselineManager`
- `ChangeDetectionEngine`
- `AuditHistoryStore`

Every collector must fail gracefully. One restricted API must never crash the application.

## V0.1 — Working Device Observatory

1. Home dashboard
2. Device information
3. CPU / RAM / storage
4. Battery
5. Sensors
6. Application inventory
7. Permissions
8. Special access
9. Device administrator
10. Device owner
11. Accessibility
12. VPN
13. Root indicators
14. Visibility Map
15. Device Baseline
16. Change Detection
17. Local Audit History

## V0.2 — Deeper Observatory

- Deeper sensor information
- Expanded network state
- Usage information
- Notification access
- Expanded security state
- JSON export
- Human-readable export
- Richer plain-English explanations
- Stronger comparison/timeline views

## V0.3 — Advanced Audit

- Advanced device audit
- Technical mode
- Detailed timeline
- Baseline comparisons
- Stronger integrity verification
- Optional encrypted backup/sync
- Optional web dashboard

## Testing requirements

The project is not considered complete because a screen renders.

Test:

- Collectors on real Android hardware
- Permission-denied paths
- Special-access-denied paths
- Unsupported API paths
- Missing/unknown state
- Baseline creation
- Baseline comparison
- Install/update/remove events where observable
- Permission changes
- Accessibility changes
- Administrator/owner changes
- VPN changes
- Hash-chain integrity
- JSON export
- Human-readable export
- UI accessibility

## Definition of done

A user should be able to install Pocket Watchtower on a real Android phone, open it, understand what the phone exposes, grant optional access one capability at a time, create a baseline, return later, see what changed, inspect the underlying observation, and understand exactly where Android prevents the app from knowing more.

**The product's credibility comes from the boundaries it refuses to cross.**
