using System.Diagnostics;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;

namespace JengaVideoStudio.Core;

// ============================================================================
// AI HARDWARE PROFILE
// ============================================================================

public enum AiComputeBackend
{
    Unknown,
    Cpu,
    Gpu
}

public enum AiPerformanceClass
{
    Minimal,
    Lightweight,
    Balanced,
    Quality,
    HighEnd
}

/// <summary>
/// The result of inspecting this computer and, optionally, testing Ollama.
///
/// This object can be saved to disk and reused by SetupService and
/// OllamaLanguageModel.
/// </summary>
public sealed class AiHardwareProfile
{
    public DateTimeOffset AssessedAt { get; set; } =
        DateTimeOffset.UtcNow;

    public string CpuName { get; set; } =
        "Unknown CPU";

    public int LogicalProcessors { get; set; }

    public double TotalRamGb { get; set; }

    public double AvailableRamGb { get; set; }

    public List<AiGpuInfo> Gpus { get; set; } =
        [];

    public AiComputeBackend Backend { get; set; } =
        AiComputeBackend.Unknown;

    public AiPerformanceClass PerformanceClass { get; set; } =
        AiPerformanceClass.Minimal;

    public bool GpuDetected { get; set; }

    public bool GpuAccelerationTested { get; set; }

    public bool GpuAccelerationAvailable { get; set; }

    public string GpuFailureReason { get; set; } =
        "";

    public string RecommendedModel { get; set; } =
        Settings.CpuModel;

    public int RecommendedContext { get; set; } =
        4096;

    public int RecommendedPrediction { get; set; } =
        1024;

    public int RecommendedBatch { get; set; } =
        32;

    public bool RecommendedCpuOnly { get; set; } =
        true;

    public double? BenchmarkTokensPerSecond { get; set; }

    public double? BenchmarkFirstTokenSeconds { get; set; }

    public TimeSpan? BenchmarkDuration { get; set; }

    public string BenchmarkModel { get; set; } =
        "";

    public bool BenchmarkSucceeded { get; set; }

    public string BenchmarkFailureReason { get; set; } =
        "";

    public double FreeDiskGb { get; set; }

    public List<string> InstalledModels { get; set; } =
        [];

    public List<string> Notes { get; set; } =
        [];

    public string Summary
    {
        get
        {
            var backend =
                Backend switch
                {
                    AiComputeBackend.Gpu => "GPU",
                    AiComputeBackend.Cpu => "CPU",
                    _ => "Unknown"
                };

            var speed =
                BenchmarkTokensPerSecond.HasValue
                    ? $"{BenchmarkTokensPerSecond.Value:0.0} tok/s"
                    : "not benchmarked";

            return
                $"{backend} · {PerformanceClass} · " +
                $"{RecommendedModel} · {speed}";
        }
    }
}

public sealed class AiGpuInfo
{
    public string Name { get; set; } =
        "Unknown GPU";

    public string Vendor { get; set; } =
        "";

    public double VramGb { get; set; }

    public string DriverVersion { get; set; } =
        "";

    public bool LooksLikeDedicatedGpu { get; set; }
}

// ============================================================================
// AI HARDWARE PROFILER
// ============================================================================

/// <summary>
/// Detects the machine's hardware and determines an appropriate local-AI
/// profile.
///
/// Important:
///
/// Hardware specifications are only the first stage. A GPU being visible to
/// Windows does NOT prove that Ollama can use it. The profiler can therefore
/// perform a real, deliberately small Ollama generation test.
///
/// If GPU execution crashes or reports a CUDA/Vulkan/runner compatibility
/// problem, the final profile falls back to CPU.
/// </summary>
public sealed class AiHardwareProfiler
{
    private const int HardwareDetectionTimeoutSeconds = 30;
    private const int OllamaProbeTimeoutSeconds = 90;

    private const string LightweightModel =
        "qwen2.5:1.5b";

    private const string BalancedModel =
        "qwen2.5:3b";

    private const string QualityModel =
        "qwen2.5:7b";

    private const string LargeModel =
        "qwen2.5:14b";

    private readonly Settings settings;
    private readonly HttpClient http;

    public AiHardwareProfiler(
        Settings settings,
        HttpClient http)
    {
        this.settings =
            settings ??
            throw new ArgumentNullException(nameof(settings));

        this.http =
            http ??
            throw new ArgumentNullException(nameof(http));
    }

    // ========================================================================
    // PUBLIC ASSESSMENT
    // ========================================================================

