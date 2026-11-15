# FoodCost Premium Visual Redesign — Audit & Design Direction

**Date:** 2026-11-16  
**Baseline:** `main` @ `c6e1671c5cc9b8926e114dcb01a8e74c4eba311d`  
**Status:** Phase 1 + initial cross-screen design-token implementation. NOT a final product release.  
**Scope:** UI only; no changes to core pricing, data persistence, backup wire format, minSdk, app version, or sales calculation.

## 1. Verified current architecture

- Kotlin/Jetpack Compose/Material 3, with `app`, `presentation`, `core`, and `data` modules.
- Android `minSdk = 26`, `targetSdk = 36`, `versionName = 0.8.3`. Prior 0.7.x hardware assumptions are obsolete.
- Existing `Vazirmatn` regular/medium/bold assets; OFL license notice already bundled.
- UI colors currently centralized in `app/src/main/kotlin/ir/foodcost/app/ui/theme/Theme.kt`; no mass migration is necessary.
- Audited UI sources: `CalculatorScreen.kt`, `IngredientScreens.kt`, `RecipeScreens.kt`, `SettingsScreen.kt`, `BackupCard.kt`, `Components.kt`, `ReferenceUi.kt`, `GroupedUi.kt`, `HelpScreen.kt`, `Navigation.kt`, `FoodCostApp.kt`, `AppIcons.kt`.
- Existing PR #36 introduced verified pricing rules. Section pricing is present in baseline despite past removal requests: preserve this branch's existing behavior until a separate product change is decided.

## 2. Evidence-based audit findings (pre-change)

| Priority | Evidence | User impact | Treatment |
| --- | --- | --- | --- |
| P0 | Food-cost dashboard text `#EE6105` on `#FFF1E3`: approximately **2.98:1** contrast | Small monetary text difficult to read | `#964619` on `#FFF0E1`: **5.90:1** |
| P0 | Underpriced warning text `#E62625` on `#FFDEDC`: approximately **3.58:1** | Warning text below normal-text AA | `#A52725` on `#FCE7E4`: **6.06:1** |
| P1 | Search filter icon had directly clickable `24.dp` icon | Small action target | Wrap in `48.dp` IconButton; make common search well at least 48dp |
| P1 | Common `bodyLarge` 14sp and `bodyMedium` 13sp | Tight Persian body copy on dense screens | 15sp/22sp and 14sp/20sp; requires 2× font screenshot regression |
| P2 | Selected navigation tile used a decorative gradient | Visual noise | Solid selected surface + consistent shape |
| P2 | Global card corners reached 28dp | Visually bloated relative to dense financial forms | Reduce shared corner scale to 8/10/16/20/24dp |
| Watch | Pricing fields, backed drafts, settings screens, backup workflow | Any UI change may disturb persistence or input visibility | Do not touch business logic; run native calculator/IME/recovery regression |
| Watch | Surface statuses not encoded by color alone | A11y and clarity | Preserve text labels/icons; verify selected, error, empty and disabled states in native screenshots |

This is a **source-level audit**, not a claim that every native UI state or screen has been visually inspected after the change.

## 3. Three visual color directions

The HEX values below describe their intended **light-theme** color roles. Each pair of financial foreground/background must be checked before use; only direction C is currently applied.

| Role | A — Professional Modern | B — Premium Financial | C — Warm Contemporary (selected) |
| --- | --- | --- | --- |
| Primary | `#225B65` | `#253B52` | `#225D50` |
| Secondary | `#536D74` | `#62707B` | `#526A61` |
| Accent | `#A15D32` | `#986B31` | `#964619` |
| Background | `#F6F8F8` | `#F7F7F5` | `#F8F8F4` |
| Surface | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| Primary text | `#192B31` | `#1B2832` | `#1B2925` |
| Secondary text | `#526469` | `#55616A` | `#52645B` |
| Border | `#CCD7D8` | `#D2D8DB` | `#CAD4CB` |
| Success text / surface | `#236B47` / `#E5F4EB` | `#236342` / `#E7F3E9` | `#20693B` / `#E6F5E9` |
| Warning text / surface | `#805615` / `#FFF3DC` | `#805615` / `#FFF3DC` | `#805615` / `#FFF3DC` |
| Error text / surface | `#A52725` / `#FCE7E4` | `#A52725` / `#FCE7E4` | `#A52725` / `#FCE7E4` |
| Sales text / surface | `#245279` / `#EAF3F7` | `#204A89` / `#EDF2FB` | `#204A89` / `#EDF2FB` |
| Food cost text / surface | `#91511D` / `#FFF1E4` | `#86501F` / `#FDF0E3` | `#964619` / `#FFF0E1` |
| Selected state | Primary with white text | Primary with white text | `#225D50` with `#FFFFFF` |
| Disabled state | Neutral label / muted surface | Neutral label / muted surface | `#52645B` / `#E9EEEA` |

