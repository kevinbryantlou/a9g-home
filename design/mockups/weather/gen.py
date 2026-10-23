import sys
W=sys.argv[1]
FONT='<link href="https://fonts.googleapis.com/css2?family=Schibsted+Grotesk:wght@400;500;600;700&display=swap" rel="stylesheet">'
# line icons, 1 colour, stroke only
ICON={
 "sun":'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2.3M12 19.2v2.3M2.5 12h2.3M19.2 12h2.3M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6"/></svg>',
 "partly":'<svg viewBox="0 0 24 24"><path d="M8.5 6.2a3.6 3.6 0 0 1 6.4 1.5"/><path d="M8 3v1.4M3.6 7.3H5M4.8 4.1l1 1"/><path d="M7.5 19.5h9.3a3.6 3.6 0 0 0 .3-7.2 5 5 0 0 0-9.6 1.3 3 3 0 0 0 0 5.9z"/></svg>',
 "cloud":'<svg viewBox="0 0 24 24"><path d="M7 18.5h10.2a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 18.5z"/></svg>',
 "drizzle":'<svg viewBox="0 0 24 24"><path d="M7 15h10.2a4 4 0 0 0 .4-8A5.5 5.5 0 0 0 7 8.5 3.3 3.3 0 0 0 7 15z"/><path d="M9 18l-.8 2M13 18l-.8 2M17 18l-.8 2"/></svg>',
}
days=[("Thu","drizzle",67,59),("Fri","cloud",70,55),("Sat","sun",76,54),("Sun","cloud",79,66),("Mon","cloud",81,68)]
def ic(n,size,stroke=1.6,op=1):
    return ICON[n].replace("<svg ",f'<svg style="width:{size}px;height:{size}px;fill:none;stroke:#fff;stroke-width:{stroke};stroke-linecap:round;stroke-linejoin:round;opacity:{op};vertical-align:middle" ')
