using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using JengaVideoStudio.Core;

namespace JengaVideoStudio.App;

public sealed partial class StudioViewModel
{
    // ========================================================================
    // PRESENTATION STATE
    // ========================================================================

    private StudioPage page =
        StudioPage.Home;

    private bool advancedSetup;
    private bool producing;
    private bool dirty;
    private bool hasLiveProgress;

    private string savedFingerprint =
        "";

    private string saveStatus =
        "No project open";

    private string narrationTestIdentity =
        "";

    private string componentSignature =
        "";

    private string diagnosedConfiguration =
        "";

    private readonly Stopwatch productionClock =
        new();

    private readonly CancellationTokenSource lifetime =
        new();

    private readonly SemaphoreSlim thumbnailGate =
        new(1);

    private readonly HashSet<string> thumbnailRequests =
        [];

    private DispatcherTimer? uiTimer;

    private SceneCard? selectedCard;

    // ========================================================================
    // COLLECTIONS
    // ========================================================================

    public ObservableCollection<ProjectCard> ProjectCards { get; } =
        [];

    public ObservableCollection<SceneCard> SceneCards { get; } =
        [];

    public ObservableCollection<ComponentCard> Components { get; } =
    [
        new(
            "Local AI",
            "Research notes and script writing"),

        new(
            "Video engine",
            "Rendering and video validation"),

        new(
            "Narration",
            "Voiceover on this computer"),

        new(
            "Image generation",
            "Optional illustrative scenes"),

        new(
            "Video generation",
            "Optional generated clips"),

        new(
            "YouTube",
            "Optional publishing connection")
    ];

    public ObservableCollection<PipelineStep> PipelineSteps { get; } =
    [
        new("Research"),
        new("Evidence"),
        new("Script"),
        new("Scenes"),
        new("Narration"),
        new("Visuals"),
        new("Render"),
        new("Quality")
    ];

    // ========================================================================
    // NAVIGATION
    // ========================================================================

    public StudioPage Page
    {
        get => page;

        set
        {
            if (page == value)
                return;

            if (page == StudioPage.Review)
            {
                ReleasePreview?.Invoke();
            }

            Set(ref page, value);

            NotifyNavigationProperties();
        }
    }

    public int PageIndex
    {
        get => (int)Page;

        set
        {
            if (Enum.IsDefined(
                    typeof(StudioPage),
                    value))
            {
                Page =
                    (StudioPage)value;
            }
        }
    }

    public bool IsHome =>
        Page == StudioPage.Home;

    public bool IsCreate =>
        Page == StudioPage.Create;

    public bool IsProjects =>
        Page == StudioPage.Projects;

    public bool IsProduction =>
        Page == StudioPage.Production;

    public bool IsReview =>
        Page == StudioPage.Review;

    public bool IsExport =>
        Page == StudioPage.Export;

    public bool IsSettings =>
        Page == StudioPage.Settings;

    public bool IsHelp =>
        Page == StudioPage.Help;

    public bool IsProjectWorkspace =>
        IsCreate ||
        IsProduction ||
        IsReview ||
        IsExport;

    public string PageTitle =>
        Page == StudioPage.Settings
            ? "Manage components"
            : Page.ToString();

    private void NotifyNavigationProperties()
    {
        Notify(nameof(IsHome));
        Notify(nameof(IsCreate));
        Notify(nameof(IsProjects));
        Notify(nameof(IsProduction));
        Notify(nameof(IsReview));
        Notify(nameof(IsExport));
        Notify(nameof(IsSettings));
        Notify(nameof(IsHelp));
        Notify(nameof(IsProjectWorkspace));
        Notify(nameof(PageTitle));
        Notify(nameof(PageIndex));
        Notify(nameof(LiveActivityTitle));
        Notify(nameof(ProductionJournalEmptyText));
        Notify(nameof(HasProductionJournal));
    }

    // ========================================================================
    // SETUP PRESENTATION
    // ========================================================================

    public bool AdvancedSetup
    {
        get => advancedSetup;
        set => Set(ref advancedSetup, value);
    }

    // ========================================================================
    // CURRENT PROJECT PRESENTATION
    // ========================================================================

    public string CurrentTitle
    {
        get
        {
            if (Current == null)
                return "No project open";

            if (!string.IsNullOrWhiteSpace(
                    Current.Metadata.Title))
            {
                return Current.Metadata.Title;
            }

            return string.IsNullOrWhiteSpace(
                Current.Idea)
                ? "Untitled project"
                : Current.Idea;
        }
    }

    public string CurrentSummary
    {
        get
        {
            if (Current == null)
                return "";

            var count =
                Current.Scenes.Count;

            var durationAvailable =
                count > 0 &&
                Current.Scenes.All(
                    s =>
                        double.IsFinite(s.Duration) &&
                        s.Duration > 0);

            var duration =
                durationAvailable
                    ? PresentationFiles.Time(
                        Current.Scenes.Sum(
                            s => s.Duration))
                    : "Not timed yet";

            return
                $"{duration}  ·  {count} " +
                (count == 1 ? "scene" : "scenes");
        }
    }

