# Pocket Watchtower v1.1 — Correlation + Owner-Controlled Root

## Release target

Build on v1.0.0 without weakening its evidence boundaries.

### Required

1. Temporal correlation windows
2. Signal-family grouping
3. Human-readable explanations
4. Tap-through evidence drill-down
5. Current Device explanation parity

### Root

Root is treated as an owner-controlled capability, not a normal Android runtime permission.

Before a privileged probe:

- Explain what additional visibility could provide.
- Explain what the probe will do.
- Require explicit owner acceptance.
- Record consent locally.
- Use the normal device root authorization mechanism.

The first probe is identity-only (`id`) and is non-invasive. No exploit, bypass, persistence, hidden daemon, or device modification is permitted.

## Correlation model

A correlation is a time-bounded group of observations from one or more signal families.

Suggested families:

- Device state
- Battery / power
- Memory / resources
- Network / connectivity
- Application / package state
- Process visibility
- Accessibility / special access
- Security / administration

A stronger correlation is one containing multiple distinct families. Repetition of the same metric alone should not be inflated into a stronger finding.

Every correlation exposes:

- Time window
- Contributing signal families
- Exact observations
- Why it matters
- Ordinary possible explanations
- What would strengthen it
- What it does not prove
- Android visibility limitations

`0` correlations remains a valid result.

## Evidence drill-down

The navigation model is:

**Correlation → observation → event record**

The owner must be able to inspect timestamps, before/after values, source/status where available, and event hashes.

## Current Device parity

Current Device cards use the same explanatory fields as historical events:

- What it is
- What it means
- Why it matters
- What it does not mean
- Android visibility

This prevents live state from becoming an unexplained black box while historical events receive richer treatment.

## Safety / integrity boundary

The observatory reports observable state. It does not infer identity, intent, causation, compromise, spying, interception, or law-enforcement activity solely from timing or privileged access.
