using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace JengaVideoStudio.Core;

// ============================================================================
// JSON
// ============================================================================

public static class Json
{
    public static readonly JsonSerializerOptions Options =
        new()
        {
            WriteIndented = true,
            PropertyNameCaseInsensitive = true,
            AllowTrailingCommas = true,
            ReadCommentHandling = JsonCommentHandling.Skip,
            Converters =
            {
                new JsonStringEnumConverter()
            }
        };

    public static string Encode<T>(T value)
    {
        return JsonSerializer.Serialize(
            value,
            Options);
    }

    public static T Decode<T>(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new InvalidDataException(
                "JSON content is empty.");
        }

        try
        {
            return JsonSerializer.Deserialize<T>(
                       value,
                       Options)
                   ?? throw new InvalidDataException(
                       "JSON contained no object.");
        }
        catch (JsonException e)
        {
            throw new InvalidDataException(
                "JSON could not be read because it is malformed or " +
                "does not match the expected data structure.",
                e);
        }
    }

    /// <summary>
    /// Saves JSON transactionally. The destination is replaced only after
    /// the complete temporary file has been written successfully.
    /// </summary>
    public static async Task Save<T>(
        string path,
        T value,
        CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new ArgumentException(
                "JSON destination path is empty.",
                nameof(path));
        }

        ct.ThrowIfCancellationRequested();

        var fullPath =
            Path.GetFullPath(path);

        var directory =
            Path.GetDirectoryName(fullPath)
            ?? throw new InvalidOperationException(
                "JSON destination has no parent directory.");

        Directory.CreateDirectory(directory);

        var temp =
            fullPath + ".tmp";

        try
        {
            var json =
                Encode(value);

            await File.WriteAllTextAsync(
                temp,
                json,
                new UTF8Encoding(
                    encoderShouldEmitUTF8Identifier: false),
                ct);

            ct.ThrowIfCancellationRequested();

            File.Move(
                temp,
                fullPath,
                true);
        }
        catch
        {
            TryDelete(temp);
            throw;
        }
    }

    private static void TryDelete(string path)
    {
        try
        {
            if (File.Exists(path))
                File.Delete(path);
        }
        catch
        {
            // Cleanup must not conceal the original persistence failure.
        }
    }
}

// ============================================================================
// SETTINGS
// ============================================================================

public sealed class Settings
{
    public const int CurrentVersion = 1;

    // ------------------------------------------------------------------------
    // LOCAL AI MODEL PROFILES
    // ------------------------------------------------------------------------

    /// <summary>
    /// Recommended lightweight model for CPU-only machines.
    /// This is the default for new installations.
    /// </summary>
    public const string CpuModel =
        "qwen2.5:1.5b";

    /// <summary>
    /// Higher-quality model intended primarily for machines with suitable
    /// GPU acceleration or for users who explicitly accept slower inference.
    /// </summary>
    public const string QualityModel =
        "qwen2.5:7b";

    public int Version { get; set; } =
        CurrentVersion;

    public string ProjectRoot { get; set; } =
        Path.Combine(
            Environment.GetFolderPath(
                Environment.SpecialFolder.MyDocuments),
            "Jenga Video Studio");

    // ------------------------------------------------------------------------
    // LOCAL AI
    // ------------------------------------------------------------------------

    public string OllamaEndpoint { get; set; } =
        "http://127.0.0.1:11434";

    /// <summary>
    /// Model selected for local language-model work.
    ///
    /// New installations use the lightweight CPU model. An existing or
    /// manually selected model is not silently replaced.
    /// </summary>
    public string Model { get; set; } =
        CpuModel;

    /// <summary>
    /// Instructs Ollama generation requests to avoid GPU layers.
    ///
    /// This is particularly useful on older or incompatible GPUs.
    /// </summary>
    public bool CpuOnly { get; set; }

    /// <summary>
    /// Recommended model for the currently selected compute mode.
    ///
    /// This is advisory and does not overwrite Model, so an advanced user can
    /// deliberately run the larger model on CPU if desired.
    /// </summary>
    [JsonIgnore]
    public string RecommendedModel =>
        CpuOnly
            ? CpuModel
            : QualityModel;

