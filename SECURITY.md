# Security policy

## Reporting a vulnerability

Report vulnerabilities privately. **Do not open a public issue, pull request or discussion for a security problem.**

1. Open [Report a vulnerability](https://github.com/mandanai/devscope-compose/security/advisories/new) (Security tab > Advisories > Report a vulnerability). This uses GitHub Private Vulnerability Reporting.
2. Include what maintainers need to investigate:
   - Affected module(s) and version or commit.
   - Platform (Android/iOS) and OS version.
   - Steps or a minimal project to reproduce, and the observed impact (for example unredacted data in a span, export or log).
   - Any suggested fix.
3. Remove real credentials and personal data from anything you attach.

If you cannot use GitHub advisories, open an issue asking for a private contact without describing the vulnerability.

## Supported versions

JourneyScope is pre-release (`0.x`). Security fixes are applied to the latest release and `main`. Older versions are not guaranteed a fix. <!-- TODO(maintainer): confirm the support policy before the first stable release -->

## Disclosure

Please give maintainers reasonable time to investigate and release a fix before any public disclosure. Reports are assessed for reproducibility and impact (privacy leaks, redaction bypass, data exposure through export, and release-build exposure are in scope). Maintainers will coordinate the fix and advisory with you. No response or fix timeline is promised.

## Scope notes

JourneyScope has no network transport and stores traces locally; see [privacy and security](docs/privacy-security.md). Issues in third-party dependencies should be reported to their maintainers.
