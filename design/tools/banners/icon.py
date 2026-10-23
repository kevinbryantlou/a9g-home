import sys, zipfile, os, glob
from androguard.core.axml import ARSCParser, AXMLPrinter
apkdir, out, pkg = sys.argv[1], sys.argv[2], sys.argv[3]
base = glob.glob(os.path.join(apkdir, pkg+"--base.apk"))[0]
z = zipfile.ZipFile(base)
x = AXMLPrinter(z.read("AndroidManifest.xml")).get_xml_obj()
ns = "{http://schemas.android.com/apk/res/android}"
ids=set()
for el in x.iter():
    for a in ("icon","roundIcon"):
        v = el.get(ns+a)
        if v: ids.add(v)
print(pkg, ids)
for f in sorted(glob.glob(os.path.join(apkdir, pkg+"--*.apk"))):
    zz = zipfile.ZipFile(f)
    if "resources.arsc" not in zz.namelist(): continue
    try: p = ARSCParser(zz.read("resources.arsc"))
    except Exception as e: continue
    for v in ids:
        try: res = p.get_resolved_res_configs(int(v.lstrip("@"),16))
        except Exception: continue
        for cfg, path in res:
            q = cfg.get_qualifier() or "default"
            print("  ", os.path.basename(f), q, path)
            if isinstance(path,str) and path.endswith((".png",".webp")) and path in zz.namelist():
                open(os.path.join(out, f"{pkg}__{v.lstrip('@')}__{q}{os.path.splitext(path)[1]}"),"wb").write(zz.read(path))
