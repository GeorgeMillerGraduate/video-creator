using System.Net;
using JengaVideoStudio.Core;

int passed=0;
void Check(bool value,string name) { if(!value) throw new Exception("FAIL: "+name); Console.WriteLine("PASS: "+name); passed++; }
void Throws(Action action,string name) { try { action(); } catch(InvalidDataException) { Check(true,name); return; } throw new Exception("FAIL: expected rejection: "+name); }
Check(PublicWeb.Canonical("https://example.com/a#fragment")=="https://example.com/a","URL fragment canonicalisation");
Throws(()=>PublicWeb.Canonical("file:///etc/passwd"),"Non-HTTPS research URL rejected");
Check(PublicWeb.IsPrivate(IPAddress.Parse("127.0.0.1"))&&PublicWeb.IsPrivate(IPAddress.Parse("::ffff:10.0.0.1")),"Private and IPv4-mapped destinations rejected");
Check(!PublicWeb.IsPrivate(IPAddress.Parse("8.8.8.8")),"Public address accepted");
var wrapped=Captions.Wrap("One exceptionallylongwordthatexceedsthelinelimit should never overflow a phone caption",28);
Check(wrapped.All(x=>x.Length<=28),"Caption lines bounded, including long words");
var scene=new Scene{Narration="A short sentence. Another sentence for a phone sized caption.",Heading="A clear headline",Duration=6,Points=["First point","Second point"],ClaimIds=["C1"]};
var cues=Captions.ForScene(scene);
Check(Math.Abs(cues.Last().End-6)<0.001&&cues.Zip(cues.Skip(1)).All(pair=>Math.Abs(pair.First.End-pair.Second.Start)<0.001),"Captions cover measured duration without gaps");
Check(Captions.Safe(@"{\pos(0,0)} injected") == "(/pos(0,0)) injected","ASS override injection neutralised");
Check(Captions.Ass(scene,"S1").Contains("Text\nDialogue:"),"ASS events separated from header");
var source=new Source{Id="S1",Text="This is a sufficiently long quotation to prove an exact source match."};
var brief=new Brief{Summary="Evidence",Claims=Enumerable.Range(1,3).Select(i=>new Claim{Id=$"C{i}",Text="Supported claim",Evidence=[new Evidence{SourceId="S1",Quote=source.Text}]}).ToList()};
Grounding.Validate(brief,[source]); Check(true,"Exact source evidence accepted");
brief.Claims[0].Evidence[0].Quote="This quotation was completely invented by a model.";
Throws(()=>Grounding.Validate(brief,[source]),"Fabricated quotation rejected");
var folder=Path.Combine(Path.GetTempPath(),"jenga-tests-"+Guid.NewGuid().ToString("N")); Directory.CreateDirectory(folder);
try
{
 var p=new Project{Folder=folder,Idea="Test",Scenes=[scene],State=ProductionState.Cancelled}; var store=new ProjectStore(); await store.Save(p);
 var restored=store.Load(Path.Combine(folder,"project.json")); Check(restored.State==ProductionState.Cancelled&&restored.Scenes[0].Narration==scene.Narration,"Cancelled project retains scene data");
 Throws(()=>p.PathFor("../escape.txt"),"Project traversal rejected");
 var h=ProductionPipeline.ContentHash(p); scene.Narration+=" Changed."; Check(h!=ProductionPipeline.ContentHash(p),"Narration edit invalidates upload/render fingerprint");
 var argsList=FfmpegRenderer.SceneArguments(scene,"voice.wav",null,"scene-01.ass","out.mp4");
 Check(argsList.Contains("libx264")&&argsList.Contains("aac")&&argsList.Contains("yuv420p")&&!argsList.Any(x=>x.Contains(scene.Narration)),"Renderer uses safe arguments and expected codecs");
 if(args.Contains("--render"))
 {
  var settings=new Settings{Ffmpeg=Environment.GetEnvironmentVariable("JENGA_FFMPEG")??"ffmpeg",Ffprobe=Environment.GetEnvironmentVariable("JENGA_FFPROBE")??"ffprobe"};
  p.Brief=new Brief{Summary="Synthetic test only",Claims=[new Claim{Id="C1",Text="Fixture",Evidence=[new Evidence{SourceId="S1",Quote="Fixture"}]}]};
  p.TargetSeconds=3; scene.Duration=3; scene.Audio="audio/test.wav"; scene.AudioHash="synthetic-test"; scene.Narration="Synthetic rendering test. This is not an AI production.";
  WriteTone(p.PathFor(scene.Audio),3);
  var renderer=new FfmpegRenderer(settings); await renderer.Render(p,new Progress<ProgressUpdate>(),default); await renderer.Validate(p,default);
  Check(File.Exists(p.PathFor(p.FinalVideo)),"Real FFmpeg portrait render and full decode validation");
  var dest=Path.GetFullPath("render-smoke.mp4");File.Copy(p.PathFor(p.FinalVideo),dest,true);Console.WriteLine("Synthetic fixture output: "+dest);
 }
}
finally {Directory.Delete(folder,true);}
Console.WriteLine($"{passed} checks passed.");
static void WriteTone(string path,double seconds)
{
 const int rate=24000; int samples=(int)(rate*seconds);
 using var writer=new BinaryWriter(File.Create(path));writer.Write("RIFF"u8.ToArray());writer.Write(36+samples*2);writer.Write("WAVEfmt "u8.ToArray());writer.Write(16);writer.Write((short)1);writer.Write((short)1);writer.Write(rate);writer.Write(rate*2);writer.Write((short)2);writer.Write((short)16);writer.Write("data"u8.ToArray());writer.Write(samples*2);
 for(int i=0;i<samples;i++)writer.Write((short)(Math.Sin(i*2*Math.PI*220/rate)*1500));
}
