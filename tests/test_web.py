"""End-to-end tests for the app screens (android/app/src/main/assets/index.html).

The same page runs as the web app and inside the Android app. Tests run it in headless Chromium,
both as the web app and with a fake `window.Android` bridge standing in for the native side.
Any JavaScript error fails the test.

Run:  pip install pytest playwright && python -m playwright install chromium && pytest tests
"""
import json
import pathlib
import subprocess
import sys

import pytest
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAGE = (ROOT / "android/app/src/main/assets/index.html").as_uri()

# Fake native bridge. The "disk" lives in sessionStorage so it survives reloads, like data.json.
FAKE_ANDROID = """
(() => {
  const ss = sessionStorage, get = (k, d) => { const v = ss.getItem(k); return v === null ? d : v };
  const snaps = () => JSON.parse(get("snaps", "{}"));
  window.__calls = [];
  window.Android = {
    load: () => get("disk", ""),
    save: json => { window.__calls.push("save"); if(get("saveOk", "1") !== "1") return false; JSON.parse(json); ss.setItem("disk", json); return true },
    snapshots: () => JSON.stringify(Object.keys(snaps()).sort().reverse()),
    restore: d => { const s = snaps()[d]; if(!s) return false; ss.setItem("disk", s); return true },
    lastCrash: () => { const c = get("crash", ""); ss.removeItem("crash"); return c },
    status: () => JSON.stringify({notifications:true, exact:true, fullScreen:true, battery:true, camera:true, version:"test", versionCode:7}),
    prayerTimes: () => "",
    setBars(){}, share(){}, copy(){}, openUrl(){}, requestNotifications(){}, openExactAlarmSettings(){},
    openFullScreenSettings(){}, requestBattery(){}, requestLocation(){}, scanCode(){}, testAlarm(){}
  };
})();
"""


@pytest.fixture(scope="session")
def browser():
    with sync_playwright() as p:
        b = p.chromium.launch()
        yield b
        b.close()


class App:
    """One app session: a page plus the errors and analytics pings it produced."""

    def __init__(self, browser, android=False, scheme="light", width=360, preset=None):
        self.ctx = browser.new_context(viewport={"width": width, "height": 740}, timezone_id="Africa/Cairo",
                                       locale="ar-EG", color_scheme=scheme)
        self.errors, self.pings, self.requests = [], [], []
        if preset:
            # native-side state as it is when the app starts (set before the page loads; writing
            # sessionStorage right before a reload can race with Chromium's storage sync)
            self.ctx.add_init_script("if(!sessionStorage.getItem('seeded')){ sessionStorage.setItem('seeded','1'); "
                                     + " ".join(f"sessionStorage.setItem({json.dumps(k)}, {json.dumps(v)});" for k, v in preset.items()) + " }")
        if android:
            self.ctx.add_init_script(FAKE_ANDROID)
        self.page = self.ctx.new_page()
        self.page.on("pageerror", lambda e: self.errors.append(str(e)))
        self.page.on("console", lambda m: m.type == "error" and self.errors.append(m.text))
        self.page.route("**/*", self._net)
        self.page.goto(PAGE)
        self.page.wait_for_timeout(150)

    def _net(self, route):
        url = route.request.url
        if url.startswith("file:"):
            return route.continue_()
        self.requests.append(url)
        if "goatcounter" in url:
            self.pings.append(url)
            return route.fulfill(status=204, body="")
        if url.endswith("/version.json"):
            return route.fulfill(status=200, content_type="application/json", body=json.dumps({"versionCode": 99}))
        return route.abort()

    def click(self, sel):
        self.page.click(sel)

    def S(self):
        return self.page.evaluate("JSON.parse(JSON.stringify(window.__yawmi.S))")

    def js(self, code):
        return self.page.evaluate(code)

    def reload(self):
        self.page.reload()
        self.page.wait_for_timeout(150)

    def onboard(self, name="مهران"):
        p = self.page
        p.fill("#ob-name", name); self.click("[data-act=ob-next]")
        self.click("[data-act=ob-fajr][data-v=some]"); self.click("[data-act=ob-next]")
        self.click("[data-act=ob-work][data-v=office]"); self.click("[data-act=ob-next]")
        self.click("[data-act=ob-next]")
        self.click("[data-act=ob-train][data-v='4']"); self.click("[data-act=ob-supp][data-v=creatine]"); self.click("[data-act=ob-next]")
        for g in ("water", "teeth", "read"):
            self.click(f"[data-act=ob-goal][data-v={g}]")
        self.click("[data-act=ob-next]")
        self.click("[data-act=ob-finish]")
        p.wait_for_timeout(100)

    def toast(self):
        return self.page.text_content("#toast")

    def no_overflow(self):
        assert self.js("document.documentElement.scrollWidth <= window.innerWidth"), "page scrolls sideways"

    def close(self):
        errors = self.errors
        self.ctx.close()
        assert not errors, errors