    /// <summary>
    /// True when the currently selected model matches the recommended model
    /// for the selected compute mode.
    /// </summary>
    [JsonIgnore]
    public bool IsUsingRecommendedModel =>
        string.Equals(
            Model,
            RecommendedModel,
            StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// True when the selected model is the lightweight CPU profile.
    /// </summary>
    [JsonIgnore]
    public bool IsLightweightModel =>
        string.Equals(
            Model,
            CpuModel,
            StringComparison.OrdinalIgnoreCase);

    // ------------------------------------------------------------------------
    // FFMPEG
    // ------------------------------------------------------------------------

    public string Ffmpeg { get; set; } =
        "ffmpeg";

    public string Ffprobe { get; set; } =
        "ffprobe";

    // ------------------------------------------------------------------------
    // SPEECH
    // ------------------------------------------------------------------------

    public string SpeechProvider { get; set; } =
        "Windows";

    public string Python { get; set; } =
        "python";

    public string PiperPython { get; set; } =
        "";

    public string PiperVoice { get; set; } =
        "";

    public string WindowsVoice { get; set; } =
        "";

    // ------------------------------------------------------------------------
    // COMFYUI
    // ------------------------------------------------------------------------

    public string ComfyEndpoint { get; set; } =
        "http://127.0.0.1:8188";

    public string ImageWorkflow { get; set; } =
        "";

    public string VideoWorkflow { get; set; } =
        "";

    public bool EnableImages { get; set; }

    public bool EnableVideo { get; set; }

    // ------------------------------------------------------------------------
    // YOUTUBE
    // ------------------------------------------------------------------------

    public string YouTubeChannelUrl { get; set; } =
        "";

    public string GoogleClientJson { get; set; } =
        "";

    public string DefaultPrivacy { get; set; } =
        "private";

    // ------------------------------------------------------------------------
    // STORAGE
    // ------------------------------------------------------------------------

    [JsonIgnore]
    public static string DataRoot =>
        Path.Combine(
            Environment.GetFolderPath(
                Environment.SpecialFolder.LocalApplicationData),
            "JengaVideoStudio");

    [JsonIgnore]
    public static string FilePath =>
        Path.Combine(
            DataRoot,
            "settings.json");

    public static Settings Load()
    {
        if (!File.Exists(FilePath))
            return new Settings();

        try
        {
            var settings =
                Json.Decode<Settings>(
                    File.ReadAllText(FilePath));

            settings.ValidateAndNormalise();

            return settings;
        }
        catch (Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or InvalidDataException)
        {
            // Do not silently replace damaged settings with defaults.
            // That could unexpectedly change model/provider/output choices.
            throw new InvalidDataException(
                $"Jenga settings could not be loaded from '{FilePath}'.",
                e);
        }
    }

    public async Task Save()
    {
        ValidateAndNormalise();

        await Json.Save(
            FilePath,
            this);
    }

    public void ValidateAndNormalise()
    {
        if (Version != CurrentVersion)
        {
            throw new InvalidDataException(
                $"Unsupported settings version {Version}. " +
                $"This build supports version {CurrentVersion}.");
        }

        if (string.IsNullOrWhiteSpace(ProjectRoot))
        {
            throw new InvalidDataException(
                "Project storage location is empty.");
        }

        ProjectRoot =
            Path.GetFullPath(
                Environment.ExpandEnvironmentVariables(
                    ProjectRoot.Trim()));

        if (string.IsNullOrWhiteSpace(OllamaEndpoint))
        {
            OllamaEndpoint =
                "http://127.0.0.1:11434";
        }

        if (string.IsNullOrWhiteSpace(ComfyEndpoint))
        {
            ComfyEndpoint =
                "http://127.0.0.1:8188";
        }

        Util.LocalEndpoint(OllamaEndpoint);
        Util.LocalEndpoint(ComfyEndpoint);

        OllamaEndpoint =
            OllamaEndpoint.TrimEnd('/');

        ComfyEndpoint =
            ComfyEndpoint.TrimEnd('/');

        // If no model has been selected, choose an appropriate model for
        // the selected compute mode.
        //
        // IMPORTANT: a non-empty manual selection is preserved.
        if (string.IsNullOrWhiteSpace(Model))
        {
            Model =
                CpuOnly
                    ? CpuModel
                    : QualityModel;
        }

        Model =
            Model.Trim();

        if (string.IsNullOrWhiteSpace(Ffmpeg))
            Ffmpeg = "ffmpeg";

        if (string.IsNullOrWhiteSpace(Ffprobe))
            Ffprobe = "ffprobe";

        if (string.IsNullOrWhiteSpace(Python))
            Python = "python";

        if (string.IsNullOrWhiteSpace(SpeechProvider))
            SpeechProvider = "Windows";

        if (!string.Equals(
                SpeechProvider,
                "Windows",
                StringComparison.OrdinalIgnoreCase)
            &&
            !string.Equals(
                SpeechProvider,
                "Piper",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                $"Unsupported speech provider '{SpeechProvider}'.");
        }

        SpeechProvider =
            string.Equals(
                SpeechProvider,
                "Piper",
                StringComparison.OrdinalIgnoreCase)
                ? "Piper"
                : "Windows";

        DefaultPrivacy =
            NormalisePrivacy(
                DefaultPrivacy);
    }

    private static string NormalisePrivacy(
        string value)
    {
        if (string.Equals(
                value,
                "public",
                StringComparison.OrdinalIgnoreCase))
        {
            return "public";
        }

        if (string.Equals(
                value,
                "unlisted",
                StringComparison.OrdinalIgnoreCase))
        {
            return "unlisted";
        }

        // Safety-oriented default: unknown values never become public.
        return "private";
    }
}

// ============================================================================
// PRODUCTION STATE
// ============================================================================

public enum ProductionState
{
    Created,
    Researching,
    AnalysingSources,
    WritingScript,
    PlanningScenes,
    GeneratingNarration,
    GeneratingVisuals,
    Rendering,
    QualityChecking,
    ReadyForReview,
    Uploading,
    Uploaded,
    Failed,
    Cancelled
}

// ============================================================================
// RESEARCH
// ============================================================================

public sealed class Source
{
    public string Id { get; set; } = "";

