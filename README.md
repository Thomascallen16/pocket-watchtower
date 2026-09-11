# Pocket Watchtower

**Know Your Device.**

Pocket Watchtower is a local-first Android device observatory and integrity recorder. It turns the phone's own observable state into a plain-English, timestamped record of what is known, what changed, what requires permission, and what Android does not expose.

> **Evidence before inference.**

Pocket Watchtower does **not** claim to identify who caused a device change merely because a change was observed.

## What it does

Pocket Watchtower is the owner's device observatory. It answers:

- What is inside my phone?
- What is my phone doing?
- What apps are installed and what access do they have?
- Who or what has elevated device access?
- What special access is enabled?
- What changed, and when?
- What can Pocket Watchtower actually see?
- What can Android restrict or hide?

The interface should be understandable in seconds, with deeper technical detail available when wanted.

## Truth-status model

Every observation is explicitly classified. Pocket Watchtower must use statuses such as:

- **KNOWN**
- **OBSERVED**
- **USER-GRANTED**
- **REQUIRES PERMISSION**
- **REQUIRES SPECIAL ACCESS**
- **RESTRICTED BY ANDROID**
- **NOT EXPOSED BY ANDROID**
- **UNKNOWN**

An observation must never silently become an accusation or conclusion.

## Core areas

### Device Observatory

CPU, memory, storage, battery, display, sensors, operating system, network state, Bluetooth, NFC, VPN and other information legitimately exposed by Android.

### Application Observatory

Installed applications to the extent Android permits, including versions, system/user classification, permissions, and relevant special access.

### Authority Map

Device administrator, device owner, accessibility services, VPN, notification access, usage access, overlay access, and other elevated capabilities exposed by Android.

### Permission Center

A plain-English view of sensitive permissions and which applications currently hold them, with Android Settings paths where appropriate.

### Visibility Map

A first-class feature showing:

- 🟢 **CAN SEE**
- 🟡 **NEEDS YOUR PERMISSION**
- 🟠 **ANDROID LIMITS THIS**
- ⚪ **CANNOT DETERMINE**

This prevents the app from pretending it can see information Android does not expose.

### Baseline & Change Detection

The owner can create a local device baseline and later see observable changes such as application installs/removals/updates, permission changes, accessibility changes, administrator/owner changes, VPN changes, special-access changes, security configuration changes, developer/debug changes, and system updates where exposed.

Each event records a timestamp, before/after state when available, source/API, status, and a plain-English explanation.

### Event Intelligence

Version 0.6 adds a human-readable intelligence layer around recorded changes:

- Activity bursts group closely timed observations so a long event stream can be understood at a glance.
- Events receive a conservative **Routine observation** or **Notable change** assessment based on observable magnitude, not suspected intent.
- Confidence describes the certainty of the observation itself, not the cause.
- Closely timed events may be identified as correlated in time, while explicitly stating that correlation is not causation.
- Every event can be tapped for **What it is / What it means / Why it matters / What it does not mean / Android visibility**.

### Local Audit History

Pocket Watchtower maintains a local chronological record of observable events. Existing tamper-evident/hash-chain work remains part of the project and should be strengthened rather than discarded.

Exports include JSON-backed event history through the app's share flow and a human-readable report with snapshot SHA-256 and evidence-chain verification.

## Security and privacy principles

- Local-first.
- No required account.
- No telemetry upload by default.
- No sale of device data.
- No silent collection.
- No hidden backend required for core functionality.
- Official Android APIs only.
- No sandbox bypasses, exploits, covert surveillance, or secret privilege escalation.
- Settings changes should use Android's own security UI rather than bypassing it.

## Important limits

Pocket Watchtower cannot reliably determine every form of surveillance, provider-side access, lawful records request, remote provider record, sophisticated network interception, or activity occurring outside the device. **Not visible on the phone does not mean it did not happen.**

If Android does not expose something, Pocket Watchtower must say so plainly:

> **Android doesn't expose this information to Pocket Watchtower.**

It must never represent an unexplained event as proof of law-enforcement activity, spying, compromise, or any other accusation.

## Build direction

The implementation is native Android using Kotlin, Jetpack Compose and modern Android architecture. Device collectors are isolated from the UI and must fail gracefully when information is unavailable or restricted.

See [`POCKET_WATCHTOWER_BUILD_SPEC.md`](./POCKET_WATCHTOWER_BUILD_SPEC.md) for the complete product and engineering specification.

## Status

**v0.6.0 — Event Intelligence** is the active development line. The repository remains the canonical home for Pocket Watchtower and continues expanding the event recorder into the full local device observatory described above.