    public bool HasVideo
    {
        get
        {
            if (Current == null ||
                string.IsNullOrWhiteSpace(
                    Current.FinalVideo))
            {
                return false;
            }

            try
            {
                return File.Exists(
                    Current.PathFor(
                        Current.FinalVideo));
            }
            catch
            {
                return false;
            }
        }
    }

    public bool HasScenes =>
        SceneCards.Count > 0;

    public bool HasProjects =>
        ProjectCards.Count > 0;

    public bool HasSuggestions =>
        Suggestions.Count > 0;

    // ========================================================================
    // PROGRESS
    // ========================================================================

    /// <summary>
    /// True when detailed production telemetry is available for the journal.
    /// </summary>
    public bool HasProductionJournal =>
        ProductionJournal.Count > 0;

    /// <summary>
    /// Friendly heading for the live activity panel.
    /// </summary>
    public string LiveActivityTitle
    {
        get
        {
            if (!Busy)
            {
                return Current?.State switch
                {
                    ProductionState.Failed =>
                        "Production needs attention",

                    ProductionState.Cancelled =>
                        "Production cancelled",

                    ProductionState.ReadyForReview =>
                        "Production complete",

                    ProductionState.Uploaded =>
                        "Upload complete",

                    _ =>
                        "Current activity"
                };
            }

            return producing
                ? FriendlyStage(Stage)
                : "Current activity";
        }
    }

    /// <summary>
    /// Short explanatory text shown when the journal has no entries yet.
    /// </summary>
    public string ProductionJournalEmptyText =>
        Busy
            ? "Detailed production activity will appear here as work progresses."
            : "Start or resume production to see detailed activity here.";

    public string ProgressLabel
    {
        get
        {
            /*
             * Busy is authoritative.
             *
             * This prevents an old Indeterminate=true value from displaying
             * "Working..." after an operation has already failed or ended.
             */
            if (!Busy)
            {
                if (Current?.State ==
                    ProductionState.Failed)
                {
                    return "Needs attention";
                }

                if (Current?.State ==
                    ProductionState.Cancelled)
                {
                    return "Cancelled";
                }

                if (Current?.State ==
                    ProductionState.ReadyForReview)
                {
                    return "Complete";
                }

                if (Current?.State ==
                    ProductionState.Uploaded)
                {
                    return "Uploaded";
                }

                return hasLiveProgress
                    ? $"{Math.Clamp(Progress, 0, 100):0}%"
                    : "Saved state";
            }

            if (Indeterminate)
                return "Working…";

            return
                $"{Math.Clamp(Progress, 0, 100):0}%";
        }
    }

    public string StageTitle =>
        FriendlyStage(Stage);

    public string Elapsed
    {
        get
        {
            if (productionClock.Elapsed.TotalSeconds < 1)
                return "";

            return
                "Elapsed " +
                productionClock.Elapsed.ToString(
                    @"hh\:mm\:ss");
        }
    }

    // ========================================================================
    // STORAGE / SAVE STATUS
    // ========================================================================

    public string DiskSpace { get; private set; } =
        "Storage not checked";

    public string SaveStatus
    {
        get => saveStatus;

        private set =>
            Set(
                ref saveStatus,
                value);
    }

    public bool HasUnsavedChanges =>
        dirty;

    public bool NeedsRender
    {
        get
        {
            if (Current == null ||
                Current.Scenes.Count == 0)
            {
                return false;
            }

            try
            {
                return !string.Equals(
                    Current.FinalHash,
                    ProductionPipeline.ContentHash(
                        Current),
                    StringComparison.Ordinal);
            }
            catch
            {
                // If the current content cannot be fingerprinted safely,
                // assume another render is required.
                return true;
            }
        }
    }

    // ========================================================================
    // SELECTED SCENE
    // ========================================================================

    public string SelectedSceneLabel
    {
        get
        {
            if (Current == null ||
                SelectedScene == null)
            {
                return "Select a scene";
            }

            var index =
                Current.Scenes.IndexOf(
                    SelectedScene);

            if (index < 0)
                return "Select a scene";

            return
                $"Scene {index + 1} of {Current.Scenes.Count}";
        }
    }

    public string SelectedSceneDuration
    {
        get
        {
            var duration =
                SelectedScene?.Duration ?? 0;

            return double.IsFinite(duration) &&
                   duration > 0
                ? PresentationFiles.Time(duration)
                : "Timing pending";
        }
    }

    public string PreviewNote
    {
        get
        {
            if (!HasVideo)
                return "No finished render yet.";

            return NeedsRender
                ? "Showing the last render. Your edits need rendering."
                : "Saved video • portrait 9:16";
        }
    }

    public SceneCard? SelectedSceneCard
    {
        get => selectedCard;

        set
        {
            if (ReferenceEquals(
                    selectedCard,
                    value))
            {
                return;
            }

            Set(
                ref selectedCard,
                value);

            if (value != null)
            {
                SelectedScene =
                    value.Model;
            }
        }
    }

