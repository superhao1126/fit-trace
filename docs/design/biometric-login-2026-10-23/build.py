"""Editable biometric-login design and an offline click-through, stdlib only."""
from pathlib import Path
from html import escape
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
TOKENS = json.loads((ROOT.parent / 'final/theme-tokens.json').read_text())
STATES = [
    ('settings-off', '01 · 设置 / 未开启'),
    ('settings-on', '02 · 设置 / 已开启'),
    ('login', '03 · 首页登录 / 指纹优先'),
    ('email-login', '04 · 首页登录 / 邮箱回退'),
    ('verify-email', '05 · 开启 / 按需验证邮箱'),
    ('verify-error', '06 · 开启 / 验证码错误'),
    ('system-prompt', '07 · 系统验证 / 示意'),
    ('disable', '08 · 设置 / 关闭确认'),
    ('not-enrolled', '09 · 设置 / 尚未录入'),
    ('unsupported', '10 · 设置 / 设备不支持'),
    ('check-error', '11 · 设置 / 检查失败'),
    ('pending', '12 · 设置 / 开启结果待确认'),
    ('login-cancel', '13 · 登录 / 已取消验证'),
    ('login-network', '14 · 登录 / 网络异常'),
    ('login-invalid', '15 · 登录 / 凭据失效'),
    ('login-locked', '16 · 登录 / 系统暂时锁定'),
]

old = ET.parse(ROOT.parent / 'fittrace-login-caljimi-2026-10-19.svg').getroot()
NS = '{http://www.w3.org/2000/svg}'
first = next(e for e in old if e.tag == NS+'g')
illustration = ''.join(ET.tostring(e, encoding='unicode') for e in first
                       if e.tag in (NS+'ellipse', NS+'g'))
illustration = re.sub(r'ns\d+:', '', illustration)

