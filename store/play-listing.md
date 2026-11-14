# يومي على Google Play: كل اللي هتلصقه في Play Console

كل الصور جاهزة في الفولدر ده:
- `icon-512.png`: أيقونة التطبيق (512×512)
- `feature-graphic.png`: الصورة الكبيرة (1024×500)
- `screenshots/`: ٧ صور للموبايل (1080×1920)
- ملف التطبيق: `yawmi.aab` من [آخر release](https://github.com/mahran20/yawmi/releases/latest)
- سياسة الخصوصية: https://mahran20.github.io/yawmi/privacy.html

---

## Store listing: بالعربي (اللغة الأساسية: ar)

**اسم التطبيق (٣٠ حرف بالكتير):**
يومي: عاداتك وصلواتك ومهامك

**وصف قصير (٨٠ حرف بالكتير):**
تابع صلواتك وعاداتك ومهامك بمواعيدك انت، مع تقارير ومنبه فجر بيصحّيك بجد.

**الوصف الكامل:**
يومي مساحة بسيطة لمتابعة يومك زي ما انت عاوزه.

ابدأ بـ٥ أسئلة سريعة: بتشتغل ولا بتدرس، وأيام إجازتك، وبتتمرن كام يوم في الأسبوع، وعاوز تبني إيه. ويومي يبنيلك مساحة على مقاسك. أو ابدأ بصفحة فاضية وابنيها بنفسك.

• مواعيد لكل بند: كل يوم، أو أيام معينة (الشغل من الأحد للخميس بس)، أو عدد مرات في الأسبوع (التمرين ٤ مرات). ومش هيتحسب عليك في الإجازة أو يوم الراحة.
• مواقيت الصلاة من غير نت، وإشعار عند كل أذان فيه زرار «صليت ✓».
• منبه فجر بأعلى صوت ومفيهوش snooze، ومش بيسكت غير لما تقوم تمسح كود متعلّق بعيد عن سريرك.
• تاسكات ومشاوير بآخر يوم أو بمعاد، ممكن ترن زي المنبه، وتفضل تفكّرك لحد ما تخلصها.
• تقارير للأسبوع والشهر وأي فترة: نسبة الإنجاز، أقوى وأضعف عادة، أحسن يوم في الأسبوع، والسلسلة.
• أيقونة ولون لكل قسم وبند، ووضع فاتح وداكن و٦ ألوان.
• خصوصية: بياناتك على موبايلك بس. مفيش حساب ولا تسجيل دخول.

---

## Store listing: English (add as a translation: en-US)

**App name:** Yawmi: Habits, Prayers & Tasks

**Short description:** Track prayers, habits and tasks on your schedule, with reports and a Fajr alarm.

**Full description:**
Yawmi is a simple space to track your day the way you want.

Answer 5 quick questions (work or study, your days off, how many days you train, what you want to build) and Yawmi builds a space that fits you. Or start from a blank page.

• A schedule for every item: every day, specific days (work only Sunday to Thursday), or N times a week (train 4 times). Days off and rest days never count against you.
• Offline prayer times and a notification at each adhan with a "Prayed ✓" button.
• A Fajr alarm at full volume with no snooze that only stops when you get up and scan a code placed away from your bed.
• Tasks and errands with a deadline or a time. They can ring like an alarm and keep reminding you until you mark them done.
• Reports for this week, this month or any period: completion rate, strongest and weakest habits, best day of the week, streaks.
• An icon and color for every section and item, light and dark mode, and 6 accent colors.
• Private: your data stays on your phone. No account, no login.

---

## App content (السياسات)

| السؤال | الإجابة |
|---|---|
| Privacy policy | `https://mahran20.github.io/yawmi/privacy.html` |
| Ads | No, my app does not contain ads |
| App access | All functionality is available without special access |
| Target audience | 18 and over (اختار 13–17 كمان لو عاوز، بس ده بيزوّد مراجعة Families) |
| Content rating | جاوب الاستبيان: مفيش عنف ولا محتوى جنسي ولا مقامرة ولا تواصل بين المستخدمين. هيطلع Everyone / 3+ |
| News app | No |
| Government app | No |
| Financial features | None |
| Health apps | No (هو متابعة عادات، مش تطبيق صحي طبي) |

### Data safety

- **Does your app collect or share any of the required user data types?** Yes
- **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS)
- **Do you provide a way for users to request that their data is deleted?** Yes (الإحصائيات مجهولة، ومفيش بيانات شخصية متخزنة على سيرفر. وتقدر تقفلها من الإعدادات)
- **Data types collected:**
  - **App activity → App interactions:** Collected · Not shared · Optional (users can turn it off) · Purpose: **Analytics**
  - **Device or other IDs:** Not collected
  - **Location:** Not collected (الموقع بيتحسب عليه المواقيت على الجهاز ومش بيطلع منه)
  - **Photos/videos:** Not collected (الكاميرا للمسح بس)
  - **Personal info / Health:** Not collected (كل اللي بتكتبه على الجهاز بس)

### Permission declarations

**Foreground service: systemExempted (Android 14+)**
- **Feature:** The Fajr wake-up alarm and task alarms. When an exact alarm fires, a foreground service plays the alarm sound and vibration until the user dismisses it.
- **User impact if delayed or interrupted:** The user would not be woken up for the Fajr prayer, or would miss a task they asked to be alarmed for.
- **Video:** صوّر شاشة الموبايل ٣٠ ثانية وارفعه على YouTube (Unlisted): افتح الإعدادات ← منبه الفجر ← «جرّب المنبه بعد ٢٠ ثانية» ← اقفل الشاشة ← المنبه يرن ← امسح الكود ← يسكت.

**Full-screen intent (USE_FULL_SCREEN_INTENT)**
- اختار: **Alarm** (My app's core functionality includes alarms). المنبه بيظهر على الشاشة المقفولة عشان المستخدم يقدر يسكته.

**Exact alarms (SCHEDULE_EXACT_ALARM)**
- مش محتاج declaration. المستخدم بيفعّله بنفسه من الإعدادات، والتطبيق بيوريله زرار «فعّله».

**Camera**
- بيُستخدم بس لمسح كود QR أو باركود تسكيت المنبه. مش محتاج declaration.

---

## Release notes (لكل release)

```
<ar>
مواعيد لكل بند (أيام معينة أو عدد مرات في الأسبوع)، أسئلة ذكية تبني مساحتك، تقارير أسبوعية وشهرية، وتاسكات بآخر يوم ورنّة وتذكير متكرر.
</ar>
<en-US>
Per-item schedules (specific days or N times a week), a smart setup, weekly and monthly reports, and tasks with deadlines, alarm rings and repeat reminders.
</en-US>
```