    public string Url { get; set; } = "";

    public string Title { get; set; } = "";

    public string Publisher { get; set; } = "";

    public string Text { get; set; } = "";

    public DateTimeOffset RetrievedAt { get; set; } =
        DateTimeOffset.UtcNow;

    public string Notes { get; set; } =
        "Publication date unknown. Verify currency manually.";
}

public sealed class Evidence
{
    public string SourceId { get; set; } = "";

    public string Quote { get; set; } = "";
}

public sealed class Claim
{
    public string Id { get; set; } = "";

    public string Text { get; set; } = "";

    public List<Evidence> Evidence { get; set; } =
        [];

    public string Caveat { get; set; } = "";
}

public sealed class Brief
{
    public string Summary { get; set; } = "";

    public List<Claim> Claims { get; set; } =
        [];
}

// ============================================================================
// SCRIPT / SCENES
// ============================================================================

public sealed class Scene
{
    public int Number { get; set; }

    public string Narration { get; set; } =
        "";

    public string Heading { get; set; } =
        "";

    public string VisualType { get; set; } =
        "Typography";

    public string VisualPrompt { get; set; } =
        "";

    public List<string> Points { get; set; } =
        [];

    public List<string> ClaimIds { get; set; } =
        [];

    public string Audio { get; set; } =
        "";

    public string Visual { get; set; } =
        "";

    public string AudioHash { get; set; } =
        "";

    public string VisualHash { get; set; } =
        "";

    public int Revision { get; set; }

    public double Duration { get; set; }
}

public sealed class ScriptPlan
{
    public string Title { get; set; } =
        "";

    public string Description { get; set; } =
        "";

    public List<Scene> Scenes { get; set; } =
        [];
}

// ============================================================================
// VIDEO / YOUTUBE METADATA
// ============================================================================

public sealed class VideoMetadata
{
    public string Title { get; set; } =
        "";

    public string Description { get; set; } =
        "";

    public string Tags { get; set; } =
        "";

    public string Category { get; set; } =
        "27";

