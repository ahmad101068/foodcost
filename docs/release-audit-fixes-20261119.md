# Release audit corrections for FoodCost 0.8.3

Baseline: `311d471e73c4d6f1aaa6fcca7fdb8d8dd268cf64`. These changes retain version
0.8.3, minSdk 26, targetSdk 36, financial formulas, the database schema and backup format.

| Finding | Correction | Regression evidence |
| --- | --- | --- |
| A01: renaming a menu section detaches its foods from their pricing rule | Keep the row's original identity during editing; save renamed rules and recipe associations atomically from the effective date, including scheduled future associations and rules | `MenuSectionsTest`, `CompleteAuditProbeTest`, `CompleteAuditNativeTest` |
| A02: a requested encrypted export becomes plaintext after component recreation | Persist only pending-request metadata. Keep the password in memory. Abort with an explanation if the encrypted request returns without its password | Native recreation, encrypted round-trip and explicitly unencrypted export cases |
| A03: incomplete extra costs are labeled suitable | Incomplete extras set `INCOMPLETE`; the dashboard uses the existing provisional-price label | JVM and native incomplete-cost cases |
| A04: deeply nested JSON causes a stack overflow | Reject nesting beyond 64 object/array levels as an ordinary `JsonException`; normal backup format and import handling remain intact | Boundary, deeply nested array and object regressions; existing backup suite |
| A05: Persian navigation words split at 320dp/200% font | Use two rows above 150% font on widths under 600dp, retaining font scaling and 48dp touch targets | Native Android configuration and text-layout assertions |
| A06: CI omits audit and UX tests | Share one explicit isolated-process suite between local checks, API 35 PR tests and API 34 walkthrough. Validate coverage against every native test method | `scripts/check_android_test_suite.py` |
| Intermittent pricing form race | Acknowledge the submitted settings group explicitly, independently of which intermediate frame Compose renders; retain edits on other pages and edits made during saving | `SettingsEditingTest`, existing `PricingMethodsTest` |
| Essential field boundary contrast | Use `#7F8B85` for input/strong outlines; retain pale decorative dividers | Contrast calculation against white and subtle surfaces |

Section names remain the persisted key. No identifier migration was introduced. Renames preserve
closed figures; a closed-date refusal or future-name collision rolls back the entire operation.
Archived foods are included. Backups replay the same existing dated operation types.

The export password is deliberately absent from saved instance state. A document picker can leave
an empty file if the export aborts; no financial data is written in that case. The user must start
the export again. Explicitly selecting an unencrypted backup remains supported.

The navigation change applies to accessibility font scaling. At the reference viewport and 100%
font the four-column structure remains. Stronger essential outlines are a documented contrast
correction relative to the pale reference images, not a claim of exact pixel equivalence.

## Validation and release boundary

Local JVM checks: 321 tests (77 core, 84 data, 160 presentation). The canonical native suite
contains 42 tests in 21 isolated runs. See CI run results and the delivery report for actual
Android execution outcomes; inclusion in the suite alone is not a passing execution result.

```sh
./gradlew :core:test :data:test :presentation:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check_android_test_suite.py
./scripts/pixel083/run_device_checks.sh "$(command -v adb)" native-results
```

Only run the native suite on a disposable emulator: its fixtures replace test data. The runner
refuses a physical device. Signed build jobs now require a successful complete walkthrough, run
all three JVM suites and release lint, and continue to store binaries only in unpublished drafts.

The previous strict SSIM target of 0.98 is **not** established by these fixes. Reference fidelity,
TalkBack, a physical-device run, runtime 16KB-page validation and upgrading an owner's installed
production data remain separate acceptance evidence. Passing these regressions must not be
represented as a production-release approval or proof that every release criterion has passed.
