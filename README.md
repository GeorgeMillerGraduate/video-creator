# Jenga Video Studio — Personal Test 0.2

## Latest update: dark interface polish

The existing 0.2 interface now has explicit dark shell colours, shared control
styling, a wrapping Home dashboard, compact project/help/component cards, and
one-way display bindings fixing the reported Duration/Status startup errors.
All C# code and backend behavior are unchanged. See [UI polish notes](docs/UI-POLISH.md)
and [Windows checklist](docs/UI-POLISH-WINDOWS-CHECKLIST.md).
Windows compilation and runtime verification remain outstanding: dotnet is not
installed in the build environment.


A C#/.NET 10 WPF source draft for George's personal testing, now with a redesigned desktop interface. No payment system, subscription, activation key, trial counter or usage limit is included.

**Status: intermediate source draft, not a verified finished release.** The media recipe has been exercised with real FFmpeg. This environment had no .NET SDK or Windows runtime, and network restrictions prevented acquiring the SDK. The solution has therefore **not been compiled**, and its full AI-to-video path has **not been run** here. See `docs/IMPLEMENTATION-STATUS.md` for the remaining differences from the master specification.

## What changed in 0.2

The existing application is preserved, with a permanent sidebar, Home/project cards, clearer production display, visual scene selection, a scene inspector, save feedback, a separate Export workspace and simpler component management. The original core services, project models, prompts, workflows and C# tests are unchanged. See `docs/UI-REDESIGN.md` for the file list and verification report.

The supplied notes report a successful launch of 0.1 on the user's PC. The redesigned 0.2 has not been compiled or launched here because the environment still lacks .NET and Windows. Run the Windows verification script and `docs/UI-WINDOWS-CHECKLIST.md`.

## Start here

1. Read **SETUP-AND-DOWNLOADS.html** (opens in your browser).
2. Use Windows x64 with Visual Studio 2026 (18.0+) and the **.NET desktop development** workload plus a .NET 10 SDK. The recommended choice is the SDK bundled with your updated Visual Studio. [Microsoft compatibility table](https://learn.microsoft.com/en-us/dotnet/core/porting/versioning-sdk-msbuild-vs).
3. Extract this entire folder somewhere writable; open `JengaVideoStudio.sln`.
4. Restore, build, set **JengaVideoStudio.App** as the startup project, and press F5.
5. In **Manage Components** (Settings), install/launch Ollama from its official installer, download a model, download FFmpeg, and test narration. No model weights are inside this ZIP.
6. Start with **Light**, Windows narration, 45–60 seconds and an evergreen topic. Add Piper or ComfyUI after the basic pipeline works.
7. Read `docs/ACCEPTANCE-CHECKLIST.md` plus `docs/UI-WINDOWS-CHECKLIST.md`, and run `scripts/Verify-Windows.ps1` to record the first real Windows verification.

The first build may expose issues that could not be detected without a C# compiler here. This is not advertised as a proven zero-configuration executable. Please retain build errors and project logs when reporting a problem.

## Implemented source path

Idea → live encyclopedia discovery plus external references / supplied article URLs → source text and provenance → local AI research brief with exact-quotation checks → local AI scene script → local speech → graphics or configured ComfyUI visuals → per-scene FFmpeg encoding → final portrait MP4 + SRT + thumbnail → structural validation → review → optional Google OAuth / resumable YouTube upload.

The default speech fallback uses installed Windows speech, not neural AI. Piper provides optional local neural speech. No paid generative-AI key is required. YouTube needs your own Google Desktop OAuth client configuration. Research discovery does not require a search API key, but its breadth is limited.

## Projects and recovery

Projects default to `Documents\Jenga Video Studio`. Settings, downloaded tools and protected Google tokens use `%LOCALAPPDATA%\JengaVideoStudio`. Ollama normally manages models under `%USERPROFILE%\.ollama\models` (or its configured `OLLAMA_MODELS` location).

Every project has atomic `project.json`, sources, claim ledger, script, scene data, narration, assets, render intermediates, SRT, thumbnail, final MP4 and logs. Open a recent project and use **Resume / Render**. Narration and scene clips are cached by content. **Regenerate selected visual** changes only that scene's visual seed/revision; existing research and other assets remain available.

Stop the embedded preview before changing/rerendering a video. The application also releases it when starting a render. If another player is holding a file open, close that player.

## Tests

`tests/JengaVideoStudio.Tests` is a dependency-free console harness (use `dotnet run`, not `dotnet test`). It checks evidence rejection, serialization, path restrictions, subtitle timing, rendering arguments and content invalidation. `--render` adds a synthetic FFmpeg integration test. These C# tests are supplied but were not executed here.

`python scripts/verify_media_recipe.py` is a separate portable check that was executed here, using installed Python/Pillow and FFmpeg. It recreates the media command recipe; it does not execute the C# code. Results and a synthetic test frame are in `verification/`.

## Source layout

- `src/JengaVideoStudio.Core`: domain, persistence, research, providers, production orchestration, encoding, setup and YouTube protocol.
- `src/JengaVideoStudio.App`: WPF MVVM interface, commands and Windows DPAPI credential storage.
- `prompts`: versioned machine-output prompts.
- `workflows`: stock ComfyUI image workflow and configuration instructions.
- `docs`: downloads, architecture, limitations, licences and acceptance checklist.
- `scripts`: speech bridge and verification helpers.

All Google sign-in takes place in the system browser. The application never collects a Google password. Upload defaults to private and requires review plus a separate confirmation. An accepted upload is not a guarantee that YouTube processing has finished or that it is public.
