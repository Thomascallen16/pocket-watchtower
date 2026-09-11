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

### Event Intelligence — v1.1

The event intelligence layer now treats correlation as a review aid rather than a conclusion:

- **Temporal correlation windows** group closely timed observations.
- **Signal families** distinguish battery/power, memory/resources, network/connectivity, application state, process visibility, accessibility/special access, and security/administration.
- **Human-readable explanations** tell the owner what happened, why it matters, ordinary possible reasons, what would strengthen the signal, and what it does not prove.
- **Evidence drill-down** connects every correlation back to its exact timestamped observations and original event records.
- **Current Device explanation parity** applies the same plain-English explanation model to live device cards.
- **Zero correlation remains valid.** No qualifying correlation is not proof that nothing happened or that a device is uncompromised.

### Owner-Controlled Root Observatory

Root is not an Android runtime permission. Pocket Watchtower therefore does not silently grant itself privileged access.

The v1.1 root layer requires explicit owner consent before attempting a minimal, non-invasive identity probe through the device's normal root authorization mechanism. It records consent/revocation locally and distinguishes root availability from the meaning of any device event.

It does **not** exploit vulnerabilities, bypass authorization, persist hidden privileged access, or modify the device as part of the probe.

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
- Official Android APIs for ordinary observation.
- No sandbox bypasses, exploits, covert surveillance, or secret privilege escalation.
- Privileged observation requires explicit owner consent and the device's normal root authorization mechanism.
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

**v1.1 development line — Correlation + Owner-Controlled Root Observatory.** The v1.0.0 Owner Forensic Observatory remains the release baseline. The v1.1 branch adds the correlation architecture and explicit root-consent foundation without weakening the evidence boundaries.
