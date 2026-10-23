import sys
W=sys.argv[1]
FONT='<link href="https://fonts.googleapis.com/css2?family=Schibsted+Grotesk:wght@400;500;600;700&display=swap" rel="stylesheet">'
ICON={
 "sun":'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2.3M12 19.2v2.3M2.5 12h2.3M19.2 12h2.3M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"/></svg>',
 "cloud":'<svg viewBox="0 0 24 24"><path d="M7 18.5h10.2a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 18.5z"/></svg>',
}
def ic(n,size,stroke,col):
    return ICON[n].replace("<svg ",f'<svg style="width:{size}px;height:{size}px;fill:none;stroke:{col};stroke-width:{stroke};stroke-linecap:round;stroke-linejoin:round;vertical-align:-5px;margin:0 8px 0 10px" ')
days=[("Fri","cloud",70),("Sat","sun",76),("Sun","cloud",79),("Mon","cloud",81)]
def page(idle=False, night=False):
    ink="#E6DED6" if night else "#FFFFFF"
    blk_op=.85 if idle else 1
    if idle:   scrim="linear-gradient(to bottom,rgba(10,10,10,.35) 0%,rgba(10,10,10,.06) 50%,rgba(10,10,10,.22) 100%)"
    elif night:scrim="rgba(10,10,10,.45)"
    else:      scrim="rgba(10,10,10,.30)"
    left="" if idle else f'<div style="position:absolute;left:0;top:0;width:845px;height:1080px;background:linear-gradient(90deg,rgba(0,0,0,{.6 if night else .4}) 0%,rgba(0,0,0,{.6 if night else .4}) 50%,rgba(0,0,0,0) 100%)"></div>'
    rows="";y=150
    for i,n in enumerate(["YouTube","Prime Video","Netflix","PS4","Spotify"]):
        on=i==0
        tag=' <span style="font-size:34px;font-weight:600;letter-spacing:0;opacity:.7">HDMI 2</span>' if n=="PS4" else ""
        rows+=f'<div style="position:absolute;left:110px;top:{y}px;font:700 150px/1 \'Schibsted Grotesk\';letter-spacing:-.035em;color:{ink};opacity:{1 if on else .45};transform-origin:0 80%;transform:scale({1 if on else .64});white-space:nowrap">{n}{tag}</div>'
        y+=165 if on else 108
    menu=f'''{rows}
<img src="youtube.png" style="position:absolute;left:1330px;top:405px;width:480px;height:270px;border-radius:16px;filter:brightness({.8 if night else 1})">
<div style="position:absolute;left:84px;top:950px;display:flex;gap:12px;font:600 34px 'Schibsted Grotesk';color:{ink}">{''.join(f'<span style="height:60px;padding:0 26px;display:flex;align-items:center;opacity:.6">{u}</span>' for u in ["Inputs","All apps","Settings"])}</div>'''
    fc="".join(f'<span style="margin-left:{0 if i==0 else 30}px">{d}{ic(n,28,1.8,ink)}{t}°</span>' for i,(d,n,t) in enumerate(days))
    block=f'''<div style="position:absolute;right:110px;top:64px;text-align:right;color:{ink};opacity:{blk_op};transform-origin:100% 0;transform:none">
 <div style="display:flex;justify-content:flex-end;align-items:baseline;gap:30px;line-height:1">
  <span style="font:400 84px/1 'Schibsted Grotesk';letter-spacing:-.03em;opacity:.8">65°</span>
  <span style="font:400 128px/1 'Schibsted Grotesk';letter-spacing:-.035em;opacity:.95">5:17</span>
 </div>
 <div style="margin-top:12px;font:500 26px/1.2 'Schibsted Grotesk';opacity:.6">Thursday 22 October</div>
 <div style="margin-top:10px;font:500 28px/1.2 'Schibsted Grotesk';opacity:.85">Mostly clear. High 67°, low 59°.</div>
 <div style="margin-top:14px;font:500 26px/1.2 'Schibsted Grotesk';opacity:.72;white-space:nowrap">{fc}</div>
</div>'''
    credit=f'<div style="position:absolute;right:110px;bottom:56px;font:500 20px \'Schibsted Grotesk\';color:{ink};opacity:{.7 if idle else .5}">McWay Falls, Big Sur. Photo: King of Hearts, CC BY-SA 3.0, cropped</div>'
    return f'''<!doctype html><html><head><meta charset="utf-8">{FONT}<style>*{{margin:0;box-sizing:border-box}}body{{width:1920px;height:1080px;overflow:hidden;position:relative;font-family:"Schibsted Grotesk",sans-serif;background:url(big_sur.jpg) center/cover}}</style></head><body>
<div style="position:absolute;inset:0;background:{scrim}"></div>{left}
{"" if idle else menu}
{block}
{credit}
</body></html>'''
for k,kw in {"wx-d-home":{},"wx-d-idle":{"idle":True},"wx-d-home-night":{"night":True}}.items():
    open(f"{W}/{k}.html","w").write(page(**kw))