    /// <summary>
    /// Performs the complete assessment.
    ///
    /// probeOllama:
    ///     true  = perform a real local-AI capability test.
    ///     false = hardware inspection only.
    ///
    /// The method does not automatically download multi-gigabyte models.
    /// It benchmarks an appropriate installed model when possible.
    /// </summary>
    public async Task<AiHardwareProfile> Assess(
        bool probeOllama,
        IProgress<ProgressUpdate>? progress,
        CancellationToken ct)
    {
        progress?.Report(
            new ProgressUpdate(
                "AI hardware",
                "Inspecting this computer",
                5,
                true));

        var profile =
            await DetectHardware(ct);

        ct.ThrowIfCancellationRequested();

        progress?.Report(
            new ProgressUpdate(
                "AI hardware",
                "Checking local storage",
                15,
                true));

        profile.FreeDiskGb =
            DetectFreeDiskSpace();

        progress?.Report(
            new ProgressUpdate(
                "AI hardware",
                "Checking installed Ollama models",
                25,
                true));

        profile.InstalledModels =
            await GetInstalledModels(ct);

        SelectInitialProfile(profile);

        if (!probeOllama)
        {
            profile.Notes.Add(
                "Ollama capability testing was skipped.");

            FinaliseProfile(profile);

            return profile;
        }

        ct.ThrowIfCancellationRequested();

        // --------------------------------------------------------------------
        // GPU TEST
        // --------------------------------------------------------------------

        if (profile.GpuDetected)
        {
            progress?.Report(
                new ProgressUpdate(
                    "AI hardware",
                    "Testing whether Ollama can really use the GPU",
                    40,
                    true));

            await TestGpuAcceleration(
                profile,
                ct);
        }
        else
        {
            profile.GpuAccelerationTested =
                false;

            profile.GpuAccelerationAvailable =
                false;

            profile.Backend =
                AiComputeBackend.Cpu;

            profile.RecommendedCpuOnly =
                true;

            profile.Notes.Add(
                "No suitable dedicated GPU was detected.");
        }

        ct.ThrowIfCancellationRequested();

        // --------------------------------------------------------------------
        // SELECT MODEL AFTER BACKEND TEST
        // --------------------------------------------------------------------

        SelectModelForUsableHardware(
            profile);

        // --------------------------------------------------------------------
        // BENCHMARK
        // --------------------------------------------------------------------

        progress?.Report(
            new ProgressUpdate(
                "AI hardware",
                $"Benchmarking {profile.RecommendedModel}",
                65,
                true));

        await BenchmarkRecommendedProfile(
            profile,
            ct);

        // --------------------------------------------------------------------
        // PERFORMANCE-BASED STEP DOWN
        // --------------------------------------------------------------------

        ApplyBenchmarkAdjustment(
            profile);

        FinaliseProfile(profile);

        progress?.Report(
            new ProgressUpdate(
                "AI hardware",
                $"Selected {profile.RecommendedModel} · " +
                $"{BackendText(profile)}",
                100,
                false));

        return profile;
    }

    // ========================================================================
    // HARDWARE DETECTION
    // ========================================================================