    public string SceneNarration
    {
        get =>
            SelectedScene?.Narration ?? "";

        set
        {
            if (SelectedScene == null)
                return;

            value ??= "";

            if (SelectedScene.Narration == value)
                return;

            SelectedScene.Narration =
                value;

            Edited();
        }
    }

    public string SceneHeading
    {
        get =>
            SelectedScene?.Heading ?? "";

        set
        {
            if (SelectedScene == null)
                return;

            value ??= "";

            if (SelectedScene.Heading == value)
                return;

            SelectedScene.Heading =
                value;

            Edited();

            selectedCard?.Refresh();

            Notify(nameof(CurrentTitle));
        }
    }

    public string SceneVisualPrompt
    {
        get =>
            SelectedScene?.VisualPrompt ?? "";

        set
        {
            if (SelectedScene == null)
                return;

            value ??= "";

            if (SelectedScene.VisualPrompt == value)
                return;

            SelectedScene.VisualPrompt =
                value;

            Edited();
        }
    }

    public string SceneVisualType
    {
        get =>
            SelectedScene?.VisualType ??
            "Typography";

        set
        {
            if (SelectedScene == null)
                return;

            value ??= "Typography";

            if (!VisualTypes.Contains(
                    value,
                    StringComparer.Ordinal))
            {
                return;
            }

            if (SelectedScene.VisualType == value)
                return;

            SelectedScene.VisualType =
                value;

            /*
             * Changing visual type invalidates the previously generated
             * visual. Otherwise an old generated image/video could survive
             * after switching back to typography or another visual mode.
             */
            SelectedScene.VisualHash = "";
            SelectedScene.Visual = "";

            Edited();

            selectedCard?.Refresh();
        }
    }

    // ========================================================================
    // PRESENTATION COMMANDS
    // ========================================================================

    public ICommand Navigate { get; private set; } =
        null!;

    public ICommand OpenProjectCard { get; private set; } =
        null!;

    public ICommand RepairComponents { get; private set; } =
        null!;

    public ICommand ViewLog { get; private set; } =
        null!;

    public ICommand PreviewScene { get; private set; } =
        null!;

    public ICommand PreviewFinal { get; private set; } =
        null!;

    public event Action<Uri, double, double?>? PlaybackRequested;

    // ========================================================================
    // INITIALISATION
    // ========================================================================

    private void InitializePresentation(
        bool first)
    {
        Navigate =
            new Command(
                parameter =>
                {
                    if (!Enum.TryParse<StudioPage>(
                            parameter?.ToString(),
                            true,
                            out var target))
                    {
                        return;
                    }

                    if (target is
                            StudioPage.Production
                            or StudioPage.Review
                            or StudioPage.Export
                        &&
                        Current == null)
                    {
                        target =
                            StudioPage.Create;
                    }

                    Page =
                        target;
                });

        OpenProjectCard =
            new Command(
                parameter =>
                {
                    if (parameter is not ProjectCard card ||
                        !Idle)
                    {
                        return;
                    }

                    SelectedRecent =
                        card.Project;

                    if (LoadProject.CanExecute(null))
                    {
                        LoadProject.Execute(null);
                    }
                },
                () =>
                    Idle);

        RepairComponents =
            Async(
                RepairCore);

        ViewLog =
            new Command(
                _ =>
                {
                    if (Current == null)
                        return;

                    var file =
                        Path.Combine(
                            Current.Folder,
                            "logs",
                            "production.log");

                    if (File.Exists(file))
                    {
                        Util.Open(file);
                    }
                    else
                    {
                        Detail =
                            "No production log exists yet.";
                    }
                },
                () =>
                    Current != null);

        PreviewScene =
            new Command(
                _ =>
                    RequestScenePlayback(),
                () =>
                    Idle &&
                    SelectedSceneCard != null &&
                    File.Exists(
                        SelectedSceneCard.ClipPath));

        PreviewFinal =
            new Command(
                _ =>
                {
                    if (!HasVideo ||
                        Current == null)
                    {
                        return;
                    }

                    try
                    {
                        var path =
                            Current.PathFor(
                                Current.FinalVideo);

                        if (File.Exists(path))
                        {
                            PlaybackRequested?.Invoke(
                                new Uri(
                                    path,
                                    UriKind.Absolute),
                                0,
                                null);
                        }
                    }
                    catch
                    {
                        Detail =
                            "The saved video could not be opened.";
                    }
                },
                () =>
                    Idle &&
                    HasVideo);

        Suggestions.CollectionChanged +=
            (_, _) =>
                Notify(
                    nameof(HasSuggestions));

        ProductionJournal.CollectionChanged +=
            (_, _) =>
            {
                Notify(nameof(HasProductionJournal));
                Notify(nameof(ProductionJournalEmptyText));
            };

        uiTimer =
            new DispatcherTimer
            {
                Interval =
                    TimeSpan.FromSeconds(1)
            };

        uiTimer.Tick +=
            (_, _) =>
            {
                if (disposed)
                    return;

                Notify(nameof(Elapsed));
                Notify(nameof(ProgressLabel));
                Notify(nameof(LiveActivityTitle));
                Notify(nameof(ProductionJournalEmptyText));

                if (!Busy)
                {
                    CheckUnsaved();
                }

                var signature =
                    ComponentSignature();

                if (!string.Equals(
                        signature,
                        componentSignature,
                        StringComparison.Ordinal))
                {
                    componentSignature =
                        signature;

                    UpdateComponents();
                }
            };

        uiTimer.Start();

        UpdateComponents();

        Page =
            first
                ? StudioPage.Settings
                : StudioPage.Home;
    }

