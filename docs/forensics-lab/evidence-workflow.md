# Evidence-first handling workflow

This is a conservative learning workflow for a device you own or are authorized to examine. It is not a formal forensic standard or a guarantee of legal admissibility.

## Before collection

1. Write down why the device is being examined and who authorized the work.
2. Record date/time, time zone, device condition, battery state, connection state, and visible prompts.
3. Note every action taken, including connecting USB, enabling debugging, and approving the laptop.
4. If legal proceedings are involved, seek case-specific advice before altering the device. Do not factory-reset, root, unlock the bootloader, install a “cleaner,” or run repair tools.

## During collection

1. Use a trusted Windows laptop and official tools.
2. Review the collector script before running it.
3. Preserve raw outputs. Do not edit the collected files to make them “cleaner.”
4. If a command fails, preserve the error output and record the failure rather than silently retrying with broader privileges.
5. Do not treat an empty result as proof of absence. Android may restrict visibility.

## After collection

1. Keep the original collection folder unchanged.
2. Verify the SHA-256 manifest and store a separate copy of it.
3. Make a working copy for analysis.
4. For every conclusion, record: source file, exact observation, interpretation, alternative explanations, and confidence/unknowns.
5. Maintain a simple custody log for every copy or transfer.

## Claim discipline

Use these labels in notes:

- **OBSERVED** — directly shown in a collected output.
- **VERIFIED** — independently checked against a reliable source or repeatable test.
- **INFERENCE** — an interpretation that may have alternatives.
- **CONTRADICTION** — two sources disagree; preserve both.
- **UNKNOWN** — the available data cannot answer the question.

A timestamp correlation does not establish causation. A package appearing in an inventory does not establish that it was malicious or that a person installed it. A missing log does not establish that an event never occurred.
