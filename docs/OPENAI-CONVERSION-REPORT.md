> Historical 3.1 report. For this 3.2 release, see [MULTIPART-UPGRADE.md](MULTIPART-UPGRADE.md) and the current verification results.

# OpenAI conversion report — EduVideo Studio 3.1

## Scope and architecture

This is the existing Studio with one narration provider, not a replacement graphics application.
The source inventory covered all 130 pre-refactor Java files (18,072 lines), resources, XML examples
and pom.xml. Existing scene, mathematical graphics, timeline, rendering and editor systems remain.
All final Java sources retain the George Miller copyright header and type/method Javadoc.

Added production classes: OpenAIVoices.java, OpenAISpeechProvider.java, WavAudio.java

Changed production classes: Ui.java, PreferencesDialog.java, MainWindow.java, VoicePane.java, ProjectDialogs.java, ProductionBuilder.java, GaussianEliminationMain.java, Main.java, SudokuBacktrackingMain.java, AudioMixer.java, SpeechVoice.java, SpeechProvider.java, VoiceSettings.java, SpeechCache.java, StudioSettings.java, Cancellation.java, ProductionEvents.java

Removed production class: OfflineSpeechProvider.java

Added tests: OpenAISpeechTest. Existing StudioTest and ProductionTest were updated to the new
voice catalogue and corrupt-cache recovery contract. ProcessExecutionTest covers subprocess behavior.

## OpenAI integration

OpenAISpeechProvider uses Java 17 HttpClient, the fixed official HTTPS /v1/audio/speech endpoint,
Bearer authentication, model/voice/input/speed and response_format=wav. Redirects are disabled.
Responses are limited to 64 MiB; connection/request timeouts are explicit. A worker polls cancellation
and cancels a pending request. No automatic retries or alternative speech providers are used.
Operational errors distinguish authentication, permissions, quota, rate limits, model/voice rejection,
network failure, timeout and invalid audio. Raw server errors and HTTP request headers are not logged.

OpenAIVoices is the single model/voice catalogue. Four model choices and 13 built-in voices are
supported, with the nine-voice subset for tts-1 / tts-1-hd. See README for the exact list.
Official sources checked on 2026-09-25:
- https://developers.openai.com/api/docs/guides/text-to-speech
- https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create

## GUI and credentials

Preferences > Narration and the Narration workspace share VoicePane. It provides masked key entry,
Show/Hide, a link to the API-key dashboard, optional Remember, model/voice/speed selection, sample
text, Test connection and Play preview. The key is never requested in XML or source code.
Voice choices populate immediately. Test connection checks model access; Play preview uses the
same provider as production and plays validated WAV through Java Sound. Network/audio work runs
on background workers, with JavaFX updates returned to the application thread.

The GUI key takes precedence over OPENAI_API_KEY. Default storage is session-only. Explicit Remember
uses Java Preferences (not encrypted; disclosed in the GUI) separately from non-secret configuration.
Queued jobs snapshot session settings so session-only keys work. A source scan found no hard-coded
OpenAI keys. Tests verify echoed error bodies and cache keys do not disclose test credentials.

## WAV duration and normalization

Old SpeechCache.duration divided Java Sound's header-derived frameLength by frameRate. That can
interpret a streaming data-length sentinel as real sample count and produce hours of fake duration.
No failing user WAV was supplied, so this is a reproduced failure mechanism rather than inspection
of the exact file from the user's computer. The new regression explicitly covers this case.

WavAudio parses RIFF chunks, validates received lengths, resolves recognized streaming data-length
sentinels against actual payload, and counts complete sample frames. Duration = dataBytes /
blockAlignment / sampleRate. It supports PCM 8/16/24/32-bit and float 32/64-bit mono/stereo WAV,
canonicalizing to signed 16-bit PCM with correct finite headers while preserving sample rate.
Malformed, truncated, empty or unsupported audio is rejected rather than guessed or reinterpreted.
AudioMixer alone performs the sample-rate/channel conversion to its 48 kHz stereo mixing format.

The cache key includes provider/version, model, resolved voice, text and relevant settings, but
never credentials. Entries use a new namespace so obsolete offline entries cannot collide.
Corrupt cached entries regenerate; valid cache hits remain duration-checked. Real overruns fail
with the cue ID and measured duration. Speech is never silently truncated or time-stretched.

## Windows process cleanup

Cancellation now waits for native process exit before releasing streams and ownership. The frame
encoder is protected by a finally block, including cancellation and startup-registration races.
The previous code destroyed FFmpeg asynchronously and removed its reference immediately, allowing
JUnit/Windows file deletion to race encoder.log's open handle. Temporary command-log cleanup is
best effort and cannot mask the original subprocess error. Tests remain enabled.

## Maven and verification

Java 17, JavaFX 17 and RichTextFX remain. No SDK or new runtime dependency was added. Gson, already
present, now handles OpenAI request/error JSON instead of local model configuration. Piper/eSpeak
implementation and executable/model-folder controls were removed. Python is not required.

Build command: mvn clean install — BUILD SUCCESS.
Tests: 103 passed, zero failures/errors/skips. Local HTTP tests cover cache identity (including
model/voice/text/key changes), WAV lengths, malformed/empty responses, timing, cache reuse and
recovery, authentication redaction, rate-limit/quota distinctions and HTTP cancellation. A mock
Speech API WAV was rendered through the real cache, mixer, renderer and FFmpeg to an MP4.
This integration fixture is a tone, not synthesized human narration, and is not presented as one.

GUI smoke test: see verification/BUILD-RESULTS.txt for final results and included actual screenshots.

## Remaining limits

No real paid OpenAI synthesis was executed: the user has not supplied an API key to this environment.
Actual model access, billing, narration quality and cue fit must be checked with Play preview / Render.
Native Windows behavior and speaker playback were not tested here; tests and GUI run on Linux.
The supported catalogue is documented and versioned, not dynamically fetched from a voice-list API.
Key persistence is not an encrypted OS credential vault. Custom voices and style instructions are
not exposed. Live timeline preview remains visual-only; final MP4 includes synchronized audio.
No native Windows installer is included. Existing XML with obsolete explicit voice IDs requires
removing that override or selecting an OpenAI voice. Other working XML and rendering stay intact.
