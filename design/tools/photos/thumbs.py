import json, sys, os, time, urllib.request, hashlib
from PIL import Image, ImageDraw
UA={"User-Agent":"beacon2-tv-launcher-photo-picker/0.1 (personal project)"}
c=json.load(open(sys.argv[1])); outdir=sys.argv[2]
rows=[]
for key,lst in c.items():
    seen=set(); items=[]
    for i,r in enumerate(lst):
        if r["title"] in seen: continue
        seen.add(r["title"])
        fn=os.path.join(outdir, hashlib.md5(r["title"].encode()).hexdigest()+".jpg")
        if not os.path.exists(fn):
            for a in range(4):
                try:
                    time.sleep(0.7)
                    with urllib.request.urlopen(urllib.request.Request(r["thumb"],headers=UA),timeout=30) as resp:
                        open(fn,"wb").write(resp.read())
                    break
                except Exception as e:
                    time.sleep(5*(a+1))
        if os.path.exists(fn): items.append((len(items), r, fn))
    rows.append((key, items))
W=240; H=135
sheet=Image.new("RGB",(160+8*(W+6), len(rows)*(H+24)),"white")
d=ImageDraw.Draw(sheet)
idx={}
for ri,(key,items) in enumerate(rows):
    y=ri*(H+24)
    d.text((4,y+50),key,fill="black")
    for j,(n,r,fn) in enumerate(items[:8]):
        try:
            im=Image.open(fn).convert("RGB"); im.thumbnail((W,H))
            sheet.paste(im,(160+j*(W+6),y))
            d.text((160+j*(W+6),y+H+2),f"{key}#{j} {r['lic'][:12]}",fill="black")
            idx[f"{key}#{j}"]=r
        except Exception as e: pass
sheet.save(sys.argv[3]); json.dump(idx,open(sys.argv[4],"w"),indent=1)
