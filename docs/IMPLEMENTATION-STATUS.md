# Requirements and verification status — 0.2

This is the intermediate personal draft requested in the current message. It does **not** satisfy the master document's complete-product definition of done yet. No Windows compilation or full acceptance run was possible in the supplied environment. “Implemented” below means source is present and connected to the interface, not that its external integration was exercised.

| Area | Draft status | Remaining work / qualification |
| --- | --- | --- |
| WPF + MVVM | Redesigned shell, eight workspaces and asynchronous commands | Windows build, launch, accessibility and visual UI QA unverified |
| Persisted projects | Atomic JSON, relative paths, source/audio/scene caches | No database, no autosave of every keystroke, no prior-version migration |
| Local LLM | Ollama JSON generation, retries, CPU override, unload | No native embedded llama.cpp runtime; real model calls untested |
| Model setup | Pull progress, installed model digest list, FFmpeg hash check, isolated Piper setup | Ollama/Python installed interactively; no universal automatic model-pack installer |
| Hardware | Windows CIM CPU/RAM/GPU/VRAM, output disk space | Advisory text; no benchmark, precise acceleration probe or automatic profile selection |
| Research | Live Wikipedia search/extracts, some external references, user URLs, robots restriction checks | No general search provider, recency filtering, robust article extraction or sophisticated ranking |
| Claim ledger | Model synthesis; exact source quote and ID validation | Quote presence is not entailment/fact verification; no independent contradiction classifier |
| Scripts and scenes | Structured writing/directing prompt; reference checks; editable scenes | Writing and scene planning share one model pass; no standalone full-script re-plan command |
| Narration | Windows speech fallback and optional Piper neural provider; hashed segments | Neural voice untested; no pronunciation dictionary or word alignment |
| Captions | ASS burned into scenes plus SRT export; measured segment duration | Intra-segment timing estimated; no subtitle editor UI or emphasis editor |
| Graphics | Typography and labelled lists for diagrams/timelines/statistics | Simple cards, not rich factual maps/charts/composites; some headings/labels truncated to fit |
| Images | Configurable ComfyUI API workflow; included SD1.5 example; pan/zoom | Models and ComfyUI require guided setup; not live-tested |
| Video | Configurable text-to-video workflow adapter; MP4/WebM saved outputs | No installed default video model, image conditioning, candidate ranking or prompt-specific seed control |
| Assets | Local generated files with provenance; no automatic unlicensed image scraping | No third-party stock search, user asset import UI or licence-review browser |
| Rendering | 1080×1920 H.264/AAC, 30 fps, loudness normalization, scene cache, concatenation, thumbnail | Hard cuts; no crossfades, background music or ducking. Real recipe tested, C# invocation untested |
| QA | ffprobe structure/timing + full decode; claim references and warnings | No silence detection, factual truth classifier or rendered-text overflow detector |
| Review | Preview, scene edit/regenerate, source evidence display, metadata and disclosure controls | Playback scrubbing and saved-scene thumbnails added in 0.2; no visual candidate browser or thumbnail uploader |
| YouTube | Loopback OAuth + PKCE, protected tokens, channel lookup, chunk upload, resume/retry, returned ID | Not live-tested; Google project/account setup required; no processing-state monitor |
| Cancellation | Cancellation tokens, owned child-process termination, queued-job removal | Active shared ComfyUI job may continue; reopening shows saved state, not a separate Interrupted badge |
| Profiles | Light/Balanced, tone, duration, CPU flag | No High/Maximum, named profile saving, topic embedding history or category exclusion UI |
| Logging | Per-project production log, argument files, probe output, runtime install records | Limited structured diagnostics; no central log-viewer UI |
| Commercial | Not implemented, as requested | No trial, subscription, activation, payment or installer logic |

## Tests actually performed here

- Both XAML documents and project XML parsed successfully. This checks XML well-formedness, **not WPF compilation**.
- Real FFmpeg graphic scene and still-image zoompan scene rendered with synthetic audio.
- Scenes concatenated into a 1080×1920 H.264/AAC MP4; duration and streams checked; full decode passed.
- A rendered frame was inspected for caption/heading layout.
- Source/configuration/package checks were performed. No C# compiler result is available.

See `verification/media-recipe-result.txt`, `media-recipe-probe.json`, and `render-fixture.png`. The fixture is not evidence of successful AI research or narration.

## Supplied but not executed here

The C# console tests, Windows verification script, actual model download/inference tests, neural voice test, ComfyUI generation and YouTube OAuth/upload. Full completion must wait for the Windows acceptance checklist. The missing .NET compiler is an environmental limit; the feature gaps listed above are implementation scope limits.

## UI redesign verification

See `UI-REDESIGN.md` and `../verification/ui-static-checks.txt`. Core/provider files and existing tests are unchanged. The new UI is not Windows-compiled or runtime-verified here. The supplied redesign notes report that the prior 0.1 interface launched on the user's PC.
