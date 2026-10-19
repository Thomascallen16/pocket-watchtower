# Windows setup: free Android examination tools

## 1. Start with a clean workspace

Create a folder such as `C:\\PhoneForensics\\Cases`. Keep case notes and collected exports there. Do not store passwords, unlock PINs, or account recovery codes in case notes.

## 2. Install Android Platform Tools

Download **Android SDK Platform-Tools** from the official Android developer site: https://developer.android.com/tools/releases/platform-tools

1. Extract the ZIP to a stable location, for example `C:\\Android\\platform-tools`.
2. Open PowerShell.
3. Run `cd C:\\Android\\platform-tools`.
4. Run `./adb.exe version` to confirm ADB launches.

You can either run commands from that folder or add it to your Windows PATH. Avoid unofficial “ADB installer” bundles.

## 3. Enable USB debugging only when appropriate

On your own Android phone, Developer options and USB debugging may be required for ADB. Menu names vary by manufacturer and Android version. Enable it only when you understand the implications, connect to your own trusted laptop, and approve only the computer you intend to authorize.

For evidence preservation, document the time you enabled debugging and any other device changes. If the phone may be evidence in an active legal matter, pause before changing settings and get case-specific preservation advice.

## 4. Verify the connection

With the phone connected by a known-good USB cable:

```powershell
./adb.exe devices -l
```

Check the phone screen for an authorization prompt. The expected state is `device`; `unauthorized` means the phone has not approved this computer. Do not try to bypass that prompt.

## 5. Run the baseline collector

From the repository folder, open PowerShell and run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\forensics\\Collect-AndroidBaseline.ps1 -OutputRoot "$env:USERPROFILE\\PhoneForensics\\Cases"
```

The script only runs non-destructive ADB queries and saves the output locally. Review the script before running it. If PowerShell policy or your organization's device policy blocks execution, do not weaken system policy globally; use an approved method.

## What this starter setup cannot do

ADB is not a magical full-phone extraction tool. Android version, manufacturer, USB debugging state, user authorization, app sandboxing, encryption, and privilege level all affect visibility. A baseline is a snapshot—not proof of who caused an event, and not a complete forensic image.
