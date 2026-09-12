# Security Policy

## Purpose

Pocket Watchtower is an owner-controlled Android observatory. It reports observable device state and changes; it does not infer compromise merely from an event.

## Reporting a security issue

Please report suspected security vulnerabilities privately to the repository owner rather than opening a public issue with exploit details.

Include:

- affected version or commit
- reproducible steps
- expected behavior
- observed behavior
- logs or evidence that can be safely shared
- any known security impact

Do not include passwords, private keys, tokens, or other secrets.

## Repository safeguards

Sensitive workflow and application changes should be reviewed before release. The repository should use protected `main` branch rules requiring successful validation, review, and preventing force-pushes or branch deletion.
