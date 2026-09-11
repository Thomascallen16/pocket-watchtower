# Pocket Watchtower — Release Notes

## v1.1.0 — Integrity Observatory baseline

This release establishes the first coherent 1.x release identity for Pocket Watchtower.

### Release-ready foundations

- Canonical application version: `1.1.0` / version code `110`.
- Android release CI derives the APK filename and artifact name from Gradle `versionName` instead of a stale hard-coded version.
- Fixed the Compose visual layer compilation failure in `ObservatoryVisuals.kt`.
- The dashboard is now the single Android launcher entry point; Owner Actions remains an internal activity.
- Local baseline, observable change detection, evidence history, SHA-256 event chaining and verification remain preserved.
- Event intelligence and correlation views remain explicitly evidence-first: correlation is not causation.
- Human-readable audit export remains local/share-based and does not require an account or backend.
- Owner remediation stays Android-mediated and owner-controlled.

### Trust boundary

Pocket Watchtower does not claim to identify an actor from an observation, prove compromise from the absence of evidence, or see provider-side records that Android does not expose.

### Validation

The post-fix Android release build completed successfully before the 1.1.0 release-alignment work. The next CI run is the release-candidate validation for the versioning and manifest changes in this release.
