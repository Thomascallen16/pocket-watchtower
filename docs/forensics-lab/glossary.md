# Plain-English glossary: Windows + Android

This glossary explains the words you will see in commands and reports.

| Term | Plain-English meaning |
|---|---|
| **ADB** | Android Debug Bridge: a command-line tool that lets an authorized computer communicate with an Android device. |
| **Android SDK Platform-Tools** | Google's official package containing ADB and related tools. |
| **Shell** | A text interface where you type commands. PowerShell is a Windows shell; Android also has a shell. |
| **PowerShell** | Microsoft's command-line and automation environment on Windows. |
| **Command / argument** | An instruction / the extra value that tells it what to act on. |
| **USB debugging** | An Android developer setting that allows an authorized computer to communicate with the device over ADB. |
| **Authorization** | The phone has explicitly trusted the connected computer for ADB. |
| **Device property** | A value Android exposes about the device or its build, often queried with `getprop`. |
| **Manufacturer / model** | The reported device maker and model identifier. These values are device-reported, not independent proof of physical identity. |
| **Build** | The installed operating-system software version and build configuration. |
| **Build fingerprint** | A system string identifying a particular Android build. It is useful for comparison, but it can be altered on modified devices. |
| **Security patch level** | The patch date reported by Android. It does not guarantee every fix is present or that the device is uncompromised. |
| **Serial number** | A device identifier that may be sensitive. Avoid publishing it in public logs or bug reports. |
| **Read-only collection** | Gathering information without intentionally changing the source device. Even connecting accessories or enabling debugging can alter state, so document those actions. |
| **Acquisition** | The process of collecting data from a source. A logical collection is not the same as a physical image. |
| **Logical extraction** | Data made available through interfaces, backups, or APIs, subject to permissions and operating-system limits. |
| **Physical image** | A bit-level copy of storage. Modern encryption and device protections often make this unavailable without specialized lawful methods. |
| **Artifact** | A piece of potentially useful digital evidence, such as a log, database, file, or timestamp. |
| **Metadata** | Information about data, such as file size, timestamps, sender fields, or device properties. Metadata can be missing, transformed, or misleading. |
| **Hash / SHA-256** | A fixed-length digest calculated from file bytes. Matching hashes support that two byte sequences are identical; a hash alone does not prove when or how a file was created. |
| **Manifest** | A list of collected files and their hashes. |
| **Chain of custody** | A record of who handled evidence, when, why, and how it was transferred or stored. |
| **Baseline** | A recorded snapshot used for comparison with a later snapshot. |
| **Diff** | A comparison showing what changed between two outputs. |
| **Permission** | An Android-controlled authorization an app may need to access a category of data or feature. |
| **Privilege / root** | Elevated access beyond a normal app or shell. Root access is not assumed by this lab. |
| **Package** | An Android application identifier, often written like `com.example.app`. |
| **Exit code** | A number a command returns; commonly 0 means success and non-zero indicates some kind of failure. Check the actual output too. |
| **stdout / stderr** | Standard output / standard error: two channels a command can use to print normal results and error messages. |
| **UTC / local time** | UTC is a common reference time zone; local time depends on the computer's time zone and daylight-saving rules. Record the time zone when interpreting timelines. |
| **Observed** | Directly returned or recorded by a tool. |
| **Inferred** | A reasoned interpretation drawn from observations; it is not the same as a directly observed fact. |
| **Unknown / not exposed** | The available tool could not determine the answer. This does not prove the event did or did not happen. |

## Reading a command

Example:

```powershell
adb shell getprop ro.build.version.release
```

- `adb`: run Android Debug Bridge on the computer.
- `shell`: ask the Android device to execute a command through its shell interface.
- `getprop`: query an Android system property.
- `ro.build.version.release`: the specific property requested, commonly the Android release version.

The output is the device's reported value. Record it as an observation, not as a guarantee about the device's entire security state.
