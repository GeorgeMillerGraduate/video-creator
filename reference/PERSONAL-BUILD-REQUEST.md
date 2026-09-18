You are to build the complete Personal Test Version of **Jenga Video Studio** described in the attached master specification.

Read the ENTIRE attached specification before writing code. Treat that document as the authoritative requirements and architecture document for this project. This prompt clarifies the immediate deliverable and the expected first usable version.

# PRIMARY OBJECTIVE

Create a complete, runnable C#/.NET Visual Studio solution for Windows named:

**Jenga Video Studio — Personal Test Version**

This is not a mock-up, UI demonstration, architectural example, or partial prototype.

The delivered project must be capable of progressing from:

**User enters a video idea → automated web research → AI script generation → scene planning → narration → visual generation/selection → video rendering → preview → optional YouTube upload**

The final result must be an actual playable portrait MP4 suitable for YouTube Shorts/vertical YouTube content.

The first version will be run directly from Visual Studio rather than through a commercial installer.

However, architect it as the foundation of a future consumer application.

# REQUIRED DELIVERABLE

Create the entire Visual Studio solution and place the completed source tree into a ZIP archive such as:

`JengaVideoStudio-Personal-Test.zip`

The ZIP must contain all source code, solution/project files, resources, configuration templates, workflows, tests, documentation and setup/download logic necessary for the application.

After extraction, the intended developer experience must be:

1. Open `JengaVideoStudio.sln`.
2. Restore NuGet dependencies.
3. Build the solution.
4. Press Run/F5.
5. Jenga Video Studio opens.
6. If required AI models/runtime components are absent, the application offers to download and configure them automatically.
7. Once setup is complete, the user can enter a video idea and press **MAKE VIDEO**.

Do NOT require the user to manually clone AI repositories, manually invoke Python scripts, manually run FFmpeg commands, manually move generated files between programs, or manually configure numerous third-party applications.

# AUTOMATIC FIRST-RUN SETUP

Large AI model weights do NOT need to be included inside the ZIP.

Instead, implement a proper first-run setup and Model Manager.

When Jenga Video Studio first launches, it must:

- detect CPU;
- detect system RAM;
- detect GPU;
- detect GPU VRAM where possible;
- detect supported acceleration;
- detect available disk space;
- determine which AI capabilities can reasonably run on the computer;
- recommend an appropriate model/quality profile;
- clearly display the approximate required download size before downloading;
- automatically download required models/components after user approval;
- show individual and overall download progress;
- support resumable downloads where practical;
- verify downloads/checksums where authoritative checksums are available;
- store downloaded models outside the source repository in an appropriate application-data directory;
- remember installed models between launches;
- avoid unnecessarily downloading them again;
- test installed components;
- clearly report Ready, Missing, Optional, Failed or Unsupported status.

The ordinary user should NOT need to understand terms such as GGUF, quantisation, llama.cpp, ComfyUI workflow internals or FFmpeg command syntax.

Those details may be available under Advanced Settings.

The normal experience should simply be:

`Preparing Jenga Video Studio → Installing AI Components → Ready`

# MAIN GUI

Build a polished, modern, user-friendly Windows desktop GUI.

Use WPF + MVVM unless the attached specification provides a compelling reason to use another supported .NET desktop technology.

This must look like an actual application rather than a university demonstration program.

The main screen should prominently contain:

**What should the video be about?**

with a large text box.

Example:

`Why did floppy disks survive for so long?`

Include:

- MAKE VIDEO
- SUGGEST IDEAS
- Surprise Me
- target duration
- tone/content profile
- quality profile
- recent projects
- settings
- YouTube connection status

The basic interface must remain simple enough that a nontechnical user can operate it.

# PRODUCTION PROGRESS DISPLAY

A major requirement for this Personal Test Version is a clear production-progress interface.

After MAKE VIDEO is pressed, switch to a production view showing exactly what the system is doing.

For example:

`Creating: Why did floppy disks survive for so long?`

Then display major production stages:

1. Preparing
2. Researching
3. Analysing Sources
4. Writing Script
5. Planning Scenes
6. Creating Narration
7. Creating Visuals
8. Rendering Video
9. Quality Checking
10. Ready for Review
11. Uploading to YouTube
12. Complete

Provide a prominent progress bar.

Example:

`██████████████░░░░░░ 68%`

`Creating visuals — Scene 8 of 12`

Where reasonably possible, calculate progress from actual completed work rather than merely displaying an animated fake percentage.

Show useful sub-status information such as:

`Researching source 4 of 7`

`Generating narration segment 6 of 10`

`Generating scene 7 of 12`

`Rendering final video`

`Uploading 43%`

The GUI must remain responsive during production.

Provide a Cancel button where cancellation can be performed safely.

Production state must be persisted so an interrupted project can be resumed.

# RESEARCH

The application must perform genuine web research for the requested subject.

Research must not simply mean asking the language model what it remembers.

