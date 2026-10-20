from pathlib import Path
import json,xml.etree.ElementTree as E,re
ROOT=Path(__file__).resolve().parents[1];screens=json.loads((ROOT/'screens.json').read_text());catalog=json.loads((ROOT/'catalog.json').read_text());ns='{http://www.w3.org/2000/svg}'
keys={k.split(':')[0] for k in screens};errors=[];small=[];over=[];targets=set();texts=0
for k,source in screens.items():
 name,width,mode=k.split(':');r=E.fromstring(source)
 if r.get('width')!=width:errors.append([k,'wrong canvas width'])
 for n in r.iter():
  if n.get('data-go'):targets.add(n.get('data-go'))
  if n.tag!=ns+'text':continue
  texts+=1;font=float(n.get('font-size','14'))
  if int(width)==320 and font<11:small.append([k,font])
  if int(width)!=320:continue
  # Conservative estimate for CJK labels; photographs/vector paths are checked visually.
  value=''.join(n.itertext());x=float(n.get('x','0'));span=sum(font*(1 if ord(c)>255 else .55) for c in value);anchor=n.get('text-anchor','start')
  right=x+(span/2 if anchor=='middle' else 0 if anchor=='end' else span)
  if right>322:over.append([k,round(right,2),value])
for alias,destination in catalog['aliases'].items():
 if destination not in keys:errors.append([alias,'missing alias destination',destination])
for target in targets:
 if target not in keys and target not in catalog['aliases'] and target not in ['back','home','profile'] and not target.startswith('key:') and not target.startswith('policy-full:') and target not in ['photo-date','photo-note']:errors.append(['missing hotspot target',target])
for name in keys:
 for width in [320,390]:
  for mode in [0,1]:
   if f'{name}:{width}:{mode}' not in screens:errors.append([name,'missing variant',width,mode])
# Assert the body/photo action paths that were also exercised in the native Figma player.
for name,target in [('body','body-detail'),('photos','photo-upload'),('photo-upload','photos')]:
 r=E.fromstring(screens[f'{name}:390:0']);assert any(n.get('data-go')==target for n in r.iter())
result={'states':len(keys),'variants':len(screens),'textNodesAcrossVariants':texts,'widths':[320,390],'themes':['light','dark'],'smallTextAt320':small,'estimatedRightOverflowsAt320':over,'errors':errors,'nativeFigmaVerified':['body save -> detail -> back','photos -> upload -> save -> photos'],'localBrowserVerified':['home -> body -> edited weight','meal -> portion -> selected list -> detail','strength -> input -> selected -> confirm -> workout detail','photo upload failure in dark at 320px'],'limits':'Static geometry checks and manual browser design review; not app/device/API acceptance. Native Figma prototypes cover body and photos; other flows are in the local review prototype.'}
(ROOT/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
assert not errors and not small and not over,result
print(json.dumps({k:v for k,v in result.items() if k not in ['limits']},ensure_ascii=False))
