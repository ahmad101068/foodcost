# FoodCost 0.8.0 — execution and audit evidence (2026-11-11)

## Provenance

- Upstream `main` SHA: `190ec43e1427c81c420de7f01e21645227e9ae10` (PR #31, financial pricing conversion fixes).
- Input: `foodcost-work-handoff.zip`; all 127 archived source files matched the handoff SHA-256 manifest. The accompanying patch SHA-256 was `abd8d46e6ba2dba30d6098908849e4dd1e577ee018ee64039af178bf79ed0c53`.
- The provided patch was reverse-checked against the provided source, then cleanly applied to a reconstructed baseline. Representative upstream base Git blob hashes were independently checked using the connected GitHub repository.
- No old PR #29/#30 code was merged; PR #31 conversion rules, 34-digit precision, validation of carried amounts, and its 19 regression tests remain intact.

## Verification matrix

| Check | Outcome | Evidence |
| --- | --- | --- |
| Handoff source checksum | PASS | 127/127 source files match manifest |
| Handoff patch checksum | PASS | SHA-256 matches manifest |
| `git apply --reverse --check`, `git apply --check` | PASS | Recovered patch round trip |
| Kotlin modules and unit tests, offline test runner (Kotlin 1.9.0) | **PASS: 267 passed, 0 failed** | `scripts/test-local.sh` output retained separately in delivery |
| Gradle Kotlin 2.1.20/JUnit 5 | NOT RUN LOCALLY | No Gradle distribution / network resolution in this environment; GitHub Actions needed |
| Android debug and androidTest Kotlin compile | NOT RUN LOCALLY | Android SDK not installed; GitHub Actions needed |
| Android emulator tests, SQLite, recovery guard, exit, screen capture | NOT RUN LOCALLY | No emulator / Android SDK; GitHub Actions needed |
| Production-signed release APK/AAB and certificate pin | NOT RUN LOCALLY | App's signing private key must only be used via GitHub Actions secrets |

The *local* Kotlin runner was made compatible with its available Kotlin 1.9.0 compiler by using `ArrayDeque.add` (equivalent tail insertion in the affected stack and test queue) and enabling Kotlin compiler friend paths for tests that exercise internal members. These minor changes do not change financial calculations or preferences. Gradle 2.1.20 remains the authoritative CI compiler.

## Expected behavior to verify in instrumentation

- General pricing: default target applies to every recipe; section/recipe overrides stay stored but inactive.
- Menu sections: recipe override, then section override, then general fallback; switching back must restore saved section targets.
- Legacy settings without `scope`: decode as `MENU_SECTIONS` to preserve pricing behavior. General-only settings encode their scope and require backup version 8. Existing canonical records in menu-sections mode omit the extra field.
- Legacy `NEW_RIAL` preference/draft migrates to toman without changing rial values; enum/codec remain backward-compatible.
- Settings saving is page-scoped; unfinished drafts in other pages do not overwrite saved values. Emergency backup remains accessible.
- Calculator retains the food-cost gauge/cards and supports category dropdown, optional discount/details, and exit confirmation. All screenshots must originate from a running APK on an emulator/device, not approved concept images.

## Delivery criteria still pending after local tests

GitHub Actions branch-specific Gradle results, emulator screenshots, signed release APK certificate verification, and manual/automated visual comparison are required before calling this release production-ready or merging into `main`.