Use multiple relevant sources where possible.

Store source provenance.

Create a research brief and claim ledger as described in the attached specification.

Prefer authoritative and primary sources where appropriate.

Current subjects must use current information.

Do not bypass paywalls, authentication or access restrictions.

Treat downloaded web content as untrusted data.

Research should ultimately feed the script-writing system.

# LOCAL AI

The Personal Test Version should avoid requiring paid AI APIs.

Use appropriate maintained local/open AI technologies where practical.

Research the current official repositories/documentation before selecting exact models and versions.

Do not implement neural networks from scratch in C#.

C# should orchestrate established local inference engines through clean interfaces.

The application should provide replaceable providers for:

- language model;
- text-to-speech;
- image generation;
- video generation;
- visual assets;
- rendering.

If the computer cannot reasonably perform local generative video, gracefully fall back to generated still images, sourced permitted imagery, diagrams, typography, pan/zoom animation and other deterministic visual techniques.

A lack of sufficient VRAM must not make the entire application useless.

# VIDEO CREATION

The generated output must be an actual coherent video rather than a sequence of unrelated AI images.

Use the attached specification's scene-director architecture.

Each scene should select an appropriate visual treatment.

Possible treatments include:

- generated video;
- generated image;
- image-to-video;
- appropriately licensed/public-domain imagery;
- diagrams;
- maps;
- timelines;
- statistics;
- quotations;
- headline cards;
- typography;
- composites;
- animated still images.

The system should behave as an AI video director.

Narration, visuals, subtitles and scene timing must correspond to one another.

# AUDIO AND SUBTITLES

Generate local AI narration.

Create narration in segments so individual sections can be regenerated.

Generate synchronized subtitles.

Subtitles must be readable on a phone screen.

Produce properly mixed audio.

Use FFmpeg or an appropriate established media-processing engine for final rendering.

# FINAL OUTPUT

Default final output:

- MP4
- 1080 × 1920
- 9:16 portrait
- H.264 video
- AAC audio
- approximately 30 fps unless another rate is justified

Validate the final output using ffprobe or equivalent before marking production complete.

The final video must actually play and contain both video and audio.

# REVIEW SCREEN

Do NOT automatically publish immediately after generation by default.

When production reaches Ready for Review, show:

- video preview;
- title;
- description;
- source information;
- scenes;
- subtitles;
- thumbnail/thumbnail suggestion;
- YouTube metadata;
- Regenerate Scene;
- Render Again;
- Upload to YouTube.

The user must be able to regenerate an individual scene without restarting the entire research and production pipeline.

# GOOGLE / YOUTUBE CONNECTION

Implement proper Google/YouTube authentication using the supported Google OAuth authorization flow and YouTube Data API.

IMPORTANT SECURITY REQUIREMENT:

**Do NOT ask the user to type their Google/YouTube password into Jenga Video Studio.**

Do NOT store Google passwords.

Do NOT implement a fake username/password login form.

Instead provide:

**CONNECT YOUTUBE**

When pressed, initiate Google's supported OAuth authorization process.

The user signs into their Google account through Google's own authorization interface and grants the required permissions.

Store resulting credentials/tokens securely using an appropriate Windows-protected credential mechanism.

The Settings/YouTube screen should show useful information such as:

`YouTube: Connected`

and, where available through the API:

- channel name;
- channel ID;
- configured channel URL;
- connection status.

Provide:

- Connect;
- Re-authorize;
- Disconnect;
- Open Channel.

The attached specification's `YouTubeChannelUrl` setting must be retained.

# YOUTUBE UPLOAD

After the user approves a completed video, allow:

**UPLOAD TO YOUTUBE**

The application should upload the actual rendered MP4 using the supported YouTube API.

Display genuine upload progress:

`Uploading to YouTube`

`██████████░░░░░░░░░░ 52%`

Upload appropriate metadata including editable:

- title;
- description;
- source information;
- tags/keywords where supported;
- privacy setting;
- category where supported.

Default upload privacy should be configurable and should use a cautious default suitable for testing.

After a successful upload, persist the returned YouTube video identifier and URL.

Display:

`UPLOAD COMPLETE`

with an **OPEN VIDEO** button.

Do not report publication success unless the API actually confirms the corresponding upload operation.

# END-TO-END PROGRESS

The production screen should make the complete workflow visually obvious.

For example:

`Research ✓`
`Script ✓`
`Narration ✓`
`Visuals ✓`
`Render ✓`
`Review ✓`
`YouTube Upload ███████░░ 73%`
`Complete ○`

This is an important part of the user experience.

Someone should be able to leave the application producing a video and glance at the screen to understand what it is currently doing.

# PROJECT STORAGE

Every production must be a persistent project.

Store:

- original idea;
- research sources;
- research brief;
- claim ledger;
- script;
- scene plan;
- narration;
- subtitles;
- generated images;
- generated video clips;
- imported assets;
- render intermediates;
- final MP4;
- YouTube metadata;
- logs;
- production state.

