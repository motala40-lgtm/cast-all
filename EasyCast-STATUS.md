# Easy Cast — وضعیت پروژه (به‌روز شده)

## مشخصات کلی
* **اسم اپ:** Easy Cast (ایزی کست)
* **Package name:** com.app.castall.scanner
* **ریپازیتوری گیت‌هاب:** https://github.com/motala40-lgtm/cast-all
* **آخرین versionCode/versionName:** 1052 / 1.5.2
* **ایمیل پشتیبانی:** Newlifetech25@hotmail.com
* **Privacy Policy:** https://motala40-lgtm.github.io/cast-all/privacy-policy.html

## محیط کاری کاربر
* فقط از **موبایل** (سامسونگ Z Fold5، Android 13، زبان سیستم سوئدی) استفاده می‌شود، بدون کامپیوتر.
* ابزار اصلی: **Termux** (نصب‌شده از F-Droid/گیت‌هاب، نه گوگل پلی) برای git/gh، و Chrome برای گیت‌هاب.
* کد از طریق Claude نوشته و zip می‌شود، کاربر با unzip در Termux پیاده‌سازی و git push می‌کند.
* گیت‌هاب اکشن (build.yml) به‌صورت خودکار APK می‌سازد.
* برای گرفتن لاگ کرش: منوی مخفی سامسونگ (*#9900# → SysDump → dumpstate/logcat) یا wireless adb.

## روال استاندارد Deploy (همیشه ثابت)
```bash
cd ~
rm -rf ~/cast-all
unzip ~/storage/downloads/FILENAME.zip -d ~/cast-all
cd ~/cast-all
pwd   # باید بشه: /data/data/com.termux/files/home/cast-all
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
1. هر بار باید `git init` دوباره زده شود چون zip شامل `.git` نیست.
2. همیشه باید دقیقاً توی پوشه‌ی `~/cast-all` بود؛ اگر اشتباهی توی `~` باشیم، فایل‌های شخصی دیگر پابلیک می‌شوند.
3. Termux از F-Droid نصب شده — برای `termux-setup-storage`، مجوز **"All files access"** باید جدا و دستی از تنظیمات گوشی (Permission manager → Files) به Termux داده شود؛ فقط "Files and media" معمولی کافی نیست.

## معماری کد (فایل‌های کلیدی)
* **MainActivity.java** — UI binding، Cast session، DLNA orchestration، media control، Settings sheet، Sleep Timer، Mirror Cast wiring
* **DlnaDevice.java** — مدل دستگاه DLNA
* **DlnaDiscovery.java** — کشف دستگاه SSDP
* **DlnaController.java** — دستورات SOAP (Play/Pause/Stop/Seek/SetAVTransportURI + پروفایل‌های DLNA برای MP3/JPEG)
* **CastKeepAliveService.java** — foreground service نگه‌داشتن CPU/Wi-Fi حین کست فایل محلی
* **ScreenMirrorService.java** — سرویس Mirror Cast: MediaProjection + MediaCodec (H.264) + TsMuxer + MirrorHttpServer
* **TsMuxer.java** — ماکسر دستی MPEG-TS برای بسته‌بندی خروجی انکودر به سگمنت‌های HLS
* **MirrorHttpServer.java** — سرور NanoHTTPD اختصاصی برای پخش playlist.m3u8 + سگمنت‌های .ts (پورت 8091)
* **LocalWebServer** (کلاس داخلی MainActivity) — سرور NanoHTTPD برای فایل محلی (پورت 8080)؛ دیگر بر اساس اسم فایل مسیر نمی‌سازد (مسیر ثابت `media.<ext>`)
* **res/values/colors.xml + values-night** — پالت رنگی + رنگ‌های اختصاصی دکمه‌های Video/Photo/Music/Mirror + گرادیانت آسمانی
* **res/layout/bottom_sheet_settings.xml** — شیت تنظیمات (تم + تایمر خواب)
* **res/layout/activity_cast.xml** — صفحه‌ی اصلی

## فیچرهای کامل و تست‌شده ✅ (تجمیعی، از ابتدای پروژه تا الان)
1. کست ویدیو/عکس/موزیک محلی + از طریق URL، هم Chromecast هم DLNA
2. پشتیبانی DLNA کامل (رفع باگ‌های chunked encoding، MIME mismatch، کد 206)
3. کنترل پخش کامل (Play/Pause، ±۱۰ ثانیه، Seekbar)
4. تم روشن/تاریک با دکمه‌ی اختصاصی در Settings (ذخیره‌شده در SharedPreferences)
5. لوگوی جدید (بدون هاله‌ی آبی، شفاف) به‌جای متن Easy Cast در هدر
6. حذف نوار "در حال جستجو" از صفحه‌ی اصلی
7. گالری استاندارد اندروید (Photo Picker) برای انتخاب ویدیو/عکس به‌جای چوزر عمومی فایل
8. دکمه‌های رنگی: ویدیو=سبزآبی، عکس=نارنجی، موزیک=بنفش، میرور=آبی آسمانی
9. پس‌زمینه‌ی گرادیانت آبی آسمانی (روشن‌تر به سمت بالا)
10. Settings Sheet: انتخاب تم + تایمر خواب (۱۵/۳۰/۶۰ دقیقه یا لغو) — با پاز خودکار پخش
11. فیکس مهم: امکان تعویض فایل حین پخش بدون نیاز به بستن اپ (سرور محلی دیگر ری‌استارت نمی‌شود، فقط سورس آپدیت می‌شود)
12. فیکس مهم: آدرس URL فایل محلی دیگر به اسم واقعی فایل وابسته نیست (مسیر ثابت بر اساس نوع فایل) — رفع خطای "فرمت نامعتبر" روی تلویزیون
13. فیکس DLNA: پروفایل دقیق برای MP3/JPEG (چون تلویزیون‌های سامسونگ بدون این پروفایل فایل صوتی را رد می‌کردند)
14. Mirror Cast فاز ۱: گرفتن مجوز MediaProjection + capture pipeline پایه
15. Mirror Cast فاز ۲: انکود H.264 زنده (MediaCodec) + بسته‌بندی MPEG-TS دستی (TsMuxer) + سرور HLS زنده (MirrorHttpServer) + اتصال خودکار به Chromecast/DLNA متصل
16. فیکس باگ مهم TsMuxer: محاسبه‌ی adaptation field وقتی هم PCR هم padding لازم بود (فریم‌های خیلی کوچیک صفحه‌ی ساکن) باعث تزریق بایت خراب می‌شد — فیکس شد

## کارهای در حال انجام / ناقص ⚠️
1. **Mirror Cast هنوز کامل کار نمی‌کند:**
   * فاز ۲ (v1052) باگ اصلی بسته‌بندی TS رفع شد، ولی طبق آخرین تست کاربر هنوز روی سامسونگ می‌چرخد (تست دقیق بعد از فیکس آخر هنوز کامل تأیید نشده).
   * قدم بعدی: تست دقیق روی هر دو دستگاه (Android و Samsung) بعد از v1052؛ اگر باز هم مشکل بود، چک کردن مستقیم playlist از طریق مرورگر گوشی (`http://127.0.0.1:8091/playlist.m3u8`) و اگر segment واقعاً پخش می‌شود ولی روی Samsung DLNA خاص کار نمی‌کند، احتمالاً محدودیت خود دستگاه است نه کد.
   * نکته: ممکن است لازم باشد رزولوشن/بیت‌ریت انکودر (فعلاً حداکثر ۹۶۰px، ۲Mbps، ۱۵fps) برای دستگاه‌های کندتر تنظیم شود.
2. **Playlist خودکار / صف پخش** (چند فایل پشت سر هم، خودکار برود بعدی) — هنوز شروع نشده.
3. **اسلایدشوی عکس با تایمر** — هنوز شروع نشده.
4. کارهای قدیمی‌تر که احتمالاً از قبل حل شده ولی دوباره چک نشده:
   * تاریخچه‌ی کست‌های اخیر (ایده مطرح شد، ساخته نشد)
   * اتصال خودکار به آخرین دستگاه هنگام باز شدن اپ (ایده مطرح شد، ساخته نشد)

## نسخه‌بندی
* آخرین: **1052 / 1.5.2**
* برای هر تغییر بعدی باید افزایش یابد.