    private static async Task<AiHardwareProfile> DetectHardware(
        CancellationToken ct)
    {
        var profile =
            new AiHardwareProfile();

        try
        {
            const string command = """
                $c = Get-CimInstance Win32_ComputerSystem
                $o = Get-CimInstance Win32_OperatingSystem
                $p = Get-CimInstance Win32_Processor
                $g = Get-CimInstance Win32_VideoController

                $gpuList = @(
                    $g | ForEach-Object {
                        [pscustomobject]@{
                            Name = $_.Name
                            AdapterRAM = [int64]$_.AdapterRAM
                            DriverVersion = $_.DriverVersion
                            VideoProcessor = $_.VideoProcessor
                        }
                    }
                )

                [pscustomobject]@{
                    Processor = ($p.Name -join ', ')
                    LogicalProcessors = [Environment]::ProcessorCount
                    TotalRAM = [int64]$c.TotalPhysicalMemory
                    AvailableRAM = [int64]($o.FreePhysicalMemory * 1KB)
                    GPUs = $gpuList
                } | ConvertTo-Json -Depth 5 -Compress
                """;

            var result =
                await ChildProcess.Run(
                    "powershell.exe",
                    [
                        "-NoLogo",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        command
                    ],
                    null,
                    null,
                    TimeSpan.FromSeconds(
                        HardwareDetectionTimeoutSeconds),
                    ct);

            using var document =
                JsonDocument.Parse(result);

            var root =
                document.RootElement;

            profile.CpuName =
                GetString(
                    root,
                    "Processor")
                ?? "Unknown CPU";

            profile.LogicalProcessors =
                GetInt(
                    root,
                    "LogicalProcessors");

            profile.TotalRamGb =
                BytesToGb(
                    GetLong(
                        root,
                        "TotalRAM"));

            profile.AvailableRamGb =
                BytesToGb(
                    GetLong(
                        root,
                        "AvailableRAM"));

            if (root.TryGetProperty(
                    "GPUs",
                    out var gpus))
            {
                if (gpus.ValueKind ==
                    JsonValueKind.Array)
                {
                    foreach (var gpu in
                             gpus.EnumerateArray())
                    {
                        profile.Gpus.Add(
                            ParseGpu(gpu));
                    }
                }
                else if (gpus.ValueKind ==
                         JsonValueKind.Object)
                {
                    profile.Gpus.Add(
                        ParseGpu(gpus));
                }
            }

            profile.GpuDetected =
                profile.Gpus.Any(
                    gpu =>
                        gpu.LooksLikeDedicatedGpu);

            profile.Notes.Add(
                $"Detected {profile.LogicalProcessors} logical CPU processors " +
                $"and {profile.TotalRamGb:0.0} GB RAM.");
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            profile.CpuName =
                Environment.GetEnvironmentVariable(
                    "PROCESSOR_IDENTIFIER")
                ?? "Unknown CPU";

            profile.LogicalProcessors =
                Environment.ProcessorCount;

            profile.Notes.Add(
                "Detailed Windows hardware detection failed: " +
                e.Message);

            profile.Notes.Add(
                "Conservative CPU settings will be used.");
        }

        return profile;
    }

    private static AiGpuInfo ParseGpu(
        JsonElement element)
    {
        var name =
            GetString(
                element,
                "Name")
            ?? "Unknown GPU";

        var lower =
            name.ToLowerInvariant();

        var dedicated =
            lower.Contains("nvidia") ||
            lower.Contains("geforce") ||
            lower.Contains("quadro") ||
            lower.Contains("tesla") ||
            lower.Contains("radeon") ||
            lower.Contains("arc");

        var vendor =
            lower.Contains("nvidia")
                ? "NVIDIA"
                : lower.Contains("amd") ||
                  lower.Contains("radeon")
                    ? "AMD"
                    : lower.Contains("intel")
                        ? "Intel"
                        : "Unknown";

        return new AiGpuInfo
        {
            Name =
                name,

            Vendor =
                vendor,

            VramGb =
                BytesToGb(
                    GetLong(
                        element,
                        "AdapterRAM")),

            DriverVersion =
                GetString(
                    element,
                    "DriverVersion")
                ?? "",

            LooksLikeDedicatedGpu =
                dedicated
        };
    }

    // ========================================================================
    // INITIAL HARDWARE PROFILE
    // ========================================================================

    private static void SelectInitialProfile(
        AiHardwareProfile profile)
    {
        // Start conservatively. The real Ollama probe may upgrade this.

        profile.Backend =
            AiComputeBackend.Cpu;

        profile.RecommendedCpuOnly =
            true;

        if (profile.TotalRamGb <= 0)
        {
            profile.PerformanceClass =
                AiPerformanceClass.Minimal;

            profile.RecommendedModel =
                LightweightModel;

            return;
        }

        if (profile.TotalRamGb < 8)
        {
            profile.PerformanceClass =
                AiPerformanceClass.Minimal;

            profile.RecommendedModel =
                LightweightModel;

            profile.Notes.Add(
                "Less than 8 GB system RAM detected.");

            return;
        }

        if (profile.TotalRamGb < 16)
        {
            profile.PerformanceClass =
                AiPerformanceClass.Lightweight;

            profile.RecommendedModel =
                LightweightModel;

            return;
        }

        if (profile.TotalRamGb < 32)
        {
            profile.PerformanceClass =
                AiPerformanceClass.Balanced;

            profile.RecommendedModel =
                BalancedModel;

            return;
        }

        profile.PerformanceClass =
            AiPerformanceClass.Balanced;

        profile.RecommendedModel =
            BalancedModel;
    }

    // ========================================================================
    // GPU CAPABILITY TEST
    // ========================================================================

