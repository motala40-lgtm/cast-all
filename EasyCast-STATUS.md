# Easy Cast — وضعیت پروژه (به‌روز شده)

## مشخصات کلی
* **اسم اپ:** Easy Cast (ایزی کست)
* **Package name:** com.app.castall.scanner
* **ریپازیتوری گیت‌هاب:** https://github.com/motala40-lgtm/cast-all
* **آخرین versionCode/versionName:** 1072 / 1.10.1
* **ایمیل پشتیبانی:** Newlifetech25@hotmail.com
* **Privacy Policy:** https://motala40-lgtm.github.io/cast-all/privacy-policy.html
* **compileSdk/targetSdk:** 36 (آپدیت شده برای الزام گوگل‌پلی از ۳۱ آگوست ۲۰۲۶)
* **AGP/Gradle:** 8.13.2 / 8.13

## محیط کاری کاربر
* فقط از **موبایل** (سامسونگ Z Fold5، Android 13، زبان سیستم سوئدی) استفاده می‌شود، بدون کامپیوتر.
* ابزار اصلی: **Termux** (نصب‌شده از F-Droid/گیت‌هاب، نه گوگل پلی) برای git/gh، و Chrome برای گیت‌هاب.
* کد از طریق Claude نوشته و zip می‌شود، کاربر با unzip در Termux پیاده‌سازی و git push می‌کند.
* گیت‌هاب اکشن (build.yml) به‌صورت خودکار APK (debug+release) و AAB (release) می‌سازد.
* پوشه‌ی پشتیبان کامل پروژه: `/storage/emulated/0/Download/ADM/EasyCast`

## روال استاندارد Deploy (همیشه ثابت)
```bash
cd ~
rm -rf ~/cast-all
unzip /storage/emulated/0/Download/FILENAME.zip -d ~/cast-all
cd ~/cast-all
git init
git remote add origin https://github.com/motala40-lgtm/cast-all.git
git add .
git commit -m "پیام"
git branch -M main
git push -f origin main
```
چک بیلد:
```bash
gh run list --limit 1 --json databaseId,status,conclusion --jq '.[0]'
```
اگه فیل شد:
```bash
gh run view <ID> --log-failed | grep -i "error" | head -40
```

### نکات ایمنی مهم
1. هر بار باید `git init` دوباره زده شود چون zip شامل `.git` نیست (مگر zip از یه پروژه‌ی دیگه با .git کپی شده باشه).
2. همیشه باید دقیقاً توی پوشه‌ی `~/cast-all` بود.
3. مسیر `~/storage/downloads` توی Termux گاهی خراب/غیرقابل‌اعتماده — همیشه از مسیر مستقیم `/storage/emulated/0/Download/` استفاده کن.
4. کلید امضای **debug** (`app/debug.keystore`) عمداً commit شده تا هر بیلد جدید امضای یکسان داشته باشه (نیازی به Uninstall نباشه). کلید **release** هرگز commit نمی‌شه (فقط از طریق GitHub Secret به‌صورت base64 رمزگشایی می‌شه).

## معماری کد (فایل‌های کلیدی)
* **MainActivity.java** — UI binding، Cast session، DLNA orchestration، media control، Settings sheet، Sleep Timer، Playlist، تم سفارشی
* **DlnaDevice.java** — مدل دستگاه DLNA
* **DlnaDiscovery.java** — کشف دستگاه SSDP
* **DlnaController.java** — دستورات SOAP (صف تک‌نخی + retry خودکار برای «Transition not available»)
* **CastKeepAliveService.java** — foreground service نگه‌داشتن CPU/Wi-Fi حین کست فایل محلی
* **LocalWebServer** (کلاس داخلی MainActivity) — سرور NanoHTTPD برای فایل محلی، مسیر ثابت `media.<ext>` (نه اسم واقعی فایل)

## فیچرهای کامل و تست‌شده ✅
1. کست ویدیو/عکس/موزیک محلی + از طریق URL (بخش تاشو، پایین صفحه)، هم Chromecast هم DLNA
2. **Playlist خودکار:** انتخاب چندتایی عکس/ویدیو از گرید گالری + امکان افزودن جدای موزیک؛ پیشروی خودکار (عکس=تایمر ۶ثانیه، ویدیو/موزیک=پایان طبیعی پخش)
3. کنترل پخش کامل (Play/Pause، ±۱۰ ثانیه، Seekbar، کنترل صدا)
4. **Settings آکاردئونی:** تم (روشن/تاریک/سیستم/سفارشی با عکس دلخواه) + رنگ اصلی (Accent، فقط روی پس‌زمینه و seekbar) + تایمر خواب (با بنر زنده روی صفحه‌ی اصلی) + Support
5. ۱۱ زبان: فارسی، انگلیسی، عربی، سوئدی، آلمانی، فرانسوی، اسپانیایی، ترکی، روسی، چینی
6. لوگوی بزرگ (۱۹۲dp) وسط صفحه، هدر و پس‌زمینه‌ی آبی هماهنگ
7. اسپینر «در حال اتصال» روی هر ردیف دستگاه هنگام تلاش برای وصل شدن
8. آیکون adaptive + legacy، Feature Graphic و متن Store Listing آماده برای گوگل‌پلی

## کارهای در حال انجام / ناقص ⚠️
* Mirror Cast (کست کردن خود صفحه‌ی گوشی) — **کامل حذف شده و کنار گذاشته شده** (بی‌ثباتی زیاد داشت)
* اسلایدشو با موزیک هم‌زمان (نه پشت‌سرهم) — از نظر فنی با Cast/DLNA معمولی ممکن نیست (یک استریم در هر لحظه)
* Edge-to-edge بعد از آپدیت به API 36: مشکل مشخص شد — `android:statusBarColor`/`navigationBarColor` توی styles.xml روی اندروید ۱۵+ نادیده گرفته می‌شن، پس هدر و دکمه‌ی پایین ممکن بود زیر نوار وضعیت/ناوبری بیفتن. در نسخه‌ی 1072 با `WindowCompat`/`ViewCompat` در MainActivity به‌صورت دستی درست شد (پدینگ بر اساس system bar insets). **هنوز روی گوشی واقعی چک بصری نشده** — بعد از نصب حتماً هدر بالا و دکمه‌ی «انتخاب دستگاه» پایین رو نگاه کن.

## نسخه‌بندی
* آخرین: **1072 / 1.10.1**
* برای هر تغییر بعدی باید افزایش یابد.