    // ========================================================================
    // SCENE SELECTION
    // ========================================================================

    private void NotifySceneSelection()
    {
        var card =
            SceneCards.FirstOrDefault(
                c =>
                    ReferenceEquals(
                        c.Model,
                        SelectedScene));

        if (!ReferenceEquals(
                selectedCard,
                card))
        {
            selectedCard =
                card;

            Notify(
                nameof(SelectedSceneCard));
        }

        Notify(nameof(ScenePoints));
        Notify(nameof(SceneNarration));
        Notify(nameof(SceneHeading));
        Notify(nameof(SceneVisualPrompt));
        Notify(nameof(SceneVisualType));
        Notify(nameof(SelectedSceneLabel));
        Notify(nameof(SelectedSceneDuration));

        if (IsReview &&
            Idle &&
            card != null &&
            File.Exists(card.ClipPath))
        {
            PlaybackRequested?.Invoke(
                new Uri(
                    card.ClipPath,
                    UriKind.Absolute),
                0,
                null);
        }

        CommandManager.InvalidateRequerySuggested();
    }

    private void RequestScenePlayback()
    {
        var card =
            SelectedSceneCard;

        if (card == null ||
            !File.Exists(card.ClipPath))
        {
            return;
        }

        PlaybackRequested?.Invoke(
            new Uri(
                card.ClipPath,
                UriKind.Absolute),
            0,
            null);
    }

    // ========================================================================
    // UNSAVED CHANGE TRACKING
    // ========================================================================

    private static string EditableFingerprint(
        Project project)
    {
        return Util.Hash(
            ProductionPipeline.ContentHash(project) +
            Json.Encode(project.Metadata) +
            "|" +
            project.EditorialReviewed);
    }

    private string EditableFingerprint()
    {
        return Current == null
            ? ""
            : EditableFingerprint(Current);
    }

    public void CheckUnsaved()
    {
        if (Current == null)
        {
            if (dirty)
            {
                dirty = false;
                Notify(nameof(HasUnsavedChanges));
            }

            SaveStatus =
                "No project open";

            return;
        }

        if (producing)
            return;

        bool changed;

        try
        {
            changed =
                !string.Equals(
                    EditableFingerprint(),
                    savedFingerprint,
                    StringComparison.Ordinal);
        }
        catch
        {
            SaveStatus =
                "Unable to verify saved state";

            return;
        }

        if (changed != dirty)
        {
            dirty =
                changed;

            Notify(
                nameof(HasUnsavedChanges));
        }

        Notify(nameof(NeedsRender));
        Notify(nameof(PreviewNote));
        Notify(nameof(CurrentTitle));
        Notify(nameof(CurrentSummary));

        if (dirty)
        {
            SaveStatus =
                "Unsaved changes";

            return;
        }

        try
        {
            var file =
                Path.Combine(
                    Current.Folder,
                    "project.json");

            var saved =
                File.Exists(file)
                    ? $"Saved {File.GetLastWriteTime(file):HH:mm:ss}"
                    : "Not saved";

            SaveStatus =
                saved +
                (NeedsRender
                    ? " · Render required"
                    : "");
        }
        catch
        {
            SaveStatus =
                "Saved state unavailable";
        }
    }

    private void Edited()
    {
        CheckUnsaved();

        Notify(nameof(NeedsRender));
        Notify(nameof(PreviewNote));
        Notify(nameof(CurrentSummary));

        CommandManager.InvalidateRequerySuggested();
    }

    private void RememberSaved()
    {
        if (Current == null)
            return;

        try
        {
            var file =
                Path.Combine(
                    Current.Folder,
                    "project.json");

            var saved =
                store.Load(file);

            savedFingerprint =
                EditableFingerprint(saved);

            CheckUnsaved();
        }
        catch (
            Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or InvalidDataException)
        {
            SaveStatus =
                "Saved state could not be verified";
        }
    }

    private async Task SaveCurrent(
        CancellationToken ct = default)
    {
        if (Current == null)
            return;

        SaveStatus =
            "Saving…";

        try
        {
            await store.Save(
                Current,
                ct);

            RememberSaved();
        }
        catch
        {
            SaveStatus =
                "Save failed · Changes remain in memory";

            throw;
        }
    }

