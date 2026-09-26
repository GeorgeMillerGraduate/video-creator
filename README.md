# New in 3.2: responsive workspace and multi-XML productions

Start with [START-HERE.md](START-HERE.md). The complete upgrade guide and conversion report is [docs/MULTIPART-UPGRADE.md](docs/MULTIPART-UPGRADE.md).

- Normal maximized startup; independent resizable timeline and lower workspace.
- Aspect-preserving Fit preview and 50/75/100/125/150/200% zoom.
- `edu-production` manifests combine independent `edu-video` lessons.
- Global music, compatible legacy music continuity, global speech/effect offsets.
- Shared streaming renderer, queue and OpenAI audio pipeline.
- Real chapter and whole-production frame progress; bounded thumbnails.

Existing single-XML workflows and the OpenAI setup below remain supported.

# Jenga-Code EduVideo Studio 3.2 — OpenAI Narration

Java 17 / Maven / JavaFX. The XML animation engine, editor, timeline, actual frame preview,
production monitor, sequential render queue, music mixing and FFmpeg export remain in place.
OpenAI is the only narration provider. Python, Piper, eSpeak and cloud-provider SDKs are not required.

## Start

Extract into a NEW project directory (recommended), then open its pom.xml in NetBeans.
Use JDK 17 and run:

```bat
mvn clean install
mvn javafx:run
```

The supplied NetBeans run action starts JavaFX. Launch-Studio.bat also runs Maven.
FFmpeg must be installed separately: Tools > Preferences > Projects & Rendering lets you
browse to ffmpeg.exe and test it. The preconfigured render profiles remain Preview (960x540/30),
Standard (1920x1080/30) and Final (1920x1080/60).

## Configure narration — no source edits

1. Open Tools > Preferences > Narration.
2. Use **Create / manage OpenAI API key** to open https://platform.openai.com/api-keys.
3. Paste your key into the masked API key field.
4. Select a model and voice. The default is gpt-4o-mini-tts / Marin.
5. Test connection checks access to the selected model; it does not generate audio or test billing.
6. Play preview generates a short WAV and plays it. This is a billable Speech API request.
7. Save, then open your XML and Render Video with OpenAI narration enabled.

Narration and previews require internet access and OpenAI API billing. A ChatGPT subscription
is not used as the API credential. Label published narration as AI-generated for your audience.
See the official guide: https://developers.openai.com/api/docs/guides/text-to-speech

The Narration workspace panel contains the same controls. Click Save narration settings before
starting a new job. Render dialog voice selection overrides the default; explicit XML voices
remain authoritative. No API key or provider belongs in XML.

### Key handling

A GUI-configured key takes precedence over OPENAI_API_KEY. By default a pasted key lasts only
for the current application session. Optional **Remember on this computer (not encrypted)**
stores it in Java Preferences, separate from the ordinary studio.properties file. On Windows,
Java Preferences normally uses the current user's registry. It is NOT an encrypted credential vault.
Clear the field, uncheck Remember and save to forget the saved key; an environment key can still apply.
Never put API keys in project XML, source control, shared screenshots or command-line arguments.

Non-secret settings are saved under %USERPROFILE%\.jenga-code\studio.properties.
The existing file migrates on Save: old speech-engine/voice-directory settings are removed.
Old explicit Piper/eSpeak/Google XML voice IDs must be removed or changed to an OpenAI voice.
Omitting voice, or voice="default", uses the current default. Old cached files cannot collide
with the new OpenAI namespace and may be removed through Tools > Speech Cache.

## XML and sample lessons

Try examples/production/openai-voice-check.xml for a short first render.
Open examples/production/sudoku-backtracking.xml for the 78-second Sudoku explainer.
It contains all board graphics, highlights, real solver-trace placements, rollback, and narration.
No image or model downloads are needed. Actual speech duration varies by model/voice; if a cue
exceeds its slot, extend max-end or shorten its text. Nothing is automatically stretched or cut.

```xml
<speak id="intro" start="0.4" max-end="8.6">
  Backtracking tries a choice, explores its consequences, and undoes a dead end.
</speak>
```

Optional voice="alloy" and rate="1.0" are supported. Pitch and SSML are rejected clearly.
The shared XML reference, examples and four project wizard templates document visual elements.
File > New Project creates valid XML. Open, edit, Save, Validate and Problems jump-to-error work
as before. Preview is visual-only; play the completed MP4 to hear synchronized narration.

## Models and voices

Official catalogue verified 2026-09-25, centralized in OpenAIVoices:
- gpt-4o-mini-tts and gpt-4o-mini-tts-2025-12-15:
  alloy, ash, ballad, coral, echo, fable, nova, onyx, sage, shimmer, verse, marin, cedar.
- tts-1 and tts-1-hd: alloy, ash, coral, echo, fable, onyx, nova, sage, shimmer.

Built-in voices are multilingual; there is no installed-language selector. Text determines the
spoken language. Model availability and account access are checked by the API. Custom voices
and style-instruction editing are not exposed in this release. Speed supports 0.25–4.0.

## CLI

After mvn package (use forward slashes instead on Linux/macOS):

```bat
java -jar target\EduVideo.jar --list-voices
java -jar target\EduVideo.jar --test-voice marin voice-test.wav
java -jar target\EduVideo.jar build examples\production\sudoku-backtracking.xml output\sudoku.mp4 --profile preview
java -jar target\EduVideo.jar frame examples\production\sudoku-backtracking.xml --time 30 output\frame.png --profile preview
```

CLI uses the saved key or OPENAI_API_KEY. GUI session-only keys are deliberately not shared with
separate CLI processes. Override non-secret model using -Deduvideo.openai.model=tts-1 before -jar;
use --voice alloy on build for its default voice. --no-speech intentionally omits narration while
retaining music/effects. No fallback voice is ever substituted.

## Audio, caching and cancellation

The Speech API returns WAV. WavAudio validates RIFF chunks and actual payload length, repairs
recognized streaming length placeholders, and canonicalizes supported PCM/float data to signed
16-bit PCM at its native rate/channel count. Duration is real sample frames divided by sample rate.
SpeechCache hashes provider/version, model, resolved voice, text and speech settings—not the key.
Valid cached clips are reused; corrupt clips are removed and regenerated. AudioMixer alone resamples
to its 48 kHz stereo mix format, also mixing existing music with narration ducking.

Network calls and rendering run on workers. Cancel aborts a pending HTTP future and terminates the
owned encoder; its handles are reaped before releasing it. Temporary process logs cannot mask the
original error if Windows briefly retains a lock. Details can be copied from error dialogs.
No automatic HTTP retries are performed; repeat a failed render explicitly after resolving the error.

## Validation and limitations

See docs/OPENAI-CONVERSION-REPORT.md and verification/BUILD-RESULTS.txt for actual checks.
Tests use a loopback HTTP server and synthesized PCM fixtures, not a real OpenAI account.
No live paid OpenAI call has been made during this refactor. Windows-specific behavior must still
be confirmed on your machine; build, audio, HTTP and GUI smoke checks were run on Linux.
