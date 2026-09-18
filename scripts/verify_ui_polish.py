"""Source-level UI regression checks. Does not replace a Windows build or UI test."""
from pathlib import Path
import re,xml.etree.ElementTree as E,json,hashlib
r=Path(__file__).resolve().parents[1];app=r/'src/JengaVideoStudio.App'
n='{http://schemas.microsoft.com/winfx/2006/xaml/presentation}';x='{http://schemas.microsoft.com/winfx/2006/xaml}'
errors=[];count=0
def check(ok,msg):
 global count
 count+=1
 if not ok:errors.append(msg)
docs={p:E.parse(p) for p in app.rglob('*.xaml')}
for p,t in docs.items():
 root=t.getroot()
 if root.tag in (n+'Window',n+'UserControl'):
  check(root.get('Background')=='{StaticResource AppBackgroundBrush}',f'{p.name}: explicit dark root')
  check(root.get('Foreground')=='{StaticResource TextPrimaryBrush}',f'{p.name}: explicit readable root')
 for e in t.iter(n+'Run'):
  b=e.get('Text','')
  check(not b.startswith('{Binding') or 'Mode=OneWay' in b or 'Mode=OneTime' in b,f'{p.name}: Run binding must not write to display data')
 for e in t.iter(n+'TextBox'):
  if e.get('IsReadOnly')=='True':
   check('Mode=OneWay' in e.get('Text',''),f'{p.name}: read-only TextBox binding')
 if p.name!='Palette.xaml':
  check(not re.search(r'(?:Foreground|Background|BorderBrush|Stroke|Fill)="#[0-9A-Fa-f]+"',p.read_text()),f'{p.name}: no inline brush colours')
 for e in t.iter(n+'ResourceDictionary'):
  if e.get('Source'):check((p.parent/e.get('Source')).exists(),f'{p.name}: merged dictionary exists')
# Measured palette contrast, including text drawn over the hero gradient.
pal=docs[app/'Themes/Palette.xaml'];col={e.get(x+'Key'):e.get('Color') for e in pal.iter(n+'SolidColorBrush') if e.get('Color','').startswith('#')}
def lum(h):
 v=[int(h[i:i+2],16)/255 for i in (1,3,5)]
 v=[z/12.92 if z<=.04045 else ((z+.055)/1.055)**2.4 for z in v]
 return sum(a*b for a,b in zip(v,[.2126,.7152,.0722]))
def ratio(a,b):
 a,b=sorted([lum(a),lum(b)]);return (b+.05)/(a+.05)
ratios={}
for foreground in ['TextPrimaryBrush','TextSecondaryBrush','TextMutedBrush','AccentHoverBrush','SuccessBrush','WarningBrush']:
 for background in ['AppBackgroundBrush','SurfaceBrush','SurfaceRaisedBrush','BorderBrush']:
  q=ratio(col[foreground],col[background]);ratios[foreground+'/'+background]=round(q,2)
  check(q>=4.5,f'{foreground} / {background} contrast {q:.2f}')
check(ratio(col['OnAccentBrush'],col['AccentBrush'])>=4.5,'Primary button contrast')
check(ratio(col['TextSecondaryBrush'],'#174C60')>=4.5,'Hero secondary text contrast')
# All C# source and original test files must remain byte-identical for this visual pass.
contract=json.loads((r/'verification/polish-preserved-code.json').read_text())
for path,digest in contract.items():check(hashlib.sha256((r/path).read_bytes()).hexdigest()==digest,f'Code preserved: {path}')
summary=f'{count} UI polish source checks; {len(errors)} failures.\n'
summary+='XML/resource/contrast/source checks only. No Windows compilation, live bindings, playback or layout testing.\n'
summary+='\n'.join(errors)
(r/'verification/ui-polish-checks.txt').write_text(summary+'\n'+json.dumps(ratios,indent=2)+'\n')
print(summary)
if errors:raise SystemExit(1)