    // ========================================================================
    // LEAVING / CLOSING PROJECT
    // ========================================================================

    public async Task<bool> ConfirmProjectChange()
    {
        CheckUnsaved();

        if (!HasUnsavedChanges)
            return true;

        var answer =
            MessageBox.Show(
                "Save your changes before leaving this project?",
                "Unsaved changes",
                MessageBoxButton.YesNoCancel,
                MessageBoxImage.Question);

        if (answer ==
            MessageBoxResult.Cancel)
        {
            return false;
        }

        if (answer ==
            MessageBoxResult.No)
        {
            return true;
        }

        try
        {
            await SaveCurrent();
            return true;
        }
        catch (Exception e)
        {
            MessageBox.Show(
                e.Message,
                "Could not save",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);

            return false;
        }
    }

    // ========================================================================
    // PROJECT CARDS
    // ========================================================================

    private void RefreshProjectCards()
    {
        ProjectCards.Clear();

        foreach (var project in Recent)
        {
            ProjectCards.Add(
                new ProjectCard(project));
        }

        Notify(nameof(HasProjects));
    }

    // ========================================================================
    // SCENE CARDS
    // ========================================================================

    private void RefreshSceneCards()
    {
        if (Current == null)
        {
            SceneCards.Clear();

            SelectedScene = null;
            SelectedSceneCard = null;

            Notify(nameof(HasScenes));
            Notify(nameof(CurrentSummary));

            return;
        }

        var rebuild =
            SceneCards.Count !=
            Current.Scenes.Count;

        if (!rebuild)
        {
            for (var i = 0;
                 i < SceneCards.Count;
                 i++)
            {
                if (!ReferenceEquals(
                        SceneCards[i].Model,
                        Current.Scenes[i]))
                {
                    rebuild = true;
                    break;
                }
            }
        }

        /*
         * Start positions are immutable in SceneCard, so a narration duration
         * change also requires rebuilding the adapter list.
         */
        if (!rebuild)
        {
            var expectedStart =
                0d;

            foreach (var card in SceneCards)
            {
                if (Math.Abs(
                        card.Start -
                        expectedStart) > 0.01)
                {
                    rebuild = true;
                    break;
                }

                expectedStart +=
                    SafeDuration(
                        card.Model.Duration);
            }
        }

        if (rebuild)
        {
            SceneCards.Clear();

            var start =
                0d;

            for (var i = 0;
                 i < Current.Scenes.Count;
                 i++)
            {
                var scene =
                    Current.Scenes[i];

                SceneCards.Add(
                    new SceneCard(
                        Current,
                        scene,
                        i,
                        start));

                start +=
                    SafeDuration(
                        scene.Duration);
            }
        }

        foreach (var card in SceneCards)
        {
            card.Refresh();

            QueueThumbnail(card);
        }

        if (SelectedScene == null ||
            !Current.Scenes.Contains(
                SelectedScene))
        {
            SelectedScene =
                Current.Scenes.FirstOrDefault();
        }
        else
        {
            NotifySceneSelection();
        }

        Notify(nameof(HasScenes));
        Notify(nameof(CurrentSummary));
        Notify(nameof(CurrentTitle));
        Notify(nameof(SelectedSceneDuration));
    }

    private static double SafeDuration(
        double value)
    {
        return double.IsFinite(value) &&
               value > 0
            ? value
            : 0;
    }

    // ========================================================================
    // THUMBNAILS
    // ========================================================================

    private async void QueueThumbnail(
        SceneCard card)
    {
        if (disposed ||
            !File.Exists(card.ClipPath))
        {
            return;
        }

        string key;

        try
        {
            key =
                Util.Hash(
                    card.ClipPath +
                    "|" +
                    File.GetLastWriteTimeUtc(
                        card.ClipPath).Ticks);
        }
        catch
        {
            return;
        }

        if (!thumbnailRequests.Add(key))
            return;

        var acquired =
            false;

        try
        {
            await thumbnailGate.WaitAsync(
                lifetime.Token);

            acquired = true;

            if (disposed ||
                lifetime.IsCancellationRequested)
            {
                return;
            }

            var folder =
                Path.Combine(
                    Core.Settings.DataRoot,
                    "ui-thumbnails");

            Directory.CreateDirectory(folder);

            var output =
                Path.Combine(
                    folder,
                    key + ".png");

            if (!File.Exists(output))
            {
                var partial =
                    output + ".partial.png";

                TryDeleteFile(partial);

                await ChildProcess.Run(
                    Settings.Ffmpeg,
                    [
                        "-y",
                        "-v", "error",
                        "-i", card.ClipPath,
                        "-frames:v", "1",
                        "-vf", "scale=320:-1",
                        partial
                    ],
                    null,
                    null,
                    TimeSpan.FromSeconds(30),
                    lifetime.Token);

                if (!File.Exists(partial) ||
                    new FileInfo(partial).Length < 100)
                {
                    TryDeleteFile(partial);

                    throw new InvalidDataException(
                        "FFmpeg did not create a usable thumbnail.");
                }

                File.Move(
                    partial,
                    output,
                    true);
            }

            if (disposed)
                return;

            var image =
                PresentationFiles.Image(
                    output);

            if (image != null)
            {
                card.SetThumbnail(image);
            }
        }
        catch (OperationCanceledException)
            when (lifetime.IsCancellationRequested)
        {
            // Application is closing.
        }
        catch
        {
            /*
             * Thumbnail creation is presentation-only and must never make
             * production or project loading fail.
             *
             * Removing the key permits a later retry.
             */
            thumbnailRequests.Remove(key);
        }
        finally
        {
            if (acquired)
            {
                try
                {
                    thumbnailGate.Release();
                }
                catch (ObjectDisposedException)
                {
                }
            }
        }
    }

