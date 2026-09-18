# Components, versions and licences

No external binaries or model weights are redistributed in this source ZIP. Downloading a component separately does not itself remove licence obligations. This is a technical inventory, not commercial licence clearance.

| Component | Version choice / record | Licence and source |
| --- | --- | --- |
| .NET / WPF | Targets net10.0; use patched SDK bundled with current Visual Studio; record `dotnet --info` | Runtime/source MIT; Microsoft product tooling terms differ. https://github.com/dotnet/wpf |
| Ollama | User-installed maintained runtime; installed model inventory records digests | MIT engine; model licence separate. https://github.com/ollama/ollama |
| Qwen2.5 7B / 1.5B | Explicit model tags; Ollama records resolved digest | These two variants list Apache-2.0. https://ollama.com/library/qwen2.5:7b and https://ollama.com/library/qwen2.5:1.5b . Other Qwen sizes can differ; 3B is intentionally not the default. |
| Piper | Installer pins piper-tts==1.8.0; pip freeze written after setup | GPL-3.0-or-later. https://pypi.org/project/piper-tts/ and https://github.com/OHF-Voice/piper1-gpl |
| Lessac medium | Downloaded by Piper; no source weight bundled | Voice-specific dataset licence linked from its model card, not assumed MIT/GPL or commercially cleared. https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/lessac/medium/MODEL_CARD |
| Windows System.Speech | Uses installed OS/.NET Framework assembly via fixed script | Microsoft Windows component; not redistributed |
| FFmpeg / ffprobe | Current Essentials release from gyan.dev; supplied SHA-256 verified and full version recorded | FFmpeg licensing depends on build flags; Essentials includes GPL components. https://ffmpeg.org/legal.html and https://www.gyan.dev/ffmpeg/builds/ |
| ComfyUI | User-installed version; workflow JSON chosen explicitly | GPL-3.0 for engine; model/custom-node terms are separate. https://github.com/Comfy-Org/ComfyUI |
| Image/video checkpoints | No automatic model choice or bundled weights | Follow selected model card. SD1.5 example requires separate checkpoint and applicable OpenRAIL/model terms; no blanket commercial clearance |
| Wikipedia / MediaWiki | Live public API requests; retrieval timestamps saved | Wikimedia content terms apply; source text is research input, not a licence to reproduce full articles in output. https://www.mediawiki.org/wiki/API:Search |
| Google OAuth / YouTube API | Direct documented HTTPS protocols | Google API/YouTube terms and project restrictions apply. https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol |

API contracts and upstream documentation were consulted on 17 September 2026. A runtime integration was not verified merely by reading its documentation. Exact Ollama/FFmpeg runtime releases are recorded at setup instead of freezing users onto an old binary. Model download sizes and runtime compatibility can change.
