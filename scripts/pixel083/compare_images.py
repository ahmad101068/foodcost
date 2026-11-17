#!/usr/bin/env python3
"""Reproducible RGB SSIM + overlay + absolute error; never masks financial content.

Crop only the emulator letterbox. Platform status/navigation bars are excluded using
recorded insets. No elastic alignment or per-component image fitting is performed.
"""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw

def boxmean(a,n=7):
    p=n//2
    a=np.pad(a,((p,p),(p,p),(0,0)),mode='reflect')
    integral=np.pad(a,((1,0),(1,0),(0,0))).cumsum(0).cumsum(1)
    return (integral[n:,n:]-integral[:-n,n:]-integral[n:,:-n]+integral[:-n,:-n])/(n*n)

def ssim(x,y):
    x=np.asarray(x,dtype=np.float64);y=np.asarray(y,dtype=np.float64)
    if x.shape!=y.shape: raise ValueError((x.shape,y.shape))
    ux=boxmean(x);uy=boxmean(y);norm=49/48
    vx=(boxmean(x*x)-ux*ux)*norm;vy=(boxmean(y*y)-uy*uy)*norm;vxy=(boxmean(x*y)-ux*uy)*norm
    score=((2*ux*uy+6.5025)*(2*vxy+58.5225))/((ux*ux+uy*uy+6.5025)*(vx+vy+58.5225))
    return float(score[3:-3,3:-3].mean())

def viewport(im):
    a=np.asarray(im.convert('RGB'))
    visible=(a.max(axis=2)>12)
    ys,xs=np.where(visible)
    box=(int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1))
    return im.crop(box).convert('RGB'),box

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--actual',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--actual-status',type=int,default=55);parser.add_argument('--actual-nav',type=int,default=110)
    args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=True);results=[]
    for ref in sorted(args.reference.glob('0*.png')):
        raw=Image.open(args.actual/ref.name)
        actual,crop=viewport(raw)
        original=actual.size
        if actual.width!=941:
            actual=actual.resize((941,round(actual.height*941/actual.width)),Image.Resampling.LANCZOS)
        actual.save(args.out/(ref.stem+'-actual.png'))
        r=Image.open(ref).convert('RGB')
        # Compare equal-size app-owned content; OS bar heights differ by Android image.
        top=args.actual_status;bottom=actual.height-args.actual_nav
        h=min(bottom-top,1594-54)
        a=actual.crop((0,top,941,top+h));b=r.crop((0,54,941,54+h))
        overlay=Image.blend(b,a,.5);overlay.save(args.out/(ref.stem+'-overlay.png'))
        err=np.abs(np.asarray(a,dtype=np.int16)-np.asarray(b,dtype=np.int16)).mean(2)
        hot=np.stack([np.minimum(255,err*4),np.maximum(0,255-err*7),np.zeros_like(err)],axis=2).astype('uint8')
        Image.fromarray(hot).save(args.out/(ref.stem+'-heatmap.png'))
        side=Image.new('RGB',(941*2,1672),'white');side.paste(r,(0,0));side.paste(actual,(941,0));side.save(args.out/(ref.stem+'-reference-actual.png'))
        measurement=json.loads((args.reference.parent/(ref.stem+'-measurements.json')).read_text())
        components={}
        for label,v in measurement['rectangles'].items():
            x1,y1,x2,y2=v['xyxy_px'];y1-=54;y2-=54
            if y1>=0 and y2<=h:components[label]=ssim(b.crop((x1,y1,x2,y2)),a.crop((x1,y1,x2,y2)))
        results.append({'reference':ref.name,'raw_size_px':raw.size,'letterbox_crop_px':crop,'viewport_size_px':original,'normalized_viewport_px':actual.size,'ui_reference_crop_px':[0,54,941,54+h],'ui_actual_crop_px':[0,top,941,top+h],'ssim_rgb':ssim(b,a),'ssim_target':.98,'ssim_pass':ssim(b,a)>=.98,'mean_absolute_rgb_error':float(err.mean()),'component_ssim':components,'note':'Data, wording and native glyph differences remain included; crops do not conceal them. Platform bar sizes excluded; no vertical stretching.'})
    (args.out/'metrics.json').write_text(json.dumps(results,indent=2))
    print(json.dumps([{k:r[k] for k in ('reference','normalized_viewport_px','ssim_rgb','ssim_pass')}for r in results],indent=2))
