# Jenga Video Studio: setup and downloads

Personal Test 0.2 • Prepared for George Miller • 17 September 2026

## What this download actually contains

The ZIP contains the Visual Studio solution and the orchestration code. It connects specialist programs together; it does not contain gigabytes of trained AI weights. It is an intermediate draft for testing, not an installer or a finished commercial product. No usage limits or licensing features are active.

**Testing status:** the C# solution has not been compiled or launched in this Linux workspace. Its media-rendering recipe passed a real FFmpeg test. Windows, actual local AI, Google sign-in and uploads still need the checks in the included acceptance checklist.

## Downloads at a glance

| Component | Needed for | How you obtain it | Approximate space / credentials |
| --- | --- | --- | --- |
| Visual Studio 2026 + .NET desktop workload + .NET 10 SDK | Build/run source | Install using Microsoft's installer | Several GB; no AI key |
| Ollama for Windows | Local script-writing AI | Install and launch official Windows download once | Runtime size varies; allow several GB |
| Qwen2.5 7B | Recommended local writing model | App: Download text model | About 4.7 GB download; recommend 16 GB+ RAM |
| Qwen2.5 1.5B | Smaller, less reliable alternative | Select in Setup, then download | About 986 MB; weaker research and JSON quality |
| FFmpeg Essentials + ffprobe | Encode/check the MP4 | App: Download and configure FFmpeg | About 110 MB download; reserve 1 GB |
| Installed Windows voice | Easiest narration test | Select Windows, then Test narration | Usually already installed; not neural AI |
| Python 3.11/3.12 x64 + Piper 1.8.0 | Optional neural narration | Install Python once, then app: Install neural voice | Reserve 1 GB including packages and ~65 MB voice |
| ComfyUI + chosen image/video weights | Optional generated visuals | Guided manual installation; then app invokes workflows | Several GB to tens of GB; GPU needs depend on model |
| Google Desktop OAuth client JSON | Optional YouTube upload | Create in your Google Cloud project | Client configuration and browser consent; no Google password in app |

