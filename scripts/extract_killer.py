#!/usr/bin/env python3
"""
Extract the Killer engine from TRMX-GUI main (PR #70, merge 09c8f722) into dev.killer.

The engine is copied verbatim except for:
  * package rename  dev.trmx.gui.killer -> dev.killer.engine
  * TRMX/Termux dependencies removed (bridge packages, MainActivity return intent)
  * MainActivity return intent retargeted to Killer
  * scripts/patches/trmx-engine-safety.patch adds Killer safety and result checks

Usage: python3 scripts/extract_killer.py <trmx-killer-dir> <dest-dir>
"""
import re, sys, pathlib, subprocess

SRC = pathlib.Path(sys.argv[1])
DST = pathlib.Path(sys.argv[2])
DST.mkdir(parents=True, exist_ok=True)

def read(name):
    return (SRC / name).read_text(encoding="utf-8")

def rename_pkg(text):
    return (text.replace("package dev.trmx.gui.killer", "package dev.killer.engine")
                .replace("dev.trmx.gui.killer", "dev.killer.engine"))

# ---------------------------------------------------------------- AppInfo.kt
appinfo = rename_pkg(read("AppInfo.kt"))
(DST / "AppInfo.kt").write_text(appinfo, encoding="utf-8")

# ------------------------------------------------------------ AppCategories.kt
cats = rename_pkg(read("AppCategories.kt"))
cats = re.sub(
    r"\n\s*// TRMX & Termux bridge.*?\n\s*\"com.termux\.styling\",\n",
    "\n        // Killer itself — closing it mid-run would abort its own engine.\n        \"dev.killer\",\n",
    cats, count=1, flags=re.S)
cats = cats.replace('if (pkg == "dev.trmx.gui") return true', 'if (pkg == "dev.killer") return true')
cats = cats.replace('if (pkg.startsWith("com.termux")) return true\n', '')
(DST / "AppCategories.kt").write_text(cats, encoding="utf-8")

# ------------------------------------------- AppKillerAccessibilityService.kt
svc = rename_pkg(read("AppKillerAccessibilityService.kt"))
(DST / "AppKillerAccessibilityService.kt").write_text(svc, encoding="utf-8")

# ------------------------------------------------------------ AppKillerEngine.kt
eng = rename_pkg(read("AppKillerEngine.kt")).replace("import dev.trmx.gui.MainActivity", "import dev.killer.MainActivity")
(DST / "AppKillerEngine.kt").write_text(eng, encoding="utf-8")

# --------------------------------------------------------- AppKillerViewModel.kt
vm = rename_pkg(read("AppKillerViewModel.kt"))
(DST / "AppKillerViewModel.kt").write_text(vm, encoding="utf-8")

# Apply the maintained, reviewable adaptation after the verbatim port.
patch = pathlib.Path(__file__).resolve().parent / "patches" / "trmx-engine-safety.patch"
subprocess.run(["patch", "--batch", "--forward", "-d", str(DST.resolve()), "-p0", "-i", str(patch)], check=True)

for p in sorted(DST.iterdir()):
    print(f"{p.name:40s} {len(p.read_text().splitlines()):5d} lines")
