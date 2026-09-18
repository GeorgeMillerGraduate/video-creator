using System.ComponentModel;
using System.Globalization;
using System.Runtime.CompilerServices;
using System.Windows.Media.Imaging;
using JengaVideoStudio.Core;

namespace JengaVideoStudio.App;

// ============================================================================
// NAVIGATION
// ============================================================================

public enum StudioPage
{
    Home,
    Create,
    Projects,
    Production,
    Review,
    Export,
    Settings,
    Help
}

// ============================================================================
// PRESENTATION BASE
// ============================================================================

public abstract class PresentationItem : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected void Changed(
        [CallerMemberName] string? name = null)
    {
        PropertyChanged?.Invoke(
            this,
            new PropertyChangedEventArgs(name));
    }

    protected void AllChanged()
    {
        PropertyChanged?.Invoke(
            this,
            new PropertyChangedEventArgs(string.Empty));
    }
}

// ============================================================================
// COMPONENT CARD
// ============================================================================

public sealed class ComponentCard : PresentationItem
{
    private string status =
        "Not checked";

    private string detail =
        "Run a component check.";

    private string tone =
        "Quiet";

    public ComponentCard(
        string name,
        string description)
    {
        Name =
            string.IsNullOrWhiteSpace(name)
                ? "Component"
                : name.Trim();

        Description =
            description?.Trim() ?? "";
    }

    public string Name { get; }

    public string Description { get; }

    public string Status =>
        status;

    public string Detail =>
        detail;

    public string Tone =>
        tone;

    public string Symbol =>
        tone switch
        {
            "Ready" => "✓",
            "Attention" => "!",
            _ => "○"
        };

    public void Update(
        string newStatus,
        string newDetail,
        string newTone = "Quiet")
    {
        newStatus =
            string.IsNullOrWhiteSpace(newStatus)
                ? "Not checked"
                : newStatus.Trim();

        newDetail =
            newDetail?.Trim() ?? "";

        newTone =
            NormaliseTone(newTone);

        if (status == newStatus &&
            detail == newDetail &&
            tone == newTone)
        {
            return;
        }

        status =
            newStatus;

        detail =
            newDetail;

        tone =
            newTone;

        AllChanged();
    }

    private static string NormaliseTone(
        string value)
    {
        return value switch
        {
            "Ready" => "Ready",
            "Attention" => "Attention",
            _ => "Quiet"
        };
    }
}

// ============================================================================
// PIPELINE STEP
// ============================================================================

public sealed class PipelineStep : PresentationItem
{
    private string state =
        "Pending";

    public PipelineStep(
        string label)
    {
        Label =
            string.IsNullOrWhiteSpace(label)
                ? "Step"
                : label.Trim();
    }

    public string Label { get; }

    public string State =>
        state;

    public string Symbol =>
        state switch
        {
            "Complete" => "✓",
            "Working" => "●",
            _ => "○"
        };

    public void Update(
        string newState)
    {
        newState =
            NormaliseState(newState);

        if (state == newState)
            return;

        state =
            newState;

        AllChanged();
    }

    private static string NormaliseState(
        string value)
    {
        return value switch
        {
            "Complete" => "Complete",
            "Working" => "Working",
            _ => "Pending"
        };
    }
}

// ============================================================================
// PROJECT CARD
// ============================================================================

public sealed class ProjectCard
{
    public ProjectCard(
        Project project)
    {
        ArgumentNullException.ThrowIfNull(project);

        Project =
            project;

        Modified =
            ReadModified(project);

        Thumbnail =
            PresentationFiles.Image(
                PresentationFiles.TryCombine(
                    project.Folder,
                    "renders",
                    "final",
                    "thumbnail.png"));
    }

    public Project Project { get; }

    public string Title
    {
        get
        {
            if (!string.IsNullOrWhiteSpace(
                    Project.Metadata.Title))
            {
                return Project.Metadata.Title;
            }

            if (!string.IsNullOrWhiteSpace(
                    Project.Idea))
            {
                return Project.Idea;
            }

            return "Untitled project";
        }
    }

    public string Duration
    {
        get
        {
            if (Project.Scenes.Count == 0)
                return "Not timed yet";

            if (!Project.Scenes.All(
                    scene =>
                        PresentationFiles.ValidDuration(
                            scene.Duration)))
            {
                return "Not timed yet";
            }

            var total =
                Project.Scenes.Sum(
                    scene =>
                        scene.Duration);

            return PresentationFiles.Time(
                total);
        }
    }

