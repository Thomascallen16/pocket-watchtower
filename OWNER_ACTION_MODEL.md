# Pocket Watchtower — Owner Action Model v1.1

Pocket Watchtower is an owner-controlled forensic observatory. Observation and remediation are deliberately separated.

## Action classes

### 1. Review
- Open the Android app-info screen for a package.
- Review permissions, battery, storage, notifications, and system controls.

### 2. Stop
- For an ordinary app, Watchtower opens Android's system controls rather than pretending it can silently force-stop another package.
- If Watchtower is configured as device owner, Android device-policy capabilities may be used where the platform permits them.

### 3. Suspend
- Device/profile-owner policy can suspend packages.
- A failed or restricted suspension is reported as an Android limitation, not as a Watchtower success.

### 4. Uninstall
- The owner is handed to Android's uninstall/system UI.
- Watchtower never silently deletes another application.

### 5. File cleanup
- User-selected documents can be handed to the Storage Access Framework and deleted only when the selected document provider exposes deletion support.
- Watchtower-owned cache can be cleaned directly.
- Evidence history is not included in generic cleanup operations.

## Evidence-preserving rules

1. Every remediation action is owner-initiated.
2. The action target must be explicit.
3. Destructive actions use system confirmation or an Android-authorized policy path.
4. Failed actions remain visible as failures.
5. Observation never becomes an accusation merely because an action is available.
6. Cleanup never silently destroys Watchtower evidence.

## UX direction

Each app/process/file card should expose an **OWNER ACTIONS** area with:

- **Review** — open system details.
- **Stop** — open Android stop/force-stop controls when direct control is unavailable.
- **Suspend** — available only where Android policy authority permits it.
- **Uninstall** — open Android uninstall flow.
- **Delete selected file** — user selects the exact document through the system picker.
- **Clean Watchtower cache** — limited to Watchtower-owned cache.

The UI must explain *why* an action is available and *which Android authority* actually performs it.
