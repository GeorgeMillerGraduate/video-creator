# Architecture

The WPF App is the composition root. It creates replaceable language, research, speech, visual, renderer and credential providers and passes them into `ProductionPipeline`. The UI uses bindings, observable state and commands; process/IO-heavy production runs outside the dispatcher, with progress marshalled back through `Progress<T>`.

Core has no WPF dependency and no third-party NuGet packages. Its provider contracts are in `Domain.cs`. `YouTubePublisher` uses the documented HTTP protocol directly, rather than a Google SDK. The Windows App supplies DPAPI-backed credential storage.

`ProjectStore` writes JSON to a temporary file and atomically replaces the current file. Completed research and scripts are reused. Narration is keyed by narration text/provider identity, visual work by prompt/type/revision/profile, and rendered clips by media/overlay inputs. Upload is blocked if scene text/directions differ from the rendered fingerprint. User edits still need editorial verification.

The model receives source material as JSON data under a separate system instruction. Quotation validation checks that each cited extract exists in its supplied source. It cannot prove a paraphrase is true. Public research uses bounded HTTPS fetches, public-address checks, conservative robots disallow handling, size limits and no browser/script execution. This is not a general-purpose hardened web crawler.

Subprocesses use `ProcessStartInfo.ArgumentList` with no shell. Narration text is passed by stdin or a file. FFmpeg subtitle paths are internally generated relative names in a controlled rendering directory, avoiding user paths in filter syntax. The Windows speech bridge is a fixed PowerShell script, never user-generated shell code.

Google refresh/access tokens and resumable session URLs are encrypted with the current Windows user's DPAPI. They are separate from settings and project JSON. OAuth uses browser consent, state verification and PKCE. Upload uses eight-MiB chunks, checks server acknowledgement after errors, and persists the final ID before removing the session credential. It never auto-publishes following rendering.

Future perpetual-key licensing can be added at the application boundary without putting payment decisions inside the production providers. No signing key, entitlement service or licensing design is implemented in this draft.
