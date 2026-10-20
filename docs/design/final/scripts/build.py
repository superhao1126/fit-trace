from pathlib import Path
from html import escape
import xml.etree.ElementTree as E
import re,json,copy,math
OUT=Path(__file__).resolve().parents[1];ROOT=OUT.parent
NS='{http://www.w3.org/2000/svg}'
LIGHT=dict(bg='#F7F8FA',paper='#FFFFFF',text='#222222',muted='#777777',soft='#E6F8EE',primary='#24C867',accent='#229D60',line='#E6EAE8',danger='#B6473F',dangerSoft='#FFF1EE')
DARK=dict(bg='#111713',paper='#1D2620',text='#E9F1EB',muted='#ACB9B0',soft='#163E29',primary='#24C867',accent='#62DEA0',line='#35483D',danger='#FFB4AB',dangerSoft='#432823')
r=E.parse(ROOT/'fittrace-login-caljimi-2026-10-19.svg').getroot();art=copy.deepcopy(next(g for g in r.iter(NS+'g') if g.get('transform')=='translate(129 163) scale(1.12)'));art.attrib.pop('transform');ART=E.tostring(art,encoding='unicode').replace('ns0:','').replace(':ns0','')
class UI:
 def __init__(self,w=390,dark=False):self.w=w;self.c=DARK if dark else LIGHT;self.m=20 if w==320 else 24;self.i=w-2*self.m
 def R(self,x,y,w,h,c='paper',rad=18):return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rad}" fill="{self.c.get(c,c)}"/>'
 def T(self,x,y,t,size=14,c='text',bold=400,anchor='start'):return f'<text x="{x}" y="{y}" font-family="Noto Sans SC" font-size="{size}" font-weight="{bold}" text-anchor="{anchor}" fill="{self.c.get(c,c)}">{escape(t)}</text>'
 def G(self,s,go):return f'<g data-go="{go}" role="button" tabindex="0" aria-label="{go}">{s}</g>'
 def B(self,y,label,go,x=None,w=None,secondary=False):
  x=self.m if x is None else x;w=self.i if w is None else w
  return self.G(self.R(x,y,w,50,'soft' if secondary else 'primary',25)+self.T(x+w/2,y+32,label,15,'text' if secondary else '#092B17',600,'middle'),go)
 def H(self,title,sub='',back='home'):
  return self.R(0,0,self.w,844,'bg',24)+self.R(0,0,self.w,180,'soft',24)+self.T(self.m,28,'9:41',12,bold=600)+self.T(self.w-self.m,28,'5G ▰',11,anchor='end')+self.G(self.T(self.m,78,'‹',28),back)+self.T(self.w/2,77,title,20,bold=600,anchor='middle')+self.T(self.m,119,sub,12,'muted')
 def tabs(self,labels,active,y=145):
  s='';wid=self.i/len(labels)
  for i,l in enumerate(labels):s+=self.R(self.m+i*wid,y,wid-4,36,'soft' if i==active else 'paper',18)+self.T(self.m+(i+.5)*wid-2,y+24,l,12,'accent' if i==active else 'muted',500,'middle')
  return s
 def row(self,y,label,value,go='',h=54):
  s=self.R(self.m,y,self.i,h)+self.T(self.m+16,y+33,label,13)+self.T(self.w-self.m-16,y+33,value,13,'accent',500,'end')
  return self.G(s,go) if go else s
 def mascot(self,x,y,scale=.7):return f'<g transform="translate({x} {y}) scale({scale})">{ART}</g>'
 def blank(self,title,message,button,go,failed=False):
  return self.H(title)+self.mascot(self.w/2-35,265,.65)+self.T(self.w/2,445,message,18,bold=600,anchor='middle')+self.T(self.w/2,477,'检查网络后重试' if failed else '每一次记录都值得留下',12,'muted',anchor='middle')+self.B(547,button,go)
 def modal(self,base,title,lines,ok='确认删除',go='body-empty'):
  s=base+self.R(0,0,self.w,844,'#000000',24).replace('fill="#000000"','fill="#000000" opacity=".45"')+self.R(self.m,280,self.i,264)+self.T(self.w/2,325,title,20,bold=600,anchor='middle')
  for i,l in enumerate(lines):s+=self.T(self.m+20,365+i*27,l,12,'muted')
  return s+self.B(462,'取消','back',self.m+16,(self.i-44)/2,True)+self.B(462,ok,go,self.w/2+6,(self.i-44)/2)
 def body(self,state='ready'):
  if state=='empty':return self.blank('身体记录','还没有身体记录','记录一次体重','body')
  if state=='failed':return self.blank('身体记录','身体数据读取失败','重新加载','body-history',True)
  if state=='delete':return self.modal(self.body('detail'),'删除这条记录？',['10月19日 · 70.2 kg','删除后无法恢复。'],go='body-empty')
  if state=='detail':
   s=self.H('身体记录详情','10月19日 · 08:00')+self.R(self.m,155,self.i,169)+self.T(self.w/2,199,'体重',13,'muted',anchor='middle')+self.T(self.w/2,267,'70.2 kg',40,bold=600,anchor='middle')
   for i,(l,v) in enumerate([('体脂率','23.5%'),('腰围','78 cm'),('胸围','—'),('臀围','—'),('备注','晨起记录')]):s+=self.row(345+i*60,l,v)
   return s+self.B(700,'编辑记录','body')+self.B(764,'删除这条记录','body-delete',secondary=True)
  if state=='history':
   s=self.H('身体记录','按日期查看身体变化')+self.R(self.m,150,self.i,112)+self.T(self.m+16,184,'最近一次体重',12,'muted')+self.T(self.m+16,230,'70.2 kg',30,bold=600)+self.T(self.w-self.m-16,229,'10月19日',12,'muted',anchor='end')
   for i,(d,v) in enumerate([('10月19日','70.2 kg'),('10月16日','70.4 kg'),('10月12日','70.8 kg')]):s+=self.row(300+i*72,d,v+' ›','body-detail',60)
   return s+self.B(758,'＋ 记录身体数据','body')
  s=self.H('记录身体数据','10月19日 · 08:00  ⌄',back='body-history')+self.T(self.m,186,'当前体重',17,bold=600)+self.R(self.m,205,self.i,230)+self.T(self.w/2,281,'70.2' if state!='invalid' else '—',44,'accent',600,'middle')+self.T(self.w/2,311,'kg · 点击数字输入',12,'muted',anchor='middle')
  for i in range(31):
   x=self.m+18+i*(self.i-36)/30;h=32 if i%10==0 else 19 if i%5==0 else 12;s+=f'<path d="M{x} 338v{h}" stroke="{self.c["line"]}"/>'
  s+=self.T(self.w/2,402,'上次 70.4 kg · 3 天前',12,'muted',anchor='middle')+self.G(self.R(self.m+8,232,self.i-16,87,'#FFFFFF',0).replace('fill="#FFFFFF"','fill="#FFFFFF" opacity="0"'),'body-keyboard')
  s+=self.row(453,'体脂率 · 选填','23.5% ›','body-more')+self.row(519,'围度与备注','展开 ›','body-more')+self.T(self.m,605,'体重必填；其余指标留空表示未记录。',12,'muted')
  if state in ['savefail','invalid']:s+=self.R(self.m,688,self.i,52,'dangerSoft',14)+self.T(self.m+14,720,'保存失败，输入已保留' if state=='savefail' else '请填写有效的体重',12,'danger')
  s+=self.B(758,'重新保存' if state=='savefail' else '保存记录','body-detail')
  if state=='more':
   s+=self.R(0,0,self.w,844,'#000000',24).replace('fill="#000000"','fill="#000000" opacity=".4"')+self.R(0,320,self.w,524)+self.T(self.m,365,'补充身体数据',20,bold=600)
   for i,(l,v) in enumerate([('体脂率','23.5 %'),('腰围','78 cm'),('胸围','选填'),('臀围','选填'),('备注','晨起记录')]):s+=self.row(389+i*58,l,v,'body')
   s+=self.B(758,'确认补充','body')
  if state=='keyboard':s+=self.keyboard('body')
  return s
 def keyboard(self,go):
  s=self.R(0,520,self.w,324)+self.T(self.m,552,'输入体重 · kg',14,bold=600)
  gap=8;kw=(self.i-3*gap)/4
  for i,k in enumerate(['1','2','3','⌫','4','5','6','7','8','9','.','0']):
   x=self.m+i%4*(kw+gap);y=572+i//4*55;s+=self.G(self.R(x,y,kw,47,'soft',12)+self.T(x+kw/2,y+31,k,22,anchor='middle'),'key:'+k)
  return s+self.B(758,'完成输入',go)
 def silhouette(self,x,y,w,h):
  # Explicit anonymous specimen, not a user's or competitor's photograph.
  return self.R(x,y,w,h,'soft',16)+f'<g transform="translate({x+w/2} {y+h*.25})" fill="{self.c["muted"]}" opacity=".25"><circle r="{w*.09}"/><path d="M{-w*.13} {w*.15} Q{-w*.2} {h*.3} {-w*.16} {h*.55} L{-w*.03} {h*.55} V{h*.33} H{w*.03} V{h*.55} H{w*.16} Q{w*.2} {h*.3} {w*.13} {w*.15}Z"/></g>'+self.T(x+w/2,y+h-14,'示例占位',11,'muted',anchor='middle')
 def photos(self,state='ready'):
  if state=='empty':return self.blank('身材照片','留下第一张身材照片','添加照片','photo-upload')
  if state=='failed':return self.blank('身材照片','照片加载失败','重试','photos',True)
  if state=='delete':return self.modal(self.photos('detail'),'删除这张照片？',['删除后无法恢复。','照片记录和备注将一起移除。'],go='photo-empty')
  if state=='detail':return self.H('照片详情','10月19日 · 正面',back='photos')+self.silhouette(self.m,153,self.i,426)+self.row(598,'备注','阶段记录')+self.B(758,'删除照片','photo-delete',secondary=True)
  s=self.H('身材照片','按月份回看身体的变化')+self.tabs(['全部','正面','侧面','背面','其他'],0)+self.T(self.m,226,'2026年10月',16,bold=600)
  cw=(self.i-12)/2
  for i in range(4):
   x=self.m+i%2*(cw+12);y=245+i//2*222;s+=self.G(self.silhouette(x,y,cw,180)+self.T(x,y+205,['10月19日 · 正面','10月19日 · 侧面','10月12日 · 正面','10月12日 · 背面'][i],12,'muted'),'photo-detail')
  s+=self.B(758,'＋ 添加照片','photo-upload')
  if state in ['upload','uploadfail','uploading']:
   s+=self.R(0,0,self.w,844,'#000000',24).replace('fill="#000000"','fill="#000000" opacity=".4"')+self.R(0,206,self.w,638)+self.T(self.m,250,'添加身材照片',20,bold=600)+self.silhouette(self.w/2-63,274,126,175)+self.tabs(['正面','侧面','背面','其他'],0,469)+self.row(523,'拍摄日期','10月19日 ›','photo-date')+self.row(587,'备注 · 选填','阶段记录','photo-note')+self.T(self.m,680,'尽量保持相同光线、距离和拍摄角度。',12,'muted')
   if state=='uploadfail':s+=self.R(self.m,703,self.i,38,'dangerSoft',12)+self.T(self.m+12,728,'保存失败，已保留照片与填写内容',12,'danger')
   s+=self.B(758,'正在保存…' if state=='uploading' else '重新保存' if state=='uploadfail' else '保存照片','photos')
  return s

