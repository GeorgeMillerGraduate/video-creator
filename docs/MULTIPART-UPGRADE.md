# EduVideo Studio 3.2 — multipart production upgrade

## What changed

This upgrade builds on the delivered OpenAI 3.1 project. It retains the existing Graphics2D renderer, scene/animation vocabulary, narration cache, OpenAI provider, mixing policy, FFmpeg encoder, single-XML workflows, project library and sequential render queue. It introduces no new runtime dependencies.

The application opens maximized using `Stage.setMaximized(true)`, without exclusive fullscreen. Normal restore, resize and minimize still work. Windowed dimensions and workspace divider preferences are retained. Library and inspector widths stay approximately fixed as the window grows; the central workspace receives extra width. Split panes independently resize the main columns, timeline, and lower Log / Problems / Production / Render queue area. Starting or finishing production does not force a new divider position.

Preview Fit follows the available viewport and preserves the source aspect ratio (16:9 for the supplied lessons). Percentage zoom refers to logical source pixels and provides scrollbars when needed. Interactive frames use a bounded 1280-pixel-wide raster cache, so zoom is for composition inspection, not a full-resolution pixel proof; final output uses the selected delivery profile. The preview is visual-only, as in 3.1. Use completed MP4 playback to audition the full mix.

The monitor has an independently resizable picture/statistics split, scrollable statistics, and an optional **Recent frames** strip. Only twelve reduced thumbnails and the latest frame are retained. UI updates remain throttled to 10 Hz. Every required video frame still reaches FFmpeg.

## Manifest format

Ordinary lesson XML still uses `<edu-video version="1.0">`. A production uses a different root, so no existing lesson schema is replaced:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<edu-production version="1.0" title="Fourier course"
                width="1920" height="1080" fps="30"
                output="output/fourier-complete.mp4">
  <audio>
    <music src="audio/background.wav" volume="0.15"
           loop="true" fade-in="1.5" fade-out="2"/>
  </audio>
  <parts>
    <part src="01-introduction.xml" title="Introduction"/>
    <part src="02-fourier-series.xml" title="Fourier series"/>
    <part src="03-heat-equation.xml" title="Heat equation"/>
    <part src="04-solution.xml" title="Worked solution"/>
    <part src="05-summary.xml" title="Summary"/>
  </parts>
