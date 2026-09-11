# Pocket Watchtower

Local-first Android device integrity and event recorder.

Pocket Watchtower records **observable device-state changes**, not accusations or conclusions about who caused them.

## Project principle

> Evidence before inference.

The app distinguishes observed changes from interpretation and records what a normal Android application can actually verify.

## V0.1 goals

- Device and Android baseline
- Observable cellular/SIM state where permitted by Android
- Network/VPN state where exposed by public APIs
- Local timestamped event timeline
- Tamper-evident hash chain
- JSON and human-readable export
- No account
- No required cloud backend
- Minimal permissions

## Important limitation

Pocket Watchtower cannot reliably detect every form of surveillance, provider-side access, lawful records request, or sophisticated network interception. It must never represent an unexplained event as proof of law-enforcement activity.

## Status

Initial repository scaffold. Android implementation follows next.
