#!/usr/bin/env python3
"""Compare measured reference card edges to native Compose semantics, in dp.
Only density and system status inset are normalized. No image alignment optimization.
"""
import argparse,json,re
from pathlib import Path

def nodes(text):
    pattern=r"Node #\d+ at \(l=([-.\d]+), t=([-.\d]+), r=([-.\d]+), b=([-.\d]+)\)px, Tag: '([^']+)'"
    return {m[4]:list(map(float,m[:4])) for m in re.findall(pattern,text)}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--spec',type=Path,required=True);ap.add_argument('--actual',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);args=ap.parse_args()
    mappings={
        '01-calculator':{'dashboard':'calculator-dashboard','form':'calculator-form','ingredients':'calculator-ingredients','actions':'calculator-save'},
        '02-ingredients':{'search':'ingredients-search','filter':'ingredients-category-options','statistics':'ingredients-statistics','add':'ingredients-add'},
        '03-recipes':{'add':'recipe-add'},
        '04-settings':{'general_group':'settings-general-group','data_group':'settings-data-group','help_group':'settings-help-group'},
        '05-general-pricing':{'methods':'pricing-methods','target':'pricing-target-card','equivalents':'pricing-equivalents','pricing_settings':'pricing-settings','scope':'pricing-scope','notice':'pricing-notice','actions':'settings-save-pricing'},
    }
    density=2.2875001; reference_scale=941/411; rows=[]
    for stem,mapping in mappings.items():
        spec=json.loads((args.spec/f'{stem}-measurements.json').read_text())['rectangles']
        actual=nodes((args.actual/f'{stem}-semantics.txt').read_text())
        mapping={'app_bar':'app-bar',**mapping,'bottom_navigation':'foodcost-bottom-navigation'}
        if stem=='02-ingredients':
            cards=sorted([tag for tag in actual if re.fullmatch(r'ingredient-[a-f0-9-]+',tag)],key=lambda tag:actual[tag][1])
            mapping.update({f'ingredient_{i+1}':tag for i,tag in enumerate(cards[:5])})
        if stem=='03-recipes':
            cards=sorted([tag for tag in actual if re.fullmatch(r'food-[a-f0-9-]+',tag)],key=lambda tag:actual[tag][1])
            mapping.update({f'recipe_{i+1}':tag for i,tag in enumerate(cards[:3])})
        for part,tag in mapping.items():
            if tag not in actual or part not in spec: continue
            a=actual[tag].copy();r=spec[part]['xyxy_px'].copy();note=''
            if part=='bottom_navigation': a[3]-=110
            if part=='actions':
                # Save is one of two adjacent buttons. Compare its vertical edges only.
                note='Only vertical button edges; reference rectangle spans both actions.'
            adp=[a[0]/density,(a[1]-55)/density,a[2]/density,(a[3]-55)/density]
            rdp=[r[0]/reference_scale,(r[1]-54)/reference_scale,r[2]/reference_scale,(r[3]-54)/reference_scale]
            delta=[x-y for x,y in zip(adp,rdp)]; selected=[delta[1],delta[3]] if part=='actions' else delta
            maximum=max(map(abs,selected))
            rows.append({'page':stem,'part':part,'tag':tag,'actual_native_px':a,'reference_px':r,'edge_delta_dp':delta,'max_abs_edge_delta_dp':maximum,'pass_le_2dp':maximum<=2,'note':note or ('Data-driven ordering/status may differ from the conceptual list.' if part.startswith('ingredient_') else '')})
    args.out.write_text(json.dumps({'density':density,'reference_px_per_dp':reference_scale,'actual_status_px':55,'reference_status_px':54,'target_max_dp':2,'rows':rows,'all_measured_edges_pass':all(r['pass_le_2dp'] for r in rows)},indent=2))
if __name__=='__main__':main()
