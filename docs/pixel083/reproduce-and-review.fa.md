# بازتولید و بازبینی کاندید

این شاخه کاندید بررسی مالک است و مجوز ادغام یا انتشار ندارد. مبنا `6c2bf74b713f4febfda53019324af053298d56ef`، نسخهٔ برنامه 0.8.3، minSdk برابر 26 و targetSdk برابر 36 باقی مانده‌اند.

## سورس و Build

بستهٔ خصوصی شامل آرشیو سورس commit تحویلی، Git bundle شاخهٔ مستقل، diff نسبت به مبنا، گزارش‌ها، پنج PNG مرجع، تصاویر اجرای واقعی و APK آزمایشی است. Git bundle را می‌توان مستقل از اتصال GitHub باز کرد:

```sh
git clone -b feature/foodcost-083-pixel-accurate-ui FoodCost-0.8.3-Pixel-Candidate.bundle foodcost-review
cd foodcost-review
```

برای Build، JDK 17 و Android SDK 36 با Build Tools 35 لازم‌اند. پروژه از wrapper اصلی Gradle 8.14.3 استفاده می‌کند. مسیر SDK را طبق محیط خود در `local.properties` یا `ANDROID_HOME` تعیین کنید؛ مسیرهای همین محیط وارد سورس تحویلی نشده‌اند.

```sh
./gradlew :core:test :data:test :presentation:test
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

بدون کلید اصلی، خروجی release عمداً unsigned است. APK قابل نصب تحویلی debug با شناسهٔ `ir.foodcost.app.debug` است و کنار برنامهٔ اصلی نصب می‌شود؛ اطلاعات نسخهٔ اصلی را خودکار نمی‌خواند. هیچ keystore، رمز یا کلید انتشار داخل بسته نیست. برای بازتولید امضای همان فایل تحویلی، صرفاً داشتن سورس کافی نیست؛ checksum و گواهی عمومی برای شناسایی همان APK ثبت شده‌اند.

## تصاویر و اندازه‌گیری

گالری `review/index.html` پس از استخراج ZIP، بدون اینترنت باز می‌شود. اسلایدر روی ناحیهٔ متعلق به برنامه کار می‌کند؛ لینک تصویر، PNG خام را باز می‌کند. PDF شش‌صفحه‌ای برای مشاهدهٔ سریع است. مرجع با دادهٔ واقعی برنامه در بعضی اعداد، وضعیت‌ها و برچسب‌ها تفاوت دارد؛ گزارش `functional-reference-differences.fa.md` این موارد را توضیح می‌دهد.

ابزارهای Python تحلیل به Pillow، NumPy و ReportLab نیاز دارند؛ هیچ‌کدام وابستگی runtime اپلیکیشن نیستند. فرمان‌های بازتولید پس از استخراج بسته:

```sh
python3 scripts/pixel083/compare_images.py --reference docs/pixel083/reference --actual evidence/matrix-delivery-api26/411-100 --out comparison-reproduced
python3 scripts/pixel083/geometry_audit.py --spec docs/pixel083 --actual evidence/matrix-delivery-api26/411-100 --out geometry-reproduced.json
python3 scripts/pixel083/color_audit.py --root docs/pixel083 --actual evidence/matrix-delivery-api26/411-100 --out color-reproduced.json
python3 scripts/pixel083/audit_native_layouts.py --matrix evidence/matrix-delivery-api26 --out viewport-reproduced.json
```

در این فرمان‌ها فرض می‌شود تصاویر مرجع در مسیر `docs/pixel083/reference` و شواهد در مسیر `evidence` کنار سورس استخراج شده‌اند. فایل‌های مرجع و شواهد حجیم به مخزن عمومی فرستاده نشده‌اند؛ نسخهٔ دقیق آن‌ها فقط در بستهٔ خصوصی همراه است. نوارهای سیستم API26 با inset بالای 55px و پایین 110px کنار گذاشته می‌شوند. تصویر 942px با مقیاس یکنواخت به عرض 941px تبدیل می‌شود؛ هیچ کشیدگی عمودی یا حذف متن مالی انجام نمی‌شود.

## حدود نتیجه

هدف SSIM کلی 0.98 و اجزای اصلی 0.97 هنوز تأیید نشده‌اند. موفقیت Build یا تست مالی به معنی قبولی تطبیق تصویر نیست. API34 و API35 با AOSP ATD اجرای عملکردی دارند، اما فریم سیاه آن محیط‌ها شاهد بصری نیست. آزمون روی گوشی فیزیکی، TalkBack و اندازه‌گیری واقعی مصرف باتری انجام نشده است. نتیجهٔ دقیق هر معیار در گزارش تحویل و JSONهای همراه ثبت شده است.