    public string Status =>
        Project.State switch
        {
            ProductionState.ReadyForReview =>
                "Ready for review",

            ProductionState.Uploaded =>
                "Uploaded",

            ProductionState.Uploading =>
                "Uploading",

            ProductionState.Created =>
                "Draft",

            ProductionState.Cancelled =>
                "Paused · Resume",

            ProductionState.Failed =>
                "Needs attention",

            _ =>
                "Saved progress · Resume"
        };

    public string Modified { get; }

    public BitmapSource? Thumbnail { get; }

    private static string ReadModified(
        Project project)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(
                    project.Folder))
            {
                return "Save time unavailable";
            }

            var file =
                Path.Combine(
                    project.Folder,
                    "project.json");

            if (!File.Exists(file))
                return "Save time unavailable";

            var modified =
                File.GetLastWriteTime(file);

            return
                "Saved " +
                modified.ToString(
                    "dd MMM yyyy, HH:mm",
                    CultureInfo.CurrentCulture);
        }
        catch (
            Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or ArgumentException
                or NotSupportedException)
        {
            return "Save time unavailable";
        }
    }
}

// ============================================================================
// SCENE CARD
// ============================================================================

/// <summary>
/// Display adapter over the original Scene object.
///
/// It deliberately holds the original Scene reference rather than creating
/// another editable copy.
/// </summary>
public sealed class SceneCard : PresentationItem
{
    private readonly Project project;

    private BitmapSource? thumbnail;

    public SceneCard(
        Project project,
        Scene scene,
        int index,
        double start)
    {
        ArgumentNullException.ThrowIfNull(project);
        ArgumentNullException.ThrowIfNull(scene);

        if (index < 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(index));
        }

        this.project =
            project;

        Model =
            scene;

        Number =
            checked(index + 1);

        Start =
            PresentationFiles.ValidDuration(start)
                ? start
                : 0;
    }

    public Scene Model { get; }

    public int Number { get; }

    public double Start { get; }

    public string Title
    {
        get
        {
            var heading =
                string.IsNullOrWhiteSpace(
                    Model.Heading)
                    ? "Untitled scene"
                    : Model.Heading.Trim();

            return
                $"{Number:00}  {heading}";
        }
    }

    public string Heading =>
        Model.Heading ?? "";

    public string Duration =>
        HasDuration
            ? PresentationFiles.Time(
                Model.Duration)
            : "Not timed";

    public string Range
    {
        get
        {
            if (!HasDuration)
                return "Timing not available";

            return
                $"{PresentationFiles.Time(Start)} → " +
                $"{PresentationFiles.Time(Start + Model.Duration)}";
        }
    }

    public double TimelineWidth
    {
        get
        {
            if (!HasDuration)
                return 0;

            /*
             * Avoid pathological values from corrupt project data producing
             * enormous WPF layout dimensions.
             */
            return Math.Clamp(
                Model.Duration * 12,
                0,
                3600);
        }
    }

    public bool HasDuration =>
        PresentationFiles.ValidDuration(
            Model.Duration);

    public string Status
    {
        get
        {
            if (File.Exists(ClipPath))
                return "Rendered";

            if (VisualExists())
                return "Visual ready";

            if (AudioExists())
                return "Narrated";

            return "Planned";
        }
    }

    public string PreviewLabel
    {
        get
        {
            if (Thumbnail != null)
                return "Saved scene visual";

            if (!string.IsNullOrWhiteSpace(
                    Model.Visual))
            {
                return "Video preview pending";
            }

            return "Graphic scene";
        }
    }

    public string ClipPath =>
        PresentationFiles.TryCombine(
            project.Folder,
            "renders",
            "scenes",
            $"scene-{Number:00}.mp4");

    public BitmapSource? Thumbnail =>
        thumbnail;

    public void Refresh()
    {
        BitmapSource? candidate =
            null;

        if (!string.IsNullOrWhiteSpace(
                Model.Visual))
        {
            try
            {
                var path =
                    project.PathFor(
                        Model.Visual);

                candidate =
                    PresentationFiles.Image(path);
            }
            catch (
                Exception e)
                when (
                    e is IOException
                    or InvalidDataException
                    or UnauthorizedAccessException
                    or ArgumentException
                    or NotSupportedException)
            {
                candidate = null;
            }
        }

        /*
         * Do not clear a rendered thumbnail merely because the underlying
         * visual is a video or deterministic graphic. QueueThumbnail may
         * already have supplied a better preview.
         */
        if (candidate != null)
        {
            thumbnail =
                candidate;
        }

        AllChanged();
    }

    public void SetThumbnail(
        BitmapSource image)
    {
        ArgumentNullException.ThrowIfNull(image);

        thumbnail =
            image;

        AllChanged();
    }

    private bool VisualExists()
    {
        if (string.IsNullOrWhiteSpace(
                Model.Visual))
        {
            return false;
        }

        try
        {
            return File.Exists(
                project.PathFor(
                    Model.Visual));
        }
        catch
        {
            return false;
        }
    }

    private bool AudioExists()
    {
        if (string.IsNullOrWhiteSpace(
                Model.Audio))
        {
            return false;
        }

        try
        {
            return File.Exists(
                project.PathFor(
                    Model.Audio));
        }
        catch
        {
            return false;
        }
    }
}

