"""Portable FFmpeg recipe test. This does NOT compile or execute the C# application."""
from pathlib import Path
import subprocess,json,re,tempfile,wave,math,struct,sys
root=Path(__file__).resolve().parents[1]
out=root/'verification';out.mkdir(exist_ok=True)
source=(root/'src/JengaVideoStudio.Core/Captions.cs').read_text()
header=re.search(r'new StringBuilder\("""\n(.*?)\n"""\)',source,re.S).group(1)+'\n'
with tempfile.TemporaryDirectory(prefix='jenga-render-') as td:
 d=Path(td)
 events=[
 'Dialogue: 0,0:00:00.00,0:00:03.00,Label,,0,0,0,,JENGA  /  EXPLAINED',
 'Dialogue: 0,0:00:00.00,0:00:03.00,Heading,,0,0,0,,{\\fad(180,120)}Why floppy disks\\Nstayed useful',
 'Dialogue: 0,0:00:00.00,0:00:03.00,Point,,0,0,0,,{\\pos(110,740)\\fad(250,120)}01  /  Compatibility',
 'Dialogue: 0,0:00:00.35,0:00:03.00,Point,,0,0,0,,{\\pos(110,925)\\fad(250,120)}02  /  Existing equipment',
 'Dialogue: 0,0:00:00.70,0:00:03.00,Point,,0,0,0,,{\\pos(110,1110)\\fad(250,120)}03  /  Replacement costs',
 'Dialogue: 0,0:00:00.00,0:00:03.00,Label,,0,0,0,,{\\pos(95,1740)\\fs28}Synthetic renderer fixture — not factual research',
 'Dialogue: 0,0:00:00.00,0:00:01.50,Caption,,0,0,0,,This is a rendering test,\\Nnot a generated documentary.',
 'Dialogue: 0,0:00:01.50,0:00:03.00,Caption,,0,0,0,,Checking captions, sound,\\Nand portrait dimensions.'
 ]
 (d/'scene-01.ass').write_text(header+'\n'.join(events)+'\n')
 rate=24000
 with wave.open(str(d/'voice.wav'),'wb') as f:
  f.setparams((1,2,rate,0,'NONE','not compressed'))
  f.writeframes(b''.join(struct.pack('<h',int(1500*math.sin(i*2*math.pi*220/rate))) for i in range(rate*3)))
 vf='scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,drawbox=x=85:y=610:w=810:h=5:color=0x64b5e2:t=fill,drawbox=x=85:y=680:w=6:h=560:color=0x64b5e2@0.5:t=fill,ass=scene-01.ass'
 args=['ffmpeg','-y','-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=0x101c2c:s=1080x1920:r=30','-i','voice.wav','-vf',vf,'-map','0:v:0','-map','1:a:0','-af','loudnorm=I=-16:TP=-1.5:LRA=11','-t','3.000','-r','30','-c:v','libx264','-preset','veryfast','-crf','21','-pix_fmt','yuv420p','-c:a','aac','-ar','48000','-ac','2','-b:a','192k','-movflags','+faststart','scene-01.mp4']
 subprocess.run(args,cwd=d,check=True,capture_output=True)
 # Exercise the image path with a non-1080 input and the exact zoompan expression.
 from PIL import Image
 Image.new('RGB',(512,896),'#213449').save(d/'image.png')
 imageargs=args.copy(); start=imageargs.index('-f'); end=imageargs.index('-i',start)+2
 imageargs[start:end]=['-loop','1','-framerate','30','-i','image.png']
 imageargs[imageargs.index('-vf')+1]="scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,zoompan=z='min(zoom+0.00008,1.06)':x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d=1:s=1080x1920:fps=30,drawbox=x=0:y=0:w=iw:h=600:color=0x101c2c@0.75:t=fill,ass=scene-01.ass"
 imageargs[-1]='scene-02.mp4';subprocess.run(imageargs,cwd=d,check=True,capture_output=True)
 (d/'list.txt').write_text("file 'scene-01.mp4'\nfile 'scene-02.mp4'\n")
 subprocess.run(['ffmpeg','-y','-v','error','-f','concat','-safe','1','-i','list.txt','-c','copy','-movflags','+faststart','final.mp4'],cwd=d,check=True,capture_output=True)
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json','final.mp4'],cwd=d))
 video=next(s for s in probe['streams'] if s['codec_type']=='video'); audio=next(s for s in probe['streams'] if s['codec_type']=='audio')
 assert (video['width'],video['height'],video['codec_name'])==(1080,1920,'h264')
 assert audio['codec_name']=='aac' and abs(float(probe['format']['duration'])-6)<0.2
 subprocess.run(['ffmpeg','-v','error','-xerror','-i','final.mp4','-f','null','-'],cwd=d,check=True,capture_output=True)
 subprocess.run(['ffmpeg','-y','-v','error','-ss','1','-i','final.mp4','-frames:v','1',str(out/'render-fixture.png')],cwd=d,check=True,capture_output=True)
 (out/'media-recipe-probe.json').write_text(json.dumps(probe,indent=2))
 (out/'media-recipe-result.txt').write_text('PASS: real FFmpeg graphic scene, still-image zoompan scene, concatenation, H.264/AAC 1080x1920, duration check and full decode.\nNOT TESTED: C# execution, Windows UI, actual neural narration, research, LLM, ComfyUI or YouTube.\n'+subprocess.check_output(['ffmpeg','-version'],text=True).splitlines()[0]+'\n')
 print((out/'media-recipe-result.txt').read_text())
