# Windows acceptance checklist

All boxes are initially unchecked. Record the PC hardware, runtime/model versions, project folder and any error. Do not mark a step complete just because its source code exists.

- [ ] Build Debug and Release using `scripts/Verify-Windows.ps1`; keep its transcript.
- [ ] C# console assertions pass.
- [ ] WPF opens; tabs, dropdowns, resizing and buttons are usable at your display scaling.
- [ ] Setup detects hardware and shows missing components clearly.
- [ ] Ollama model pull completes; installed digest appears; Test local AI returns useful JSON.
- [ ] FFmpeg checksum check and installed version check complete.
- [ ] Windows voice test speaks; optional Piper voice test speaks.
- [ ] Enter the floppy-disk question, 60 seconds, Light; press Make Video.
- [ ] At least two real source records exist with actual retrieved text and URLs.
- [ ] Claims contain exact source extracts; inspect whether they support the actual narration.
- [ ] Scene timings come from narration WAVs; no clipped phrases or unwanted long silences.
- [ ] MP4 plays with both audio and video; captions are legible and reasonably timed.
- [ ] Thumbnail and SRT exist; final probe and production log exist.
- [ ] Cancel a new production during narration. Reopen and Resume; completed audio is reused.
- [ ] Edit one scene's narration and render; that audio/clip changes, unrelated audio is reused.
- [ ] Change one heading and render; narration stays cached but the overlay changes.
- [ ] Change a scene after rendering; uploading is blocked until rerendered.
- [ ] Optional: configure ComfyUI image workflow; a real generated image is saved and animated.
- [ ] Optional: stop ComfyUI before a selected regeneration; a graphic fallback is reported.
- [ ] Optional: configure a real text-to-video workflow; returned MP4/WebM appears in the scene.
- [ ] Connect YouTube through Google browser consent; verify displayed channel name/ID.
- [ ] Confirm metadata/audience/disclosure and upload as PRIVATE only for the first test.
- [ ] Progress reflects acknowledged bytes; returned video ID is saved; open and play on YouTube.
- [ ] Simulate a network interruption during another upload; retry queries the saved session.
- [ ] Disconnect revokes/removes the token; reconnect works.

Known platform restrictions (such as private-only uploads or expired test consent) should be recorded separately from application bugs. Do not share credential files in test reports.
