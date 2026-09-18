using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using JengaVideoStudio.Core;
using Microsoft.Win32;

namespace JengaVideoStudio.App;

public sealed partial class StudioViewModel :
    INotifyPropertyChanged,
    IDisposable
{
    // ========================================================================
    // SERVICES
    // ========================================================================

    private readonly HttpClient http =
        new(
            new HttpClientHandler
            {
                AllowAutoRedirect = false
            })
        {
            Timeout = TimeSpan.FromMinutes(60)
        };

    private readonly HttpClient downloads =
        new()
        {
            Timeout = TimeSpan.FromMinutes(60)
        };

    private readonly PublicWeb web =
        new();

    private readonly ProjectStore store =
        new();

    private readonly ProtectedCredentials credentials =
        new();

    private CancellationTokenSource? operation;

    private bool disposed;

    // ========================================================================
    // SETTINGS / COLLECTIONS
    // ========================================================================

    public Settings Settings { get; }

    public ObservableCollection<Project> Recent { get; } =
        [];

    public ObservableCollection<string> Suggestions { get; } =
        [];

    public ObservableCollection<string> Events { get; } =
        [];

    /// <summary>
    /// Detailed chronological production telemetry for the bottom journal.
    /// Newest entries are inserted first, matching the existing Events view.
    /// </summary>
    public ObservableCollection<string> ProductionJournal { get; } =
        [];

    private string liveActivity =
        "Waiting for production to start.";

    public string LiveActivity
    {
        get => liveActivity;
        private set => Set(ref liveActivity, value ?? "");
    }

    public string[] Models { get; } =
    [
        "qwen2.5:7b",
        "qwen2.5:1.5b"
    ];

    public string[] Tones { get; } =
    [
        "Clear and curious",
        "Serious documentary",
        "Light humour"
    ];

    public string[] Qualities { get; } =
    [
        "Light",
        "Balanced"
    ];

    public string[] SpeechProviders { get; } =
    [
        "Windows",
        "Piper"
    ];

    public string[] PrivacyOptions { get; } =
    [
        "private",
        "unlisted",
        "public"
    ];

    public string[] VisualTypes { get; } =
    [
        "Typography",
        "Diagram",
        "Timeline",
        "StatisticCard",
        "GeneratedImage",
        "GeneratedVideo"
    ];

    // ========================================================================
    // BACKING FIELDS
    // ========================================================================

    private string idea =
        "Why did floppy disks survive for so long?";

    private string tone =
        "Clear and curious";

    private string quality =
        "Light";

    private string urls =
        "";

    private string detail =
        "Start with Setup, then create your first video.";

    private string stage =
        "Welcome";

    private string diagnostics =
        "Press Check components to inspect this PC.";

    private string youtube =
        "YouTube: not connected";

    private string sourceText =
        "";

    private string warningText =
        "";

    private int seconds =
        60;

    private int tab;

    private double progress;

    private bool busy;

    private bool indeterminate;

    private Project? project;

    private Project? selectedRecent;

    private Scene? scene;

    private string? suggestion;

    private Uri? preview;

    // ========================================================================
    // CREATE
    // ========================================================================

    public string Idea
    {
        get => idea;
        set => Set(ref idea, value);
    }

    public string Tone
    {
        get => tone;
        set => Set(ref tone, value);
    }

    public string Quality
    {
        get => quality;
        set => Set(ref quality, value);
    }

    public string SuppliedUrls
    {
        get => urls;
        set => Set(ref urls, value);
    }

    public int TargetSeconds
    {
        get => seconds;
        set => Set(ref seconds, value);
    }

    // ========================================================================
    // LEGACY TAB ADAPTER
    // ========================================================================

    public int Tab
    {
        get => tab;

        set
        {
            if (!Set(ref tab, value))
                return;

            Page =
                value switch
                {
                    1 => StudioPage.Production,
                    2 => StudioPage.Review,
                    3 => StudioPage.Settings,
                    _ => StudioPage.Create
                };
        }
    }

    // ========================================================================
    // OPERATION STATE
    // ========================================================================

    public bool Busy
    {
        get => busy;

        private set
        {
            if (!Set(ref busy, value))
                return;

            Notify(nameof(Idle));
            Notify(nameof(ProgressLabel));

            CommandManager.InvalidateRequerySuggested();
        }
    }

    public bool Idle =>
        !Busy;

    public bool Indeterminate
    {
        get => indeterminate;

        set
        {
            if (!Set(ref indeterminate, value))
                return;

            Notify(nameof(ProgressLabel));
        }
    }

    public double Progress
    {
        get => progress;

        set
        {
            var safe =
                double.IsFinite(value)
                    ? Math.Clamp(value, 0, 100)
                    : 0;

            if (!Set(ref progress, safe))
                return;

            Notify(nameof(ProgressLabel));
        }
    }

    public string Stage
    {
        get => stage;

        set
        {
            if (!Set(ref stage, value ?? ""))
                return;

            Notify(nameof(StageTitle));
            Notify(nameof(ProgressLabel));
        }
    }

    public string Detail
    {
        get => detail;
        set => Set(ref detail, value ?? "");
    }

    // ========================================================================
    // SETUP / DIAGNOSTICS
    // ========================================================================

    public string Diagnostics
    {
        get => diagnostics;

        set
        {
            if (!Set(ref diagnostics, value ?? ""))
                return;

            diagnosedConfiguration =
                ConfigurationSignature();

            UpdateComponents();
        }
    }

    public string YouTubeStatus
    {
        get => youtube;

        set
        {
            if (!Set(ref youtube, value ?? ""))
                return;

            UpdateComponents();
        }
    }

    // ========================================================================
    // PROJECT DISPLAY
    // ========================================================================

    public string SourceText
    {
        get => sourceText;
        set => Set(ref sourceText, value ?? "");
    }

    public string WarningText
    {
        get => warningText;
        set => Set(ref warningText, value ?? "");
    }

    public Project? Current
    {
        get => project;

        private set
        {
            if (!Set(ref project, value))
                return;

            hasLiveProgress = false;

            Progress = 0;
            Indeterminate = false;
            LiveActivity = "Waiting for production to start.";
            ProductionJournal.Clear();

            scene = null;
            selectedCard = null;

            SceneCards.Clear();

            Notify(nameof(HasProject));
            Notify(nameof(Scenes));
            Notify(nameof(CurrentTitle));
            Notify(nameof(CurrentSummary));
            Notify(nameof(HasVideo));
            Notify(nameof(NeedsRender));
            Notify(nameof(PreviewNote));
        }
    }

    public bool HasProject =>
        Current != null;

    public List<Scene> Scenes =>
        Current?.Scenes ?? [];

    public Scene? SelectedScene
    {
        get => scene;

        set
        {
            if (!Set(ref scene, value))
                return;

            NotifySceneSelection();
        }
    }

    public string ScenePoints
    {
        get =>
            SelectedScene == null
                ? ""
                : string.Join(
                    "\n",
                    SelectedScene.Points);

        set
        {
            if (SelectedScene == null)
                return;

            var points =
                (value ?? "")
                    .Split(
                        '\n',
                        StringSplitOptions.TrimEntries |
                        StringSplitOptions.RemoveEmptyEntries)
                    .ToList();

            if (SelectedScene.Points.SequenceEqual(points))
                return;

            SelectedScene.Points =
                points;

            Edited();
        }
    }

    public Project? SelectedRecent
    {
        get => selectedRecent;
        set => Set(ref selectedRecent, value);
    }

    public string? SelectedSuggestion
    {
        get => suggestion;
        set => Set(ref suggestion, value);
    }

    public Uri? Preview
    {
        get => preview;
        private set => Set(ref preview, value);
    }

    // ========================================================================
    // COMMANDS
    // ========================================================================

    public ICommand Make { get; }

    public ICommand Suggest { get; }

    public ICommand Surprise { get; }

    public ICommand UseIdea { get; }

    public ICommand Cancel { get; }

    public ICommand Resume { get; }

    public ICommand LoadProject { get; }

    public ICommand Regenerate { get; }

    public ICommand OpenFolder { get; }

    public ICommand OpenVideo { get; }

    public ICommand SaveEdits { get; }

    public ICommand Check { get; }

    public ICommand SaveSettings { get; }

    public ICommand InstallFfmpeg { get; }

    public ICommand PullModel { get; }

    public ICommand InstallPiper { get; }

    public ICommand TestNarration { get; }

    public ICommand TestModel { get; }

    public ICommand Browse { get; }

    public ICommand OpenLink { get; }

    public ICommand Connect { get; }

    public ICommand Disconnect { get; }

    public ICommand Upload { get; }

    public ICommand OpenChannel { get; }

    public ICommand OpenYouTubeVideo { get; }

    // ========================================================================
    // PREVIEW
    // ========================================================================

    public event Action? ReleasePreview;

    // ========================================================================
    // CONSTRUCTION
    // ========================================================================

    public StudioViewModel()
    {
        Directory.CreateDirectory(
            Core.Settings.DataRoot);

        var first =
            !File.Exists(
                Core.Settings.FilePath);

        try
        {
            Settings =
                Core.Settings.Load();
        }
        catch (Exception e)
        {
            Settings =
                new Settings();

            MessageBox.Show(
                "Settings could not be loaded. " +
                "Defaults are active.\n\n" +
                e.Message,
                "Jenga Video Studio",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
        }

        // --------------------------------------------------------------------
        // CREATE VIDEO
        // --------------------------------------------------------------------

        Make =
            Async(
                async ct =>
                {
                    if (!await ConfirmProjectChange())
                        return;

                    ValidateNewProject();

                    Current =
                        store.Create(
                            Settings,
                            Idea,
                            TargetSeconds,
                            Tone,
                            Quality,
                            SuppliedUrls);

                    await store.Save(
                        Current,
                        ct);

                    RememberSaved();

                    await Produce(ct);
                });

        // --------------------------------------------------------------------
        // IDEAS
        // --------------------------------------------------------------------

        Suggest =
            Async(
                ct =>
                    SuggestIdeas(
                        ct,
                        false));

        Surprise =
            Async(
                ct =>
                    SuggestIdeas(
                        ct,
                        true));

        UseIdea =
            new Command(
                _ =>
                {
                    if (!string.IsNullOrWhiteSpace(
                            SelectedSuggestion))
                    {
                        Idea =
                            SelectedSuggestion;
                    }
                },
                () =>
                    Idle &&
                    !string.IsNullOrWhiteSpace(
                        SelectedSuggestion));

        // --------------------------------------------------------------------
        // CANCEL
        // --------------------------------------------------------------------

        Cancel =
            new Command(
                _ =>
                {
                    if (operation == null ||
                        operation.IsCancellationRequested)
                    {
                        return;
                    }

                    Detail =
                        "Cancelling safely…";

                    operation.Cancel();
                },
                () =>
                    Busy &&
                    operation != null &&
                    !operation.IsCancellationRequested);

        // --------------------------------------------------------------------
        // RESUME
        // --------------------------------------------------------------------

        Resume =
            Async(
                Produce,
                () =>
                    Current != null);

        // --------------------------------------------------------------------
        // REGENERATE SCENE
        // --------------------------------------------------------------------

        Regenerate =
            Async(
                async ct =>
                {
                    if (SelectedScene == null)
                        return;

                    checked
                    {
                        SelectedScene.Revision++;
                    }

                    // Invalidate only generated media for this revision.
                    SelectedScene.VisualHash = "";
                    SelectedScene.Visual = "";

                    await SaveCurrent(ct);

                    await Produce(ct);
                },
                () =>
                    Current != null &&
                    SelectedScene != null);

        // --------------------------------------------------------------------
        // LOAD PROJECT
        // --------------------------------------------------------------------

        LoadProject =
            Async(
                async ct =>
                {
                    var selected =
                        SelectedRecent;

                    if (selected == null)
                        return;

                    if (!await ConfirmProjectChange())
                        return;

                    ReleasePreview?.Invoke();
                    Preview = null;

                    var projectFile =
                        Path.Combine(
                            selected.Folder,
                            "project.json");

                    Current =
                        store.Load(projectFile);

                    SelectedScene = null;

                    RememberSaved();
                    RefreshProject();
                    RefreshPipeline();

                    Stage =
                        Current.State.ToString();

                    Detail =
                        !string.IsNullOrWhiteSpace(
                            Current.Error)
                            ? Current.Error
                            : "Project loaded from its saved state.";

                    Tab =
                        Current.Scenes.Count > 0
                            ? 2
                            : 1;

                    ct.ThrowIfCancellationRequested();
                },
                () =>
                    SelectedRecent != null);

        // --------------------------------------------------------------------
        // SAVE
        // --------------------------------------------------------------------

        SaveEdits =
            Async(
                async ct =>
                {
                    await SaveCurrent(ct);

                    Detail =
                        "Edits saved. Render again to apply " +
                        "scene changes to the video.";
                },
                () =>
                    Current != null);

        // --------------------------------------------------------------------
        // OPEN FILES
        // --------------------------------------------------------------------

        OpenFolder =
            new Command(
                _ =>
                {
                    if (Current != null &&
                        Directory.Exists(Current.Folder))
                    {
                        Util.Open(
                            Current.Folder);
                    }
                },
                () =>
                    Current != null &&
                    Directory.Exists(Current.Folder));

        OpenVideo =
            new Command(
                _ =>
                {
                    if (TryGetFinalVideoPath(
                            out var path))
                    {
                        Util.Open(path);
                    }
                },
                () =>
                    Idle &&
                    TryGetFinalVideoPath(out _));

        // --------------------------------------------------------------------
        // COMPONENT CHECK
        // --------------------------------------------------------------------

        Check =
            Async(
                async ct =>
                {
                    Diagnostics =
                        await Setup()
                            .Diagnose(ct);

                    string? googleCredential;

                    try
                    {
                        googleCredential =
                            credentials.Read(
                                "google");
                    }
                    catch (Exception e)
                    {
                        YouTubeStatus =
                            "YouTube credentials could not be read: " +
                            e.Message;

                        return;
                    }

                    if (googleCredential == null)
                    {
                        YouTubeStatus =
                            "YouTube: not connected";

                        return;
                    }

                    try
                    {
                        YouTubeStatus =
                            await YouTube()
                                .Channel(ct);
                    }
                    catch (OperationCanceledException)
                    {
                        throw;
                    }
                    catch (Exception e)
                    {
                        YouTubeStatus =
                            "YouTube connection could not be verified: " +
                            e.Message;
                    }
                });

        // --------------------------------------------------------------------
        // SAVE SETTINGS
        // --------------------------------------------------------------------

        SaveSettings =
            Async(
                async ct =>
                {
                    ValidateSettings();

                    await Settings.Save();

                    ct.ThrowIfCancellationRequested();

                    Detail =
                        "Settings saved.";

                    UpdateComponents();
                });

        // --------------------------------------------------------------------
        // OLLAMA MODEL
        // --------------------------------------------------------------------

        PullModel =
            Async(
                async ct =>
                {
                    ValidateSettings();

                    var estimate =
                        Settings.Model.Contains(
                            "1.5b",
                            StringComparison.OrdinalIgnoreCase)
                            ? "about 1 GB"
                            : "about 4.7 GB";

                    if (!Confirm(
                            $"Download {Settings.Model} ({estimate}) " +
                            "using Ollama?\n\n" +
                            "Allow additional disk space and memory for " +
                            "runtime overhead. Existing Ollama layers can " +
                            "be reused."))
                    {
                        return;
                    }

                    await Setup()
                        .PullModel(
                            Reporter(),
                            ct);

                    Diagnostics =
                        await Setup()
                            .Diagnose(ct);
                });

        // --------------------------------------------------------------------
        // FFMPEG
        // --------------------------------------------------------------------

        InstallFfmpeg =
            Async(
                async ct =>
                {
                    if (!Confirm(
                            "Download FFmpeg Essentials from gyan.dev?\n\n" +
                            "Roughly 110 MB will be downloaded. Reserve " +
                            "about 1 GB for download and extraction. " +
                            "The upstream SHA-256 checksum will be verified."))
                    {
                        return;
                    }

                    await Setup()
                        .InstallFfmpeg(
                            Reporter(),
                            ct);

                    Notify(nameof(Settings));

                    Diagnostics =
                        await Setup()
                            .Diagnose(ct);
                });

        // --------------------------------------------------------------------
        // PIPER
        // --------------------------------------------------------------------

        InstallPiper =
            Async(
                async ct =>
                {
                    if (!Confirm(
                            "Install Piper 1.8.0 into a private Python " +
                            "environment and download the Lessac neural " +
                            "voice?\n\n" +
                            "Python 3.11 or 3.12 x64 must already be " +
                            "installed. Reserve about 1 GB."))
                    {
                        return;
                    }

                    await Setup()
                        .InstallPiper(
                            Reporter(),
                            ct);

                    Notify(nameof(Settings));

                    Diagnostics =
                        await Setup()
                            .Diagnose(ct);
                });

        // --------------------------------------------------------------------
        // NARRATION TEST
        // --------------------------------------------------------------------

        TestNarration =
            Async(
                async ct =>
                {
                    ValidateSettings();

                    var speech =
                        new LocalSpeech(Settings);

                    var file =
                        Path.Combine(
                            Core.Settings.DataRoot,
                            "voice-test.wav");

                    await speech.Speak(
                        "Jenga Video Studio is ready to tell a story.",
                        file,
                        ct);

                    narrationTestIdentity =
                        speech.Identity;

                    UpdateComponents();

                    Util.Open(file);

                    Detail =
                        "Narration test created. Check that you can hear it.";
                });

        // --------------------------------------------------------------------
        // MODEL TEST
        // --------------------------------------------------------------------

        TestModel =
            Async(
                async ct =>
                {
                    ValidateSettings();

                    var result =
                        await Llm()
                            .Generate<Ideas>(
                                "ideas",
                                new
                                {
                                    theme = "computing",
                                    previousTopics =
                                        Array.Empty<string>()
                                },
                                ct);

                    if (result.Suggestions.Count == 0)
                    {
                        throw new InvalidDataException(
                            "The local AI returned valid JSON but no ideas.");
                    }

                    Diagnostics =
                        "Local AI structured output test returned:\n" +
                        string.Join(
                            "\n",
                            result.Suggestions);
                });

        // --------------------------------------------------------------------
        // FILE PICKERS
        // --------------------------------------------------------------------

        Browse =
            new Command(
                p =>
                    ChooseFile(
                        p?.ToString() ?? ""),
                () =>
                    Idle);

        OpenLink =
            new Command(
                p =>
                {
                    if (p is not string url)
                        return;

                    if (!Uri.TryCreate(
                            url,
                            UriKind.Absolute,
                            out var uri))
                    {
                        return;
                    }

                    if (!string.Equals(
                            uri.Scheme,
                            Uri.UriSchemeHttps,
                            StringComparison.OrdinalIgnoreCase))
                    {
                        return;
                    }

                    Util.Open(
                        uri.AbsoluteUri);
                });

        // --------------------------------------------------------------------
        // YOUTUBE CONNECTION
        // --------------------------------------------------------------------

        Connect =
            Async(
                async ct =>
                {
                    ValidateSettings();

                    await Settings.Save();

                    await YouTube()
                        .Connect(ct);

                    YouTubeStatus =
                        await YouTube()
                            .Channel(ct);
                });

        Disconnect =
            Async(
                async ct =>
                {
                    await YouTube()
                        .Disconnect(ct);

                    YouTubeStatus =
                        "YouTube: disconnected";
                });

        // --------------------------------------------------------------------
        // YOUTUBE UPLOAD
        // --------------------------------------------------------------------

        Upload =
            Async(
                async ct =>
                {
                    var current =
                        Current
                        ?? throw new InvalidOperationException(
                            "No project is open.");

                    if (!string.IsNullOrWhiteSpace(
                            current.Metadata.VideoId))
                    {
                        throw new InvalidOperationException(
                            "This project was already uploaded. " +
                            "Use Open YouTube video.");
                    }

                    if (!current.EditorialReviewed)
                    {
                        throw new InvalidOperationException(
                            "Confirm that you have reviewed the video, " +
                            "sources and disclosure settings before uploading.");
                    }

                    if (!TryGetFinalVideoPath(
                            out _))
                    {
                        throw new FileNotFoundException(
                            "The finished MP4 could not be found. " +
                            "Render the project before uploading.");
                    }

                    YouTubeStatus =
                        await YouTube()
                            .Channel(ct);

                    if (!Confirm(
                            $"Upload this MP4 to the connected YouTube " +
                            $"account as {current.Metadata.Privacy}?\n\n" +
                            $"Title: {current.Metadata.Title}\n\n" +
                            YouTubeStatus))
                    {
                        return;
                    }

                    Tab = 1;

                    try
                    {
                        await YouTube()
                            .Upload(
                                current,
                                Reporter(),
                                ct);

                        RememberSaved();
                    }
                    catch
                    {
                        // Upload failure must not make a valid rendered video
                        // look like a failed production.
                        current.State =
                            ProductionState.ReadyForReview;

                        await store.Save(
                            current,
                            CancellationToken.None);

                        throw;
                    }

                    RefreshProject();
                    RefreshPipeline();
                },
                () =>
                    Current != null &&
                    TryGetFinalVideoPath(out _));

        // --------------------------------------------------------------------
        // YOUTUBE LINKS
        // --------------------------------------------------------------------

        OpenChannel =
            new Command(
                _ =>
                {
                    if (TryGetYouTubeUrl(
                            Settings.YouTubeChannelUrl,
                            out var uri))
                    {
                        Util.Open(
                            uri.AbsoluteUri);

                        return;
                    }

                    MessageBox.Show(
                        "Enter a valid https://www.youtube.com/... " +
                        "channel URL in Setup.",
                        "Jenga Video Studio",
                        MessageBoxButton.OK,
                        MessageBoxImage.Information);
                });

        OpenYouTubeVideo =
            new Command(
                _ =>
                {
                    var id =
                        Current?.Metadata.VideoId;

                    if (string.IsNullOrWhiteSpace(id))
                        return;

                    Util.Open(
                        "https://www.youtube.com/watch?v=" +
                        Uri.EscapeDataString(id));
                },
                () =>
                    !string.IsNullOrWhiteSpace(
                        Current?.Metadata.VideoId));

        // Presentation partial owns navigation, timers and visual adapters.
        InitializePresentation(first);

        RefreshRecent();
    }

    // ========================================================================
    // ASYNC COMMAND WRAPPER
    // ========================================================================

    private ICommand Async(
        Func<CancellationToken, Task> action,
        Func<bool>? extra = null)
    {
        return new Command(
            async _ =>
                await Run(action),
            () =>
                Idle &&
                (extra?.Invoke() ?? true));
    }

    // ========================================================================
    // OPERATION LIFECYCLE
    // ========================================================================

    private async Task Run(
        Func<CancellationToken, Task> action)
    {
        if (Busy || disposed)
            return;

        Busy = true;

        AddProductionJournalText(
            "SYSTEM",
            "Operation started");

        operation =
            new CancellationTokenSource();

        /*
         * Until a service sends a real ProgressUpdate, the operation is
         * legitimately indeterminate.
         */
        hasLiveProgress = false;
        Progress = 0;
        Indeterminate = true;

        try
        {
            await action(
                operation.Token);
        }
        catch (OperationCanceledException)
            when (operation.IsCancellationRequested)
        {
            Indeterminate = false;

            Stage =
                Current?.State == ProductionState.Cancelled
                    ? "Cancelled"
                    : Stage;

            Detail =
                "Cancelled. Saved stages and completed downloads " +
                "can be resumed.";

            AddEvent(
                "Operation cancelled.");
        }
        catch (TimeoutException e)
        {
            Indeterminate = false;
            Stage = "Needs attention";
            Detail = e.Message;

            AddEvent(
                "Timeout — " + e.Message);

            MessageBox.Show(
                e.Message,
                "Jenga Video Studio",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
        }
        catch (Exception e)
        {
            /*
             * This is the important fix for the stale Working… state.
             *
             * No exception can leave Indeterminate=true.
             */
            Indeterminate = false;

            Stage = "Needs attention";
            Detail = FriendlyException(e);

            AddEvent(
                $"{e.GetType().Name} — {Detail}");

            MessageBox.Show(
                Detail,
                "Jenga Video Studio",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
        }
        finally
        {
            /*
             * Clear progress BEFORE Busy. This prevents bindings from briefly
             * displaying an idle application as Working….
             */
            Indeterminate = false;

            if (Current?.State == ProductionState.Failed)
            {
                Stage = "Needs attention";

                if (!string.IsNullOrWhiteSpace(
                        Current.Error))
                {
                    Detail =
                        Current.Error;
                }
            }
            else if (
                Current?.State == ProductionState.Cancelled)
            {
                Stage = "Cancelled";
            }

            var oldOperation =
                operation;

            operation = null;

            oldOperation?.Dispose();

            Busy = false;

            RefreshRecent();
            RefreshPipeline();

            Notify(nameof(ProgressLabel));
            Notify(nameof(StageTitle));
        }
    }

    // ========================================================================
    // PRODUCTION
    // ========================================================================

    private async Task Produce(
        CancellationToken ct)
    {
        if (Current == null)
            return;

        ValidateSettings();

        await Settings.Save();

        ReleasePreview?.Invoke();
        Preview = null;

        Tab = 1;

        var llm =
            Llm();

        var pipeline =
            new ProductionPipeline(
                store,
                llm,
                new ResearchService(
                    llm,
                    web),
                new LocalSpeech(
                    Settings),
                new ComfyVisuals(
                    Settings,
                    http),
                new FfmpegRenderer(
                    Settings));

        var current =
            Current;

        var reporter =
            Reporter();

        producing = true;

        productionClock.Restart();

        try
        {
            /*
             * Pipeline.Run is already asynchronous. Do not wrap it in
             * Task.Run; doing so adds another scheduling layer and makes
             * cancellation/error ownership harder to reason about.
             */
            await pipeline.Run(
                current,
                reporter,
                ct);

            ct.ThrowIfCancellationRequested();

            if (current.State ==
                ProductionState.ReadyForReview)
            {
                Tab = 2;
            }
        }
        finally
        {
            producing = false;

            productionClock.Stop();

            RememberSaved();
            RefreshProject();
            RefreshPipeline();

            Notify(nameof(Elapsed));
        }
    }

    // ========================================================================
    // IDEA GENERATION
    // ========================================================================

    private async Task SuggestIdeas(
        CancellationToken ct,
        bool choose)
    {
        ValidateSettings();

        var response =
            await Llm()
                .Generate<Ideas>(
                    "ideas",
                    new
                    {
                        theme =
                            string.IsNullOrWhiteSpace(Idea)
                                ? "computing"
                                : Idea.Trim(),

                        previousTopics =
                            Recent
                                .Select(p => p.Idea)
                                .Where(
                                    x =>
                                        !string.IsNullOrWhiteSpace(x))
                                .Take(100)
                                .ToArray()
                    },
                    ct);

        Suggestions.Clear();

        foreach (var item in response.Suggestions
                     .Where(
                         x =>
                             !string.IsNullOrWhiteSpace(x))
                     .Select(x => x.Trim())
                     .Distinct(
                         StringComparer.OrdinalIgnoreCase)
                     .Take(8))
        {
            Suggestions.Add(item);
        }

        if (choose &&
            Suggestions.Count > 0)
        {
            Idea =
                Suggestions[
                    Random.Shared.Next(
                        Suggestions.Count)];
        }
    }

    // ========================================================================
    // PROGRESS REPORTING
    // ========================================================================

    private IProgress<ProgressUpdate> Reporter()
    {
        return new Progress<ProgressUpdate>(
            update =>
            {
                if (disposed)
                    return;

                var incomingStage =
                    update.Stage ?? "";

                var incomingDetail =
                    update.Detail ?? "";

                var changed =
                    !string.Equals(
                        Stage,
                        incomingStage,
                        StringComparison.Ordinal)
                    ||
                    !string.Equals(
                        Detail,
                        incomingDetail,
                        StringComparison.Ordinal);

                hasLiveProgress = true;

                Stage =
                    incomingStage;

                Detail =
                    incomingDetail;

                Progress =
                    update.Percent;

                Indeterminate =
                    update.Indeterminate;

                LiveActivity =
                    BuildLiveActivity(
                        update);

                if (changed)
                {
                    AddProductionJournalEntry(
                        update);

                    AddEvent(
                        $"{FriendlyStage(incomingStage)} — " +
                        incomingDetail);
                }

                Notify(nameof(StageTitle));
                Notify(nameof(ProgressLabel));
                Notify(nameof(LiveActivity));

                if (!producing)
                    return;

                RefreshPipeline();

                if (changed)
                {
                    RefreshSceneCards();

                    FollowCurrentScene(
                        incomingDetail);
                }
            });
    }

    private string BuildLiveActivity(
        ProgressUpdate update)
    {
        var friendlyStage =
            FriendlyStage(
                update.Stage ?? "");

        var detail =
            string.IsNullOrWhiteSpace(
                update.Detail)
                ? "Working…"
                : update.Detail.Trim();

        var progressText =
            update.Indeterminate
                ? "Working"
                : $"{Math.Clamp(update.Percent, 0, 100):0}%";

        var elapsed =
            producing
                ? productionClock.Elapsed
                : TimeSpan.Zero;

        return
            $"{friendlyStage}\n" +
            $"{detail}\n\n" +
            $"STATUS\n" +
            $"{progressText}\n" +
            $"Elapsed {elapsed:hh\\:mm\\:ss}";
    }

    private void AddProductionJournalEntry(
        ProgressUpdate update)
    {
        var stageName =
            FriendlyStage(
                update.Stage ?? "");

        var detail =
            string.IsNullOrWhiteSpace(
                update.Detail)
                ? "Working…"
                : update.Detail.Trim();

        var progressSuffix =
            update.Indeterminate
                ? ""
                : $" · {Math.Clamp(update.Percent, 0, 100):0}%";

        var entry =
            $"{DateTime.Now:HH:mm:ss} · " +
            $"{stageName} — {detail}" +
            progressSuffix;

        // Consecutive identical telemetry is noise, especially when a
        // local model emits frequent streaming updates.
        if (ProductionJournal.Count > 0)
        {
            var previous =
                ProductionJournal[0];

            var separator =
                previous.IndexOf(" · ", StringComparison.Ordinal);

            var previousBody =
                separator >= 0
                    ? previous[(separator + 3)..]
                    : previous;

            var currentSeparator =
                entry.IndexOf(" · ", StringComparison.Ordinal);

            var currentBody =
                currentSeparator >= 0
                    ? entry[(currentSeparator + 3)..]
                    : entry;

            if (string.Equals(
                    previousBody,
                    currentBody,
                    StringComparison.Ordinal))
            {
                return;
            }
        }

        ProductionJournal.Insert(
            0,
            entry);

        // Detailed telemetry can be much noisier than the general event
        // stream. 500 entries is enough for a long production run while
        // keeping the WPF collection bounded.
        while (ProductionJournal.Count > 500)
        {
            ProductionJournal.RemoveAt(
                ProductionJournal.Count - 1);
        }
    }

    // ========================================================================
    // PROJECT REFRESH
    // ========================================================================

    private void RefreshProject()
    {
        Notify(nameof(Current));
        Notify(nameof(Scenes));
        Notify(nameof(HasProject));

        if (Current == null)
        {
            SourceText = "";
            WarningText = "";
            Preview = null;
            return;
        }

        if (SelectedScene == null ||
            !Current.Scenes.Contains(
                SelectedScene))
        {
            SelectedScene =
                Current.Scenes.FirstOrDefault();
        }

        RefreshSceneCards();

        Notify(nameof(CurrentSummary));
        Notify(nameof(CurrentTitle));
        Notify(nameof(HasVideo));
        Notify(nameof(NeedsRender));
        Notify(nameof(PreviewNote));

        SourceText =
            BuildSourceText(Current);

        WarningText =
            string.Join(
                "\n\n",
                Current.Warnings);

        Preview = null;

        if (TryGetFinalVideoPath(
                out var finalVideo))
        {
            Preview =
                new Uri(
                    finalVideo,
                    UriKind.Absolute);
        }

        if (!string.IsNullOrWhiteSpace(
                Current.Error))
        {
            Detail =
                Current.Error;
        }
    }

    private static string BuildSourceText(
        Project project)
    {
        var sources =
            string.Join(
                "\n\n",
                project.Sources.Select(
                    source =>
                        $"[{source.Id}] {source.Title}\n" +
                        $"{source.Url}\n" +
                        $"Retrieved {source.RetrievedAt:u}\n" +
                        $"{source.Notes}"));

        if (project.Brief == null)
            return sources;

        if (sources.Length > 0)
            sources += "\n\n";

        return sources +
               Json.Encode(
                   project.Brief);
    }

    // ========================================================================
    // RECENT PROJECTS
    // ========================================================================

    private void RefreshRecent()
    {
        Recent.Clear();

        foreach (var recent in
                 store.Recent(Settings))
        {
            Recent.Add(recent);
        }

        RefreshProjectCards();
    }

    // ========================================================================
    // SERVICE FACTORIES
    // ========================================================================

    private OllamaLanguageModel Llm() =>
        new(
            Settings,
            http);

    private SetupService Setup() =>
        new(
            Settings,
            downloads);

    private YouTubePublisher YouTube() =>
        new(
            Settings,
            http,
            credentials,
            store);

    // ========================================================================
    // VALIDATION
    // ========================================================================

    private void ValidateNewProject()
    {
        if (string.IsNullOrWhiteSpace(Idea))
        {
            throw new InvalidOperationException(
                "Enter a video idea first.");
        }

        if (TargetSeconds < 15 ||
            TargetSeconds > 180)
        {
            throw new InvalidOperationException(
                "Choose a duration from 15 to 180 seconds.");
        }

        if (!Tones.Contains(
                Tone,
                StringComparer.Ordinal))
        {
            throw new InvalidOperationException(
                "Select a valid narration tone.");
        }

        if (!Qualities.Contains(
                Quality,
                StringComparer.Ordinal))
        {
            throw new InvalidOperationException(
                "Select a valid production quality.");
        }
    }

    private void ValidateSettings()
    {
        Settings.ValidateAndNormalise();

        Directory.CreateDirectory(
            Settings.ProjectRoot);
    }

    // ========================================================================
    // FILE PICKERS
    // ========================================================================

    private void ChooseFile(
        string target)
    {
        if (Busy)
            return;

        if (target == "projects")
        {
            var folder =
                new OpenFolderDialog
                {
                    Title =
                        "Choose project folder"
                };

            if (folder.ShowDialog() == true)
            {
                Settings.ProjectRoot =
                    folder.FolderName;

                Notify(nameof(Settings));
                UpdateComponents();
            }

            return;
        }

        var filter =
            target is "oauth" or "image" or "video"
                ? "JSON files|*.json|All files|*.*"
                : target is "ffmpeg" or "ffprobe"
                    ? "Executable files|*.exe|All files|*.*"
                    : target is "python"
                        ? "Python executable|python.exe|Executable files|*.exe|All files|*.*"
                        : target is "voice"
                            ? "Piper voice model|*.onnx|All files|*.*"
                            : "All files|*.*";

        var dialog =
            new OpenFileDialog
            {
                Filter = filter
            };

        if (dialog.ShowDialog() != true)
            return;

        switch (target)
        {
            case "oauth":
                Settings.GoogleClientJson =
                    dialog.FileName;
                break;

            case "image":
                Settings.ImageWorkflow =
                    dialog.FileName;
                break;

            case "video":
                Settings.VideoWorkflow =
                    dialog.FileName;
                break;

            case "ffmpeg":
                Settings.Ffmpeg =
                    dialog.FileName;
                break;

            case "ffprobe":
                Settings.Ffprobe =
                    dialog.FileName;
                break;

            case "python":
                Settings.Python =
                    dialog.FileName;
                break;

            case "voice":
                Settings.PiperVoice =
                    dialog.FileName;
                break;

            default:
                return;
        }

        Notify(nameof(Settings));
        UpdateComponents();
    }

    // ========================================================================
    // PATH HELPERS
    // ========================================================================

    private bool TryGetFinalVideoPath(
        out string path)
    {
        path = "";

        var current =
            Current;

        if (current == null ||
            string.IsNullOrWhiteSpace(
                current.FinalVideo))
        {
            return false;
        }

        try
        {
            path =
                current.PathFor(
                    current.FinalVideo);

            return File.Exists(path);
        }
        catch (
            Exception e)
            when (
                e is IOException
                or InvalidDataException
                or ArgumentException
                or NotSupportedException)
        {
            path = "";
            return false;
        }
    }

    private static bool TryGetYouTubeUrl(
        string value,
        out Uri uri)
    {
        uri = null!;

        if (!Uri.TryCreate(
                value,
                UriKind.Absolute,
                out var parsed))
        {
            return false;
        }

        if (!string.Equals(
                parsed.Scheme,
                Uri.UriSchemeHttps,
                StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var validHost =
            string.Equals(
                parsed.Host,
                "youtube.com",
                StringComparison.OrdinalIgnoreCase)
            ||
            string.Equals(
                parsed.Host,
                "www.youtube.com",
                StringComparison.OrdinalIgnoreCase);

        if (!validHost)
            return false;

        uri = parsed;
        return true;
    }

    // ========================================================================
    // UI HELPERS
    // ========================================================================

    private static bool Confirm(
        string text)
    {
        return MessageBox.Show(
                   text,
                   "Jenga Video Studio",
                   MessageBoxButton.YesNo,
                   MessageBoxImage.Question)
               == MessageBoxResult.Yes;
    }

    private void AddProductionJournalText(
        string area,
        string message)
    {
        if (disposed)
            return;

        var safeArea =
            string.IsNullOrWhiteSpace(area)
                ? "SYSTEM"
                : area.Trim().ToUpperInvariant();

        var safeMessage =
            string.IsNullOrWhiteSpace(message)
                ? "Working…"
                : message.Trim();

        ProductionJournal.Insert(
            0,
            $"{DateTime.Now:HH:mm:ss} · " +
            $"{safeArea} — {safeMessage}");

        while (ProductionJournal.Count > 500)
        {
            ProductionJournal.RemoveAt(
                ProductionJournal.Count - 1);
        }
    }

    private void AddEvent(
        string message)
    {
        Events.Insert(
            0,
            $"{DateTime.Now:HH:mm:ss} · {message}");

        // Keep a long session from growing the UI collection indefinitely.
        while (Events.Count > 250)
        {
            Events.RemoveAt(
                Events.Count - 1);
        }
    }

    private static string FriendlyException(
        Exception exception)
    {
        if (exception is TimeoutException)
            return exception.Message;

        if (exception is HttpRequestException)
        {
            return
                "A local or network service could not complete the " +
                "request. " +
                exception.Message;
        }

        if (exception is FileNotFoundException)
        {
            return
                "A required file could not be found. " +
                exception.Message;
        }

        if (exception is UnauthorizedAccessException)
        {
            return
                "Jenga does not have permission to access a required " +
                "file or folder. " +
                exception.Message;
        }

        return string.IsNullOrWhiteSpace(
            exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    // ========================================================================
    // PROPERTY CHANGED
    // ========================================================================

    public event PropertyChangedEventHandler? PropertyChanged;

    private void Notify(
        [CallerMemberName] string? name = null)
    {
        PropertyChanged?.Invoke(
            this,
            new PropertyChangedEventArgs(name));
    }

    private bool Set<T>(
        ref T field,
        T value,
        [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(
                field,
                value))
        {
            return false;
        }

        field = value;

        Notify(name);

        CommandManager.InvalidateRequerySuggested();

        return true;
    }

    // ========================================================================
    // DISPOSAL
    // ========================================================================

    public void Dispose()
    {
        if (disposed)
            return;

        disposed = true;

        uiTimer?.Stop();

        try
        {
            lifetime.Cancel();
        }
        catch (ObjectDisposedException)
        {
        }

        try
        {
            operation?.Cancel();
        }
        catch (ObjectDisposedException)
        {
        }

        operation?.Dispose();
        operation = null;

        lifetime.Dispose();

        thumbnailGate.Dispose();

        http.Dispose();
        downloads.Dispose();
        web.Dispose();

        GC.SuppressFinalize(this);
    }
}