    /// <summary>
    /// Defaults to private. Upload code should never infer public visibility.
    /// </summary>
    public string Privacy { get; set; } =
        "private";

    public bool MadeForKids { get; set; }

    public bool ContainsSyntheticMedia { get; set; } =
        true;

    public string VideoId { get; set; } =
        "";

    public string ActualPrivacy { get; set; } =
        "";
}

// ============================================================================
// PROJECT
// ============================================================================

public sealed class Project
{
    public const int CurrentVersion = 1;

    public int Version { get; set; } =
        CurrentVersion;

    public string Id { get; set; } =
        Guid.NewGuid().ToString("N");

    public DateTimeOffset CreatedAt { get; set; } =
        DateTimeOffset.UtcNow;

    public string Idea { get; set; } =
        "";

    public string Tone { get; set; } =
        "Clear and curious";

    public string Quality { get; set; } =
        "Light";

    public int TargetSeconds { get; set; } =
        60;

    public string SuppliedUrls { get; set; } =
        "";

    public ProductionState State { get; set; }

    public List<Source> Sources { get; set; } =
        [];

    public Brief? Brief { get; set; }

    public List<Scene> Scenes { get; set; } =
        [];

    public VideoMetadata Metadata { get; set; } =
        new();

    public List<string> Warnings { get; set; } =
        [];

    public string FinalVideo { get; set; } =
        "";

    public string FinalHash { get; set; } =
        "";

    public string Error { get; set; } =
        "";

    public bool EditorialReviewed { get; set; }

    [JsonIgnore]
    public string Folder { get; set; } =
        "";

    [JsonIgnore]
    public string Label =>
        $"{CreatedAt:dd MMM HH:mm} · {Idea} · {State}";

    /// <summary>
    /// Resolves a project-relative path and prevents traversal outside the
    /// project's own directory.
    /// </summary>
    public string PathFor(string relative)
    {
        if (string.IsNullOrWhiteSpace(Folder))
        {
            throw new InvalidOperationException(
                "Project storage folder has not been assigned.");
        }

        if (string.IsNullOrWhiteSpace(relative))
        {
            throw new ArgumentException(
                "Project-relative path is empty.",
                nameof(relative));
        }

        if (Path.IsPathRooted(relative))
        {
            throw new InvalidDataException(
                "Project paths must be relative.");
        }

        var root =
            Path.GetFullPath(Folder);

        var rootWithSeparator =
            root.EndsWith(
                Path.DirectorySeparatorChar)
                ? root
                : root + Path.DirectorySeparatorChar;

        var path =
            Path.GetFullPath(
                Path.Combine(
                    root,
                    relative));

        // Allow the root itself only if explicitly requested as ".".
        if (!string.Equals(
                path,
                root,
                StringComparison.OrdinalIgnoreCase)
            &&
            !path.StartsWith(
                rootWithSeparator,
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                "Project path escapes its project folder.");
        }

        var parent =
            Path.GetDirectoryName(path);

        if (!string.IsNullOrWhiteSpace(parent))
        {
            Directory.CreateDirectory(parent);
        }

        return path;
    }
}

// ============================================================================
// PROGRESS
// ============================================================================

public sealed record ProgressUpdate(
    string Stage,
    string Detail,
    double Percent,
    bool Indeterminate = false);

// ============================================================================
// SERVICE INTERFACES
// ============================================================================

public interface ILanguageModel
{
    Task<T> Generate<T>(
        string template,
        object input,
        CancellationToken ct);
}

public interface IResearchService
{
    Task<List<Source>> Research(
        Project project,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct);
}

public interface ITextToSpeechProvider
{
    string Identity { get; }

    Task Speak(
        string text,
        string output,
        CancellationToken ct);
}

public interface IVisualProvider
{
    Task<string> Create(
        Project project,
        Scene scene,
        CancellationToken ct);
}

public interface IMediaRenderer
{
    Task Render(
        Project project,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct);

    Task<double> Duration(
        string path,
        CancellationToken ct);

    Task Validate(
        Project project,
        CancellationToken ct);
}

public interface ICredentialStore
{
    void Save(
        string key,
        string value);

    string? Read(
        string key);

    void Delete(
        string key);
}