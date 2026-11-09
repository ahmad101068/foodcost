# FoodCost 0.7.9 — release preparation

- Direct APK: release-signed `foodcost.apk` in the private draft release `APK for testing`.
- Google Play: `app-release.aab` is built using the same private signing configuration and uploaded as a **private CI artifact**, not published automatically.
- Android API: compile/target 36; min 26.
- Run JVM tests, Android instrumentation tests, and Android 16 compatibility tests before submitting to Play Console.
- Verify signing certificate and upload-key enrollment with Play App Signing. APK installs from Play may have a different app signing certificate than the direct APK; cross-channel updates must be tested.
- Do not publish or make the GitHub draft release public without explicit approval.
- Prepare truthful Data Safety responses, privacy policy URL, screenshots, content rating, support contact, and country availability in Play Console.
- New personal Play Console accounts require a closed test with at least 12 opted-in testers for 14 continuous days before requesting production access.
- After the Play account exists, upload the AAB to internal testing first and check pre-launch reports.
