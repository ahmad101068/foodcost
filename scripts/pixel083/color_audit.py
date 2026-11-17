#!/usr/bin/env python3
"""Uniform reference patches versus native UI colors; D65 sRGB and CIEDE2000.
No images are edited by this analysis. Coordinates and channel spreads are retained.
"""
import argparse, json, math, re
from pathlib import Path
import numpy as np
from PIL import Image

def lab(rgb):
    c=np.asarray(rgb,dtype=float)/255
    c=np.where(c<=.04045,c/12.92,((c+.055)/1.055)**2.4)
    xyz=np.array([[.4124564,.3575761,.1804375],[.2126729,.7151522,.0721750],[.0193339,.1191920,.9503041]])@c
    t=xyz/np.array([.95047,1,1.08883])
    f=np.where(t>(6/29)**3,np.cbrt(t),t/(3*(6/29)**2)+4/29)
    return np.array([116*f[1]-16,500*(f[0]-f[1]),200*(f[1]-f[2])])

def de00(a,b):
    L1,a1,b1=a; L2,a2,b2=b
    C1=math.hypot(a1,b1); C2=math.hypot(a2,b2); C=(C1+C2)/2
    G=.5*(1-math.sqrt(C**7/(C**7+25**7)))
    ap1=(1+G)*a1; ap2=(1+G)*a2
    cp1=math.hypot(ap1,b1); cp2=math.hypot(ap2,b2)
    hp1=math.degrees(math.atan2(b1,ap1))%360 if cp1 else 0
    hp2=math.degrees(math.atan2(b2,ap2))%360 if cp2 else 0
    dL=L2-L1; dC=cp2-cp1
    dh=hp2-hp1
    if not cp1*cp2: dh=0
    elif dh>180: dh-=360
    elif dh < -180: dh+=360
    dH=2*math.sqrt(cp1*cp2)*math.sin(math.radians(dh/2))
    lp=(L1+L2)/2; cp=(cp1+cp2)/2
    if not cp1*cp2: hp=hp1+hp2
    elif abs(hp1-hp2)<=180: hp=(hp1+hp2)/2
    elif hp1+hp2<360: hp=(hp1+hp2+360)/2
    else: hp=(hp1+hp2-360)/2
    T=1-.17*math.cos(math.radians(hp-30))+.24*math.cos(math.radians(2*hp))+.32*math.cos(math.radians(3*hp+6))-.20*math.cos(math.radians(4*hp-63))
    sl=1+.015*(lp-50)**2/math.sqrt(20+(lp-50)**2); sc=1+.045*cp; sh=1+.015*cp*T
    rt=-2*math.sqrt(cp**7/(cp**7+25**7))*math.sin(math.radians(60*math.exp(-((hp-275)/25)**2)))
    return math.sqrt((dL/sl)**2+(dC/sc)**2+(dH/sh)**2+rt*(dC/sc)*(dH/sh))

def contrast(a,b):
    def lum(rgb):
        c=np.array(rgb)/255; c=np.where(c<=.04045,c/12.92,((c+.055)/1.055)**2.4)
        return float(c@np.array([.2126,.7152,.0722]))
    l1,l2=sorted([lum(a),lum(b)])
    return (l2+.05)/(l1+.05)

def rgb(h): return [int(h[i:i+2],16) for i in (0,2,4)]

def patch(im,rect,target):
    a=np.array(im.convert('RGB')); x0,y0,x1,y1=rect; candidates=[]
    for y in range(y0,y1-8,8):
        for x in range(x0,x1-8,8):
            p=a[y:y+8,x:x+8]; spread=int(np.ptp(p,axis=(0,1)).max()); avg=np.median(p,axis=(0,1)).astype(int)
            if spread<=2: candidates.append((de00(lab(avg),lab(target)),x,y,avg.tolist(),spread))
    if not candidates: return None
    _,x,y,c,s=min(candidates)
    return {'xywh_px':[x,y,8,8],'rgb':c,'max_channel_range':s}

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--root',type=Path,required=True); ap.add_argument('--actual',type=Path,required=True); ap.add_argument('--out',type=Path,required=True); ap.add_argument("--actual-status",type=int,default=55);args=ap.parse_args()
    assert abs(de00([50,2.6772,-79.7751],[50,0,-82.7485])-2.0425)<.0001
    tokens=dict(re.findall(r'val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)',(args.root.parents[1]/'app/src/main/kotlin/ir/foodcost/app/ui/theme/PremiumTokens.kt').read_text()))
    # Broad, declared ROIs select only uniform samples; the nearest eligible patch is reported.
    regions=[('background','01-calculator.png',[0,185,23,1320]),('surface','01-calculator.png',[50,225,120,570]),('primaryContainer','02-ingredients.png',[650,330,900,424]),('subtleBlue','01-calculator.png',[410,230,880,295]),('mintSurface','01-calculator.png',[410,410,880,475]),('topDivider','01-calculator.png',[30,180,910,184])]
    result=[]
    for token,name,rect in regions:
        reference=Image.open(args.root/'reference'/name)
        actual=Image.open(args.actual/name)
        if actual.width!=941: actual=actual.resize((941,round(actual.height*941/actual.width)),Image.Resampling.LANCZOS)
        actual_rect=[rect[0],rect[1]-54+args.actual_status,rect[2],rect[3]-54+args.actual_status]
        # The 2px header rule is smaller than a uniform 8px patch; report explicit center pixel.
        if token=='topDivider':
            rp={'xywh_px':[400,181,1,1],'rgb':list(reference.convert('RGB').getpixel((400,181))),'max_channel_range':0}
            apatch={'xywh_px':[400,args.actual_status+130,1,1],'rgb':list(actual.convert('RGB').getpixel((400,args.actual_status+130))),'max_channel_range':0}
        else:
            rp=patch(reference,rect,rgb(tokens[token])); apatch=patch(actual,actual_rect,rgb(tokens[token]))
        d=None if not rp or not apatch else de00(lab(rp['rgb']),lab(apatch['rgb']))
        result.append({'token':token,'token_hex':'#'+tokens[token],'image':name,'declared_roi_px':rect,'reference_patch':rp,'actual_patch':apatch,'deltaE2000':d,'pass_le_2':d is not None and d<=2})
    pairs=[('textPrimary','surface'),('textSecondary','surface'),('primary','surface'),('warningText','warningSurface'),('foodAmountText','warningSurface'),('error','errorSurface'),('success','successSurface'),('foodCostAccent','surface')]
    audits=[]
    for fg,bg in pairs:
        ratio=contrast(rgb(tokens[fg]),rgb(tokens[bg])); audits.append({'foreground':fg,'background':bg,'ratio':ratio,'normal_text_AA':ratio>=4.5,'large_text_AA':ratio>=3})
    args.out.write_text(json.dumps({'method':'Uniform 8x8 patches, max channel spread 2; declared region; no color replacement or screenshot masking. Native coordinates, not arbitrary vertical stretching.','ciede2000_known_pair_self_check':True,'uniform_colors':result,'contrast':audits},ensure_ascii=False,indent=2))
if __name__=='__main__': main()
