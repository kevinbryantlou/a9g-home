import json, sys, urllib.request, urllib.parse, urllib.error, re
API="https://commons.wikimedia.org/w/api.php"
UA={"User-Agent":"beacon2-tv-launcher-photo-picker/0.1 (kevinbryantlou personal project)"}
import time
def q(params):
    url=API+"?"+urllib.parse.urlencode(params)
    for attempt in range(6):
        time.sleep(3)
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers=UA),timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code==429: time.sleep(20*(attempt+1)); continue
            raise
    return {}
def clean(h): return re.sub(r"<[^>]+>","",h or "").strip()
subjects=json.load(open(sys.argv[1]))
out={}
for key,terms in subjects.items():
    res=[]
    for cat in ["Featured_pictures_on_Wikimedia_Commons","Quality_images"]:
        d=q({"action":"query","format":"json","generator":"search","gsrnamespace":6,"gsrlimit":12,
             "gsrsearch":f'{terms} incategory:{cat} filetype:bitmap',
             "prop":"imageinfo","iiprop":"url|size|extmetadata","iiurlwidth":640})
        for p in (d.get("query",{}).get("pages",{}) or {}).values():
            ii=p["imageinfo"][0]; m=ii.get("extmetadata",{})
            w,h=ii["width"],ii["height"]
            if w<2400 or w/h<1.45 or w/h>2.1: continue
            lic=m.get("LicenseShortName",{}).get("value","")
            res.append({"title":p["title"],"w":w,"h":h,"lic":lic,"artist":clean(m.get("Artist",{}).get("value",""))[:80],
                        "thumb":ii.get("thumburl"),"url":ii["url"],"page":ii["descriptionurl"],"cat":cat[:8]})
        if len(res)>=4: break
    out[key]=res
    print(key, len(res), [ (r["title"][5:60], r["lic"], r["w"]) for r in res[:4]])
json.dump(out,open(sys.argv[2],"w"),indent=1)
