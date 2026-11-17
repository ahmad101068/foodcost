#!/usr/bin/env python3
"""Build an offline review gallery and a six-page PDF from actual native screenshots.
The gallery is an evidence viewer, never an implementation substitute.
"""
import argparse,html,json,shutil
from pathlib import Path
from PIL import Image
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A3,landscape
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader

NAMES=['01-calculator','02-ingredients','03-recipes','04-settings','05-general-pricing']
TITLES=['Calculator','Ingredients','Recipes','Settings','General pricing']
FA=['ماشین‌حساب','مواد اولیه','غذاها','تنظیمات','قیمت‌گذاری عمومی']

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--spec',type=Path,required=True);ap.add_argument('--actual',type=Path,required=True);ap.add_argument('--before',type=Path,required=True);ap.add_argument('--comparison',type=Path,required=True);ap.add_argument('--summary',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);args=ap.parse_args()
    args.out.mkdir(parents=True,exist_ok=True); images=args.out/'images'; images.mkdir(exist_ok=True)
    summary=json.loads(args.summary.read_text());metrics=json.loads((args.comparison/'metrics.json').read_text())
    for group,source in [('reference',args.spec/'reference'),('before',args.before),('actual',args.actual)]:
        dest=images/group;dest.mkdir(exist_ok=True)
        for stem in NAMES:shutil.copy2(source/f'{stem}.png',dest/f'{stem}.png')
    for stem,metric in zip(NAMES,metrics):
        for kind in ['overlay','heatmap']:
            shutil.copy2(args.comparison/f'{stem}-{kind}.png',images/f'{stem}-{kind}.png')
        ref=Image.open(images/'reference'/f'{stem}.png').convert('RGB'); ref.crop(tuple(metric['ui_reference_crop_px'])).save(images/f'{stem}-ui-reference.png')
        actual=Image.open(args.comparison/f'{stem}-actual.png').convert('RGB'); actual.crop(tuple(metric['ui_actual_crop_px'])).save(images/f'{stem}-ui-actual.png')
        before=Image.open(images/'before'/f'{stem}.png').convert('RGB')
        if before.width!=941: before=before.resize((941,round(before.height*941/before.width)),Image.Resampling.LANCZOS)
        before.crop((0,55,941,before.height-110)).save(images/f'{stem}-ui-before.png')
    sections=[]
    for stem,title,m in zip(NAMES,FA,metrics):
        panels=''.join(f'<figure><figcaption>{label}</figcaption><a href="images/{group}/{stem}.png"><img src="images/{stem}-ui-{group}.png" loading="lazy"></a></figure>' for group,label in [('reference','مرجع تأییدشده — ناحیهٔ برنامه'),('before','اجرای مبنای ۰٫۸٫۳ — ناحیهٔ برنامه'),('actual','اجرای واقعی کاندید — ناحیهٔ برنامه')])
        sections.append(f'''<section id="{stem}"><h2>{title}</h2><p>SSIM خامِ ناحیهٔ برنامه: <b dir="ltr">{m['ssim_rgb']:.4f}</b> — هدف ۰٫۹۸: <strong class="fail">Fail</strong>. اختلاف داده‌ها و متن‌ها حذف نشده است.</p><div class="panels">{panels}</div><details><summary>لایهٔ تطبیق و نقشهٔ اختلاف</summary><label>شفافیت اجرای واقعی <input type="range" min="0" max="100" value="50" oninput="document.getElementById('{stem}-actual').style.opacity=this.value/100"></label><div class="compare"><img src="images/{stem}-ui-reference.png"><img id="{stem}-actual" class="over" src="images/{stem}-ui-actual.png"></div><a href="images/{stem}-heatmap.png">نقشهٔ اختلاف با اندازهٔ اصلی</a><img class="heat" src="images/{stem}-heatmap.png" loading="lazy"></details></section>''')
    links=' · '.join(f'<a href="#{s}">{t}</a>' for s,t in zip(NAMES,FA))
    body='''<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FoodCost 0.8.3 — بسته بازبینی</title><style>body{margin:0;background:#f6f8f4;color:#1c2a25;font:16px/1.8 Tahoma,Arial,sans-serif}main{max-width:1400px;margin:auto;padding:24px}h1{font-size:26px}h2{font-size:22px}header,section{background:white;border:1px solid #d4ddda;padding:24px;margin-bottom:20px;border-radius:12px}a{color:#006453}nav{position:sticky;top:0;background:#fff;padding:12px;border-bottom:2px solid #d3e860;z-index:2}.panels{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}figure{margin:0}figcaption{font-weight:bold;margin-bottom:8px}figure img{width:100%;border:1px solid #ddd}.fail{color:#b3323c}.compare{position:relative;width:min(100%,600px);margin:16px auto}.compare img{display:block;width:100%}.compare .over{position:absolute;inset:0;opacity:.5}.heat{display:block;max-width:420px;width:100%;margin:16px auto}input{width:200px}summary{cursor:pointer;font-weight:bold}code{direction:ltr;unicode-bidi:embed;overflow-wrap:anywhere}@media(max-width:700px){main{padding:10px}header,section{padding:14px}.panels{grid-template-columns:1fr}}</style><main>'''
    body+=f'''<header><h1>FoodCost 0.8.3 — کاندید بازبینی مالک</h1><p><b>ادغام و انتشار نهایی انجام نشده است.</b> تصاویر این صفحه از خود برنامهٔ اندروید گرفته شده‌اند. مرجع تصویری، اجرای مبنا و اجرای کاندید را جداگانه مشاهده کنید.</p><p>معیار SSIM سراسری هنوز قبول نشده است. وضعیت هر آزمون و محدودیت‌ها در گزارش همراه بسته آمده‌اند؛ این گالری به معنی تأیید نهایی نیست.</p><p>مبنا: <code>6c2bf74b713f4febfda53019324af053298d56ef</code><br>شاخه: <code>{html.escape(summary['branch'])}</code></p><p>برای مشاهدهٔ تمام جزئیات، روی هر تصویر بزنید. در مقایسهٔ لایه‌ای فقط نوارهای سیستم کنار گذاشته شده‌اند؛ مقادیر مالی ماسک نشده‌اند و تصویر عمودی کشیده نشده است.</p></header><nav>{links}</nav>'''
    body+=''.join(sections)+'</main></html>'
    (args.out/'index.html').write_text(body)
    pdf=args.out/'FoodCost-0.8.3-Reference-Before-After.pdf'; c=canvas.Canvas(str(pdf),pagesize=landscape(A3));w,h=landscape(A3)
    c.setTitle('FoodCost 0.8.3 native reference review - not approved for merge')
    def text(y,value,size=13,color='#1c2a25'):
        c.setFillColor(HexColor(color));c.setFont('Helvetica',size);c.drawString(40,y,value)
    text(h-70,'FOODCOST 0.8.3 | NATIVE REFERENCE IMPLEMENTATION',24)
    text(h-104,'CANDIDATE FOR OWNER REVIEW - NOT APPROVED FOR MERGE',16,'#b3323c')
    lines=[
        'Baseline: 6c2bf74b713f4febfda53019324af053298d56ef',
        'Branch: '+summary['branch'],
        'Version, financial engine, persistence and backup formats remain on 0.8.3.',
        'Screenshots are from the running native Compose app. They are not design renders.',
        'The five pages contain: reference / baseline app / candidate app / UI difference.',
        'System status and navigation bars are excluded from metrics, not financial content.',
        'No vertical image stretching, per-component registration or financial text masks are used.',
        'Raw UI SSIM target 0.98 is NOT PASSED. See the measured scores on each page.',
        'Geometry, color samples, native semantics, test logs and source diff accompany this PDF.',
        'Some concept content is incompatible with baseline formulas/features; see the Persian report.',
        'Open index.html in the extracted package for the opacity slider and full-resolution images.',
        'This package is evidence for review, not a production release or a merge authorization.',
    ]
    for i,line in enumerate(lines): text(h-156-i*30,line,12)
    c.showPage()
    labels=['REFERENCE PNG','BASELINE 0.8.3 NATIVE','CANDIDATE NATIVE','UI DIFFERENCE HEATMAP']
    for stem,title,m in zip(NAMES,TITLES,metrics):
        text(h-40,f'{title} | FoodCost 0.8.3',20)
        text(h-65,f'Raw UI SSIM: {m["ssim_rgb"]:.4f} | Target >= 0.98: FAIL',12,'#b3323c')
        col=(w-80)/4
        sources=[images/f'{stem}-ui-reference.png',images/f'{stem}-ui-before.png',images/f'{stem}-ui-actual.png',images/f'{stem}-heatmap.png']
        for i,(label,p) in enumerate(zip(labels,sources)):
            x=40+i*col;c.setFont('Helvetica-Bold',10);c.setFillColor(HexColor('#006453'));c.drawString(x,h-94,label)
            im=Image.open(p);maxw=col-14;maxh=h-165;scale=min(maxw/im.width,maxh/im.height);iw,ih=im.width*scale,im.height*scale
            c.drawImage(ImageReader(im),x+(maxw-iw)/2,h-108-ih,width=iw,height=ih)
        text(42,'App-owned UI shown. Native originals, viewport and system-inset metadata accompany the package.',10)
        text(25,'Heatmap compares only the app-owned UI; OS bars are excluded. Numeric and wording differences remain.',10)
        c.showPage()
    c.save();print(str(pdf))
if __name__=='__main__':main()
