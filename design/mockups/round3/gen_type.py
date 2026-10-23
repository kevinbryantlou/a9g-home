import sys,colorsys,json
D=sys.argv[1]; cfg=json.loads(sys.argv[2])
FONT='<link href="https://fonts.googleapis.com/css2?family=Schibsted+Grotesk:wght@400;500;600;700&display=swap" rel="stylesheet">'
apps=[("YouTube","img/youtube.png","#2E0C0C"),("Prime Video","img/prime.png","#05222E"),("Netflix","img/netflix.png","#121212"),("PS4","ps","#081640"),("Spotify","img/spotify.png","#0A2214")]
def night(c):
    r,g,b=[int(c[i:i+2],16)/255 for i in (1,3,5)]; h,l,s=colorsys.rgb_to_hls(r,g,b); r,g,b=colorsys.hls_to_rgb(h,l*.6,s)
    return "#%02x%02x%02x"%tuple(int(v*255) for v in (r,g,b))
def page(focus,is_night,clock,bg=None,scrim=None):
    name,img,field=apps[focus]
    if is_night: field=night(field)
    txt="#E6DED6" if is_night else "#FFFFFF"; dim=.22 if is_night else .28
    rows="";y=150
    for i,a in enumerate(apps):
        on=i==focus
        if on and i>0: y+=56
        extra=' <span style="font-size:34px;font-weight:600;letter-spacing:0;opacity:.7">HDMI 2</span>' if a[0]=="PS4" else ''
        rows+=f'<div style="position:absolute;left:110px;top:{y}px;font:700 150px/1 \'Schibsted Grotesk\';letter-spacing:-.035em;color:{txt};opacity:{1 if on else dim};transform-origin:0 80%;transform:scale({1 if on else .64});white-space:nowrap">{a[0]}{extra}</div>'
        y+=165 if on else 108
    br="filter:brightness(.7);" if is_night else ""
    art=f'<img src="{img}" style="width:480px;height:270px;border-radius:16px;display:block;{br}">' if img!="ps" else f'<div style="width:480px;height:270px;border-radius:16px;background:#003791;display:flex;align-items:center;justify-content:center;font:700 96px \'Schibsted Grotesk\';color:#fff;{br}">PS4</div>'
    if bg:
        a=scrim if scrim is not None else .72
        r,g,b=[int(field[i:i+2],16) for i in (1,3,5)]
        background=f"linear-gradient(180deg,#00000000,#00000059),linear-gradient(rgba({r},{g},{b},{a}),rgba({r},{g},{b},{a})),url({bg}) center/cover"
    else:
        background=f"linear-gradient(180deg,#00000000 0%,#00000059 100%),{field}"
    return f'''<!doctype html><html><head><meta charset="utf-8">{FONT}<style>*{{margin:0;box-sizing:border-box}}body{{width:1920px;height:1080px;overflow:hidden;position:relative;font-family:"Schibsted Grotesk",sans-serif;color:{txt};background:{background}}}</style></head><body>
<div style="position:absolute;right:110px;top:80px;text-align:right"><div style="font:500 64px/1 'Schibsted Grotesk';opacity:{.45 if is_night else .7}">{clock}</div><div style="margin-top:10px;font:500 26px 'Schibsted Grotesk';opacity:.55">Thursday 22 October</div></div>
{rows}
<div style="position:absolute;left:1330px;top:405px;width:480px">{art}</div>
<div style="position:absolute;left:84px;top:950px;display:flex;gap:12px;font:600 34px 'Schibsted Grotesk'">{''.join(f'<span style="height:60px;padding:0 26px;display:flex;align-items:center;border-radius:30px;opacity:.6">{u}</span>' for u in ["Inputs","All apps","Settings"])}</div>
{('<div style="position:absolute;right:110px;bottom:56px;font:500 20px Schibsted Grotesk;opacity:.5">'+cfg.get("credit","")+'</div>') if bg else ''}
</body></html>'''
for out,args in cfg["pages"].items():
    open(f"{D}/{out}.html","w").write(page(*args))
