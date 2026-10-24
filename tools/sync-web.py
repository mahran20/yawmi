#!/usr/bin/env python3
"""Copy the app screens (android/app/src/main/assets) to the web app at the repo root.
Run after changing android/app/src/main/assets/index.html so web, iPhone and laptop stay identical."""
import pathlib, shutil
root = pathlib.Path(__file__).resolve().parent.parent
src = root / "android/app/src/main/assets"
html = (src / "index.html").read_text(encoding="utf-8")
head = """<title>يومي</title>
<meta name="description" content="يومي: متابعة الصلوات والأذكار والعادات والتاسكات">
<meta name="theme-color" content="#f2f5f3" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#101513" media="(prefers-color-scheme: dark)">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icon-192.png">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="يومي">
<meta name="apple-mobile-web-app-status-bar-style" content="default">"""
assert html.count("<title>يومي</title>") == 1
(root / "index.html").write_text(html.replace("<title>يومي</title>", head, 1), encoding="utf-8")
(root / "fonts").mkdir(exist_ok=True)
for f in (src / "fonts").glob("*.woff2"):
    shutil.copy2(f, root / "fonts" / f.name)
print("web app synced")
