# Personal Test 0.2 — dark interface polish

This is an update to the existing 0.2 application. All C# files, backend services,
models, project formats, ViewModels, commands, playback code and existing tests
are unchanged. Licensing and trial limits remain deferred.

## Changes

- `Themes/Palette.xaml` centralises semantic colours, gradients, vector icons and
  spacing tokens. Existing brush names remain available for compatibility.
- `MainWindow.xaml` explicitly sets the window and workspace background and text
  colours. This removes reliance on implicit base-Window style lookup for the
  derived MainWindow. Every view also declares its background and foreground.
- `StudioTheme.xaml` gives ordinary text and keyed headings explicit foregrounds.
  Display-only bindings are one-way, including the duration/status bindings that
  caused startup exceptions. The project card now uses ordinary TextBlocks.
- Shared dark templates cover buttons, navigation, workflow controls, input
  fields, dropdowns, checkboxes, expanders, scrollbars, playback slider, lists and
  progress bars. Unknown progress pulses rather than presenting a made-up number.
- Home has a bundled vector/gradient hero, explanatory icons, a wrapping dashboard,
  real recent-project cards, a continuation card for the currently open project,
  quick actions and compact setup guidance. No external image request is needed.
- Projects uses wrapping, bounded cards. Getting Started has four wrapping cards.
  Components uses compact wrapping cards; all advanced controls remain present.
- Production, Create, Review and Export use the same semantic palette. The
  Production side panel now scrolls in short windows. Review retains its player,
  scene strip, measured timeline and inspector. Export retains all upload fields.
- Component colours reflect existing Ready/Attention/Quiet state; unknown state
  remains unknown. No fabricated readiness, progress, thumbnails or timestamps.

The Home continuation card shows the currently open project, not an invented
last-opened timestamp. Recent projects retain actual saved timestamps. Additional
per-component installation buttons were not invented; the existing guided repair,
voice test, connection and advanced setup commands remain available.

## Reference scope

Used the supplied final-polish text and the existing Home/Production and Review
reference assets under `reference/ui-redesign`. The latest attachment contained
text only; a newer dark-dashboard concept and current screenshots were not
attached in this turn. Its described palette/layout direction is implemented.

## Verification and limits

- `scripts/verify_ui_structure.py`: XML, resources, command reachability, settings,
  routes and original backend preservation checks passed.
- `scripts/verify_ui_polish.py`: explicit root styling, safe display bindings,
  palette contrast and byte-identical C# / existing test checks passed.
- Contrast checks cover normal primary, secondary, muted, status and hero text,
  plus primary button text. They are palette calculations, not a complete rendered
  accessibility audit. Disabled controls and native Windows dialogs are separate.
- `dotnet build JengaVideoStudio.sln` and `dotnet test JengaVideoStudio.sln` could
  not run: this environment has no dotnet executable. No Windows launch, live
  binding evaluation, playback test or rendered screenshot inspection was possible.
- Earlier FFmpeg recipe evidence concerns the unchanged backend recipe only.

This package is ready for Windows testing, not certified as runtime-verified.
See `UI-POLISH-WINDOWS-CHECKLIST.md` before treating the visual work as signed off.
