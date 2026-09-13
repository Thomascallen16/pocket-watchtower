# Pocket Watchtower — Release Notes

## v1.3.0 — Sensor, privilege, package monitoring + evidence correlation

This release expands Pocket Watchtower from a general device-state recorder into a broader owner-controlled observatory while preserving the evidence-first trust boundary.

### Observatory expansion

- Expanded sensor and privilege visibility checks.
- Added explicit visibility reporting for microphone/camera global controls where Android does not expose a stable ordinary-app API.
- Added device administrator, device owner, accessibility, notification listener, overlay, usage-access, developer-options, USB debugging and wireless-debugging observations where Android exposes them.
- Expanded package and sensitive-permission inventory observations.
- Preserved explicit Android package-visibility and provider-side limitations.

### Evidence and correlation

- Local baseline and SHA-256 event chaining remain preserved.
- Activity bursts group closely timed observations without implying causation.
- Correlation signals now recognize battery, memory and storage evidence areas.
- Package inventory hash changes occurring near a significant battery-state change can produce a conservative review signal.
- Signal explanations explicitly distinguish observed timing from causal proof.
- Human-readable exports retain the evidence, correlation and visibility rules.

### Trust boundary

Pocket Watchtower does not claim to identify an actor from an observation, prove compromise from the absence of evidence, or see provider-side records that Android does not expose.

> **An observation is not an accusation.**

> **Related timing increases review value; it does not establish causation.**

### Validation target

The canonical application version is `1.3.0` / version code `130`. Release CI derives the APK filename and artifact name from Gradle `versionName` rather than a stale hard-coded release filename.