def svg(body,w=390):return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="844" viewBox="0 0 {w} 844">{body}</svg>'
BODIES=[('body','体重记录','ready'),('body-more','补充字段','more'),('body-keyboard','数字键盘','keyboard'),('body-invalid','体重校验','invalid'),('body-savefail','保存失败','savefail'),('body-history','身体历史','history'),('body-detail','身体详情','detail'),('body-delete','删除确认','delete'),('body-empty','首次无记录','empty'),('body-failed','读取失败','failed')]
PHOTOS=[('photos','照片归档','ready'),('photo-upload','上传与角度','upload'),('photo-uploading','保存中','uploading'),('photo-uploadfail','保存失败','uploadfail'),('photo-detail','照片详情','detail'),('photo-delete','删除确认','delete'),('photo-empty','首次无照片','empty'),('photo-failed','首屏失败','failed')]
ALL={}
for key,title,state in BODIES+PHOTOS:
 for w in [390,320]:
  for dark in [False,True]:
   ui=UI(w,dark);body=ui.body(state) if (key,title,state) in BODIES else ui.photos(state)
   ALL[f'{key}:{w}:{int(dark)}']=svg(body,w)

def board(items,name,cols=4):
 W=24+cols*426;H=140+math.ceil(len(items)/cols)*910+80
 parts=[UI().R(0,0,W,H,'bg',24),UI().T(24,45,name,26,bold=700)]
 for i,(key,title,body,w) in enumerate(items):
  x=24+i%cols*426;y=120+i//cols*910;parts+=[UI().T(x,y-17,title,14,bold=600),f'<g id="{key}" transform="translate({x} {y})">{body}</g>']
 return f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'+''.join(parts)+'</svg>'