// ============================================================================
// PRESENTATION FILE HELPERS
// ============================================================================

public static class PresentationFiles
{
    private static readonly HashSet<string> ImageExtensions =
        new(
            StringComparer.OrdinalIgnoreCase)
        {
            ".png",
            ".jpg",
            ".jpeg",
            ".bmp"
        };

    // ========================================================================
    // TIME
    // ========================================================================

    public static string Time(
        double seconds)
    {
        if (!double.IsFinite(seconds) ||
            seconds < 0)
        {
            seconds = 0;
        }

        /*
         * Round to whole seconds for display. This prevents a value such as
         * 59.999 appearing unexpectedly as 0:59.
         */
        var rounded =
            Math.Max(
                0,
                Math.Round(
                    seconds,
                    MidpointRounding.AwayFromZero));

        var time =
            TimeSpan.FromSeconds(
                rounded);

        if (time.TotalHours >= 1)
        {
            return
                $"{(int)time.TotalHours}:" +
                $"{time.Minutes:00}:" +
                $"{time.Seconds:00}";
        }

        return
            $"{(int)time.TotalMinutes}:" +
            $"{time.Seconds:00}";
    }

    public static bool ValidDuration(
        double seconds)
    {
        return
            double.IsFinite(seconds) &&
            seconds > 0;
    }

    // ========================================================================
    // IMAGE LOADING
    // ========================================================================

    public static BitmapSource? Image(
        string? path)
    {
        if (string.IsNullOrWhiteSpace(path))
            return null;

        try
        {
            var extension =
                Path.GetExtension(path);

            if (!ImageExtensions.Contains(
                    extension))
            {
                return null;
            }

            if (!File.Exists(path))
                return null;

            /*
             * Load the bitmap completely into memory so the source file is
             * not kept locked by WPF. This is important because renders and
             * thumbnails are regenerated in place.
             */
            using var stream =
                new FileStream(
                    path,
                    FileMode.Open,
                    FileAccess.Read,
                    FileShare.ReadWrite |
                    FileShare.Delete);

            if (stream.Length <= 0)
                return null;

            var bitmap =
                new BitmapImage();

            bitmap.BeginInit();

            bitmap.CacheOption =
                BitmapCacheOption.OnLoad;

            bitmap.CreateOptions =
                BitmapCreateOptions.IgnoreImageCache;

            bitmap.DecodePixelWidth =
                640;

            bitmap.StreamSource =
                stream;

            bitmap.EndInit();

            /*
             * Frozen BitmapSources can safely be passed between the thumbnail
             * worker and WPF's UI thread.
             */
            if (bitmap.CanFreeze)
            {
                bitmap.Freeze();
            }

            return bitmap;
        }
        catch (
            Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or NotSupportedException
                or ArgumentException
                or System.IO.FileFormatException)
        {
            return null;
        }
    }

    // ========================================================================
    // SAFE PRESENTATION PATH
    // ========================================================================

    public static string TryCombine(
        params string[] parts)
    {
        if (parts == null ||
            parts.Length == 0 ||
            parts.Any(
                string.IsNullOrWhiteSpace))
        {
            return "";
        }

        try
        {
            return Path.Combine(parts);
        }
        catch (
            Exception e)
            when (
                e is ArgumentException
                or NotSupportedException)
        {
            return "";
        }
    }
}