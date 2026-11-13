# FoodCost 0.8.3 pricing corrections

The independent audit of 0.8.2 (`e90ff8b`) reproduced six failures while all 270 existing JVM tests passed. This change retains those eleven audit cases as regression tests and fixes all five findings.

- Same-remaining channel prices always round upward and are checked against the resulting sale. Normal menu rounding still follows the user's preference.
- An explicit minimum gross profit is also enforced after menu rounding. Unattainable guarantees return the existing unavailable/too-large result.
- Discount and commission use the retained share `(100 - percentage) / 100`, preventing cancellation near 100%. No-affordable-quote results remain nullable rather than throwing.
- General and section pricing share percentage conversion, full-precision round trips, cost-basis checks and manual-entry protection for incompatible conversions. Conversion history is transient; stored records and backup formats remain unchanged.
- Equivalent targets identify their cost basis. Actual channel margin/markup explicitly use all registered costs. The graphical dashboard and financial definitions remain in place.

Regression coverage includes independent formula oracles, all rounding directions and channels, minimum-profit and maximum-price boundaries, valid extreme percentages, stored settings-to-calculator integration, section conversions and inactive-draft isolation. A separate Android instrumentation process checks the real section controls, basis labels and accepted near-100% commission/discount input, retaining native screenshots. Existing storage, recovery, currency, physical Back, dark-mode and 2x-font checks continue to run.