@pytest.fixture
def app(browser):
    apps = []

    def make(**kw):
        a = App(browser, **kw)
        apps.append(a)
        return a

    yield make
    for a in apps:
        a.close()


# ---------- web app ----------

def test_onboarding_builds_the_day(app):
    a = app()
    a.onboard()
    s = a.S()
    assert s["onboarded"] and s["settings"]["name"] == "مهران"
    labels = [i["label"] for g in s["groups"] for i in g["items"]]
    assert "الفجر" in labels and "تمرين" in labels and "كرياتين" in labels
    gym = next(i for g in s["groups"] for i in g["items"] if i["label"] == "تمرين")
    assert gym["sched"] == {"type": "weekly", "times": 4}
    assert a.page.is_visible(".fajr")
    a.no_overflow()


def test_empty_start(app):
    a = app()
    a.click("[data-act=ob-empty]")
    s = a.S()
    assert s["onboarded"] and s["groups"] == []
    assert a.page.is_visible("[data-act=ob-start]")


def test_checks_survive_reload(app):
    a = app()
    a.onboard()
    item = a.page.locator("[data-act=toggle]").first
    iid = item.get_attribute("data-id")
    item.click()
    a.reload()
    key = a.js("Object.keys(window.__yawmi.S.days).sort().pop()")
    assert a.S()["days"][key]["checks"].get(iid) is True


def test_every_screen_renders(app):
    for scheme in ("light", "dark"):
        a = app(scheme=scheme)
        a.onboard()
        a.click("[data-act=fajr-open] >> nth=0")  # the Fajr tile opens the Fajr sheet
        a.page.keyboard.press("Escape")
        for view in ("settings", "edit", "reports"):
            a.js("window.scrollTo(0,0)")
            a.click(f"header [data-act={view}]")
            a.page.wait_for_timeout(80)
            a.no_overflow()
            if view == "reports":
                for period in ("week", "month"):
                    a.click(f"[data-act=rp-period][data-v={period}]")
                a.click("[data-act=hist] >> nth=0")
                a.page.wait_for_timeout(80)
                a.page.keyboard.press("Escape")
            if view == "edit":
                a.click("[data-act=sheet-item] >> nth=0")
                a.page.keyboard.press("Escape")
                a.click("[data-act=sheet-group] >> nth=0")
                a.page.keyboard.press("Escape")
            a.click("[data-act=back]")


def test_count_habit_fills_then_resets(app):
    a = app()
    a.onboard()
    iid = a.js("""(() => { const S = window.__yawmi.S; const it = S.groups.flatMap(g => g.items).find(i => i.label === 'قراية');
                  it.target = 3; it.unit = 'كوباية'; localStorage.setItem('yawmi', JSON.stringify(S)); return it.id })()""")
    a.reload()
    sel = f"[data-act=toggle][data-id='{iid}']"

    def today():
        return next(d for d in a.S()["days"].values() if iid in d.get("counts", {}) or iid in d.get("checks", {}))

    for n in (1, 2, 3):
        a.click(sel)
        assert today()["counts"][iid] == n
    assert today()["checks"][iid] is True
    a.click(sel)  # tapping a finished count habit starts it over
    assert not any(iid in d.get("checks", {}) or iid in d.get("counts", {}) for d in a.S()["days"].values())