class Screen:
    def __init__(self, state, width, theme):
        self.state, self.w, self.h, self.theme = state, width, 844, theme
        self.t = TOKENS[theme]
        self.parts, self.hotspots = [], []

    def rect(self, x,y,w,h,fill,r=16,stroke=None,opacity=None):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"'
                          +(f' stroke="{stroke}"' if stroke else '')
                          +(f' opacity="{opacity}"' if opacity is not None else '')+'/>' )

    def text(self,x,y,s,size=14,color=None,weight=400,anchor='start'):
        self.parts.append(f'<text x="{x}" y="{y}" font-family="Noto Sans SC, PingFang SC, sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{color or self.t["text"]}">{escape(s)}</text>')

    def para(self,x,y,s,width=None,size=13,color=None,line=22):
        width = width or self.w-2*x
        current, units, rows = '', 0, []
        for ch in s:
            amount = size*(.56 if ord(ch)<128 else 1)
            if ch == '\n' or units+amount>width:
                rows.append(current);current='';units=0
                if ch=='\n':continue
            current+=ch;units+=amount
        if current:rows.append(current)
        for i,row in enumerate(rows):self.text(x,y+i*line,row,size,color)
        return y+len(rows)*line

    def hit(self,x,y,w,h,target,label):
        self.hotspots.append(dict(x=x,y=y,w=w,h=h,target=target,label=label))

    def button(self,y,label,target,secondary=False,disabled=False,x=24,width=None):
        width=width or self.w-48
        self.rect(x,y,width,52,self.t['soft'] if secondary or disabled else self.t['primary'],26)
        self.text(x+width/2,y+32,label,15,self.t['muted'] if disabled else (self.t['accent'] if secondary else '#113821'),600,'middle')
        if not disabled:self.hit(x,y,width,52,target,label)

    def link(self,y,label,target,color=None):
        self.text(self.w/2,y+28,label,14,color or self.t['accent'],500,'middle')
        self.hit(24,y,self.w-48,48,target,label)

    def finger(self,x,y,side=64):
        paths=['M8 26C8 12 18 4 32 4S56 14 56 28',
               'M15 43C18 36 16 32 17 25C18 17 24 13 32 13S47 19 47 28C47 43 42 51 38 57',
               'M24 50C28 42 27 36 26 29C25 20 38 20 38 29C39 39 36 47 32 54',
               'M6 36C7 47 4 48 3 51','M55 38C54 48 50 56 47 60','M32 30C34 42 28 54 25 58']
        self.parts.append(f'<g transform="translate({x} {y}) scale({side/64})" fill="none" stroke="{self.t["accent"]}" stroke-width="2.8" stroke-linecap="round">'+''.join(f'<path d="{p}"/>' for p in paths)+'</g>')

    def status(self):
        self.text(24,27,'9:41',12,weight=600)
        for i,h in enumerate([4,7,10,13]):self.rect(self.w-77+i*5,26-h,3,h,self.t['text'],1)
        self.rect(self.w-49,14,24,12,'none',3,self.t['text'])
        self.rect(self.w-46,17,17,6,self.t['text'],1)
        self.rect(self.w-24,18,2,4,self.t['text'],1)

    def nav(self,title='指纹登录'):
        self.parts.append(f'<path d="M29 56L21 64L29 72" fill="none" stroke="{self.t["text"]}" stroke-width="2" stroke-linecap="round"/>')
        self.text(self.w/2,70,title,18,weight=600,anchor='middle')
        self.hit(8,42,48,44,'settings-off','返回登录安全（演示）')

    def notice(self,y,copy,error=False,action=None,target='login'):
        end=self.para(40,y+24,copy,self.w-80,13,self.t['danger'] if error else self.t['accent'],21)
        height=end-y+6+(36 if action else 0)
        box=f'<rect x="24" y="{y}" width="{self.w-48}" height="{height}" rx="14" fill="{self.t["dangerSoft"] if error else self.t["soft"]}"/>'
        # Background belongs before this notice's text, regardless of wrapping.
        start = next(i for i in range(len(self.parts)-1,-1,-1) if '<text ' in self.parts[i] and f'y="{y+24}"' in self.parts[i])
        self.parts.insert(start,box)
        if action:
            self.text(40,end+20,action,13,self.t['accent'],600)
            self.hit(32,end-4,self.w-64,44,target,action)
        return y+height

    def settings(self):
        t,w,s=self.t,self.w,self.state
        self.rect(0,0,w,844,t['bg'],28);self.status();self.nav()
        on=s in ('settings-on','disable')
        issue=s in ('not-enrolled','unsupported','check-error','pending')
        self.rect(16,104,w-32,232,t['paper'],24)
        self.rect(w/2-40,124,80,80,t['soft'],26);self.finger(w/2-27,137,54)
        title='用指纹，轻松登录' if not on else '指纹登录已开启'
        if issue:title={'not-enrolled':'先录入指纹，再开启','unsupported':'当前设备暂不支持','check-error':'暂时无法确认状态','pending':'正在确认开启结果'}[s]
        self.text(w/2,241,title,21,weight=600,anchor='middle')
        self.text(w/2,270,'退出账号后，也能快捷回来' if not on else '下次登录，验证指纹即可',13,t['muted'],anchor='middle')
        badge='仅在本机生效' if not on else '已开启 · 本机有效'
        self.rect(w/2-65,289,130,27,t['soft'],13)
        self.text(w/2,307,badge,12,t['accent'],500,'middle')
        self.text(24,370,'本机登录方式',12,t['muted'],500)
        self.rect(16,386,w-32,104,t['paper'],20)
        self.text(32,417,'指纹登录',16,weight=500)
        self.text(32,443,'验证后登录当前账号',12,t['muted'])
        track=t['primary'] if on else t['line']
        self.rect(w-83,401,50,30,track,15)
        if s in ('check-error','pending'):
            self.text(w-58,421,'…',18,t['muted'],600,'middle')
        else:self.rect(w-80+(20 if on else 0),404,24,24,'#FFFFFF',12)
        if not issue:self.hit(w-100,390,82,58,'disable' if on else 'system-prompt','关闭指纹登录' if on else '开启指纹登录')
        self.text(32,473,'h***@fittrace.cn · 当前账号',12,t['muted'])
        if issue:
            copy,action,target={
                'not-enrolled':('尚未录入可用的指纹，请先到系统设置录入。','前往系统设置','system-prompt'),
                'unsupported':('此设备不支持所需的生物识别。你仍可使用邮箱登录。',None,'email-login'),
                'check-error':('网络连接失败，当前状态未确认。','重新检查','settings-off'),
                'pending':('开启结果尚未确认，请联网检查。确认前不会重复开启。','检查开启结果','settings-on'),
            }[s]
            end=self.notice(510,copy,error=s=='check-error',action=action,target=target)
            self.para(24,end+30,'邮箱登录始终可用。仅确认状态后更新开关。',size=13,color=t['muted'])
        else:
            self.text(24,527,'使用说明',12,t['muted'],500)
            self.rect(16,543,w-32,212 if w==320 else 174,t['paper'],20)
            y=572
            for i,copy in enumerate(['本机已录入的有效生物特征均可用于登录，请仅录入你信任的生物特征。','循形不会读取或保存你的指纹信息。','普通退出仍可使用；关闭后，当前账号保持登录。']):
                self.rect(32,y-12,5,5,t['accent'],2)
                y=self.para(48,y,copy,w-80,13,t['muted'],21)+10
        self.text(w/2,789,'开启与关闭仅影响当前设备',12,t['muted'],anchor='middle')
        if s=='disable':self.dialog()

    def dialog(self):
        t,w=self.t,self.w
        self.hotspots=[]
        self.rect(0,0,w,844,'#000000',28,opacity='.36')
        self.rect(24,286,w-48,254,t['paper'],24)
        self.text(48,326,'关闭指纹登录？',20,weight=600)
        self.para(48,362,'关闭后，这台设备将无法使用指纹登录。当前账号会保持登录，之后仍可用邮箱登录。',w-96,14,t['muted'],24)
        self.button(432,'确认关闭','settings-off',x=48,width=w-96)
        self.link(484,'暂不关闭','settings-on')

    def email_verify(self):
        t,w=self.t,self.w
        self.rect(0,0,w,844,t['bg'],28);self.status();self.nav('验证身份')
        self.rect(16,104,w-32,246,t['paper'],24)
        self.rect(32,126,44,44,t['soft'],14)
        self.text(54,155,'@',24,t['accent'],600,'middle')
        self.text(32,205,'确认是你，再开启',23,weight=600)
        self.para(32,237,'为保护账号，需先验证当前邮箱。验证成功后，继续系统指纹验证。',w-64,14,t['muted'],24)
        self.text(32,321,'h***@fittrace.cn',15,weight=500)
        self.text(24,387,'邮箱验证码',13,t['muted'])
        self.rect(24,402,w-48,56,t['paper'],16,t['danger'] if self.state=='verify-error' else t['line'])
        self.text(40,437,'268193' if self.state=='verify-error' else '输入 6 位验证码',15,t['text'] if self.state=='verify-error' else t['muted'])
        self.text(w-40,437,'重新获取' if self.state=='verify-error' else '获取验证码',12,t['accent'],500,'end')
        self.hit(w-124,405,96,48,'verify-email','发送验证码（演示）')
        if self.state=='verify-error':self.text(24,485,'验证码不正确，请重新输入',13,t['danger'])
        else:self.text(24,485,'验证码仅用于本次身份验证',12,t['muted'])
        self.button(518,'验证并继续','system-prompt')
        self.link(583,'暂不开启','settings-off')
        self.text(w/2,786,'身份验证通过后才会开启指纹登录',12,t['muted'],anchor='middle')

    def login(self):
        t,w,s=self.t,self.w,self.state
        dark=self.theme=='dark'
        self.rect(0,0,w,844,t['bg'],28)
        self.rect(0,0,w,355,'#163E29' if dark else '#D3F8C4',28)
        self.status()
        self.text(24,75,'循形',22,weight=700)
        self.text(83,73,'FITTRACE',10,t['muted'],600)
        self.text(24,122,'记录一点，慢慢变好',24 if w==320 else 27,weight=700)
        self.text(24,150,'饮食、运动和身体变化，都记在这里',12,t['muted'])
        self.parts.append(f'<g transform="translate({(w-390*.78)/2} 32) scale(.78)">{illustration}</g>')
        self.rect(12,294,w-24,500,t['paper'],28)
        self.text(28,334,'欢迎回来' if s!='email-login' else '邮箱登录',23,weight=700)
        if s=='email-login':
            self.text(28,359,'新邮箱验证后自动注册',12,t['muted'])
            for y,label,placeholder in [(389,'邮箱','输入你的邮箱'),(479,'验证码','输入 6 位验证码')]:
                self.text(28,y,label,12,t['muted'])
                self.rect(28,y+13,w-56,54,t['soft'],16)
                self.text(44,y+46,placeholder,14,t['muted'])
            self.text(w-44,525,'获取验证码',12,t['accent'],500,'end')
            self.button(579,'验证并登录','settings-on',disabled=True,x=28,width=w-56)
            self.link(640,'使用指纹登录','login')
            self.rect(28,710,16,16,'none',4,t['line'])
            self.text(52,722,'我已阅读并同意',11,t['muted'])
            self.text(52,746,'《用户协议》和《隐私政策》',11,t['accent'])
            self.hit(28,706,w-56,48,'email-login','协议沿用现有原文（演示）')
            self.text(w-28,666,'密码登录',12,t['muted'],anchor='end')
            self.hit(w-110,639,90,48,'email-login','密码模式沿用现有设计')
        else:
            self.text(28,359,'验证指纹，继续你的记录',12,t['muted'])
            self.rect(w/2-27,383,54,54,t['soft'],20)
            self.text(w/2,419,'循',22,t['accent'],600,'middle')
            self.text(w/2,465,'h***@fittrace.cn',16,weight=500,anchor='middle')
            self.text(w/2,492,'本机已开启指纹登录',12,t['muted'],anchor='middle')
            if s in ('login','system-prompt'):self.finger(w/2-20,520,40)
            label='指纹登录'
            if s=='login-invalid':label='使用邮箱登录'
            if s=='login-locked':label='指纹暂不可用'
            self.button(581,label,'email-login' if s=='login-invalid' else 'system-prompt',disabled=s=='login-locked',x=28,width=w-56)
            self.link(640,'使用邮箱登录' if s!='login-invalid' else '移除此设备上的账号', 'email-login' if s!='login-invalid' else 'login-invalid')
            if s!='login-invalid':self.text(w/2,730,'指纹信息由系统验证，循形不会读取或保存',11,t['muted'],anchor='middle')
            else:self.para(32,720,'邮箱登录后，可在设置中重新开启指纹登录。',w-64,12,t['muted'])
            if s in ('login-cancel','login-network','login-invalid','login-locked'):
                copy={'login-cancel':'已取消验证，点击指纹登录可重试。','login-network':'网络连接失败，请检查网络后重试。','login-invalid':'本机指纹登录已失效，请使用邮箱登录。','login-locked':'验证次数过多，请稍后重试或使用邮箱。'}[s]
                # A single notice replaces the small fingerprint illustration.
                self.notice(499,copy,error=s!='login-cancel')
        self.text(w/2,822,'每一点记录，都算数。',11,t['muted'],anchor='middle')

    def prompt(self):
        self.state='login';self.login();self.state='system-prompt'
        self.hotspots=[]
        t,w=self.t,self.w
        self.rect(0,0,w,844,'#000000',28,opacity='.4')
        self.rect(16,446,w-32,346,t['paper'],28)
        self.text(40,484,'Android 系统验证 · 示意',12,t['muted'])
        self.text(40,526,'登录循形',23,weight=600)
        self.para(40,559,'请验证此设备已录入的生物特征',w-80,14,t['muted'])
        self.finger(w/2-28,604,56)
        self.link(688,'取消','login-cancel')
        self.text(w/2,765,'系统弹窗样式随设备与系统版本变化',11,t['muted'],anchor='middle')

    def build(self):
        if self.state in ('verify-email','verify-error'):self.email_verify()
        elif self.state=='system-prompt':self.prompt()
        elif self.state.startswith('login') or self.state=='email-login':self.login()
        else:self.settings()
        return '<svg xmlns="http://www.w3.org/2000/svg" width="'+str(self.w)+'" height="844" viewBox="0 0 '+str(self.w)+' 844">'+''.join(self.parts)+'</svg>'

