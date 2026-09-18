using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
namespace JengaVideoStudio.Core;
public class GoogleToken
{
    public string Access { get; set; }="";
    public string Refresh { get; set; }="";
    public DateTimeOffset Expires { get; set; }
    public string ClientId { get; set; }="";
}
public class UploadSession { public string Url { get; set; }=""; public string Fingerprint { get; set; }=""; }
public sealed class YouTubePublisher(Settings settings,HttpClient http,ICredentialStore credentials,ProjectStore store)
{
    const string TokenKey="google";
    const string Api="https://www.googleapis.com/youtube/v3/";
    (string id,string secret) Client()
    {
        if(!File.Exists(settings.GoogleClientJson)) throw new InvalidOperationException("Choose your Google Desktop OAuth client JSON in Setup first. See the setup guide.");
        using var d=JsonDocument.Parse(File.ReadAllText(settings.GoogleClientJson));
        if(!d.RootElement.TryGetProperty("installed",out var c)) throw new InvalidDataException("Google credentials must be a Desktop app client, not a Web application client.");
        return(c.GetProperty("client_id").GetString()!,c.TryGetProperty("client_secret",out var sec)?sec.GetString()??"":"");
    }
    public bool Connected=>credentials.Read(TokenKey)!=null;
    static string Base64Url(byte[] b)=>Convert.ToBase64String(b).TrimEnd('=').Replace('+','-').Replace('/','_');
    public async Task Connect(CancellationToken ct)
    {
        var client=Client();
        var portProbe=new TcpListener(IPAddress.Loopback,0); portProbe.Start(); var port=((IPEndPoint)portProbe.LocalEndpoint).Port; portProbe.Stop();
        var redirect=$"http://127.0.0.1:{port}/";
        using var listener=new HttpListener(); listener.Prefixes.Add(redirect); listener.Start();
        var state=Base64Url(RandomNumberGenerator.GetBytes(32)); var verifier=Base64Url(RandomNumberGenerator.GetBytes(48));
        var query=new Dictionary<string,string> { ["client_id"]=client.id,["redirect_uri"]=redirect,["response_type"]="code",["scope"]="https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly",["state"]=state,["code_challenge"]=Base64Url(SHA256.HashData(Encoding.ASCII.GetBytes(verifier))),["code_challenge_method"]="S256",["access_type"]="offline",["prompt"]="consent" };
        Util.Open("https://accounts.google.com/o/oauth2/v2/auth?"+string.Join("&",query.Select(k=>Uri.EscapeDataString(k.Key)+"="+Uri.EscapeDataString(k.Value))));
        using var bounded=CancellationTokenSource.CreateLinkedTokenSource(ct); bounded.CancelAfter(TimeSpan.FromMinutes(5));
        HttpListenerContext context;
        while(true)
        {
            context=await listener.GetContextAsync().WaitAsync(bounded.Token);
            if(context.Request.QueryString["state"]==state) break;
            context.Response.StatusCode=400; context.Response.Close();
        }
        var code=context.Request.QueryString["code"];
        var text=Encoding.UTF8.GetBytes("You can return to Jenga Video Studio. It will confirm whether connection succeeded.");
        context.Response.ContentType="text/plain; charset=utf-8"; await context.Response.OutputStream.WriteAsync(text,bounded.Token); context.Response.Close();
        if(string.IsNullOrWhiteSpace(code)) throw new InvalidOperationException("Google authorization was declined or did not return a code.");
        using var response=await http.PostAsync("https://oauth2.googleapis.com/token",new FormUrlEncodedContent(new Dictionary<string,string>{["client_id"]=client.id,["client_secret"]=client.secret,["code"]=code,["code_verifier"]=verifier,["redirect_uri"]=redirect,["grant_type"]="authorization_code"}),bounded.Token);
        response.EnsureSuccessStatusCode();
        using var d=JsonDocument.Parse(await response.Content.ReadAsStringAsync(bounded.Token));
        credentials.Save(TokenKey,Json.Encode(new GoogleToken { Access=d.RootElement.GetProperty("access_token").GetString()!, Refresh=d.RootElement.TryGetProperty("refresh_token",out var r)?r.GetString()??"":"",Expires=DateTimeOffset.UtcNow.AddSeconds(d.RootElement.GetProperty("expires_in").GetInt32()),ClientId=client.id }));
    }
    async Task<string> Access(CancellationToken ct)
    {
        var token=Json.Decode<GoogleToken>(credentials.Read(TokenKey)??throw new InvalidOperationException("Connect YouTube first."));
        var client=Client();
        if(token.ClientId!=client.id) throw new InvalidOperationException("OAuth client changed. Reconnect YouTube.");
        if(token.Expires>DateTimeOffset.UtcNow.AddMinutes(2)) return token.Access;
        if(token.Refresh=="") throw new InvalidOperationException("Google session expired. Reconnect YouTube.");
        using var res=await http.PostAsync("https://oauth2.googleapis.com/token",new FormUrlEncodedContent(new Dictionary<string,string>{["client_id"]=client.id,["client_secret"]=client.secret,["refresh_token"]=token.Refresh,["grant_type"]="refresh_token"}),ct);
        if(!res.IsSuccessStatusCode) throw new InvalidOperationException("Google session could not be refreshed. Reconnect YouTube (test-app consent can expire).");
        using var d=JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct)); token.Access=d.RootElement.GetProperty("access_token").GetString()!; token.Expires=DateTimeOffset.UtcNow.AddSeconds(d.RootElement.GetProperty("expires_in").GetInt32()); credentials.Save(TokenKey,Json.Encode(token)); return token.Access;
    }
    public async Task<string> Channel(CancellationToken ct)
    {
        using var req=new HttpRequestMessage(HttpMethod.Get,Api+"channels?part=snippet&mine=true"); req.Headers.Authorization=new("Bearer",await Access(ct));
        using var res=await http.SendAsync(req,ct); res.EnsureSuccessStatusCode(); using var d=JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct));
        var items=d.RootElement.GetProperty("items"); if(items.GetArrayLength()==0) return "Connected account has no YouTube channel.";
        return $"Connected: {items[0].GetProperty("snippet").GetProperty("title").GetString()} ({items[0].GetProperty("id").GetString()})";
    }
    public async Task Disconnect(CancellationToken ct)
    {
        var raw=credentials.Read(TokenKey); if(raw==null) return;
        var t=Json.Decode<GoogleToken>(raw);
        using var res=await http.PostAsync("https://oauth2.googleapis.com/revoke",new FormUrlEncodedContent(new Dictionary<string,string>{["token"]=t.Refresh!=""?t.Refresh:t.Access}),ct);
        if(!res.IsSuccessStatusCode && res.StatusCode!=HttpStatusCode.BadRequest) throw new InvalidOperationException("Google could not revoke the connection. Retry or revoke it from your Google account settings.");
        credentials.Delete(TokenKey);
    }
    static void ValidateMetadata(VideoMetadata m)
    {
        if(string.IsNullOrWhiteSpace(m.Title)||m.Title.Length>100||m.Title.Contains('<')||m.Title.Contains('>')) throw new InvalidDataException("YouTube title must be 1–100 characters without angle brackets.");
        if(string.IsNullOrWhiteSpace(m.Description)) throw new InvalidDataException("Enter a video description before uploading.");
        if(!new[]{"private","unlisted","public"}.Contains(m.Privacy)) throw new InvalidDataException("Choose private, unlisted or public.");
        if(!int.TryParse(m.Category,out _)) throw new InvalidDataException("Category must be a numeric YouTube category ID.");
        if(m.Tags.Length>450) throw new InvalidDataException("Keep tags under 450 characters.");
    }
    public async Task Upload(Project p,IProgress<ProgressUpdate> progress,CancellationToken ct)
    {
        ValidateMetadata(p.Metadata);
        if(!p.EditorialReviewed) throw new InvalidOperationException("Watch the video and confirm editorial review first.");
        if(p.FinalHash!=ProductionPipeline.ContentHash(p)) throw new InvalidOperationException("Scenes changed since rendering. Render again before upload.");
        if(!string.IsNullOrEmpty(p.Metadata.VideoId)) throw new InvalidOperationException("This project already has a confirmed YouTube upload. Use Open YouTube video.");
        var file=p.PathFor(p.FinalVideo); var info=new FileInfo(file); var length=info.Length;
        var description=p.Metadata.Description+"\n\nSources:\n"+string.Join("\n",p.Sources.Select(s=>$"[{s.Id}] {s.Title}: {s.Url}"))+"\n\nMade with Jenga Video Studio. AI-assisted script and narration; generated visuals are illustrations.";
        if(Encoding.UTF8.GetByteCount(description)>5000) throw new InvalidDataException("Description plus source list exceeds YouTube's 5,000-byte limit. Shorten your description.");
        var key="upload-"+p.Id;
        var fingerprint=Util.Hash(p.FinalHash+length+info.LastWriteTimeUtc.Ticks+Json.Encode(p.Metadata));
        var existing=credentials.Read(key); var session=existing==null?null:Json.Decode<UploadSession>(existing);
        if(session!=null && session.Fingerprint!=fingerprint) { credentials.Delete(key); session=null; }
        var access=await Access(ct);
        if(session==null)
        {
            using var start=new HttpRequestMessage(HttpMethod.Post,"https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status");
            start.Headers.Authorization=new("Bearer",access); start.Headers.Add("X-Upload-Content-Length",length.ToString()); start.Headers.Add("X-Upload-Content-Type","video/mp4");
            start.Content=JsonContent.Create(new { snippet=new { title=p.Metadata.Title,description,categoryId=p.Metadata.Category,tags=p.Metadata.Tags.Split(',',StringSplitOptions.TrimEntries|StringSplitOptions.RemoveEmptyEntries) },status=new { privacyStatus=p.Metadata.Privacy,selfDeclaredMadeForKids=p.Metadata.MadeForKids,containsSyntheticMedia=p.Metadata.ContainsSyntheticMedia } });
            using var res=await http.SendAsync(start,ct); await Check(res,ct);
            var location=res.Headers.Location??throw new InvalidDataException("Google did not return a resumable upload session.");
            if(location.Scheme!="https"||!(location.Host=="googleapis.com"||location.Host.EndsWith(".googleapis.com",StringComparison.OrdinalIgnoreCase))) throw new InvalidDataException("Untrusted upload session address.");
            session=new UploadSession{Url=location.AbsoluteUri,Fingerprint=fingerprint}; credentials.Save(key,Json.Encode(session));
        }
        async Task<HttpResponseMessage> Status()
        {
            var req=new HttpRequestMessage(HttpMethod.Put,session.Url); req.Headers.Authorization=new("Bearer",await Access(ct));
            req.Content=new ByteArrayContent([]); req.Content.Headers.ContentRange=new ContentRangeHeaderValue(length);
            try { return await http.SendAsync(req,ct); } finally { req.Dispose(); }
        }
        async Task Complete(HttpResponseMessage response)
        {
            using var d=JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct));
            var id=d.RootElement.GetProperty("id").GetString(); if(string.IsNullOrEmpty(id)) throw new InvalidDataException("Google response omitted the video ID.");
            p.Metadata.VideoId=id; p.Metadata.ActualPrivacy=d.RootElement.TryGetProperty("status",out var st)&&st.TryGetProperty("privacyStatus",out var pv)?pv.GetString()??"unknown":"unknown";
            p.State=ProductionState.Uploaded; await store.Save(p); credentials.Delete(key);
            progress.Report(new("Upload complete","YouTube accepted the upload. Processing may still be underway. Privacy: "+p.Metadata.ActualPrivacy,100));
        }
        static long Offset(HttpResponseMessage response)
        {
            if(!response.Headers.TryGetValues("Range",out var range)) return 0;
            var last=range.First().Split('-').Last(); return long.Parse(last)+1;
        }
        using var status=await Status();
        if(status.IsSuccessStatusCode) { await Complete(status); return; }
        if(status.StatusCode==HttpStatusCode.NotFound||status.StatusCode==HttpStatusCode.Gone) { credentials.Delete(key); throw new IOException("Upload session expired. Press Upload again to start a new session."); }
        if((int)status.StatusCode!=308) await Check(status,ct);
        long at=Offset(status); p.State=ProductionState.Uploading; await store.Save(p,ct);
        await using var stream=File.OpenRead(file); var buffer=new byte[8*1024*1024]; int failures=0;
        while(at<length)
        {
            ct.ThrowIfCancellationRequested(); stream.Position=at;
            int n=await stream.ReadAsync(buffer.AsMemory(0,(int)Math.Min(buffer.Length,length-at)),ct);
            if(n==0) throw new IOException("Video ended unexpectedly during upload.");
            try
            {
                using var req=new HttpRequestMessage(HttpMethod.Put,session.Url); req.Headers.Authorization=new("Bearer",await Access(ct));
                req.Content=new ByteArrayContent(buffer,0,n); req.Content.Headers.ContentType=new("video/mp4"); req.Content.Headers.ContentRange=new(at,at+n-1,length);
                using var res=await http.SendAsync(req,ct);
                if(res.IsSuccessStatusCode) { await Complete(res); return; }
                if((int)res.StatusCode!=308) await Check(res,ct);
                var next=Offset(res); if(next<=at) throw new HttpRequestException("Google acknowledged no further bytes."); at=next; failures=0;
            }
            catch(HttpRequestException) when(++failures<=4)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Pow(2,failures)),ct);
                using var check=await Status();
                if(check.IsSuccessStatusCode) { await Complete(check); return; }
                if((int)check.StatusCode!=308) await Check(check,ct); at=Offset(check);
            }
            progress.Report(new("Uploading",$"{at/1e6:0.0} / {length/1e6:0.0} MB acknowledged by YouTube",100.0*at/length));
        }
        throw new IOException("All bytes sent but YouTube did not confirm completion. Retry to query the existing session.");
    }
    static async Task Check(HttpResponseMessage response,CancellationToken ct)
    {
        if(response.IsSuccessStatusCode) return;
        string reason="";
        try { using var d=JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct)); reason=d.RootElement.GetProperty("error").GetProperty("message").GetString()??""; } catch(JsonException) { } catch(KeyNotFoundException) { }
        throw new HttpRequestException($"YouTube request failed ({(int)response.StatusCode}). {reason}",null,response.StatusCode);
    }
}
