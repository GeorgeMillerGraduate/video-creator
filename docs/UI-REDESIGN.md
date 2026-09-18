# Personal Test 0.2 — interface redesign

The existing Personal Test 0.1 application has been restyled and reorganised in place. The supplied notes report that 0.1 launched on the user's Windows PC. That user report does not constitute a successful build or runtime test of this 0.2 revision.

## What changed

- A compact branded header, permanent sidebar and global Home / Create / Projects / Help navigation replace the four exposed top-level tabs.
- A separate Create → Production → Review → Export strip gives the current project its own workflow context.
- Home introduces the workflow and shows real saved project cards. Project titles, durations, states, save times and available thumbnails come from existing project data and files. Empty project lists contain a genuine empty state, not example projects.
- Create uses a shorter topic field, compact duration/tone/quality controls, secondary idea actions and one dominant Create Video action. Preferred sources remain expandable.
- Production displays the actual progress reports, a stage checklist, scene cards and the selected scene's available visual. The active scene follows existing scene-number progress messages. Logs remain in a secondary expandable journal. Unknown inference progress remains indeterminate; no remaining-time estimate is invented.
- Review is a widescreen editing workspace: prominent uncropped video preview, playback/scrubbing controls, a horizontal scene strip and a right-hand inspector. Selecting a scene updates its original model and loads its saved rendered clip when available.
- The timing overview uses measured scene durations at a fixed scale. It does not fabricate an audio waveform. Untimed scenes are omitted from this duration track and remain visible in the scene strip.
- Scene properties continue to edit the original `Scene` objects. Apply Changes uses the existing project store. Regenerate Visual and Render Final Video use the existing production pipeline.
- Save status distinguishes unsaved edits, successful saves and the need to rerender. Switching projects, starting a new project or closing checks for unsaved changes. Saved state is compared with the actual project JSON.
- Export groups the finished MP4, project folder, quality notes and all existing YouTube metadata, disclosure, privacy and upload actions.
- Manage Components displays friendly status cards and a guided Install / Repair action. The full original technical setup form is preserved inside Advanced Setup, including models, CPU option, component paths, voice controls, workflows, OAuth client selection and diagnostics.
- Shared WPF resources define navy surfaces, aqua accents, typography, inputs, dark dropdowns, buttons, navigation, cards and status indicators.

## Files changed

- `src/JengaVideoStudio.App/App.xaml`: loads shared theme resources.
- `src/JengaVideoStudio.App/MainWindow.xaml` and `.xaml.cs`: application shell, navigation host, work-area sizing and close/save handling.
- `src/JengaVideoStudio.App/StudioViewModel.cs`: preserves existing commands and services, adds presentation refresh hooks and connects the previous stage index to the new navigation.
- `src/JengaVideoStudio.App/StudioViewModel.Presentation.cs` (new): navigation, display adapters, save-state feedback, friendly component statuses, stage display and thumbnail coordination.
- `src/JengaVideoStudio.App/PresentationModels.cs` (new): small project/scene/component/stage display types. Scene adapters reference the original model; they do not create another editable scene system.
- `src/JengaVideoStudio.App/Themes/StudioTheme.xaml` (new): reusable WPF visual resources.
- `src/JengaVideoStudio.App/Views/`: Home, Create, Projects, Production, Review, Export, Settings and Help views. Only Review contains playback-specific code; other view code-behind only initialises XAML.
- `scripts/verify_ui_structure.py` (new): portable static regression checks.
- Updated README, setup guide, implementation status, verification evidence and this change note; added manual UI acceptance checklist.

## What was preserved

All files in Core, existing tests, prompts and workflow folders match their pre-redesign SHA-256 hashes. The research, AI, speech, visual generation, rendering, upload, protected-token storage and project serialization contracts have not been replaced. Every original command remains exposed directly or through a navigation/card wrapper. Every original advanced Settings binding remains present.

`LoadProject` is now invoked by each project's Open action. `OpenVideo` and upload controls moved to Export. No project schema migration or model-format change is required. Existing project and settings storage locations are unchanged.

The only additional media operation is optional UI thumbnail extraction through the already-configured FFmpeg executable. It writes to `%LOCALAPPDATA%\JengaVideoStudio\ui-thumbnails`, not to the production media. If extraction fails, the UI keeps a labelled graphic/empty preview rather than blocking the project.

## Deliberate differences from the references

The concept images contain example projects, cinematic thumbnails, a signed-in George avatar, waveforms, account menus and unsupported editing buttons. None is used as fabricated live data. No new login/account system, duplicate/delete/split/move scene operations, AI narration-regeneration command, full-screen editor or professional multitrack editor was added. Existing narration changes take effect through rerendering. Video frames remain 9:16 with letterboxing in the preview rather than being stretched into the mockup's landscape rectangle.

The installer architecture is unchanged. Install / Repair checks components, invokes existing download services with approval, and guides users to the existing runtime installer when needed; it is not a new silent universal installer. Optional visual workflows display Configured / Untested, not Ready, until separately tested. Windows narration is not claimed audible just because it is selected; a successful test creates an actual audio file for listening.

## Verification

Passed here: all XAML parses as XML; named resources resolve statically; markup event handlers and command names exist; workspace order agrees with the navigation enum; original command routes and advanced settings remain reachable; protected source files match their baseline hashes.

Attempted but blocked: Release solution build and existing C# test execution. Both commands failed because `dotnet` is not installed in this workspace. No successful C# compilation, WPF runtime render, live binding evaluation, optional-component integration test or Windows DPI check is claimed for 0.2.

The previous media-recipe evidence remains in the package. It belongs to the earlier unchanged renderer and is not a screenshot or runtime test of the redesigned WPF interface. Run `scripts/Verify-Windows.ps1` and `docs/UI-WINDOWS-CHECKLIST.md` before treating this revision as verified.