for label,items in [('body',BODIES),('photos',PHOTOS)]:
 data=[(k,f'{i+1:02} / {t}',ALL[f'{k}:390:0'].split('>',1)[1].rsplit('</svg>',1)[0],390) for i,(k,t,st) in enumerate(items)]
 (OUT/f'{label}-caljimi.svg').write_text(board(data,'身体记录 / CalJimi 风格与完整状态' if label=='body' else '身材照片 / 归档、上传与失败状态'))
# Reuse all existing editable screens for dark and narrow variants. Preserve imagery and vertical touch sizes.
SOURCE=[('login','fittrace-login-caljimi-2026-10-19.svg','登录页-'),('home','fittrace-home-states-green-2026-10-19.svg','home-state-'),('trends','fittrace-trends-archive-green-2026-10-19.svg',''),('profile','fittrace-profile-green-2026-10-19.svg','profile-screen-'),('strength','fittrace-strength-caljimi-2026-10-19.svg','strength-caljimi-'),('goal','fittrace-goal-forms-caljimi-2026-10-19.svg','form-caljimi-'),('sports','fittrace-training-caljimi-2026-10-19.svg','')]
old_to_dark={'#D3F8C4':'#183B28','#78D99E':'#1D5635','#B8EEAA':'#284C37','#E8ECEA':'#35483D','#E2EBDD':'#28382F','#E9EFE7':'#1D2620','#FFFFFF':DARK['paper'],'#F7F8FA':DARK['bg'],'#F0F6EE':DARK['bg'],'#222222':DARK['text'],'#214B33':DARK['text'],'#777777':DARK['muted'],'#63786A':DARK['muted'],'#E6F8EE':DARK['soft'],'#DDF6DE':DARK['soft'],'#229D60':DARK['accent'],'#187D45':DARK['accent'],'#E6EAE8':DARK['line'],'#D9E6D7':DARK['line'],'#FFF1EE':DARK['dangerSoft'],'#B6473F':DARK['danger'],'#E5FBE2':'#183B28','#DFF8BC':'#183B28','#B5F5C9':'#163927','#F2F8E9':DARK['paper'],'#E7EEE3':DARK['paper'],'#E8EFE4':DARK['paper'],'#FFF3D8':'#493D22'}
def variant(raw,w,dark):
 # Horizontal coordinates adapt; typography retains >=11px and vertical spacing/touch size stays unchanged.
 raw=copy.deepcopy(raw);factor=w/390
 def scale_path(d):
  # Use nested SVG x-axis transform for vector paths; text and basic geometry adapt individually.
  return d
 for n in raw.iter():
  tag=n.tag.split('}')[-1]
  if dark:
   for att in ['fill','stroke','stop-color']:
    old=n.get(att,'').upper()
    if old in old_to_dark:n.set(att,old_to_dark[old])
  if w!=390:
   if tag in ['text','rect','image','circle','ellipse','line']:
    for att in ['x','cx','x1','x2','width','rx']:
     if n.get(att):
      try:n.set(att,str(float(n.get(att))*factor))
      except ValueError:pass
    if tag=='text':
     size=float(n.get('font-size','14'));n.set('font-size',str(max(11,size*.94)))
   if tag=='path':
    prior=n.get('transform','');n.set('transform',f'scale({factor} 1) '+prior)
   if n.get('transform') and tag=='g':
    n.set('transform',re.sub(r'translate\(([-.\d]+)([, ]+)([-.\d]+)\)',lambda m:f'translate({float(m[1])*factor}{m[2]}{m[3]})',n.get('transform')))
 # Keep primary labels dark for contrast in the dark palette.
 if dark:
  for parent in raw.iter():
   rects=[n for n in parent if n.tag==NS+'rect' and n.get('fill','').upper()=='#24C867' and float(n.get('height','0'))>=40]
   for t in parent:
    if t.tag!=NS+'text':continue
    for r in rects:
     if float(r.get('x','0'))<=float(t.get('x','0'))<=float(r.get('x','0'))+float(r.get('width','0')) and float(r.get('y','0'))<=float(t.get('y','0'))<=float(r.get('y','0'))+float(r.get('height','0')):t.set('fill','#092B17')
 return raw
