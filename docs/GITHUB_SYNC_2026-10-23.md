# GitHub source sync: 2026-10-23

Android 0.6.0/63 includes immediate list-delete confirmation, Android 12+ media-management authorization, and delete-badge cleanup after cancellation. iOS remains 0.5.10/60.

Remote main before synchronization: `8cac0563b70f179d2cd5e7f5b787decd53fd8dd3` (Android 0.6.0/61).

Validation on October 23:

- Release build, Lint and JVM test tasks succeeded; unchanged Gradle outputs were reused.
- 8 test suites, 44 tests, 0 failures and 0 errors.
- Lint: 0 errors, 17 warnings and 8 informational findings.
- Git whitespace validation passed.
- No device operations were repeated. See [October 22 device validation](ANDROID_LIST_DELETE_CONFIRM_VALIDATION_2026-10-22.md).

Synchronization includes source, resources, version configuration, tests and documentation. APK/IPA, signing material, machine configuration and private device records remain excluded.

After upload, verify the actual main ref and recursive tree against local paths, types, modes and Git blob SHAs, and check the remote version configuration. The final verified commit is reported in the chat. Earlier documents retain their historical pre-upload status.
