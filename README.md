<div dir="rtl">

# يومي

**يومك بيبدأ من الفجر.**

منبه فجر مش بيسكت غير لما تقوم تمسح كود، وبيقولك تنام إمتى عشان تصحى. وحوالين الفجر بقية يومك: عاداتك وأهدافك ومهامك بمواعيدك انت. ابدأ بـ٥ أسئلة، أو بصفحة فاضية، أو بقوالب جاهزة (الصلوات، القرآن والأذكار، الصحة، العناية الشخصية، تطوير نفسي، رمضان).

## المميزات

- **علّم على يومك بدوسة:** كل بند خانة، وعداد بيوريك خلصت كام من كام.
- **مساحتك انت:** ضيف وامسح ورتّب أي قسم أو بند، ولكل واحد أيقونة ولون.
- **المظهر:** تلقائي أو فاتح أو داكن، و٦ ألوان أساسية تختار منها.
- **مواقيت الصلاة من غير نت:** محسوبة بطريقة الهيئة المصرية العامة للمساحة، لأي مدينة أو لموقعك.
- **إشعار عند كل أذان** فيه زرار «صليت ✓» يعلّم عليها من غير ما تفتح التطبيق.
- **تذكير لأي بند** في الساعة اللي تختارها، بس لو لسه ماعلّمتش عليه.
- **تاسكات ومشاوير** بتفضل موجودة لحد ما تخلصها، ومعاها تذكير اختياري.
- **Widget** على الشاشة الرئيسية، وعادات بالعدد (٨ كوبايات مية)، وتاريخ وسلسلة لكل عادة، وتقارير للأسبوع والشهر.
- **منبه الفجر:** بيرن بأعلى صوت ومفيهوش snooze، ومش بيسكت غير لما تمسح QR أو باركود متعلّق بعيد عن سريرك. وفيه بديل طوارئ بمسائل حساب عشان ماتتقفلش برة.
- **خصوصية:** كل بياناتك على جهازك بس. مفيش حساب ولا تسجيل دخول. التطبيق بيبعت مرة في اليوم إشارة مجهولة فيها نوع الجهاز بس (أندرويد، آيفون، لابتوب) عشان نعرف عدد المستخدمين، ولو حصل عطل بيبعت نوعه ومكانه في الكود بس. وتقدر تقفلها من الإعدادات.

## التثبيت

| الجهاز | الطريقة | المنبه والإشعارات |
|---|---|---|
| أندرويد | [نزّل yawmi.apk](https://github.com/mahran20/yawmi/releases/latest/download/yawmi.apk) وثبّته | ✅ |
| آيفون | افتح [mahran20.github.io/yawmi](https://mahran20.github.io/yawmi/) من Safari، دوس مشاركة ثم «إضافة إلى الشاشة الرئيسية» | ❌ (قيود آبل) |
| لابتوب | افتح [mahran20.github.io/yawmi](https://mahran20.github.io/yawmi/) من Chrome أو Edge، ودوس «تثبيت» | ❌ |

كل جهاز بيحفظ بياناته لوحده. عشان تنقل بنودك وتاريخك: الإعدادات ← «انسخ نسخة احتياطية» على جهاز، والصقها في «نقل البيانات» على التاني.

</div>

---

# Yawmi

**Your day starts at Fajr.**

A Fajr alarm that only stops when you get up and scan a code, and a bedtime worked out from Fajr so you can wake up for it. Around Fajr sits the rest of your day: your own habits, goals and tasks, on your schedule. Start with 5 quick questions, a blank page, or ready-made packs (prayers, Quran and adhkar, health, personal care, self-development, Ramadan).

## Features

- **One tap per item**, with a running count of what's done today.
- **Your own space:** add, delete and reorder any section or item, each with its own icon and color.
- **Appearance:** system, light or dark mode, and 6 accent colors.
- **Offline prayer times** using the Egyptian General Authority of Survey method, for any city or your location.
- **A notification at each adhan** with a "Prayed ✓" button that checks it off without opening the app.
- **Per-item reminders** at a time you choose, sent only if the item isn't checked yet.
- **Tasks and errands** that stay until done, with an optional reminder.
- **A home screen widget**, count habits (8 glasses of water), history and streaks for every habit, and weekly and monthly reports.
- **Fajr alarm:** full volume, no snooze, stops only when you scan a QR or barcode placed away from your bed. A math-problem fallback means you can never get locked out.
- **Private:** your data stays on your device. No account, no login. Once a day the app sends an anonymous ping with only the device type (Android, iPhone, laptop) so we can count users, and if something breaks, only the error type and its place in the code. You can turn it off in Settings.

## Install

| Device | How | Alarm & notifications |
|---|---|---|
| Android | [Download yawmi.apk](https://github.com/mahran20/yawmi/releases/latest/download/yawmi.apk) and install it | ✅ |
| iPhone | Open [mahran20.github.io/yawmi](https://mahran20.github.io/yawmi/) in Safari, tap Share, then "Add to Home Screen" | ❌ (Apple restrictions) |
| Laptop | Open [mahran20.github.io/yawmi](https://mahran20.github.io/yawmi/) in Chrome or Edge and click "Install" | ❌ |

Each device keeps its own data. To move your items and history, use Settings → "Copy a backup" on one device and paste it into "Move data" on the other.

## How it's built

- `android/` — native Android app (Kotlin): exact alarms, notifications, the lock-screen alarm with barcode scanning, and offline prayer-time calculation. The UI is a bundled web page.
- `index.html` — the same UI as a web app (PWA) for iPhone and laptops. Generated from the Android assets with `python3 tools/sync-web.py`.
- Every push to `android/` builds a signed APK with GitHub Actions and publishes it as the latest release. The app checks for new releases and offers the update.