catalog=[]
for module,filename,prefix in SOURCE:
 r=E.parse(ROOT/filename).getroot();defs=r.find(NS+'defs');groups=[g for g in r.findall(NS+'g') if (g.get('id','').startswith(prefix) if prefix else g.get('transform','').startswith('translate('))]
 # Only phone screen groups; source board notes and decorative groups are excluded.
 groups=[g for g in groups if any(n.tag==NS+'rect' and n.get('width')=='390' and n.get('height')=='844' for n in g.iter())]
 for i,g in enumerate(groups):
  if module=='profile' and i+1 in [5,15,16]:continue  # superseded goal screens
  key=f'{module}-{i+1}';base=copy.deepcopy(g);base.attrib.pop('transform',None)
  for w in [390,320]:
   for dark in [False,True]:
    parts=[]
    if defs is not None:parts.append(E.tostring(variant(defs,390,dark),encoding='unicode'))
    parts.append(E.tostring(variant(base,w,dark),encoding='unicode'))
    body=''.join(parts).replace('ns0:','').replace(':ns0','')
    ALL[f'{key}:{w}:{int(dark)}']=svg(body,w)
  catalog.append((key,module))
 # One editable dark board includes all states; a narrow-light board provides all-state overflow review.
 for mode,w,dark in [('dark',390,True),('narrow',320,False)]:
  items=[]
  for key,mod in catalog:
   if mod==module:
    body=ALL[f'{key}:{w}:{int(dark)}'].split('>',1)[1].rsplit('</svg>',1)[0];items.append((f'{key}-{mode}',f'{key} / {mode}',body,w))
  (OUT/f'{module}-{mode}.svg').write_text(board(items,f'{module} / '+('深色完整状态' if dark else '320px 窄屏完整状态')))
