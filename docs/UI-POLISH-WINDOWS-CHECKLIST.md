# Windows verification — 0.2 visual polish

Keep a copy of your current working source folder before replacing it. Open the
updated solution in Visual Studio, stop debugging, then Clean and Rebuild.
No project-data migration is required.

1. Build the whole solution and run existing tests. Launch with an existing saved
   project to exercise project-card bindings. No Duration or Status read-only
   writeback exceptions should occur.
2. Inspect Home, Create, Projects, Getting Started, Production, Review, Export,
   Manage Components and expanded Advanced Setup. Check for white workspace
   backgrounds, unreadable text or XAML binding errors in Visual Studio Output.
3. At 100%, 125% and 150% display scaling, resize the window through its supported
   size range. Confirm dashboard/cards wrap, vertical scrolling works and all
   actions remain reachable. Check short windows particularly carefully in Review.
4. Tab through navigation, buttons, dropdowns, checkboxes, expanders and the player
   slider. Verify focus, hover, selected and disabled states. Verify editable model
   selection still accepts custom text and dropdowns scroll with keyboard/mouse.
5. Open an existing project; edit/save a scene and metadata; reopen it. Check the
   existing unsaved-change prompt when switching projects. Confirm preview play,
   pause, stop and seeking; both scene and full-video playback should still work.
6. Run a short Light-quality production using configured components. Check measured
   progress and indeterminate pulses, cancellation/resume, scene selection and logs.
   Verify that switching back from indeterminate to measured progress stops pulsing.
7. Run component diagnostics and a narration test. Inspect every Advanced Setup
   field and existing browse/install/connect action. Do not infer readiness from
   colour alone: check the displayed status text and actual operation result.
8. Open the rendered MP4 and folder. Inspect all YouTube fields, privacy, audience,
   disclosure and review controls. Upload only if you intend to publish/test it.

These are manual checks to perform on Windows; they were not run in the build
container. A passing XML/source check cannot establish correct WPF runtime layout.