    private static void TryDeleteFile(
        string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch
        {
            // Best-effort presentation cache cleanup.
        }
    }

    // ========================================================================
    // FOLLOW CURRENT SCENE
    // ========================================================================

    private void FollowCurrentScene(
        string detail)
    {
        if (string.IsNullOrWhiteSpace(detail))
            return;

        var match =
            Regex.Match(
                detail,
                @"(?:Narration|Visual|Scene)\s+(\d+)\s+of",
                RegexOptions.IgnoreCase |
                RegexOptions.CultureInvariant);

        if (!match.Success)
            return;

        if (!int.TryParse(
                match.Groups[1].Value,
                out var number))
        {
            return;
        }

        if (number <= 0 ||
            number > SceneCards.Count)
        {
            return;
        }

        SelectedSceneCard =
            SceneCards[number - 1];
    }

    // ========================================================================
    // PIPELINE PRESENTATION
    // ========================================================================

    private void RefreshPipeline()
    {
        if (Current == null)
        {
            foreach (var step in PipelineSteps)
            {
                step.Update("Pending");
            }

            return;
        }

        string[] stages =
        [
            "Researching",
            "AnalysingSources",
            "WritingScript",
            "PlanningScenes",
            "GeneratingNarration",
            "GeneratingVisuals",
            "Rendering",
            "QualityChecking"
        ];

        /*
         * A pipeline step can be shown as Working only while production is
         * genuinely active. Stage text alone is never enough.
         */
        var currentIndex =
            producing &&
            Busy
                ? Array.IndexOf(
                    stages,
                    Stage)
                : -1;

        var scenesExist =
            Current.Scenes.Count > 0;

        var researchComplete =
            Current.Sources.Count >= 2;

        var evidenceComplete =
            Current.Brief != null &&
            Current.Brief.Claims.Count >= 3;

        var scriptComplete =
            scenesExist &&
            Current.Scenes.Any(
                s =>
                    !string.IsNullOrWhiteSpace(
                        s.Narration));

        var scenesComplete =
            scenesExist;

        var narrationComplete =
            scenesExist &&
            Current.Scenes.All(
                SceneHasNarration);

        var visualsComplete =
            scenesExist &&
            Current.Scenes.All(
                SceneHasVisualState);

        var renderComplete =
            HasVideo &&
            !NeedsRender;

        var qualityComplete =
            Current.State is
                ProductionState.ReadyForReview
                or ProductionState.Uploading
                or ProductionState.Uploaded
            &&
            renderComplete;

        bool[] evidence =
        [
            researchComplete,
            evidenceComplete,
            scriptComplete,
            scenesComplete,
            narrationComplete,
            visualsComplete,
            renderComplete,
            qualityComplete
        ];

        for (var i = 0;
             i < PipelineSteps.Count;
             i++)
        {
            string state;

            if (i == currentIndex)
            {
                state =
                    "Working";
            }
            else if (evidence[i])
            {
                state =
                    "Complete";
            }
            else if (currentIndex >= 0 &&
                     i < currentIndex)
            {
                state =
                    "Complete";
            }
            else
            {
                state =
                    "Pending";
            }

            PipelineSteps[i].Update(
                state);
        }
    }

    private bool SceneHasNarration(
        Scene scene)
    {
        if (string.IsNullOrWhiteSpace(
                scene.AudioHash)
            ||
            string.IsNullOrWhiteSpace(
                scene.Audio))
        {
            return false;
        }

        try
        {
            return File.Exists(
                Current!.PathFor(
                    scene.Audio));
        }
        catch
        {
            return false;
        }
    }

    private static bool SceneHasVisualState(
        Scene scene)
    {
        /*
         * Deterministic graphics legitimately have no external Visual file.
         * VisualHash is the pipeline checkpoint indicating that visual
         * preparation for the scene has completed.
         */
        return !string.IsNullOrWhiteSpace(
            scene.VisualHash);
    }

    // ========================================================================
    // FRIENDLY STAGE NAMES
    // ========================================================================