for mod,states in [('body',BODIES),('photos',PHOTOS)]:
 for mode,w,dark in [('dark',390,True),('narrow',320,False)]:
  (OUT/f'{mod}-{mode}.svg').write_text(board([(k+'-'+mode,t,ALL[f'{k}:{w}:{int(dark)}'].split('>',1)[1].rsplit('</svg>',1)[0],w) for k,t,st in states],mod+' / '+mode))
import base64
rice='data:image/png;base64,'+base64.b64encode((OUT/'assets/rice.png').read_bytes()).decode();egg='data:image/png;base64,'+base64.b64encode((OUT/'assets/egg.png').read_bytes()).decode()
def extra(ui,key):
 w=ui.w;m=ui.m;s='';R=ui.R;T=ui.T;B=ui.B;G=ui.G
 if key.startswith('meal'):
  s=ui.H('饮食记录' if key=='meal-detail' else '记录饮食','10月19日 · 午餐',back='home')+R(m,145,ui.i,43)+T(m+16,173,'搜索食物名称',13,'muted')
  if key=='meal-detail':
   for i,(n,k,img) in enumerate([('米饭','174 kcal',rice),('鸡蛋','72 kcal',egg)]):
    y=220+i*104;s+=R(m,y,ui.i,88)+f'<image x="{m+12}" y="{y+15}" width="54" height="54" href="{img}"/>'+T(m+80,y+36,n,16,bold=600)+T(m+80,y+63,k,13,'accent')
   return s+ui.row(480,'已记录热量','246 kcal')+ui.row(548,'备注','午餐')+B(700,'编辑饮食','meal')+B(764,'返回档案','archive',secondary=True)
  s+=R(m,204,ui.i,42,'soft')+G(T(m+16,231,'拍照 / 文字速记  ›',13,'accent',600),'meal-ai')
  for i,l in enumerate(['我的常用','主食杂粮','蛋奶豆类','肉类水产','蔬菜水果','自定义']):
   y=266+i*63;s+=R(m,y,72,52,'soft' if i==0 else 'paper',12)+T(m+36,y+31,l,11,'accent' if i==0 else 'muted',anchor='middle')
  for i,(n,k,img) in enumerate([('米饭','116 kcal / 100g',rice),('鸡蛋','144 kcal / 100g',egg)]):
   y=267+i*98;s+=f'<image x="{m+88}" y="{y}" width="42" height="42" href="{img}"/>'+T(m+140,y+16,n,14,bold=600)+T(m+140,y+39,k,11,'muted')+G(T(w-m-14,y+23,'+',22,'accent'),'meal-portion')
  s+=B(758,'已选 2 项 · 核对记录','meal-list')
  if key in ['meal-portion','meal-list','meal-ai','meal-custom']:
   s+=R(0,0,w,844,'#000000',24).replace('fill="#000000"','fill="#000000" opacity=".4"')+R(0,366,w,478)+T(m,409,{'meal-portion':'米饭 · 调整份量','meal-list':'已选食物 · 2 项','meal-ai':'AI 文字速记','meal-custom':'添加自定义食物'}[key],20,bold=600)
   if key=='meal-portion':
    s+=T(w/2,483,'150 g',38,'accent',600,'middle')+ui.row(523,'当前热量','174 kcal')+ui.row(587,'份量预设','半碗 / 1碗 / 2碗')+B(758,'确认份量','meal-list')
   elif key=='meal-list':s+=ui.row(445,'米饭 · 150 g','174 kcal','meal-portion')+ui.row(515,'鸡蛋 · 50 g','72 kcal','meal-portion')+T(m,623,'合计 246 kcal',18,'accent',600)+B(758,'保存饮食记录','meal-detail')
   elif key=='meal-ai':s+=R(m,437,ui.i,162,'bg')+T(m+16,474,'例如：午餐一碗米饭和一个鸡蛋',12,'muted')+T(m,642,'识别结果需要核对后再保存。',12,'muted')+B(758,'识别后核对','meal-list')
   else:s+=ui.row(444,'食物名称','输入名称')+ui.row(512,'每份重量','g')+ui.row(580,'热量 · 可后补','kcal')+B(758,'加入清单','meal-list')
  return s
 title={'workout-detail':'运动详情','template-edit':'编辑训练模板','training-plan':'每周训练计划','exercise-detail':'动作详情','password':'修改密码','about':'关于循形','agreement':'用户协议','privacy':'隐私政策'}[key]
 s=ui.H(title,'',back='profile')
 if key=='password':
  for i,(l,v) in enumerate([('当前密码','输入当前密码'),('新密码','输入新密码'),('确认新密码','再次输入新密码')]):s+=ui.row(176+i*92,l,v)
  return s+T(m,522,'保存成功后使用新密码登录。',12,'muted')+B(690,'通过邮箱验证修改','password-email',secondary=True)+B(758,'保存密码','login')
 if key in ['agreement','privacy']:
  raw=(ROOT.parents[1]/'apps/h5/src/components/PolicyContent.vue').read_text()
  articles=re.findall(r'<article[^>]*>(.*?)</article>',raw,re.S)
  article=articles[0 if key=='agreement' else 1]
  sections=re.findall(r'<section>(.*?)</section>',article,re.S)
  y=169
  for sec in sections[:2]:
   heading=re.search(r'<h2>(.*?)</h2>',sec,re.S)[1].strip();s+=T(m,y,heading,16,bold=600);y+=35
   paragraph=''.join(re.findall(r'<p>(.*?)</p>',sec,re.S));paragraph=re.sub(r'\s+','',paragraph)
   count=max(1,int(ui.i/12))
   for start in range(0,len(paragraph),count):
    if y>693:break
    s+=T(m,y,paragraph[start:start+count],12,'muted');y+=24
   y+=25
  return s+B(758,'阅读全文','policy-full:'+key)
 if key=='about':return s+ui.mascot(w/2-43,191,.8)+T(w/2,402,'循形 FitTrace',25,bold=600,anchor='middle')+T(w/2,439,'记录每一次改变',13,'muted',anchor='middle')+ui.row(520,'版本','0.1.0')+ui.row(590,'用户协议','›','agreement')+ui.row(654,'隐私政策','›','privacy')
 if key=='workout-detail':return s+ui.row(163,'训练名称','全身基础')+ui.row(231,'训练时长','19 分钟')+ui.row(299,'已完成','2 动作 · 5 组')+ui.row(395,'单臂哑铃划船','12 kg × 10 × 3')+ui.row(463,'哑铃卧推','10 kg × 12 × 2')+B(758,'编辑记录','strength-list')
 if key=='training-plan':
  for i,d in enumerate('一二三四五六日'):s+=ui.row(151+i*75,'星期'+d,'休息' if i%2 else '选择模板 ›','template-edit')
  return s+B(758,'保存计划','profile')
 if key=='template-edit':return s+ui.row(156,'模板名称','全身基础')+ui.row(230,'预计时长','30 分钟')+ui.row(322,'徒手深蹲','3 组')+ui.row(390,'跪姿俯卧撑','3 组')+B(520,'＋ 添加动作','exercise-detail',secondary=True)+B(758,'保存模板','strength')
 return s+T(m,185,'徒手深蹲',23,bold=600)+T(m,224,'腿部 · 徒手 · 入门',13,'muted')+ui.row(273,'步骤 1','双脚站稳，脚尖稍向外')+ui.row(347,'步骤 2','收紧躯干，屈髋屈膝下蹲')+ui.row(421,'步骤 3','保持可控幅度，平稳站起')+T(m,527,'动作提示',16,bold=600)+T(m,565,'膝盖随脚尖方向移动，保持自然呼吸。',12,'muted')+B(758,'加入我的模板','template-edit')
