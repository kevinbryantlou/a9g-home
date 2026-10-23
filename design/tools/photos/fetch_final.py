import json, sys, os, time, urllib.request, urllib.parse, re
from PIL import Image
UA={"User-Agent":"beacon2-tv-launcher-photo-picker/0.1 (personal project)"}
idx=json.load(open(sys.argv[1])); picks=sys.argv[2].split(","); rawdir, outdir = sys.argv[3], sys.argv[4]
API="https://commons.wikimedia.org/w/api.php"
def get(url):
    for a in range(6):
        try:
            time.sleep(2)
            with urllib.request.urlopen(urllib.request.Request(url,headers=UA),timeout=60) as r: return r.read()
        except urllib.error.HTTPError as e:
            if e.code==429: time.sleep(20*(a+1)); continue
            raise
SUFFIX=sys.argv[5] if len(sys.argv)>5 else ""
names={"golden_gate":"Golden Gate Bridge, San Francisco","grand_canyon":"Grand Canyon, Arizona","yosemite":"Yosemite Valley, California","zion":"Zion Canyon, Utah","arches":"Delicate Arch, Utah","monument_valley":"Monument Valley, Arizona","antelope":"Antelope Canyon, Arizona","yellowstone":"Grand Prismatic Spring, Yellowstone","teton":"Teton Range, Wyoming","big_sur":"McWay Falls, Big Sur","moraine":"Moraine Lake, Banff","iceland":"Kirkjufell, Iceland","matterhorn":"Matterhorn, Switzerland","fjord":"Geirangerfjord, Norway","machu":"Machu Picchu, Peru","fuji":"Mount Fuji, Japan","patagonia":"Torres del Paine, Chile","milford":"Milford Sound, New Zealand","dolomites":"Tre Cime di Lavaredo, Dolomites","death_valley":"Zabriskie Point, Death Valley","glacier":"Glacier National Park, Montana"}
manifest=[]
for pk in picks:
    r=idx[pk]; key=pk.split("#")[0]
    d=json.loads(get(API+"?"+urllib.parse.urlencode({"action":"query","format":"json","titles":r["title"],"prop":"imageinfo","iiprop":"url|extmetadata","iiurlwidth":2560})))
    ii=list(d["query"]["pages"].values())[0]["imageinfo"][0]; m=ii.get("extmetadata",{})
    artist=re.sub(r"<[^>]+>","",m.get("Artist",{}).get("value","")).strip()
    artist=re.sub(r"\s+"," ",artist)[:60]
    lic=m.get("LicenseShortName",{}).get("value","")
    raw=os.path.join(rawdir,pk.replace("#","_")+".jpg")
    if not os.path.exists(raw): open(raw,"wb").write(get(ii["thumburl"]))
    im=Image.open(raw).convert("RGB"); w,h=im.size
    tw=w; th=int(w*9/16)
    if th>h: th=h; tw=int(h*16/9)
    left=(w-tw)//2; top=(h-th)//2
    im=im.crop((left,top,left+tw,top+th)).resize((1920,1080),Image.LANCZOS)
    fn=f"{key}{SUFFIX}.jpg"; im.save(os.path.join(outdir,fn),"JPEG",quality=86,optimize=True)
    credit = f"{names[key]}" + ("" if lic in ("CC0","Public domain") else f" · Photo: {artist}, {lic}")
    manifest.append({"file":fn,"credit":credit,"place":names[key],"artist":artist,"license":lic,"source":ii["descriptionurl"]})
    print(fn, lic, artist[:40])
json.dump(manifest,open(os.path.join(outdir,"photos.json"),"w"),indent=1,ensure_ascii=False)