    private static string FriendlyStage(
        string stage)
    {
        return stage switch
        {
            "Researching" =>
                "Researching your story",

            "AnalysingSources" =>
                "Checking the evidence",

            "WritingScript" =>
                "Writing your script",

            "PlanningScenes" =>
                "Planning the scenes",

            "GeneratingNarration" =>
                "Creating narration",

            "GeneratingVisuals" =>
                "Creating visuals",

            "Rendering" =>
                "Rendering your video",

            "QualityChecking" =>
                "Checking the finished video",

            "ReadyForReview" =>
                "Ready for your review",

            "Uploading" =>
                "Uploading to YouTube",

            "Uploaded" =>
                "Upload complete",

            "Failed" or "Needs attention" =>
                "Needs attention",

            "Cancelled" =>
                "Cancelled",

            "Welcome" =>
                "Welcome",

            "Ollama" or "OLLAMA" =>
                "Local AI",

            "Evidence" or "EVIDENCE" =>
                "Checking the evidence",

            "Script" or "SCRIPT" =>
                "Writing your script",

            "Research" or "RESEARCH" =>
                "Researching your story",

            "Narration" or "NARRATION" =>
                "Creating narration",

            "Visuals" or "VISUALS" =>
                "Creating visuals",

            "Render" or "RENDER" =>
                "Rendering your video",

            "Quality" or "QUALITY" =>
                "Checking the finished video",

            _ =>
                string.IsNullOrWhiteSpace(stage)
                    ? "Jenga Video Studio"
                    : stage
        };
    }

    // ========================================================================
    // COMPONENT STATUS
    // ========================================================================

    private string ConfigurationSignature()
    {
        return string.Join(
            "|",
            Settings.OllamaEndpoint,
            Settings.Model,
            Settings.Ffmpeg,
            Settings.Ffprobe);
    }

    private string ComponentSignature()
    {
        return string.Join(
            "|",
            Settings.OllamaEndpoint,
            Settings.Model,
            Settings.CpuOnly,
            Settings.SpeechProvider,
            Settings.WindowsVoice,
            Settings.PiperVoice,
            Settings.PiperPython,
            Settings.EnableImages,
            Settings.EnableVideo,
            Settings.ComfyEndpoint,
            Settings.ImageWorkflow,
            Settings.VideoWorkflow,
            Settings.Ffmpeg,
            Settings.Ffprobe);
    }

    private void UpdateComponents()
    {
        if (Components.Count < 6)
            return;

        var fresh =
            string.Equals(
                diagnosedConfiguration,
                ConfigurationSignature(),
                StringComparison.Ordinal);

        // --------------------------------------------------------------------
        // LOCAL AI
        // --------------------------------------------------------------------

        var aiChecked =
            fresh &&
            ContainsDiagnostic(
                "Local AI:");

        var aiReady =
            aiChecked &&
            ContainsDiagnostic(
                "Local AI: READY");

        Components[0].Update(
            !aiChecked
                ? "Not checked"
                : aiReady
                    ? "Ready"
                    : "Needs setup",

            !aiChecked
                ? "Check this computer to find your writing model."
                : aiReady
                    ? Settings.CpuOnly
                        ? "Selected writing model found. CPU-only mode is enabled."
                        : "Selected writing model found."
                    : "Install or start the local writing engine.",

            !aiChecked
                ? "Quiet"
                : aiReady
                    ? "Ready"
                    : "Attention");

        // --------------------------------------------------------------------
        // VIDEO ENGINE
        // --------------------------------------------------------------------

        var ffmpegReady =
            fresh &&
            ContainsDiagnostic(
                "READY — ffmpeg version");

        var ffprobeReady =
            fresh &&
            ContainsDiagnostic(
                "READY — ffprobe version");

        var videoReady =
            ffmpegReady &&
            ffprobeReady;

        var videoChecked =
            fresh &&
            (ContainsDiagnostic("ffmpeg") ||
             ContainsDiagnostic("ffprobe"));

        Components[1].Update(
            videoReady
                ? "Ready"
                : videoChecked
                    ? "Needs setup"
                    : "Not checked",

            videoReady
                ? "Encoder and validator responded."
                : "Install or locate the video engine.",

            videoReady
                ? "Ready"
                : videoChecked
                    ? "Attention"
                    : "Quiet");

        // --------------------------------------------------------------------
        // NARRATION
        // --------------------------------------------------------------------

        var neural =
            string.Equals(
                Settings.SpeechProvider,
                "Piper",
                StringComparison.OrdinalIgnoreCase);

        var speechConfigured =
            !neural ||
            (File.Exists(Settings.PiperPython) &&
             File.Exists(Settings.PiperVoice));

        var currentSpeechIdentity =
            new LocalSpeech(Settings)
                .Identity;

        var speechTested =
            !string.IsNullOrWhiteSpace(
                narrationTestIdentity)
            &&
            string.Equals(
                narrationTestIdentity,
                currentSpeechIdentity,
                StringComparison.Ordinal);

        Components[2].Update(
            speechTested
                ? "Test audio created"
                : speechConfigured
                    ? "Test voice"
                    : "Needs setup",

            neural
                ? "Local neural narration selected."
                : "Windows narration selected. No model download needed.",

            speechTested
                ? "Ready"
                : speechConfigured
                    ? "Quiet"
                    : "Attention");

        // --------------------------------------------------------------------
        // IMAGE GENERATION
        // --------------------------------------------------------------------

        var imageWorkflowExists =
            !string.IsNullOrWhiteSpace(
                Settings.ImageWorkflow)
            &&
            File.Exists(
                Settings.ImageWorkflow);

        Components[3].Update(
            !Settings.EnableImages
                ? "Optional"
                : imageWorkflowExists
                    ? "Configured · Untested"
                    : "Needs workflow",

            "Generated illustrations are optional; " +
            "deterministic graphics work without them.",

            Settings.EnableImages &&
            !imageWorkflowExists
                ? "Attention"
                : "Quiet");

        // --------------------------------------------------------------------
        // VIDEO GENERATION
        // --------------------------------------------------------------------

        var videoWorkflowExists =
            !string.IsNullOrWhiteSpace(
                Settings.VideoWorkflow)
            &&
            File.Exists(
                Settings.VideoWorkflow);

        Components[4].Update(
            !Settings.EnableVideo
                ? "Optional"
                : videoWorkflowExists
                    ? "Configured · Untested"
                    : "Needs workflow",

            "Generated clips depend on your selected local workflow.",

            Settings.EnableVideo &&
            !videoWorkflowExists
                ? "Attention"
                : "Quiet");

        // --------------------------------------------------------------------
        // YOUTUBE
        // --------------------------------------------------------------------

        var connected =
            youtube.StartsWith(
                "Connected:",
                StringComparison.OrdinalIgnoreCase);

        Components[5].Update(
            connected
                ? "Connected"
                : "Not verified",

            connected
                ? youtube
                : "Connect or run a check to verify your channel.",

            connected
                ? "Ready"
                : "Quiet");

        // --------------------------------------------------------------------
        // STORAGE
        // --------------------------------------------------------------------

        DiskSpace =
            ReadDiskSpace();

        Notify(nameof(DiskSpace));
    }