EXTRA=['meal','meal-portion','meal-list','meal-ai','meal-custom','meal-detail','workout-detail','template-edit','training-plan','exercise-detail','password','about','agreement','privacy']
for key in EXTRA:
 for w in [390,320]:
  for dark in [False,True]:ALL[f'{key}:{w}:{int(dark)}']=svg(extra(UI(w,dark),key),w)
for mode,w,dark in [('light',390,False),('dark',390,True),('narrow',320,False)]:
 (OUT/f'other-{mode}.svg').write_text(board([(k+'-'+mode,k,ALL[f'{k}:{w}:{int(dark)}'].split('>',1)[1].rsplit('</svg>',1)[0],w) for k in EXTRA],'饮食与二级页 / '+mode))

# Remaining route and account-security screens, based on the existing Vue contracts.
def completion(ui,key):
 m=ui.m;w=ui.w;T=ui.T;R=ui.R;B=ui.B;s=ui.H({'record-hub':'添加记录','training-tools':'运动工具','sports-custom':'自定义运动','workout-invalid':'记录运动','workout-savefail':'记录运动','password-email':'邮箱验证','password-code-error':'邮箱验证','password-reset':'设置新密码','password-expired':'验证已失效','password-loadfail':'账号安全','goal-maintain':'保持目标','template-empty':'我的模板','template-failed':'训练模板','body-loading':'身体记录','photo-loading':'身材照片'}[key])
 if key=='record-hub':
  for i,(l,v,go) in enumerate([('身体数据','体重、体脂与围度','body'),('饮食记录','记录每一餐','meal'),('运动工具','运动、动作与训练计划','training-tools'),('身材照片','留下不同阶段的变化','photos')]):s+=ui.row(166+i*105,l,v+' ›',go,82)
  return s
 if key=='training-tools':
  for i,(l,go) in enumerate([('记录运动','sports'),('力量逐组训练','strength'),('动作库','exercise-detail'),('我的训练模板','strength-6'),('每周训练计划','training-plan')]):s+=ui.row(161+i*92,l,'›',go,70)
  return s
 if key in ['body-loading','photo-loading']:
  for i in range(5):s+=R(m,166+i*92,ui.i,66,'paper')+R(m+14,185+i*92,ui.i*.55,12,'line',6)
  return s+T(w/2,717,'正在读取身体记录…' if key=='body-loading' else '正在整理照片…',12,'muted',anchor='middle')
 if key=='goal-maintain':return s+R(m,164,ui.i,95,'soft')+T(m+16,203,'维持当前状态和记录习惯',16,bold=600)+T(m+16,234,'当前体重 70.2 kg',13,'muted')+T(w/2,366,'70.2 kg',42,'accent',600,'middle')+T(w/2,408,'目标预填当前体重，可继续调整',12,'muted',anchor='middle')+ui.row(517,'目标日期 · 选填','选择日期 ›')+B(758,'保存保持目标','profile')
 if key=='template-empty':return ui.blank('我的模板','还没有自己的模板','创建我的模板','template-edit')+B(620,'查看预设模板','strength-6',secondary=True)
 if key=='template-failed':return s+R(m,164,ui.i,75,'dangerSoft')+T(m+16,194,'我的模板加载失败',15,'danger',600)+T(m+16,222,'预设模板仍可查看，点击重试。',12,'muted')+ui.row(291,'全身基础','4 个动作 ›','strength-6')+B(758,'重新加载','strength-6')
 if key=='password-loadfail':return ui.blank('账号安全','账号信息读取失败','重新读取','password',True)
 if key=='password-expired':return s+ui.mascot(w/2-34,220,.6)+T(w/2,401,'请重新验证邮箱',19,bold=600,anchor='middle')+T(w/2,440,'本次验证已失效，新密码输入已清空。',12,'muted',anchor='middle')+B(758,'重新验证邮箱','password-email')
 if key in ['password-email','password-code-error']:
  s+=R(m,164,ui.i,79,'soft')+T(m+16,194,'验证当前账号的邮箱',15,bold=600)+T(m+16,225,'hello@example.com',13,'muted')+ui.row(281,'邮箱验证码','输入 6 位验证码')+B(360,'安全验证后发送验证码','password-email',secondary=True)+T(m,452,'请查看邮箱获取验证码，稍后可重新获取。',12,'muted')
  if key=='password-code-error':s+=R(m,515,ui.i,56,'dangerSoft')+T(m+14,549,'验证码无效或已过期，请重新获取',12,'danger')
  return s+B(758,'验证邮箱并继续','password-reset')
 if key=='password-reset':return s+R(m,164,ui.i,58,'soft')+T(m+16,201,'邮箱已验证，请设置新密码。',14,'accent')+ui.row(266,'新密码','输入新密码')+ui.row(345,'确认新密码','再次输入新密码')+T(m,470,'密码保存成功后，需要重新登录。',12,'muted')+B(758,'保存并重新登录','login')
 for i,(l,v) in enumerate([('运动名称','填写运动名称'),('运动类型','有氧 / 力量'),('开始时间','10月19日 18:00'),('实际时长','分钟'),('消耗热量 · 选填','kcal'),('备注 · 选填','补充说明')]):s+=ui.row(149+i*81,l,v)
 if key!='sports-custom':s+=R(m,665,ui.i,66,'dangerSoft')+T(m+14,704,'请填写有效运动名称和时长' if key=='workout-invalid' else '保存失败，已保留全部输入',12,'danger')
 return s+B(758,'重新保存' if key=='workout-savefail' else '保存运动','workout-detail')