    private async Task TestGpuAcceleration(
        AiHardwareProfile profile,
        CancellationToken ct)
    {
        profile.GpuAccelerationTested =
            true;

        var probeModel =
            SelectInstalledProbeModel(
                profile);

        if (probeModel == null)
        {
            profile.GpuAccelerationAvailable =
                false;

            profile.Backend =
                AiComputeBackend.Cpu;

            profile.RecommendedCpuOnly =
                true;

            profile.GpuFailureReason =
                "No suitable installed Ollama model was available " +
                "for the GPU capability probe.";

            profile.Notes.Add(
                "GPU detected, but acceleration could not yet be verified " +
                "because no suitable local model was installed.");

            return;
        }

        try
        {
            var result =
                await RunBenchmark(
                    probeModel,
                    cpuOnly: false,
                    ct);

            if (!result.Success)
            {
                profile.GpuAccelerationAvailable =
                    false;

                profile.Backend =
                    AiComputeBackend.Cpu;

                profile.RecommendedCpuOnly =
                    true;

                profile.GpuFailureReason =
                    result.Error;

                profile.Notes.Add(
                    "Ollama GPU probe failed. CPU mode selected.");

                return;
            }

            profile.GpuAccelerationAvailable =
                true;

            profile.Backend =
                AiComputeBackend.Gpu;

            profile.RecommendedCpuOnly =
                false;

            profile.Notes.Add(
                $"Ollama GPU probe succeeded using {probeModel}.");
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            profile.GpuAccelerationAvailable =
                false;

            profile.Backend =
                AiComputeBackend.Cpu;

            profile.RecommendedCpuOnly =
                true;

            profile.GpuFailureReason =
                e.Message;

            profile.Notes.Add(
                "Ollama GPU capability test failed. CPU mode selected.");
        }
    }

    // ========================================================================
    // MODEL SELECTION
    // ========================================================================

    private static void SelectModelForUsableHardware(
        AiHardwareProfile profile)
    {
        if (profile.Backend ==
            AiComputeBackend.Cpu)
        {
            SelectCpuModel(profile);
            return;
        }

        SelectGpuModel(profile);
    }

    private static void SelectCpuModel(
        AiHardwareProfile profile)
    {
        profile.RecommendedCpuOnly =
            true;

        // CPU inference becomes unpleasant very quickly with larger models.
        // RAM alone is therefore not treated as permission to choose 7B.

        if (profile.TotalRamGb >= 16 &&
            profile.LogicalProcessors >= 12)
        {
            profile.RecommendedModel =
                BalancedModel;

            profile.PerformanceClass =
                AiPerformanceClass.Balanced;

            profile.RecommendedContext =
                4096;

            profile.RecommendedPrediction =
                1024;

            profile.RecommendedBatch =
                32;

            return;
        }

        profile.RecommendedModel =
            LightweightModel;

        profile.PerformanceClass =
            profile.TotalRamGb >= 8
                ? AiPerformanceClass.Lightweight
                : AiPerformanceClass.Minimal;

        profile.RecommendedContext =
            4096;

        profile.RecommendedPrediction =
            1024;

        profile.RecommendedBatch =
            32;
    }

    private static void SelectGpuModel(
        AiHardwareProfile profile)
    {
        profile.RecommendedCpuOnly =
            false;

        var bestVram =
            profile.Gpus
                .Where(
                    gpu =>
                        gpu.LooksLikeDedicatedGpu)
                .Select(
                    gpu =>
                        gpu.VramGb)
                .DefaultIfEmpty(0)
                .Max();

        // WMI sometimes reports VRAM incorrectly. These are intentionally
        // conservative thresholds; the benchmark provides another safeguard.

        if (bestVram >= 16 &&
            profile.TotalRamGb >= 32)
        {
            profile.RecommendedModel =
                LargeModel;

            profile.PerformanceClass =
                AiPerformanceClass.HighEnd;

            profile.RecommendedContext =
                8192;

            profile.RecommendedPrediction =
                2048;

            profile.RecommendedBatch =
                128;

            return;
        }

        if (bestVram >= 6 &&
            profile.TotalRamGb >= 16)
        {
            profile.RecommendedModel =
                QualityModel;

            profile.PerformanceClass =
                AiPerformanceClass.Quality;

            profile.RecommendedContext =
                8192;

            profile.RecommendedPrediction =
                2048;

            profile.RecommendedBatch =
                128;

            return;
        }

        if (bestVram >= 4 &&
            profile.TotalRamGb >= 12)
        {
            profile.RecommendedModel =
                BalancedModel;

            profile.PerformanceClass =
                AiPerformanceClass.Balanced;

            profile.RecommendedContext =
                6144;

            profile.RecommendedPrediction =
                1536;

            profile.RecommendedBatch =
                96;

            return;
        }

        profile.RecommendedModel =
            LightweightModel;

        profile.PerformanceClass =
            AiPerformanceClass.Lightweight;

        profile.RecommendedContext =
            4096;

        profile.RecommendedPrediction =
            1024;

        profile.RecommendedBatch =
            64;
    }