def test_bad_data_never_crashes(app):
    a = app()
    results = a.js("""(() => { const n = window.__yawmi.normalize;
      const inputs = [null, undefined, 0, "x", [], {}, {groups:"bad"}, {groups:[{items:"no"}]}, {settings:null, days:null, tasks:null},
                      {groups:[{id:"g", items:[{id:"i", target:"9999", sched:{type:"days", days:[9,-1,"2"]}}]}], settings:{theme:{mode:"neon", accent:"pink"}, weekStart:4, fajr:{sleepHours:50}}}];
      return inputs.map(i => { const o = n(i); return [Array.isArray(o.groups), o.v, o.settings.theme.mode, o.settings.theme.accent, o.settings.weekStart, o.settings.fajr.sleepHours] }) })()""")
    for groups_ok, v, mode, accent, ws, sleep in results:
        assert groups_ok and v == 5 and mode in ("system", "light", "dark") and ws in (0, 1, 6) and 5 <= sleep <= 9
    fixed = a.js("""window.__yawmi.normalize({groups:[{id:"g", items:[{id:"i", target:"9999", sched:{type:"days", days:[9,-1,"2"]}}]}]}).groups[0].items[0]""")
    assert fixed["target"] == 99 and fixed["sched"] == {"type": "days", "days": [2]}


def test_corrupt_saved_data_starts_cleanly(app):
    a = app()
    a.js("localStorage.setItem('yawmi', '{\"groups\": [')")
    a.reload()
    assert a.page.is_visible("#ob-name")


def test_first_version_data_is_migrated(app):
    a = app()
    a.js("""localStorage.clear(); localStorage.setItem('dc2', JSON.stringify({days:{'2026-11-01':{checks:{fajr:true, quran:true}}}, tasks:{}}))""")
    a.reload()
    s = a.S()
    assert s["onboarded"] and len(s["groups"]) == 4
    assert s["days"]["2026-11-01"]["checks"] == {"fajr": True, "quran": True}


def test_import_merges_backup(app):
    a = app()
    a.onboard()
    backup = {"days": {"2026-10-01": {"checks": {"fajr": True}}}, "tasks": {"t1": {"text": "مشوار", "done": False}}}
    a.click("header [data-act=settings]")
    a.page.fill("#importbox", json.dumps(backup))
    a.click("[data-act=import]")
    s = a.S()
    assert s["days"]["2026-10-01"]["checks"]["fajr"] is True and s["tasks"]["t1"]["text"] == "مشوار"


