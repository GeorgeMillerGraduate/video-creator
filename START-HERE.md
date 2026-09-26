# Jenga-Code EduVideo Studio 3.2

Extract this ZIP into a new folder, open its `pom.xml` project in NetBeans, and run **Clean and Build**. Run Project launches the Studio. Alternatively:

```bat
mvn clean install
mvn javafx:run
```

Your OpenAI and FFmpeg preferences remain in your existing user configuration. No Python, Piper or eSpeak is required.

## Try the new complete production

Open `examples/production/fourier-course/fourier-course.xml` in Studio. It combines five independently editable lessons into an 80-second course. Use **Production parts** to reorder, add, remove, preview or render chapters. Click **Render video** for the whole production.

**File → New production** creates a manifest from selected existing lesson XML files. They must use the same source width, height and FPS. Edit the ordinary XML in **XML source**; save with Ctrl+S. To create a new chapter, use the existing **New project** wizard first.

Global music is defined once in the manifest and continues across chapter boundaries. **Global music** in the Production parts tab selects a whole-production looping bed; the XML exposes gain, fades and optional start/end limits.

The window opens normally maximized. Drag horizontal/vertical dividers to size the library, preview, inspector, timeline and lower tabs. Starting a render no longer moves your dividers. The preview offers Fit, 50%, 75%, 100%, 125%, 150% and the existing 200% mode.

See `docs/MULTIPART-UPGRADE.md` for the format, class changes, tested behavior and limitations. `verification/` contains actual screenshots and build/test results. `verification/fourier-complete-preview.mp4` is a real 80-second render with music; narration was deliberately disabled for this verification file. The supplied XML contains narration for your configured OpenAI voice.
