#!/usr/bin/env python3
"""Copy the app screens (android/app/src/main/assets) to the web app at the repo root.
Run after changing android/app/src/main/assets/index.html so web, iPhone and laptop stay identical.
With --check, change nothing and exit 1 if the web copy is out of date (used by the tests)."""
import pathlib, shutil, sys
root = pathlib.Path(__file__).resolve().parent.parent
src = root / "android/app/src/main/assets"
html = (src / "index.html").read_text(encoding="utf-8")
head = """<title>يومي</title>
<meta name="description" content="يومي: يومك بيبدأ من الفجر. منبه فجر، وعاداتك ومهامك بمواعيدك انت.">
<meta name="theme-color" content="#f3f5f9" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0e1324" media="(prefers-color-scheme: dark)">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icon-192.png">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="يومي">
<meta name="apple-mobile-web-app-status-bar-style" content="default">"""
assert html.count("<title>يومي</title>") == 1
web = html.replace("<title>يومي</title>", head, 1)
if "--check" in sys.argv:
    current = (root / "index.html").read_text(encoding="utf-8")
    fonts_ok = all((root / "fonts" / f.name).exists() for f in (src / "fonts").glob("*.woff2"))
    if current != web or not fonts_ok:
        sys.exit("index.html at the repo root is out of date: run python3 tools/sync-web.py")
    print("web app in sync")
    sys.exit(0)
(root / "index.html").write_text(web, encoding="utf-8")
(root / "fonts").mkdir(exist_ok=True)
for f in (src / "fonts").glob("*.woff2"):
    shutil.copy2(f, root / "fonts" / f.name)
print("web app synced")