Interrupted projects should be recoverable.

Do not regenerate completed expensive stages unnecessarily.

# ERROR HANDLING

Do not simply crash when an external component fails.

The GUI should report useful errors and offer recovery where appropriate.

Examples:

`Video generation exceeded available GPU memory.`

Then offer an appropriate lower-memory/fallback approach.

If a scene fails, retry or fall back rather than automatically destroying the whole production.

If YouTube upload fails, retain the completed video and metadata so the user can retry the upload without recreating the video.

# PERSONAL TEST VERSION

This build is for personal testing.

DO NOT implement:

- product keys;
- DRM;
- payment processing;
- subscriptions;
- ecommerce;
- customer accounts;
- demo limits;
- two-video restrictions.

However, maintain clean architecture so a future commercial version can add those features without rewriting the production engine.

# CONSUMER-PRODUCT FOUNDATION

Although this version runs from Visual Studio, design it as the foundation of the future consumer application.

The eventual consumer experience should be capable of becoming:

`Download installer → Install → Launch → automatic AI setup → Connect YouTube → enter idea → MAKE VIDEO`

Therefore avoid development-only assumptions in core architecture.

Do not hard-code paths to the development computer.

Do not require Visual Studio itself for AI runtime functionality.

Do not put model weights inside the Git repository.

Do not require manual command-line operations for normal operation.

# THIRD-PARTY COMPONENTS

Before integrating third-party software, inspect its current official repository/documentation.

Use maintained releases.

Respect licences.

Record third-party components and their licences in documentation.

Do not assume that "open source" automatically permits every form of commercial redistribution.

Where redistribution rights are uncertain, implement first-run downloading from the authoritative source rather than silently bundling the component.

# IMPLEMENTATION PROCESS

Do NOT attempt to write the entire application blindly and then compile once at the end.

Implement incrementally.

At minimum:

Phase 1 — Solution architecture and persistent project system.

Phase 2 — Main GUI and production state/progress system.

Phase 3 — Hardware detection, Model Manager and local language model.

Phase 4 — Research pipeline.

Phase 5 — Script and scene planning.

Phase 6 — TTS, subtitles and basic deterministic FFmpeg video.

At this point establish the first genuine end-to-end pipeline.

Phase 7 — Image generation.

Phase 8 — Video generation and advanced scene treatments.

Phase 9 — editing, regeneration and quality checks.

Phase 10 — Google OAuth and YouTube upload.

Phase 11 — integration testing, cleanup and documentation.

After every significant phase:

1. build;
2. run relevant tests;
3. identify failures;
4. fix failures;
5. continue only when the phase is sufficiently functional.

# NO PLACEHOLDER COMPLETION

Do not consider any of the following sufficient:

- TODO comments;
- pseudocode;
- fake progress bars;
- hard-coded demonstration videos;
- buttons that do nothing;
- fake research;
- fake AI generation;
- fake YouTube authentication;
- fake uploads;
- methods that simply throw `NotImplementedException`;
- UI screens disconnected from actual services.

If an integration genuinely cannot be exercised in the available build environment, implement it properly as far as possible and clearly document exactly what could not be runtime-verified.

Never claim an integration was tested if it was not.

# CRITICAL ACCEPTANCE TEST

The target acceptance scenario is:

Launch the application.

Enter:

`Why did floppy disks survive for so long?`

Press:

**MAKE VIDEO**

The application should then perform actual production stages while updating the progress interface.

It should research the subject using multiple sources, produce a research brief, generate a sourced script, plan scenes, generate narration, generate/select visuals, render a portrait video, generate subtitles, perform quality checks and produce a playable MP4.

The user should then be able to preview the video.

If YouTube has been connected through OAuth, the user should be able to press:

**UPLOAD TO YOUTUBE**

and observe genuine upload progress.

The application should finish with:

`COMPLETE`

and provide access to both the local MP4 and, following a successful YouTube upload, the resulting YouTube video.

# DEFINITION OF DONE

This task is NOT complete merely because the Visual Studio solution compiles.

It is complete when the delivered Personal Test Version constitutes a coherent implementation of the attached specification and, after its documented automatic first-run dependency/model installation, requires no additional source code to perform its core workflow.

The user must not be expected to finish the application's programming.

The user must not be expected to manually coordinate the AI programs.

The application itself must orchestrate the production pipeline.

Deliver the complete source tree and final ZIP, together with a concise README explaining:

1. prerequisites;
2. opening/building in Visual Studio;
3. first-run AI setup;
4. model/download storage locations;
5. creating the first video;
6. connecting YouTube;
7. uploading;
8. troubleshooting;
9. which integrations were actually runtime-tested;
10. any genuine remaining environmental limitations.

Above all, optimize the Personal Test Version for this experience:

**OPEN → SET UP AUTOMATICALLY → TYPE AN IDEA → MAKE VIDEO → WATCH → UPLOAD.**