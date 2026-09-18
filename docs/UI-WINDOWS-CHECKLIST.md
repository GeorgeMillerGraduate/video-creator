# UI redesign — Windows acceptance

Open the updated solution and build Debug and Release. Run the existing `scripts/Verify-Windows.ps1`. Then test these interactions on a copy of an existing project, retaining the original folder as a backup.

- [ ] Home opens on subsequent launches; first launch opens Manage Components.
- [ ] All four project stages are navigable, while Home/Projects/Help remain separate global pages.
- [ ] An empty project folder shows no example projects or made-up timestamps.
- [ ] Existing project.json files load without migration, with correct title, last-save time and measured duration.
- [ ] Missing optional components do not prevent opening a project, editing scenes or using Light production.
- [ ] Every original Advanced Setup control is reachable, editable and saved.
- [ ] Component cards initially say Not checked / Optional / Test voice; a component check does not falsely mark an unavailable dependency Ready.
- [ ] Install / Repair invokes the real existing installers/downloads only after their displayed approval; cancellation remains available in the footer.
- [ ] Make Video, suggestion actions, source URLs, cancellation and resume continue working.
- [ ] Progress matches actual stages; inference displays Working rather than a made-up percentage; new scene cards appear during production.
- [ ] Choosing a scene changes narration, heading, treatment, prompt and labels together.
- [ ] A rendered selected scene loads in the player. An unrendered scene does not display an invented generated frame.
- [ ] Play, pause, stop, scrubbing and Full video work. External MP4 playback remains available from Export.
- [ ] Editing a scene shows Unsaved changes. Apply Changes reports Saved only after the project file is written.
- [ ] Saved scene edits show Render required; rendered preview is explicitly labelled as the last render.
- [ ] Switching projects, creating another project and closing offer Save / Discard / Cancel for unsaved edits.
- [ ] Rerender releases the embedded player; thumbnails refresh after the new render.
- [ ] Regenerate Visual preserves unrelated source/audio data; it still calls the original production pipeline.
- [ ] Export retains every title, description, tag, category, privacy, audience and AI disclosure option.
- [ ] YouTube connection, disconnect, upload and returned-link actions retain their previous behavior.
- [ ] Inspect Visual Studio's Output window for WPF binding failures while moving through every page.
- [ ] Check 1920×1080 at 100%, 125% and 150% scaling, plus a 960×620 logical window. Inspect the scrollable inspector, sidebar and Advanced Setup for inaccessible controls.
- [ ] Confirm visible keyboard focus, tab order, dropdown keyboard selection and scene-card keyboard selection.

This checklist is not marked complete by the delivered static verification report. No Windows desktop runtime was available during the redesign.