def test_prayer_times_match_the_alarm(app):
    """Same reference values as PrayerTimesTest.kt, so the screen and the alarm always agree."""
    a = app()
    cases = {
        ("2026-01-15", 30.0444, 31.2357): "05:20 06:52 12:04 14:58 17:17 18:39",
        ("2026-06-21", 30.0444, 31.2357): "04:08 05:54 12:57 16:32 19:59 21:33",
        ("2026-11-14", 30.0444, 31.2357): "04:49 06:19 11:39 14:39 16:59 18:20",
        ("2026-12-21", 24.0889, 32.8998): "04:59 06:27 11:46 14:46 17:06 18:25",
    }
    for (d, lat, lng), want in cases.items():
        y, m, dd = map(int, d.split("-"))
        t = a.js(f"window.__yawmi.calcTimes(new Date({y},{m - 1},{dd},12),{lat},{lng})")
        assert " ".join(t[k] for k in ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")) == want, d


def test_errors_are_counted_without_details(app):
    a = app()
    a.onboard()
    a.js("setTimeout(() => { null.x }, 0)")
    a.page.wait_for_timeout(150)
    a.errors.clear()  # the error was on purpose
    hits = [p for p in a.pings if "%2Ferror%2F" in p]
    assert len(hits) == 1 and "TypeError" in hits[0]


def test_no_pings_when_stats_are_off(app):
    a = app()
    a.onboard()
    a.js("(() => { window.__yawmi.S.settings.analytics = false })()")
    a.pings.clear()
    a.js("setTimeout(() => { null.x }, 0)")
    a.page.wait_for_timeout(150)
    a.errors.clear()
    assert a.pings == []


# ---------- inside the Android app (fake bridge) ----------

def test_native_changes_are_not_overwritten(app):
    a = app(android=True)
    a.onboard()
    key = a.js("(() => { const d = new Date(); if(d.getHours() < 3) d.setDate(d.getDate()-1); return d.toLocaleDateString('en-CA') })()")
    ev = {"kind": "check", "date": key, "item": "fajr"}
    a.js(f"window.onNativeChange({json.dumps(ev)}); window.onNativeChange({json.dumps(ev)})")
    a.js(f"window.onNativeChange({json.dumps({'kind': 'wake', 'date': key, 'hm': '04:41'})})")
    other = a.page.locator("[data-act=toggle]").nth(3).get_attribute("data-id")
    a.click(f"[data-act=toggle][data-id='{other}']")  # a save from the page after the native change
    disk = json.loads(a.js("sessionStorage.getItem('disk')"))
    day = disk["days"][key]
    assert day["checks"]["fajr"] is True and day["checks"][other] is True and day["fajr"]["wake"] == "04:41"


def test_native_count_and_task_events(app):
    a = app(android=True)
    a.onboard()
    a.js("""(() => { const S = window.__yawmi.S; S.tasks.t1 = {text:'x', done:false, createdAt:1}; })()""")
    a.js("""window.onNativeChange({kind:'count', date:'2026-11-14', item:'w', n:2, done:false});
            window.onNativeChange({kind:'count', date:'2026-11-14', item:'w', n:1, done:false});
            window.onNativeChange({kind:'task', id:'t1', at:5}); window.onNativeChange({kind:'nonsense'})""")
    s = a.S()
    assert s["days"]["2026-11-14"]["counts"]["w"] == 2 and s["tasks"]["t1"]["done"] is True


def test_rejected_save_warns_the_user(app):
    a = app(android=True)
    a.onboard()
    a.js("sessionStorage.setItem('saveOk', '0')")
    a.click("[data-act=toggle] >> nth=0")
    assert "مقدرتش أحفظ" in a.toast()


def test_recovers_from_latest_snapshot(app):
    first = app(android=True)
    first.onboard("سارة")
    good = first.js("sessionStorage.getItem('disk')")
    # data.json and its backup are both unreadable (load returns ""), but yesterday's snapshot is fine
    a = app(android=True, preset={"disk": "", "snaps": json.dumps({"2026-11-13": good, "2026-11-12": good})})
    assert a.S()["settings"]["name"] == "سارة"
    assert "رجّعت بياناتك" in a.toast()


def test_restore_from_settings(app):
    a = app(android=True)
    a.onboard("قديم")
    old = a.js("sessionStorage.getItem('disk')")
    a.js(f"sessionStorage.setItem('snaps', JSON.stringify({{'2026-11-13': {json.dumps(old)}}}))")
    a.js("(() => { window.__yawmi.S.settings.name = 'جديد' })()")
    a.click("header [data-act=settings]")
    a.click("[data-act=restore]")
    a.click("[data-act=restore]")
    assert a.S()["settings"]["name"] == "قديم"


def test_crash_from_last_run_is_counted(app):
    first = app(android=True)
    first.onboard()
    doc = first.js("sessionStorage.getItem('disk')")
    a = app(android=True, preset={"disk": doc, "crash": "NullPointerException at Store.kt:42"})
    assert any("%2Fcrash%2Fv7%2FNullPointerException" in p for p in a.pings)


def test_update_check_uses_pages_not_api(app):
    a = app(android=True)
    a.onboard()
    a.js("localStorage.removeItem('updChecked')")
    a.reload()
    a.page.wait_for_timeout(200)
    assert json.loads(a.js("localStorage.getItem('upd')")) == {"v": 99}
    assert not any("api.github.com" in r for r in a.requests)


# ---------- repo ----------

def test_web_copy_is_in_sync():
    r = subprocess.run([sys.executable, str(ROOT / "tools/sync-web.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr or r.stdout