    // ========================================================================
    // BENCHMARK RECOMMENDED MODEL
    // ========================================================================

    private async Task BenchmarkRecommendedProfile(
        AiHardwareProfile profile,
        CancellationToken ct)
    {
        var model =
            profile.RecommendedModel;

        // Do not silently download gigabytes just to benchmark.
        if (!ContainsModel(
                profile.InstalledModels,
                model))
        {
            var fallback =
                SelectInstalledProbeModel(
                    profile);

            if (fallback == null)
            {
                profile.BenchmarkSucceeded =
                    false;

                profile.BenchmarkFailureReason =
                    $"Recommended model {model} is not installed.";

                profile.Notes.Add(
                    $"Benchmark skipped because {model} is not installed.");

                return;
            }

            profile.Notes.Add(
                $"Recommended model {model} is not installed. " +
                $"Benchmarking installed model {fallback} instead.");

            model =
                fallback;
        }

        var result =
            await RunBenchmark(
                model,
                profile.RecommendedCpuOnly,
                ct);

        profile.BenchmarkModel =
            model;

        profile.BenchmarkSucceeded =
            result.Success;

        profile.BenchmarkTokensPerSecond =
            result.TokensPerSecond;

        profile.BenchmarkFirstTokenSeconds =
            result.FirstTokenSeconds;

        profile.BenchmarkDuration =
            result.Duration;

        profile.BenchmarkFailureReason =
            result.Error;

        if (result.Success)
        {
            profile.Notes.Add(
                $"Benchmark completed at " +
                $"{result.TokensPerSecond.GetValueOrDefault():0.0} tok/s.");
        }
        else
        {
            profile.Notes.Add(
                "Benchmark failed: " +
                result.Error);
        }
    }

    // ========================================================================
    // PERFORMANCE ADJUSTMENT
    // ========================================================================

    private static void ApplyBenchmarkAdjustment(
        AiHardwareProfile profile)
    {
        if (!profile.BenchmarkSucceeded)
        {
            // A failed GPU benchmark is treated seriously.
            if (profile.Backend ==
                AiComputeBackend.Gpu)
            {
                profile.Backend =
                    AiComputeBackend.Cpu;

                profile.RecommendedCpuOnly =
                    true;

                profile.RecommendedModel =
                    LightweightModel;

                profile.PerformanceClass =
                    AiPerformanceClass.Lightweight;

                profile.RecommendedContext =
                    4096;

                profile.RecommendedPrediction =
                    1024;

                profile.RecommendedBatch =
                    32;

                profile.Notes.Add(
                    "Failed accelerated benchmark caused a conservative " +
                    "fallback to the lightweight CPU profile.");
            }

            return;
        }

        if (!profile.BenchmarkTokensPerSecond.HasValue)
            return;

        var speed =
            profile.BenchmarkTokensPerSecond.Value;

        // Below ~4 tok/s is technically usable but poor for an application
        // that repeatedly generates evidence, scripts and metadata.
        if (speed < 4)
        {
            var smaller =
                StepDownModel(
                    profile.RecommendedModel);

            if (!string.Equals(
                    smaller,
                    profile.RecommendedModel,
                    StringComparison.OrdinalIgnoreCase))
            {
                profile.Notes.Add(
                    $"Measured generation speed was only {speed:0.0} tok/s. " +
                    $"Stepping down from {profile.RecommendedModel} " +
                    $"to {smaller}.");

                profile.RecommendedModel =
                    smaller;
            }

            profile.RecommendedContext =
                Math.Min(
                    profile.RecommendedContext,
                    4096);

            profile.RecommendedPrediction =
                Math.Min(
                    profile.RecommendedPrediction,
                    1024);

            profile.RecommendedBatch =
                profile.RecommendedCpuOnly
                    ? 32
                    : Math.Min(
                        profile.RecommendedBatch,
                        64);

            return;
        }

        if (speed < 8)
        {
            profile.Notes.Add(
                $"Generation speed of {speed:0.0} tok/s is usable, " +
                "but the conservative context profile will be retained.");

            profile.RecommendedContext =
                Math.Min(
                    profile.RecommendedContext,
                    4096);

            profile.RecommendedPrediction =
                Math.Min(
                    profile.RecommendedPrediction,
                    1024);

            return;
        }

        profile.Notes.Add(
            $"Generation speed of {speed:0.0} tok/s is suitable " +
            "for the selected profile.");
    }