**Why C:** Carries forward FoodCost's familiar restrained forest-green/ivory identity; adds better role separation for money, a lighter neutral canvas, and lowers strong financial orange/red saturation. A is a strong alternative for a B2B utility; B could feel too corporate/cold for restaurant operators. None require illustrations, food photos, or decorative images.

## 4. Design system specification — implemented foundation

- **Font:** existing licensed Vazirmatn assets, no additional font download; three weights. RTL remains provided by `CompositionLocalProvider`.
- **Type:** display & headline remain M3 defaults using Vazirmatn; bodyLarge 15/22sp, bodyMedium 14/20sp, bodySmall baseline, labelLarge 14/20sp; currency figures continue through the existing `FitText` / `AmountText` implementations.
- **Space:** 4, 8, 12, 16, 24, 32dp, represented as `FoodCostSpacing`; future component work should consume these rather than repeating unrelated spacings.
- **Radius:** 8, 10, 16, 20, 24dp, via shared `MaterialTheme.shapes`.
- **Color:** `FoodCostTokens` hosts brand, financial, success, warning, error, on-surface, outline. M3 components consume `MaterialTheme.colorScheme`.
- **Financial semantics:** calculations and statuses continue to come from the pricing model; UI tokens do not encode business rules.
- **Interaction:** minimum target 48dp for modified header-add and filter actions; icon glyphs can stay smaller inside a larger hitbox.
- **Motion:** no new transitions, no spinner, no heavy assets; respect user preference for a quick calculator.
- **Dark theme:** existing scheme is retained. Native screenshot and WCAG verification are required before calling it fully approved.

## 5. Acceptance and QA requirements

1. Run `python3 scripts/check_design_contrast.py` (minimum 4.5:1 for checked normal text pairs).
2. Run existing JVM financial regressions and app Kotlin compilation, Android 14/15 instrumented suites, and native reference screenshot capture.
3. Compare all accessible screens before/after at 411dp, 320dp, 1×/1.5×/2× font; inspect overflow, clipped prices, RTL, numeric keyboard, focus, IME, snackbar, errors and dark theme.
4. Independently verify recipes, food cost/margin/markup conversions, section pricing, discounts, backup export/import and restart persistence with representative and boundary cases.
5. Measure startup, frame responsiveness, APK size delta and memory before claiming no regression.
6. Do not award a 90/100 score until screenshots and test evidence substantiate the rubric.

### Baseline evidence (not evidence of this PR)

Merged PR #36 reports 296 JVM tests and 31 instrumented Android tests on each of Android 14 and Android 15, plus signed build verification. Those reports document the **baseline**, not this redesign. The local environment cannot compile an Android APK; new CI results and real device verification must be treated separately.

## 6. Authoritative research

- Android Developers — Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Android Developers — Compose accessibility / target sizes: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android Developers — Compose text input: https://developer.android.com/develop/ui/compose/text/user-input
- W3C — WCAG 2.2 1.4.3 Contrast Minimum: https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html
- W3C — WCAG 2.2 2.5.8 Target Size Minimum: https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum
- Vazirmatn licensing: https://github.com/rastikerdar/vazirmatn

**Release gate:** the branch is a review candidate. Do not label it final/stable without current-commit automated tests, native screenshots and acceptance review.
