import sys, zipfile, os, glob
from androguard.core.axml import ARSCParser
apkdir, out, pkg, rid = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4],16)
for f in sorted(glob.glob(os.path.join(apkdir, pkg+"--*.apk"))):
    z = zipfile.ZipFile(f)
    if "resources.arsc" not in z.namelist(): continue
    try:
        p = ARSCParser(z.read("resources.arsc"))
        res = p.get_resolved_res_configs(rid)
    except Exception as e:
        print(os.path.basename(f), "err", e); continue
    for cfg, path in res:
        q = cfg.get_qualifier() or "default"
        print(os.path.basename(f), q, path)
        if isinstance(path, str) and path in z.namelist():
            ext = os.path.splitext(path)[1]
            open(os.path.join(out, f"{pkg}__{q}{ext}"), "wb").write(z.read(path))
