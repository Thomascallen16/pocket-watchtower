# Windows + Android Forensics Lab

A beginner-friendly, evidence-first learning lab for examining **your own Android phone** from a Windows laptop using free tools.

This starts as a companion learning module for Pocket Watchtower. It is not a claim that a phone can reveal everything about itself, and it is not a substitute for a qualified forensic acquisition when legal admissibility matters.

## Principles

- **Evidence before inference.** Record what a tool actually returned; do not turn an observation into an accusation.
- **Preserve first.** Keep original exports unchanged, work from copies, and hash collected files.
- **No destructive commands.** The starter workflow is read-only. It does not unlock the bootloader, root the phone, install apps, clear logs, change settings, or bypass Android protections.
- **Owner-authorized devices only.** Use this lab on devices you own or are explicitly authorized to examine.
- **Explain the limits.** ADB exposes only what Android and the device configuration permit. Missing output is not proof that an event did not happen.

## Learning path

1. [Set up Windows](windows-setup.md)
2. [Run a read-only Android baseline collection](../../scripts/forensics/Collect-AndroidBaseline.ps1)
3. [Understand the commands and vocabulary](glossary.md)
4. [Follow the evidence-handling workflow](evidence-workflow.md)
5. Review the raw output and hash manifest before drawing conclusions.

## Starter scope

The first collector records a timestamped device baseline using Android Debug Bridge (ADB): device model/manufacturer, Android version and security patch, build fingerprint, device state, and selected non-destructive system properties. It stores raw command output and a SHA-256 manifest in a new local case folder.

It does **not** extract private app data, messages, photos, call history, or cloud/provider records. Those require separate, permissioned workflows and may not be available through ADB.

## Suggested case structure

```text
case-YYYYMMDD-HHMMSS/
  collection-log.txt
  device/
    adb-version.txt
    devices.txt
    getprop.txt
    selected-properties.txt
  SHA256SUMS.csv
  NOTES.md
```

## Current status

**Phase 0 — safe foundation:** documentation + read-only baseline collector. Next phases can add a guided glossary, Windows event timeline helpers, package/permission inventory, and report generation—each with tests and explicit visibility limits.

> Do not connect an evidence device to unknown computers or run commands you do not understand. If the device may contain evidence for an active case, avoid changing it and consult a qualified forensic examiner or attorney about preservation.
