"""Static regression checks for the WPF redesign; NOT a compiler or UI runtime test."""
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as E
r=Path(__file__).resolve().parents[1]; app=r/'src/JengaVideoStudio.App'
ns={'w':'http://schemas.microsoft.com/winfx/2006/xaml/presentation','x':'http://schemas.microsoft.com/winfx/2006/xaml'}
fail=[]; checks=[]
def check(condition,message):
 (checks if condition else fail).append(message)
files=list(app.rglob('*.xaml')); docs={p:E.parse(p) for p in files}
keys={el.attrib['{'+ns['x']+'}Key'] for doc in docs.values() for el in doc.iter() if '{'+ns['x']+'}Key' in el.attrib}
for p,doc in docs.items():
 for key in re.findall(r'\{StaticResource ([A-Za-z][A-Za-z0-9]*)\}',p.read_text()):check(key in keys,f'Resource {key} resolves in {p.name}')
 for el in doc.iter():
  name=el.tag.split('}')[-1]
  for prop in el.attrib:
   if '.' not in prop and not prop.startswith('{'):check(el.find('{'+ns['w']+'}'+name+'.'+prop) is None,f'No duplicate {name}.{prop} in {p.name}')
 cls=doc.getroot().attrib.get('{'+ns['x']+'}Class')
 if cls:
  code=p.with_suffix(p.suffix+'.cs')
  check(code.exists(),f'{p.name} code-behind exists')
  if code.exists():
   body=code.read_text()
   check('partial class '+cls.split('.')[-1] in body,f'{p.name} partial class agrees')
   for event in ['Click','MediaOpened','MediaFailed','MediaEnded','ValueChanged']:
    for el in doc.iter():
     if event in el.attrib:check(bool(re.search(r'\b'+re.escape(el.attrib[event])+r'\s*\(',body)),f'{p.name} handler {el.attrib[event]} exists')
vm='\n'.join(p.read_text() for p in app.glob('StudioViewModel*.cs'))
commands=set(re.findall(r'public ICommand (\w+)\s*\{',vm))
allx='\n'.join(p.read_text() for p in files)
for command in re.findall(r'Command="\{Binding (\w+)\}"',allx):check(command in commands,f'Command {command} resolves')
for command in re.findall(r'Command="\{Binding DataContext\.(\w+),',allx):check(command in commands,f'Template command {command} resolves')
original=json.loads((r/'verification/ui-original-contract.json').read_text())
for command in original['commands']:
 check(command in commands,f'Original command {command} preserved')
 check(command in re.findall(r'Command="\{Binding (\w+)\}"',allx) or command+'.Execute(' in vm,f'Original command {command} remains reachable')
for setting in original['settings_bindings']:check(setting in allx,f'Advanced setting {setting} retained')
for name,h in original['preserved_hashes'].items():check(hashlib.sha256((r/name).read_bytes()).hexdigest()==h,f'Unchanged backend: {name}')
page_enum=re.search(r'enum StudioPage\s*\{([^}]+)',(app/'PresentationModels.cs').read_text()).group(1)
pages=[p.strip() for p in page_enum.split(',')]
root=docs[app/'MainWindow.xaml'].getroot()
views=[]
for item in root.findall('.//w:TabItem',ns):views.append(next(iter(item)).tag.split('}')[-1].replace('View',''))
check(pages==views,'Page order agrees with the hidden workspace host')
for route in re.findall(r'Command="\{Binding Navigate\}" CommandParameter="(\w+)"',allx):check(route in pages,f'Navigation route {route} exists')
for edit in ['Narration','Heading','VisualPrompt','VisualType']:check('SelectedScene.'+edit+'=value' in vm,f'{edit} edits the original scene model')
check('File.GetLastWriteTime' in vm,'Save indicator uses filesystem evidence')
check('PreviewScene' in commands and 'PlaybackRequested' in vm,'Scene preview command connects to playback event')
summary=f'{len(checks)} static checks passed; {len(fail)} failed.\n'
summary+='No C# compilation, WPF rendering, live binding evaluation or Windows integration tests were performed.\n'
summary+='\n'.join('FAIL: '+s for s in fail)
(r/'verification/ui-static-checks.txt').write_text(summary+'\n')
print(summary)
if fail:raise SystemExit(1)