    private bool ContainsDiagnostic(
        string value)
    {
        return diagnostics.Contains(
            value,
            StringComparison.OrdinalIgnoreCase);
    }

    private string ReadDiskSpace()
    {
        try
        {
            if (string.IsNullOrWhiteSpace(
                    Settings.ProjectRoot))
            {
                return "Choose a project folder";
            }

            var full =
                Path.GetFullPath(
                    Settings.ProjectRoot);

            var root =
                Path.GetPathRoot(full);

            if (string.IsNullOrWhiteSpace(root))
                return "Storage unavailable";

            var drive =
                new DriveInfo(root);

            if (!drive.IsReady)
                return "Storage unavailable";

            var gigabytes =
                drive.AvailableFreeSpace /
                1_000_000_000d;

            return
                $"{gigabytes:0.0} GB free";
        }
        catch (
            Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or ArgumentException
                or NotSupportedException)
        {
            return "Storage unavailable";
        }
    }

    // ========================================================================
    // COMPONENT REPAIR
    // ========================================================================

    private async Task RepairCore(
        CancellationToken ct)
    {
        Diagnostics =
            await Setup()
                .Diagnose(ct);

        ct.ThrowIfCancellationRequested();

        if (Components[1].Status ==
            "Needs setup")
        {
            if (Confirm(
                    "Install or repair the video engine?\n\n" +
                    "Approximately 110 MB will be downloaded. " +
                    "Reserve about 1 GB for extraction."))
            {
                await Setup()
                    .InstallFfmpeg(
                        Reporter(),
                        ct);

                Notify(nameof(Settings));

                Diagnostics =
                    await Setup()
                        .Diagnose(ct);
            }
        }

        ct.ThrowIfCancellationRequested();

        if (ContainsDiagnostic(
                "Local AI: MODEL MISSING"))
        {
            var size =
                Settings.Model.Contains(
                    "1.5b",
                    StringComparison.OrdinalIgnoreCase)
                    ? "about 1 GB"
                    : "about 4.7 GB (custom models may differ)";

            if (Confirm(
                    $"Download the selected writing model ({size})?"))
            {
                await Setup()
                    .PullModel(
                        Reporter(),
                        ct);
            }
        }
        else if (
            ContainsDiagnostic(
                "Local AI: MISSING / OFFLINE"))
        {
            AdvancedSetup =
                true;

            if (Confirm(
                    "The local writing engine is not responding.\n\n" +
                    "Open the official Ollama installer page? Install or " +
                    "launch Ollama, then run Install / Repair again."))
            {
                Util.Open(
                    "https://ollama.com/download/windows");
            }
        }

        ct.ThrowIfCancellationRequested();

        Diagnostics =
            await Setup()
                .Diagnose(ct);

        Detail =
            "Component check finished. Use Test narration to listen " +
            "to your selected voice.";
    }
}