</edu-production>
```

Paths are resolved relative to the manifest, while assets inside each chapter stay relative to that chapter's XML. The title is optional. The output path is a default for the render dialog/CLI. Root width/height/FPS default to 1920/1080/30 when omitted and must match every source lesson. `version="1.0"` and a nonempty `parts` list are required. Nesting production manifests inside productions is intentionally unsupported. Repeating a lesson is allowed; each occurrence gets a separate offset and narration identity.

Global `music` accepts `src`, `volume` (0–4, default .15), `loop` (default false), `start` (default 0), `end` (default complete duration), `fade-in` and `fade-out` (default 0). Multiple music regions are allowed. Global music is a preferred explicit production-level definition. Narration and sound effects remain in ordinary chapters.

The manifest's settings validate source compatibility. The existing delivery profiles remain authoritative for export: Preview = 960×540 / 30 FPS, Standard = 1920×1080 / 30 FPS, Final = 1920×1080 / 60 FPS. A 30-FPS source production can therefore be sampled at 60 FPS by Final, exactly as a single lesson could before.

## GUI workflow

1. Use **File → New production**, select existing XML lessons and choose a new manifest filename. The first lesson supplies the source format; incompatible parts receive a validation error.
2. Open the **Production parts** tab. Add XML Part, Remove, Up and Down change manifest references. Removing a part never deletes its source file. Use XML source for chapter titles and detailed audio settings.
3. **Open selected** opens the part as an ordinary editable lesson, with normal unsaved-change protection. Return through File → Recent projects or the library.
4. **Preview selected** seeks to that chapter in the global preview. The transport shows production and local time. **Render selected** uses the same render dialog for the independent lesson.
5. **Global music** selects a looping bed and volume for the whole production. It replaces the manifest's existing global music list; detailed/multiple regions can be authored in XML.
6. Save Production or Ctrl+S saves the authoritative XML. Chapter operations use the editor's normal undo history and dirty-state tracking. Manifest Save As rebases paths to preserve references. GUI chapter operations normalize the small manifest format and do not retain its comments/formatting; they do not rewrite lesson XML.
7. Render Complete opens the shared profile, voice, output, range and queue dialog. Speech is enabled unless explicitly deselected. An existing output is protected from overwrite.

Validation is asynchronous and debounced. Invalid edits do not become renderable snapshots. A production's timeline shows chapter blocks, globally offset speech windows and global/continuous music regions. Click blocks to seek and inspect their actual times. Revalidate after externally editing a part; filesystem changes are not watched automatically. Queue entries intentionally retain the validated lesson snapshots captured when queued.

## Rendering and timing

`FrameSequence` samples one continuous output clock across all parts. Part start time is the exact sum of earlier source durations. A cue at local 12 seconds in a part starting at 60 appears at global 72 seconds. No source timestamps are rewritten. Chapter boundaries are not independently rounded to frames; only the complete requested range is sampled at the delivery FPS. A visual boundary is necessarily quantized to that frame grid, with no accumulated per-chapter rounding drift.

The existing `ProductionBuilder` owns both single-lesson and multipart export. It validates first, resolves/measures/caches narration, creates one full-production audio mix, then streams BGR frames sequentially into one H.264 encoder. `FrameSequence` switches its worker-owned `VideoRenderer` at chapter boundaries and releases the previous renderer/backdrop cache. There is no intermediate per-part re-encoding and no full-frame collection. Source models/assets remain loaded as validated snapshots; frame/backdrop caches are bounded independently of video duration.

Narration settings are resolved before synthesis. Measured speech must still fit its original local window. Failures identify the part number, XML filename and cue; narration is neither stretched nor silently cut. The composition prefixes cue identities to avoid collisions between independently authored chapters. Cache content identities remain unchanged by part ordering/timing.

## Continuous music

A global music region is decoded once by FFmpeg for the region's entire duration. `-stream_loop -1` is used when looping is enabled; decoded PCM is disk-backed, never accumulated as repeated Java arrays. The mixer preserves global gain, outer fades, narration ducking and local sound effects, and trims the mix to the exact source duration. Final muxing uses one AAC stream.

For legacy XML, touching music regions at a chapter boundary join when their canonical asset paths, volume and loop mode match, and either their touching fades are zero or both regions have the same fade-in/fade-out policy. Only the joined region's outer fades remain. Different gain/loop settings, gaps, and incompatible fades remain separate intentional regions. A matching global region covering a local music track takes precedence, avoiding duplicate playback; different local audio is preserved.

An XML boundary does not restart a joined track, insert silence or cause a crossfade. A source file with a poorly edited loop endpoint can still click at its own loop point; use a loop-ready music asset. A nonlooping source naturally runs out if it is shorter than its requested region. Preview/range exports mix on the full production clock before taking the requested range, preserving the correct music position.

Long mixes open only currently active decoded streams and evaluate only nearby ducking envelopes. Previously completed cues do not add ongoing per-sample work. PCM intermediates consume disk space; ensure adequate free space for long videos. The conventional RIFF mix is limited to approximately 4 GiB (around six hours at 48-kHz stereo PCM16). This comfortably covers the requested 20–30 minute use case; RF64 export is not added.

## Progress and cancellation

`ProductionListener.part(PartProgress)` supplies chapter number/title, local/global time and actual chapter frame counters. The existing `frame` callback still supplies global completed/total frames and measured elapsed rendering time. Fractions and ETA describe frame generation, not fabricated overall pipeline progress. Audio preparation and final mux stages are named real transitions; their remaining time is not guessed.

Cancellation remains per job. It stops frame submission and terminates/reaps owned FFmpeg processes, closes streams and removes incomplete workspace files in a `finally` block. Source XML is untouched, and previously completed outputs are preserved. Valid speech-cache entries remain reusable. If an OS retains a temporary handle, cleanup produces a warning rather than masking the original failure. Native Windows process behavior still requires confirmation on the target machine.

A completed render publishes one MP4 plus the existing report and an optional `.chapters.txt` with delivery-relative timestamps. Chapter text generation failure does not invalidate a successfully published video.

## Major classes added

| Class | Responsibility |
|---|---|
| `production.ProductionProject` / `Part` | Validated ordered lesson snapshots, offsets, duration, chapter metadata |
| `production.ProductionAudio` | Global clock composition, compatible legacy music joining, duplicate-bed suppression |
| `production.FrameSequence` | Shared single/multipart input, exact global frame sampling, current-chapter renderer ownership |
| `production.PartProgress` | Actual part counters and local/production clocks |
| `xml.ProductionParser` | Hardened, located manifest parsing and independent lesson validation |
| `xml.ProductionWriter` | Simple manifest serialization and relative-path rebasing |
| `ui.ProductionPartsPane` | Chapter list, reference editing, global music and selected/complete actions |
| `MultipartProductionTest` | Model, compatibility, PCM continuity, real FFmpeg and cleanup coverage |

## Major classes changed

| Class | Change |
|---|---|
| `app.EduVideoStudio` | Normal maximized startup, laptop-compatible minimum size |
| `ui.MainWindow` | Manifest routing, parts tab/actions, asynchronous validation, resizable timeline/lower workspace, path-safe manifest Save As |
| `ui.PreviewPane` | Real multipart previews, global/local clocks, aspect-correct viewport sizing, percentage zoom, bounded 1280-wide preview cache |
| `ui.TimelinePane` | Compact complete-production video/speech/audio lanes |
| `ui.ProductionPane` | Bounded recent-frame strip, adaptive monitor, chapter and overall progress |
| `ui.ProjectDialogs` | Shared single/multipart render dialog, manifest output default |
| `production.RenderQueue` | Multipart snapshot jobs in the existing sequential worker |
| `production.ProductionListener` | Additive chapter-progress callback; existing listeners remain compatible |
| `export.ProductionBuilder` | Shared sequence-based streaming, chapter events, chapter file, cleanup on every outcome |
| `video.VideoRenderer` | Exact-time rendering entry point; existing frame API delegates to it |
| `audio.AudioMixer` | Part-aware errors, active-stream lifecycle and nearby-envelope sampling for long mixes |
| `cli.Main` | Validate/frame/build/render support for manifests; existing single-lesson commands retained |
| `editor.XmlEditor` | Search fields and content can shrink with their pane |
| `ui.VoicePane`, `ui.PreferencesDialog` | Wrapping narration buttons and screen-bounded preferences |
| `eduvideo-dark.css` | Compact layout, chapter styling, timeline and divider states |
| `pom.xml` | Version 3.2.0; existing Java 17 / JavaFX dependencies unchanged |

## Exact commands

Run from the extracted project directory:

```bat
mvn clean install
mvn javafx:run
java -jar target/EduVideo.jar validate examples/production/fourier-course/fourier-course.xml
java -jar target/EduVideo.jar frame examples/production/fourier-course/fourier-course.xml --time 35 chapter.png --profile standard
java -jar target/EduVideo.jar build examples/production/fourier-course/fourier-course.xml output/course.mp4 --profile final
java -jar target/EduVideo.jar build examples/production/fourier-course/fourier-course.xml output/course-preview.mp4 --profile preview --no-speech
java -jar target/EduVideo.jar build examples/production/fourier-course/03-heat-equation.xml output/chapter.mp4 --profile standard
```

`--from` and `--to` are production seconds for manifests. `--scene` remains available for ordinary lesson XML; use the independent part file or a global range for manifests. For a GUI preview of a manifest, open Studio; the legacy bare `preview lesson.xml` Swing utility remains a single-lesson compatibility tool.

OpenAI API key/voice/model settings are unchanged from 3.1. Supply them through Tools → Preferences → Narration, or `OPENAI_API_KEY`. No credentials are included in this archive. Speech previews/uncached synthesis require internet and paid API credits; `--no-speech` is an explicit editorial option that preserves music and effects.

## Verification and limitations

See `../verification/BUILD-RESULTS.txt` for final command results. Automated tests use local synthetic speech or a local mock HTTP service; no paid OpenAI request was made. The full five-part demonstration was exported with narration explicitly omitted, but its music, animation, frame count, chapter changes and final AAC/H.264 container were checked. Separate integration tests exercise generated narration, cache duration validation, global offsets, mixing and MP4 production.

Headless JavaFX checks cover 1366×768, 1920×1080, 2560×1440 and 3840×2160 layouts, actual manifest loading and global seeking. Native Windows chrome, display scaling, device audio and installed FFmpeg behavior could not be verified in this Linux environment. Existing OpenAI synthesis implementation is retained; no claim is made about live service testing in this upgrade.

There is no automatic editorial transition between unrelated chapter visuals: the XML authors determine what appears on either side of a boundary. Independent music continuity is guaranteed by composition for compatible regions, not by synthesizing crossfades between intentionally different music. Optional chapters are a sidecar text file, not embedded MP4 chapter metadata. Full-resolution visual source assets can still occupy memory even though generated frames are streamed.