Model download sizes are from the official [7B model page](https://ollama.com/library/qwen2.5:7b) and [1.5B model page](https://ollama.com/library/qwen2.5:1.5b). RAM advice is a practical starting estimate, not a benchmark or guarantee. Long context and graphics consume extra memory. For model quality, try 7B first if your PC permits it.

## Interface update in 0.2

The project uses the same backend as 0.1. Home, Create, Projects and Help are global sections; Production, Review and Export belong to the current video. Component setup has moved behind a friendly status screen, with every technical control retained under Advanced Setup. Read `docs/UI-REDESIGN.md` for the changes and `docs/UI-WINDOWS-CHECKLIST.md` for required desktop checks.

## 1. Open the source in Visual Studio

Install/update Visual Studio with the **.NET desktop development** workload and a .NET 10 SDK. Targeting .NET 10 requires Visual Studio 18.0+; use the matching SDK that your updated installation provides. [Microsoft compatibility guidance](https://learn.microsoft.com/en-us/dotnet/core/porting/versioning-sdk-msbuild-vs).

Extract the complete ZIP. Open `JengaVideoStudio.sln`. Set `JengaVideoStudio.App` as Startup Project. Restore dependencies, build, and press F5. The source has no third-party NuGet package references; the .NET/WPF SDK and targeting packs are still required.

If Windows marks downloaded files as blocked, use the ZIP's Properties → Unblock before extracting. If a build error occurs, keep its exact text; no successful build is claimed for this draft.

## 2. Prepare local text AI

Open **Settings → Manage Components**. The friendly cards show the component status; expand **Advanced Setup** for model and runtime controls. Follow **Open official Ollama download**, install it, and launch Ollama. The installer is interactive; this draft does not silently install a machine-wide AI runtime.

Choose `qwen2.5:7b`, then **Download text model**. Confirm the displayed approximate download. The app calls Ollama's pull API and displays completed bytes for the layers it knows about. Ollama owns model caching, integrity checks and resumability. Retry after an interrupted pull; completed layers should be reused. [Ollama API](https://docs.ollama.com/api/pull).

Use **Check components** and **Test local AI**. Keep Ollama running while making videos. Default address: `http://127.0.0.1:11434`. CPU-only inference is available in Setup. GPU selection otherwise belongs to Ollama; the app does not guarantee acceleration from the GPU name alone.

A smaller model may repeatedly produce invalid evidence or malformed scene plans. Use 7B, shorten the topic, or supply better sources. The app retries structural failures a limited number of times and reports a real error rather than inventing a successful result.

## 3. Prepare the video encoder

Choose **Download and configure FFmpeg**. This downloads the Windows Essentials ZIP from gyan.dev, checks the supplied SHA-256, extracts the tools under the app data folder, locates ffmpeg/ffprobe and records the version. [Upstream Windows build page](https://www.gyan.dev/ffmpeg/builds/).

If the download is blocked, download Essentials yourself, extract it, and select `ffmpeg.exe` and `ffprobe.exe` under Advanced paths. Both must come from a build with H.264, AAC and libass subtitle support. A checksum failure stops installation; retry in case the upstream release changed during the download.

## 4. Choose narration

For the quickest test select **Windows**, leave the voice name empty, then click **Test narration**. A short WAV should open in your player. This voice is the operating system's speech engine, not the desired final neural narration quality.

For local neural narration install Python 3.11 or 3.12 x64 from [python.org](https://www.python.org/downloads/windows/). In the installer, enable Python on PATH, or select its `python.exe` in Advanced paths. Then use **Install neural voice** in the app. The app creates an isolated environment, installs `piper-tts==1.8.0`, downloads `en_US-lessac-medium`, and saves its paths. No Python commands are required in normal operation. [Piper CLI documentation](https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/CLI.md).

Select **Piper** and test it before making a video. Piper's engine and its voice models have different licences. The Lessac model card links to its source dataset's terms; those terms have not been cleared for your future commercial product. [Voice model card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/lessac/medium/MODEL_CARD). Downloads are not a substitute for a licence review.

Piper installation shows stages, not fabricated byte percentages for pip. Record its installed package list from the app data folder if reproducing an issue.

## 5. Make the first video

Save settings. On Create enter **Why did floppy disks survive for so long?**, choose 45–60 seconds, **Light**, and a clear documentary tone. Press **MAKE VIDEO**.

The app discovers live encyclopedia articles, tries external references and reads any public HTTPS article URLs you supplied. It stores the retrieved text, creates a claim ledger with checked quotations, asks local AI for the script and scenes, produces narration, renders graphics, adds captions, assembles the MP4, and checks streams plus full decode.

This research path is limited: it is not a general web-search engine or a reliable current-news pipeline. For factual diversity, supply 2–4 authoritative article URLs using the optional sources box. Paywalls, blocked robots access, binary files and unreadable pages are skipped. Publication dates are not reliably extracted in this draft.

In Review, select a scene card, watch its saved clip, inspect the sources and claim ledger, and correct the narration in the right-hand inspector. Caption timing is estimated proportionally within each actual audio segment; it is not precise word alignment. The target length is approximate. Significant duration differences generate a warning.

Edit an individual scene's narration, heading, visual prompt or graphic labels. **Apply changes** stores those changes; **Render final video** applies them. The footer distinguishes unsaved changes from saved edits that still need rendering. Narration is regenerated only when its text/voice changes. **Regenerate visual** changes that scene's revision/seed and rerenders; it does not research the topic again.

## 6. Optional generated images and video

Get a functioning local ComfyUI installation from its [official installation guide](https://docs.comfy.org/installation/desktop/windows). Install the model files needed by your chosen workflow. This intermediate build does not automatically install ComfyUI, custom nodes, CUDA or large image/video models.

For a basic image path, use the included `workflows/sd15-portrait-api.json` with a compatible SD1.5 checkpoint. Its `ckpt_name` must exactly match the file in ComfyUI's checkpoints folder. The example filename is `v1-5-pruned-emaonly.safetensors`; use the model source linked by the [ComfyUI text-to-image tutorial](https://docs.comfy.org/tutorials/basic/text-to-image), and review its terms. You only configure the workflow/model; no C# coding is needed.

Select the workflow JSON in Setup, enable generated images, and choose **Balanced** on Create. The scene director selects which scenes need images. To force one after production, select its visual type as GeneratedImage and regenerate it.

For video, first get a text-to-video workflow working in ComfyUI. Export **API format**, put `{{PROMPT}}` in its positive prompt, `{{NEGATIVE}}` in its negative prompt if present, and `{{PREFIX}}` in its saved filename prefix. Select it as the video workflow, enable video and choose Balanced. The adapter accepts saved MP4/WebM outputs from `videos`, `gifs` or `images` history lists. Workflow plugins can differ: outputs outside those formats need adapter changes and are not claimed supported.

No image-to-video conditioning upload is implemented in this draft. Use text-to-video workflows only. Maps, complex diagrams and semantic candidate selection are also outside this draft. ComfyUI errors/timeouts fall back to graphics and are reported. Cancelling removes this job from the pending queue where possible but does not interrupt an active shared-server job, which may continue consuming GPU memory.

## 7. Connect YouTube correctly

You can make and watch local videos without doing this step.

In [Google Cloud Console](https://console.cloud.google.com/), create/select a project, enable **YouTube Data API v3**, configure the OAuth consent screen and add your Google account as a test user when the app is in Testing. Create credentials for an **OAuth client ID → Desktop app**, then download that client's JSON file. Keep it outside the source repository.

Choose that JSON in Setup. Add your optional channel URL. Click **Connect / Re-authorize**, sign in through Google's own browser page and grant access. The app uses a loopback redirect with PKCE and checks the returned state. It stores tokens using Windows DPAPI for your Windows user. [Google desktop OAuth flow](https://developers.google.com/identity/protocols/oauth2/native-app).

The channel URL is just an opening shortcut. The actual destination is the channel belonging to the account authorized through Google. Setup displays the returned channel name and ID. Upload refreshes the account display before confirmation.

OAuth test-mode grants can expire, and Google can restrict uploads from unaudited API projects to private visibility. Permissions, quotas and verification are controlled by Google. Never interpret a completed byte upload as a guarantee of public publication. [YouTube upload guidance](https://developers.google.com/youtube/v3/guides/uploading_a_video).

## 8. Upload only after reviewing

In **Export**, edit the title, description, tags, category, privacy, audience and synthetic-content disclosure. The app appends source URLs and an AI-production note. Default privacy is **private**. Confirm that you have reviewed the video and its evidence, then click **UPLOAD TO YOUTUBE** and confirm the destination/title/privacy.

The uploader sends real resumable chunks, reports bytes acknowledged by Google and stores the returned video ID. Its protected session record allows retrying a failed upload without recreating the video. [YouTube resumable protocol](https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol).

After success, **Open YouTube video** opens the returned ID. Processing may continue after upload. This draft prevents duplicate uploads once a project has a confirmed video ID. It does not update an already-uploaded video, upload custom thumbnails or monitor processing to completion.

## 9. Where everything goes

| Data | Default location |
| --- | --- |
| Source code | Wherever you extract the ZIP |
| Projects, MP4s, source packs, audio and logs | Documents\Jenga Video Studio |
| Settings, FFmpeg, Piper environment, voice test | %LOCALAPPDATA%\JengaVideoStudio |
| Protected Google tokens/upload sessions | %LOCALAPPDATA%\JengaVideoStudio\credentials |
| Ollama models | %USERPROFILE%\.ollama\models, unless Ollama is configured otherwise |
| ComfyUI models | Your own ComfyUI model directories |

Models are not copied into the Visual Studio solution. Project JSON uses relative asset paths, so a whole project folder can be moved. Credentials are protected for the current Windows user; copying their files to a different PC is not an account-transfer mechanism. Use Connect again there.

## 10. Troubleshooting

**No SDK / project will not load:** update Visual Studio and install its desktop workload/.NET 10 support. Do not open the solution inside the ZIP.

**Local AI unavailable:** start Ollama and check the endpoint/model. Try Test local AI. CPU generation can take minutes; a moving indicator is not a promise of a near finish.

**No readable research sources:** try an evergreen topic or add readable public article URLs. This draft has no paid general-search fallback.

**Evidence rejected:** the model returned a quotation that could not be found in the saved source. Try a stronger model or a fresh project with more relevant sources. Do not remove the check just to force a video through.

**Python opens the Microsoft Store:** install real Python or browse directly to its executable. Then retry Install neural voice.

**Piper package installation fails:** confirm Python x64 3.11/3.12 and internet access to PyPI/Hugging Face. Windows voice remains usable. Do not disable your system's security controls to make installation work.

**PowerShell speech script blocked:** a policy may block the shipped local script. Use the neural Piper provider, or have the script reviewed under the PC's normal policy. The app does not change PowerShell execution policy.

**Black/failed embedded preview:** open the MP4 in your default video player. Windows media codecs vary. If rerender says a file is in use, close all players.

**GPU runs out of memory:** choose Light, disable generated video, lower your ComfyUI workflow resolution, or force text AI to CPU. Ollama generation requests unload the model after each response to reduce overlapping GPU usage.

**Google access denied:** check Desktop client type, enabled YouTube API, consent test-user list and the account's channel. Re-authorize if consent expired.

**Upload fails:** preserve the project, check your connection/quota/account settings, and retry Upload. The final MP4 is kept. An expired upload session is cleared and can be restarted.

**Need diagnostics:** use Open project folder → logs/production.log. Setup records model digests and FFmpeg version. Do not send your OAuth JSON, tokens or the credentials folder with a bug report.

## 11. What still needs a Windows test

Run `scripts/Verify-Windows.ps1` from PowerShell in the extracted project. It builds Debug and Release, runs the C# tests, and runs a synthetic render test if settings/FFmpeg have been configured. It writes a transcript under `verification`.

Then follow `docs/ACCEPTANCE-CHECKLIST.md` for the actual floppy-disk production, cancellation/recovery, scene edit, neural voice and optional private upload. The transcript and actual first successful MP4 are the evidence required before describing this as a usable release.
