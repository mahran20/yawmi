# How يومي is built

## The shape

```
android/app/src/main/assets/index.html   the whole UI (one page, no framework, no build step)
        │  same file, copied by tools/sync-web.py
        ├── index.html (repo root)        web app on GitHub Pages: iPhone, laptop
        └── Android app (WebView)         + native code for what a web page can't do
               Kotlin: alarms, notifications, widget, prayer times, file storage
```

There is **no server and no shared database**. Each phone keeps its own data, so more users never
means more load on anything we run. What can break at scale is variety (phone makers, Android
versions, odd data), which is what the tests and safeguards below are for.

The only things that receive traffic are static: GitHub Pages (the web app and `version.json`),
GitHub Releases (the APK), and GoatCounter (anonymous counts, a few requests per user per day).

## Data

One JSON document per device (shape in `defaults()` in index.html, currently `v: 5`).

- **Web:** `localStorage["yawmi"]`.
- **Android:** `files/data.json`, through `DataFile.kt`:
  - writes go to a temp file, are synced to disk, then renamed (never half-written);
  - the previous version is kept as `data.json.bak` and used if the main file is damaged;
  - a snapshot per day is kept for 7 days (`files/snapshots/`), restorable from Settings;
  - only a JSON object is ever written; if both files are unreadable, native code changes
    nothing and the app restores the newest snapshot.

**Who writes:** the page owns the document and saves it whole (debounced 500 ms). Native code
only makes additive changes (check an item, raise a count, record a wake time, finish a task)
and sends each one to the open page (`onNativeChange`), so the page's next save keeps it.

**Changing the shape:** add the field to `defaults()`, repair old or bad values in `normalize()`,
and add a case to `test_bad_data_never_crashes`. Never remove a field that old versions wrote
without migrating it there.

## Rules that must stay true

1. Prayer times are computed twice (JS for the screen, Kotlin for the alarm). Both are tested
   against the same reference values; change one, change both.
2. A day ends at 3 AM (`CUT` in JS, `Store.CUTOFF_HOUR` in Kotlin).
3. Weekdays are numbered like JavaScript: Sunday = 0 … Saturday = 6.
4. Nothing personal leaves the device. Analytics send only event names, the platform, the app
   version, and for errors only the error type and its line. The analytics switch turns all of it off.
5. Every alarm/reminder request code range is listed in `Scheduler.kt`; don't reuse one.

## Tests

| Where | What | Run |
|---|---|---|
| `tests/test_web.py` | the page in headless Chromium, as the web app and with a fake Android bridge: onboarding, every screen in both themes, bad and old data, import, count habits, native changes, failed saves, snapshot recovery, crash and error counts, update check, web copy in sync | `pip install pytest playwright && python -m playwright install chromium && pytest tests` |
| `android/app/src/test` | prayer times, schedules, day boundary, storage recovery and snapshots | `cd android && gradle testDebugUnitTest` |

Any JavaScript error during a web test fails it.

## Releasing

1. Change `android/app/src/main/assets/index.html`, then run `python3 tools/sync-web.py`.
2. For an Android change, raise `versionCode` (and `versionName`) in `android/app/build.gradle.kts`.
3. Push to `main`. GitHub Actions runs the tests; if they pass it builds, signs, publishes
   release `v<versionCode>` (APK + AAB) and writes `version.json`, which the app checks for updates.
   A push that forgets step 2 fails instead of silently replacing the last release.
4. If the web page changed, bump `CACHE` in `sw.js` so installed web apps pick it up.
