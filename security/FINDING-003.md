# FINDING-003: vulnerable dependency commons-text

| Field | Value |
| --- | --- |
| Severity | Critical |
| Advisory | CVE-2022-42889 |
| Package | org.apache.commons:commons-text |
| Installed version | 1.9 |
| Fixed version | 1.10.0 or later |
| Affected class | `org.apache.commons.text.StringSubstitutor` with the default interpolator |
| Source | Dependency scan of `scala/build.sbt` |
| Due | 7 days from detection under the policy for critical findings |

## Summary

The default interpolator in affected versions resolves `script`, `dns` and
`url` lookups. If untrusted input reaches `StringSubstitutor`, an attacker
can run code or make the service contact a remote host.

## What the security team needs to close this

- Whether the interpolator is used in this repo and what input reaches it.
- The upgrade, with the build and the replay tests passing.
- A re-scan showing the advisory no longer applies.

This finding was written by hand for a demo. Replace it with real scanner
output before presenting.