COMPLETION=['record-hub','training-tools','sports-custom','workout-invalid','workout-savefail','password-email','password-code-error','password-reset','password-expired','password-loadfail','goal-maintain','template-empty','template-failed','body-loading','photo-loading']
for key in COMPLETION:
 for w in [390,320]:
  for dark in [False,True]:ALL[f'{key}:{w}:{int(dark)}']=svg(completion(UI(w,dark),key),w)
(OUT/'completion-light.svg').write_text(board([(k,k,ALL[f'{k}:390:0'].split('>',1)[1].rsplit('</svg>',1)[0],390) for k in COMPLETION],'账号安全与记录工具 / 路由补齐'))

# Main route aliases and click targets are explicit, not inferred from arbitrary element ordering.
ALIASES={'home':'home-2','login':'login-1','trends':'trends-1','archive':'trends-7','profile':'profile-1','settings':'profile-6','edit-profile':'profile-4','goal':'goal-1','goal-target':'goal-2','strength':'strength-1','strength-input':'strength-2','strength-list':'strength-4','sports':'sports-2'}
# IDs from existing sources vary. Fail instead of silently claiming missing modules.
for k,v in list(ALIASES.items()):
 if f'{v}:390:0' not in ALL:print('MISSING_ALIAS',k,v)
(OUT/'screens.json').write_text(json.dumps(ALL,ensure_ascii=False))
(OUT/'catalog.json').write_text(json.dumps({'aliases':ALIASES,'screens':[k for k in ALL if k.endswith(':390:0')],'sourceModules':catalog},ensure_ascii=False,indent=2))
print('screens',len(ALL),'source states',len(catalog),'body/photo states',len(BODIES+PHOTOS))