    private static string StepDownModel(
        string model)
    {
        if (ModelMatches(
                model,
                LargeModel))
        {
            return QualityModel;
        }

        if (ModelMatches(
                model,
                QualityModel))
        {
            return BalancedModel;
        }

        if (ModelMatches(
                model,
                BalancedModel))
        {
            return LightweightModel;
        }

        return model;
    }

    // ========================================================================
    // ACTUAL OLLAMA BENCHMARK
    // ========================================================================

    private async Task<BenchmarkResult> RunBenchmark(
        string model,
        bool cpuOnly,
        CancellationToken ct)
    {
        Util.LocalEndpoint(
            settings.OllamaEndpoint);

        var endpoint =
            settings.OllamaEndpoint.TrimEnd('/') +
            "/api/generate";

        using var bound =
            CancellationTokenSource.CreateLinkedTokenSource(
                ct);

        bound.CancelAfter(
            TimeSpan.FromSeconds(
                OllamaProbeTimeoutSeconds));

        var options =
            new Dictionary<string, object>
            {
                ["temperature"] = 0,
                ["num_ctx"] = 2048,
                ["num_predict"] = 48,
                ["num_batch"] = cpuOnly ? 32 : 64
            };

        if (cpuOnly)
        {
            options["num_gpu"] =
                0;
        }

        var request =
            new
            {
                model,

                prompt =
                    "Reply with one short sentence explaining why " +
                    "local hardware benchmarking is useful.",

                stream =
                    true,

                keep_alive =
                    0,

                options
            };

        var watch =
            Stopwatch.StartNew();

        try
        {
            using var message =
                new HttpRequestMessage(
                    HttpMethod.Post,
                    endpoint)
                {
                    Content =
                        JsonContent.Create(
                            request)
                };

            using var response =
                await http.SendAsync(
                    message,
                    HttpCompletionOption.ResponseHeadersRead,
                    bound.Token);

            if (!response.IsSuccessStatusCode)
            {
                var errorBody =
                    await response.Content
                        .ReadAsStringAsync(
                            bound.Token);

                return BenchmarkResult.Failed(
                    watch.Elapsed,
                    ExtractError(
                        errorBody,
                        response.StatusCode));
            }

            await using var stream =
                await response.Content
                    .ReadAsStreamAsync(
                        bound.Token);

            using var reader =
                new StreamReader(stream);

            var generated =
                new StringBuilder();

            double? firstToken =
                null;

            long? evalCount =
                null;

            long? evalDuration =
                null;

            while (true)
            {
                var line =
                    await reader.ReadLineAsync(
                        bound.Token);

                if (line == null)
                    break;

                if (string.IsNullOrWhiteSpace(line))
                    continue;

                using var packet =
                    JsonDocument.Parse(line);

                var root =
                    packet.RootElement;

                if (root.TryGetProperty(
                        "error",
                        out var error))
                {
                    return BenchmarkResult.Failed(
                        watch.Elapsed,
                        error.ValueKind ==
                        JsonValueKind.String
                            ? error.GetString()
                              ?? "Unknown Ollama error."
                            : error.ToString());
                }

                if (root.TryGetProperty(
                        "response",
                        out var output)
                    &&
                    output.ValueKind ==
                    JsonValueKind.String)
                {
                    var chunk =
                        output.GetString()
                        ?? "";

                    if (chunk.Length > 0)
                    {
                        if (!firstToken.HasValue)
                        {
                            firstToken =
                                watch.Elapsed.TotalSeconds;
                        }

                        generated.Append(
                            chunk);
                    }
                }

                if (root.TryGetProperty(
                        "eval_count",
                        out var evalCountElement)
                    &&
                    evalCountElement.TryGetInt64(
                        out var count))
                {
                    evalCount =
                        count;
                }

                if (root.TryGetProperty(
                        "eval_duration",
                        out var evalDurationElement)
                    &&
                    evalDurationElement.TryGetInt64(
                        out var duration))
                {
                    evalDuration =
                        duration;
                }

                var done =
                    root.TryGetProperty(
                        "done",
                        out var doneElement)
                    &&
                    doneElement.ValueKind ==
                    JsonValueKind.True;

                if (done)
                    break;
            }

            watch.Stop();

            if (generated.Length == 0)
            {
                return BenchmarkResult.Failed(
                    watch.Elapsed,
                    "Ollama returned no generated benchmark output.");
            }

            double? tokensPerSecond =
                null;

            if (evalCount.HasValue &&
                evalDuration.HasValue &&
                evalCount.Value > 0 &&
                evalDuration.Value > 0)
            {
                var seconds =
                    evalDuration.Value /
                    1_000_000_000d;

                if (seconds > 0)
                {
                    tokensPerSecond =
                        evalCount.Value /
                        seconds;
                }
            }

            return new BenchmarkResult
            {
                Success =
                    true,

                Duration =
                    watch.Elapsed,

                FirstTokenSeconds =
                    firstToken,

                TokensPerSecond =
                    tokensPerSecond,

                GeneratedCharacters =
                    generated.Length
            };
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            watch.Stop();

            return BenchmarkResult.Failed(
                watch.Elapsed,
                $"Ollama benchmark exceeded " +
                $"{OllamaProbeTimeoutSeconds} seconds.");
        }
        catch (HttpRequestException e)
        {
            watch.Stop();

            return BenchmarkResult.Failed(
                watch.Elapsed,
                "Could not communicate with Ollama: " +
                e.Message);
        }
        catch (JsonException e)
        {
            watch.Stop();

            return BenchmarkResult.Failed(
                watch.Elapsed,
                "Ollama returned malformed benchmark data: " +
                e.Message);
        }
        catch (Exception e)
        {
            watch.Stop();

            return BenchmarkResult.Failed(
                watch.Elapsed,
                e.Message);
        }
    }

