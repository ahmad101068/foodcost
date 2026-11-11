FoodCost 0.8.0: corrections after independent review

Legacy calculator drafts without a currency are rewritten atomically with an explicit unit before
the display preference is changed. A failed write retains the previous preference and original
file, so a restart can retry without interpreting new rials as toman. The same write ordering
also protects ordinary currency changes. Instrumented regression coverage includes restarting
without an explicit save and a blocked AtomicFile write followed by restart and retry.

The visual finish follows the approved ivory/forest-teal design: teal navigation and filters,
softer card outlines and fields, distinct indigo sales / peach food-cost / green remaining-money
dashboard accents, focused settings destinations with icons, and lighter list summaries.
The semicircle gauge and green/cream/red financial status rules are preserved.

List add actions live in the header instead of covering recipe cards. Tab and detail screens
reserve the measured snackbar height so controls remain reachable while feedback is visible.
Phone and enlarged-text channel comparisons use full-width cards. Tablets retain the table.

Acceptance tests use actual touch clicks for card/save navigation. System Back tests exercise
the device Back key, including hiding the keyboard before asking to exit. Large-text, dark-theme
and screenshot failures are propagated. Large-text channel amounts are checked for layout
overflow and all numbered acceptance screenshots (00 through 30) are required by the workflow.

Signed APK and AAB files, checksums and the public signing certificate report are retained only
in unpublished draft releases. Legacy public Actions binary artifacts are removed only after
the new signed binaries have been retained successfully. Synthetic walkthrough images and
test reports remain downloadable for review.

Validation for the final commit must include JVM tests, Android/test compilation, Android
instrumentation on API 34 and 35, release startup, matching signing certificate, and inspection
of the generated normal/dark/large-text screenshots. Final CI evidence belongs in the PR and
delivery report; this document does not assert a result before those checks have completed.