# Final all-state review boards: Current screens per mode; superseded goal screens excluded.
for mode,w,dark in [('dark-all',390,1),('narrow-all',320,0),('narrow-dark-all',320,1)]:
 items=[]
 for fullkey,markup in ALL.items():
  if not fullkey.endswith(f':{w}:{dark}'):continue
  key=fullkey.split(':')[0];body=markup.split('>',1)[1].rsplit('</svg>',1)[0]
  items.append((f'{mode}-{key}',key,body,w))
 (OUT/f'{mode}.svg').write_text(board(items,'全界面 / '+('深色完整状态' if dark else '320px 窄屏完整状态'),cols=8))

# Native Figma body/photo prototype screens and stable named hotspots.
NATIVE={'P02-body':('body',[(24,758,342,50,'P02-SAVE')]),'P03-body-detail':('body-detail',[(8,45,47,48,'P03-BACK')]),'P04-photos':('photos',[(24,758,342,50,'P04-ADD')]),'P05-upload':('photo-upload',[(24,758,342,50,'P05-SAVE')])}
for name,(key,hits) in NATIVE.items():
 r=E.fromstring(ALL[key+':390:0'])
 for x,y,w,h,ident in hits:
  g=E.SubElement(r,NS+'g',id=ident);E.SubElement(g,NS+'rect',x=str(x),y=str(y),width=str(w),height=str(h),fill='#FFFFFF',opacity='0.01')
 (OUT/(name+'.svg')).write_text(E.tostring(r,encoding='unicode').replace('ns0:','').replace(':ns0',''))

raw=(ROOT.parents[1]/'apps/h5/src/components/PolicyContent.vue').read_text()
articles=re.findall(r'<article[^>]*>(.*?)</article>',raw,re.S)
(OUT/'policies.json').write_text(json.dumps(dict(zip(['agreement','privacy'],articles)),ensure_ascii=False))
