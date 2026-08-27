# Security policy

## Supported version

Security fixes are applied to the latest published GharTV release only.

## Reporting

Do not place phone numbers, OTPs, cookies, authorization headers, Jio tokens, DRM responses, signing keys or full Android logs in a public GitHub issue. Report the behaviour without credentials and share sensitive diagnostics privately with the repository owner.

## Release integrity

Official household builds are release-signed with the persistent GharTV signing key kept outside the repository. GitHub Releases publishes the APK together with its SHA-256 checksum. The built-in updater verifies that checksum before opening Android's installer.

## Boundaries

- no cleartext network traffic;
- no custom trust manager or hostname-verification bypass;
- no signing material committed to Git;
- no static DRM decryption keys;
- no subscription or entitlement bypass;
- OTP is never persisted;
- application backup is disabled.
