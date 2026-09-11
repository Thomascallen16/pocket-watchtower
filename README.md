# Pocket Watchtower

**Know Your Device.**

Pocket Watchtower is a local-first Android device observatory and integrity recorder. It turns the phone's own observable state into a plain-English, timestamped record of what is known, what changed, what requires permission, and what Android does not expose.

> **Evidence before inference.**

Pocket Watchtower does **not** claim to identify who caused a device change merely because a change was observed.

## Current release

**v1.1.0 — Integrity Observatory baseline**

The repository now treats version `1.1.0` as the canonical release line. Android build artifacts are generated from the Gradle `versionName` rather than a second hard-coded CI version.

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

The interface is designed for progressive disclosure: a calm owner-readable summary first, with evidence and technical detail available when wanted.

## Truth-status model

Every observation is explicitly classified. Pocket Watchtower uses statuses such as:

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

Installed applications to the extent Android permits, including system/user classification and observable permission/access information. Android package-visibility restrictions remain explicit.

### Authority Map

Device administrator, device owner, accessibility services, VPN, notification access, usage access, overlay access, and other elevated capabilities exposed by Android.

### Visibility Map

A first-class feature showing:

- 🟢 **CAN SEE**
- 🟡 **NEEDS YOUR PERMISSION**
- 🟠 **ANDROID LIMITS THIS**
- ⚪ **CANNOT DETERMINE**

This prevents the app from pretending it can see information Android does not expose.

### Baseline & Change Detection

The owner can establish a local baseline and later see observable changes in the device state. Changes are recorded with timestamp, before/after state where available, category and a tamper-evident hash chain.

### Event Intelligence

Recorded changes can be grouped into activity bursts and conservative correlation signals. The intelligence layer explains why an observation may matter without turning timing or correlation into a claim of causation.

### Local Audit History

Pocket Watchtower maintains a local chronological evidence history. The chain can be verified and the current observable snapshot receives a SHA-256 digest. Human-readable reports can be shared without requiring a cloud account.

### Owner Actions

Remediation remains owner-controlled and Android-mediated. The separate Owner Actions screen can open Android application controls, provide uninstall guidance, handle device-owner-only suspension where available, and let the owner select exact documents through Android's document provider before deletion.

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

## Build

The implementation is native Android using Kotlin, Jetpack Compose and AndroidX. The release workflow builds the release APK with JDK 17 and derives its artifact name from the application's Gradle version.

The canonical product specification is [`POCKET_WATCHTOWER_BUILD_SPEC.md`](./POCKET_WATCHTOWER_BUILD_SPEC.md).

## Completion standard

Pocket Watchtower is considered complete when a real Android owner can install it, understand observable device state, inspect access and visibility boundaries, establish a baseline, return later, identify observable changes, verify the local evidence chain, export a human-readable record, and understand exactly where Android prevents the app from knowing more.

**The product's credibility comes from the boundaries it refuses to cross.**