def board(states,theme,width,cols):
    W=48+cols*(width+28)-28;H=120+((len(states)+cols-1)//cols)*910
    pieces=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',f'<rect width="{W}" height="{H}" fill="#EDF0ED"/>',
    '<text x="24" y="42" font-family="Noto Sans SC, sans-serif" font-size="26" font-weight="700" fill="#222222">循形 / 指纹登录与登录安全</text>',
    f'<text x="24" y="74" font-family="Noto Sans SC, sans-serif" font-size="14" fill="#626D65">2026.10.23 · {width}px / {theme} · 可编辑矢量与文字 · 系统验证仅作示意</text>']
    for i,(key,label) in enumerate(states):
        x=24+(i%cols)*(width+28);y=124+(i//cols)*910
        svg=Screen(key,width,theme).build()
        inner=svg[svg.index('>')+1:svg.rindex('</svg>')]
        pieces.extend([f'<text x="{x}" y="{y-14}" font-family="Noto Sans SC, sans-serif" font-size="15" fill="#222222" font-weight="600">{label}</text>',f'<g id="{key}-{theme}-{width}" transform="translate({x} {y})">{inner}</g>'])
    pieces.append('</svg>')
    return ''.join(pieces)

def main():
    manifest={}
    for theme in ('light','dark'):
        for width in (390,320):
            folder=ROOT/f'{theme}-{width}';folder.mkdir(exist_ok=True)
            for key,label in STATES:
                page=Screen(key,width,theme);svg=page.build()
                (folder/f'{key}.svg').write_text(svg)
                manifest[f'{key}-{theme}-{width}']={'label':label,'path':f'{theme}-{width}/{key}.svg','hotspots':page.hotspots}
            (ROOT/f'all-{theme}-{width}.svg').write_text(board(STATES,theme,width,4))
    (ROOT/'overview.svg').write_text(board(STATES[:4],'light',390,4))
    (ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    (ROOT/'index.html').write_text(HTML.replace('__STATES__',json.dumps(STATES,ensure_ascii=False)).replace('__MANIFEST__',json.dumps(manifest,ensure_ascii=False)))
    print(f'Built {len(manifest)} editable SVG screens and 5 boards.')

HTML='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>循形 · 指纹登录设计</title><style>
*{box-sizing:border-box}body{margin:0;background:#edf0ed;color:#222;font:14px -apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif}header{padding:20px 28px;background:#fff;border-bottom:1px solid #dde4df;display:flex;gap:14px;align-items:center;flex-wrap:wrap}h1{font-size:18px;margin:0;margin-right:auto}button,select{font:inherit;border:1px solid #dce5de;border-radius:10px;padding:10px 14px;background:white;color:#243b2c;cursor:pointer}button[aria-pressed=true]{background:#dcf6e4;border-color:#24c867}main{padding:28px;display:flex;justify-content:center;gap:24px;align-items:flex-start}.panel{position:relative;flex-shrink:0}.label{font-size:13px;color:#53665a;margin:0 0 12px 4px}.screen{position:relative;box-shadow:0 10px 40px #15391d0d;border-radius:28px;overflow:hidden;line-height:0}.screen img{display:block;width:100%}.hot{position:absolute;padding:0;background:transparent;border:0;border-radius:12px;line-height:1}.hot:focus-visible{outline:3px solid #1c7d45;outline-offset:1px}.hot:hover{background:#229d600a}.note{max-width:390px;line-height:1.8;background:white;padding:22px;border-radius:18px}.note h2{font-size:16px}.note p{color:#657069}.note button{display:block;width:100%;margin-top:12px}footer{text-align:center;color:#748277;padding:8px 20px 24px;line-height:1.8}.toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#243b2c;color:white;padding:14px 22px;border-radius:12px;z-index:9}body.overview main{flex-wrap:wrap;justify-content:center;gap:20px;padding:24px}body.overview .screen{width:260px!important}body.overview .note{display:none}@media(max-width:850px){main{flex-wrap:wrap;padding:18px}header{padding:16px}.note{width:100%}}
</style><header><h1>循形 · 指纹登录设计</h1><button id="overview" aria-pressed="true">页面总览</button><button id="flow">点击演示</button><select id="state" aria-label="页面状态"></select><button id="theme">浅色</button><button id="width">390 px</button></header><main id="main"></main><footer>设计原型 · 示例账号 · 不发送验证码、不调用系统指纹、不修改真实账号<br>应用内原生界面按共享组件实现；系统验证窗以 Android BiometricPrompt 实际呈现为准。</footer><script>
const states=__STATES__,manifest=__MANIFEST__;let theme='light',width=390,current='settings-off',overview=true,origin='login',codeSent=false;
const select=document.querySelector('#state');states.forEach(([v,l])=>select.add(new Option(l,v)));
function toast(text){document.querySelector('.toast')?.remove();let e=document.createElement('div');e.className='toast';e.textContent=text;document.body.append(e);setTimeout(()=>e.remove(),2600)}
function go(state){if(state==='system-prompt')origin=current.startsWith('settings')||current.startsWith('verify')||current==='not-enrolled'?'settings':'login';current=state;overview=false;render()}
function panel(key){let m=manifest[key+'-'+theme+'-'+width],p=document.createElement('section');p.className='panel';p.innerHTML=`<p class="label">${m.label}</p><div class="screen" style="width:${width}px"><img alt="${m.label}" src="${m.path}"></div>`;const s=p.querySelector('.screen');m.hotspots.forEach(h=>{let b=document.createElement('button');b.className='hot';b.setAttribute('aria-label',h.label);b.title=h.label;Object.assign(b.style,{left:h.x/width*100+'%',top:h.y/844*100+'%',width:h.w/width*100+'%',height:h.h/844*100+'%'});b.onclick=()=>{if(h.label.includes('发送验证码')){codeSent=true;toast('演示：验证码已发送，实际接入后可输入');return}if(h.label.includes('系统设置')){toast('演示：实际使用时打开 Android 系统设置');return}if(h.label.includes('协议')||h.label.includes('密码模式')){toast('沿用已有协议正文与密码登录设计');return}if(h.target==='login-invalid'){toast('需二次确认后仅移除本机账号；本设计不操作真实凭据');return}if(h.label.includes('验证并登录')){toast('演示：邮箱验证成功');go('settings-on');return}go(current==='system-prompt'&&h.label==='取消'&&origin==='settings'?'settings-off':h.target)};s.append(b)});return p}
function render(){document.body.classList.toggle('overview',overview);document.querySelector('#overview').setAttribute('aria-pressed',overview);document.querySelector('#flow').setAttribute('aria-pressed',!overview);select.value=current;document.querySelector('#theme').textContent=theme==='light'?'浅色':'深色';document.querySelector('#width').textContent=width+' px';let main=document.querySelector('#main');main.replaceChildren(...(overview?states.slice(0,4).map(s=>panel(s[0])):[panel(current)]));if(!overview){let aside=document.createElement('aside');aside.className='note';aside.innerHTML='<h2>交互预览</h2><p>点击画面中的开关和按钮。首次邮箱登录后，在设置主动开启；普通退出后可使用指纹重新登录。</p><p>仅在需要补充身份验证时显示邮箱验证码页。所有账号与反馈均为设计示例。</p>';
let actions=current==='system-prompt'?[['模拟验证成功',origin==='settings'?'settings-on':'login'],['模拟验证取消',origin==='settings'?'settings-off':'login-cancel']]:[['查看按需邮箱验证','verify-email'],['查看已开启设置','settings-on'],['查看指纹登录首页','login'],['查看验证码错误','verify-error'],['查看登录网络错误','login-network'],['查看凭据失效','login-invalid']];actions.forEach(([l,t])=>{let b=document.createElement('button');b.textContent=l;b.onclick=()=>{if(current==='system-prompt'&&origin==='login'&&l.includes('成功'))toast('模拟登录成功，应进入现有首页');go(t)};aside.append(b)});main.append(aside)}}
select.onchange=()=>go(select.value);document.querySelector('#overview').onclick=()=>{overview=true;render()};document.querySelector('#flow').onclick=()=>{overview=false;render()};document.querySelector('#theme').onclick=()=>{theme=theme==='light'?'dark':'light';render()};document.querySelector('#width').onclick=()=>{width=width===390?320:390;render()};render();
</script></html>'''

if __name__=='__main__':main()