    // ========================================================================
    // INSTALLED MODELS
    // ========================================================================

    private async Task<List<string>> GetInstalledModels(
        CancellationToken ct)
    {
        var result =
            new List<string>();

        try
        {
            Util.LocalEndpoint(
                settings.OllamaEndpoint);

            var endpoint =
                settings.OllamaEndpoint.TrimEnd('/') +
                "/api/tags";

            using var bound =
                CancellationTokenSource.CreateLinkedTokenSource(
                    ct);

            bound.CancelAfter(
                TimeSpan.FromSeconds(15));

            using var response =
                await http.GetAsync(
                    endpoint,
                    bound.Token);

            if (!response.IsSuccessStatusCode)
                return result;

            var body =
                await response.Content
                    .ReadAsStringAsync(
                        bound.Token);

            using var document =
                JsonDocument.Parse(
                    body);

            if (!document.RootElement.TryGetProperty(
                    "models",
                    out var models)
                ||
                models.ValueKind !=
                JsonValueKind.Array)
            {
                return result;
            }

            foreach (var model in
                     models.EnumerateArray())
            {
                var name =
                    GetString(
                        model,
                        "name");

                if (!string.IsNullOrWhiteSpace(
                        name))
                {
                    result.Add(
                        name);
                }
            }
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch
        {
            // Setup diagnostics will separately report Ollama being offline.
        }

        return result;
    }

    private static string? SelectInstalledProbeModel(
        AiHardwareProfile profile)
    {
        string[] preference =
        [
            LightweightModel,
            BalancedModel,
            QualityModel,
            LargeModel
        ];

        foreach (var preferred in
                 preference)
        {
            var installed =
                profile.InstalledModels
                    .FirstOrDefault(
                        model =>
                            ModelMatches(
                                model,
                                preferred));

            if (installed != null)
                return installed;
        }

        // If the user has some other Ollama model installed, it can still
        // provide a basic backend capability test.
        return profile.InstalledModels
            .FirstOrDefault();
    }

    private static bool ContainsModel(
        IEnumerable<string> models,
        string requested)
    {
        return models.Any(
            model =>
                ModelMatches(
                    model,
                    requested));
    }

    private static bool ModelMatches(
        string installed,
        string requested)
    {
        if (string.Equals(
                installed,
                requested,
                StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        if (!requested.Contains(':')
            &&
            string.Equals(
                installed,
                requested + ":latest",
                StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return false;
    }

    // ========================================================================
    // FINALISE
    // ========================================================================

    private static void FinaliseProfile(
        AiHardwareProfile profile)
    {
        profile.AssessedAt =
            DateTimeOffset.UtcNow;

        if (profile.Backend ==
            AiComputeBackend.Unknown)
        {
            profile.Backend =
                AiComputeBackend.Cpu;

            profile.RecommendedCpuOnly =
                true;
        }

        // Keep CPU configurations conservative.
        if (profile.RecommendedCpuOnly)
        {
            profile.RecommendedContext =
                Math.Min(
                    profile.RecommendedContext,
                    4096);

            profile.RecommendedPrediction =
                Math.Min(
                    profile.RecommendedPrediction,
                    1024);

            profile.RecommendedBatch =
                Math.Min(
                    profile.RecommendedBatch,
                    32);
        }
    }

    // ========================================================================
    // STORAGE
    // ========================================================================

    private double DetectFreeDiskSpace()
    {
        try
        {
            var projectRoot =
                string.IsNullOrWhiteSpace(
                    settings.ProjectRoot)
                    ? Environment.GetFolderPath(
                        Environment.SpecialFolder.MyDocuments)
                    : settings.ProjectRoot;

            var full =
                Path.GetFullPath(
                    projectRoot);

            var root =
                Path.GetPathRoot(
                    full);

            if (string.IsNullOrWhiteSpace(root))
                return 0;

            var drive =
                new DriveInfo(
                    root);

            return
                drive.AvailableFreeSpace /
                1_000_000_000d;
        }
        catch
        {
            return 0;
        }
    }

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    private static string ExtractError(
        string body,
        System.Net.HttpStatusCode status)
    {
        if (string.IsNullOrWhiteSpace(body))
        {
            return
                $"Ollama returned HTTP {(int)status}.";
        }

        try
        {
            using var document =
                JsonDocument.Parse(
                    body);

            if (document.RootElement.TryGetProperty(
                    "error",
                    out var error))
            {
                var value =
                    error.ValueKind ==
                    JsonValueKind.String
                        ? error.GetString()
                        : error.ToString();

                if (!string.IsNullOrWhiteSpace(value))
                    return value;
            }
        }
        catch (JsonException)
        {
            // Use safe body snippet below.
        }

        body =
            body
                .Replace(
                    "\r",
                    " ")
                .Replace(
                    "\n",
                    " ")
                .Trim();

        if (body.Length > 1200)
        {
            body =
                body[..1200] +
                "...";
        }

        return
            $"HTTP {(int)status}: {body}";
    }

    // ========================================================================
    // JSON HELPERS
    // ========================================================================

    private static string? GetString(
        JsonElement element,
        string property)
    {
        if (!element.TryGetProperty(
                property,
                out var value))
        {
            return null;
        }

        return value.ValueKind ==
               JsonValueKind.String
            ? value.GetString()
            : value.ToString();
    }

    private static long GetLong(
        JsonElement element,
        string property)
    {
        if (!element.TryGetProperty(
                property,
                out var value))
        {
            return 0;
        }

        if (value.ValueKind ==
            JsonValueKind.Number
            &&
            value.TryGetInt64(
                out var number))
        {
            return number;
        }

        return 0;
    }

    private static int GetInt(
        JsonElement element,
        string property)
    {
        if (!element.TryGetProperty(
                property,
                out var value))
        {
            return 0;
        }

        if (value.ValueKind ==
            JsonValueKind.Number
            &&
            value.TryGetInt32(
                out var number))
        {
            return number;
        }

        return 0;
    }

    private static double BytesToGb(
        long bytes)
    {
        if (bytes <= 0)
            return 0;

        return
            bytes /
            (1024d * 1024d * 1024d);
    }

    private static string BackendText(
        AiHardwareProfile profile)
    {
        return profile.Backend switch
        {
            AiComputeBackend.Gpu =>
                "GPU acceleration",

            AiComputeBackend.Cpu =>
                "CPU-only",

            _ =>
                "unknown backend"
        };
    }

    // ========================================================================
    // INTERNAL BENCHMARK RESULT
    // ========================================================================

    private sealed class BenchmarkResult
    {
        public bool Success { get; init; }

        public TimeSpan Duration { get; init; }

        public double? FirstTokenSeconds { get; init; }

        public double? TokensPerSecond { get; init; }

        public int GeneratedCharacters { get; init; }

        public string Error { get; init; } =
            "";

        public static BenchmarkResult Failed(
            TimeSpan duration,
            string error)
        {
            return new BenchmarkResult
            {
                Success =
                    false,

                Duration =
                    duration,

                Error =
                    error
            };
        }
    }
}