def base(extra_css, top_right, bottom_right, idle=False, idle_block=""):
    rows=""; y=150
    names=["YouTube","Prime Video","Netflix","PS4","Spotify"]
    for i,n in enumerate(names):
        on=i==0
        tag=' <span style="font-size:34px;font-weight:600;letter-spacing:0;opacity:.7">HDMI 2</span>' if n=="PS4" else ""
        rows+=f'<div style="position:absolute;left:110px;top:{y}px;font:700 150px/1 \'Schibsted Grotesk\';letter-spacing:-.035em;color:#fff;opacity:{1 if on else .45};transform-origin:0 80%;transform:scale({1 if on else .64});white-space:nowrap">{n}{tag}</div>'
        y+=165 if on else 108
    menu=f'''{rows}
<img src="youtube.png" style="position:absolute;left:1330px;top:405px;width:480px;height:270px;border-radius:16px">
<div style="position:absolute;left:84px;top:950px;display:flex;gap:12px;font:600 34px 'Schibsted Grotesk';color:#fff">{''.join(f'<span style="height:60px;padding:0 26px;display:flex;align-items:center;opacity:.6">{u}</span>' for u in ["Inputs","All apps","Settings"])}</div>
{top_right}'''
    shade="rgba(10,10,10,.105)" if idle else "rgba(10,10,10,.30)"
    left="" if idle else '<div style="position:absolute;left:0;top:0;width:845px;height:1080px;background:linear-gradient(90deg,rgba(0,0,0,.4) 0%,rgba(0,0,0,.4) 50%,rgba(0,0,0,0) 100%)"></div>'
    credit=f'<div style="position:absolute;right:110px;bottom:56px;font:500 20px \'Schibsted Grotesk\';color:#fff;opacity:{.7 if idle else .5}">McWay Falls, Big Sur. Photo: King of Hearts, CC BY-SA 3.0, cropped</div>'
    return f'''<!doctype html><html><head><meta charset="utf-8">{FONT}<style>*{{margin:0;box-sizing:border-box}}body{{width:1920px;height:1080px;overflow:hidden;position:relative;font-family:"Schibsted Grotesk",sans-serif;color:#fff;background:url(big_sur.jpg) center/cover}}{extra_css}</style></head><body>
<div style="position:absolute;inset:0;background:{shade}"></div>{left}
{"" if idle else menu}
{idle_block}
{bottom_right}
{credit}
</body></html>'''
clock_std='<div style="position:absolute;right:110px;top:80px;text-align:right"><div style="font:500 64px/1 \'Schibsted Grotesk\';opacity:.7">5:17</div><div style="margin-top:10px;font:500 26px \'Schibsted Grotesk\';opacity:.55">Thursday 22 October</div>{w}</div>'
pages={}
# A: one line under the date
wA=f'<div style="margin-top:14px;font:500 26px \'Schibsted Grotesk\';opacity:.8;display:flex;gap:10px;justify-content:flex-end;align-items:center">{ic("partly",30,1.7)}<span>65° mostly clear</span><span style="opacity:.6">67° / 59°</span></div>'
pages["wx-a-home"]=base("",clock_std.format(w=wA),"")
idleA=f'''<div style="position:absolute;left:110px;bottom:120px"><div style="font:500 30px 'Schibsted Grotesk';opacity:.85;display:flex;gap:14px;align-items:center"><span>5:17</span><span style="opacity:.5">|</span>{ic("partly",34,1.7)}<span>65° mostly clear</span></div></div>'''
pages["wx-a-idle"]=base("","","",idle=True,idle_block=idleA)
# B: five-day strip
strip=lambda op: '<div style="display:flex;gap:34px;justify-content:flex-end">'+''.join(f'<div style="text-align:center;opacity:{1 if i==0 else op}"><div style="font:600 22px \'Schibsted Grotesk\';opacity:.7">{d}</div><div style="margin:8px 0 6px">{ic(n,40,1.5)}</div><div style="font:600 24px \'Schibsted Grotesk\'">{hi}°</div><div style="font:500 20px \'Schibsted Grotesk\';opacity:.6">{lo}°</div></div>' for i,(d,n,hi,lo) in enumerate(days))+'</div>'
wB=f'<div style="position:absolute;right:110px;top:745px">{strip(.75)}</div>'
pages["wx-b-home"]=base("",clock_std.format(w='<div style="margin-top:14px;font:600 30px \'Schibsted Grotesk\';opacity:.9">65° mostly clear</div>'),wB)
idleB=f'''<div style="position:absolute;left:110px;bottom:110px;display:flex;align-items:flex-end;gap:56px"><div><div style="font:500 96px/1 'Schibsted Grotesk';letter-spacing:-.02em">5:17</div><div style="font:500 28px 'Schibsted Grotesk';opacity:.8;margin-top:10px">65° mostly clear</div></div><div style="padding-bottom:4px">{strip(.75)}</div></div>'''
pages["wx-b-idle"]=base("","","",idle=True,idle_block=idleB)
# C: big type, matching the menu
wC='''<div style="position:absolute;right:110px;top:70px;text-align:right"><div style="font:300 150px/1 'Schibsted Grotesk';letter-spacing:-.04em">65°</div><div style="font:500 28px 'Schibsted Grotesk';opacity:.8;margin-top:8px">Mostly clear. High 67°, low 59°</div><div style="font:500 24px 'Schibsted Grotesk';opacity:.55;margin-top:8px">5:17 &nbsp;Thursday 22 October</div></div>'''
pages["wx-c-home"]=base("",wC,"")
idleC='''<div style="position:absolute;left:110px;bottom:110px"><div style="font:300 220px/0.9 'Schibsted Grotesk';letter-spacing:-.045em">65°</div><div style="font:500 34px 'Schibsted Grotesk';opacity:.85;margin-top:14px">Mostly clear in Pasadena &nbsp;&nbsp;<span style="opacity:.6">5:17</span></div></div>'''
pages["wx-c-idle"]=base("","","",idle=True,idle_block=idleC)
for k,v in pages.items(): open(f"{W}/{k}.html","w").write(v)
