using System.IO.Compression;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace JengaVideoStudio.Core;

public sealed class SetupService(
    Settings settings,
    HttpClient http)
{
    private const string PiperVersion = "1.8.0";
    private const string PiperVoiceName = "en_US-lessac-medium";

    // =====================================================================
    // DIAGNOSTICS
    // =====================================================================

    public async Task<string> Diagnose(
        CancellationToken ct)
    {
        var report = new List<string>();

        report.Add("JENGA VIDEO STUDIO — SYSTEM DIAGNOSTICS");
        report.Add(
            $"Checked: {DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss}");

        await DiagnoseAiHardware(report, ct);

        DiagnoseStorage(report);

        await DiagnoseOllama(report, ct);

        await DiagnoseExecutable(
            report,
            "FFmpeg",
            settings.Ffmpeg,
            ct);

        await DiagnoseExecutable(
            report,
            "FFprobe",
            settings.Ffprobe,
            ct);

        await DiagnoseSpeech(report, ct);

        await DiagnoseComfyUi(report, ct);

        return string.Join(
            Environment.NewLine + Environment.NewLine,
            report);
    }

    // =====================================================================
    // AI HARDWARE ASSESSMENT
    // =====================================================================

    /// <summary>
    /// Runs the real hardware/Ollama profiler, applies the recommended
    /// compute profile to Settings, saves it, and writes a reusable copy of
    /// the assessment to ai-hardware-profile.json.
    ///
    /// This deliberately tests Ollama rather than assuming that a GPU
    /// reported by Windows is actually usable.
    /// </summary>
    private async Task DiagnoseAiHardware(
        List<string> report,
        CancellationToken ct)
    {
        try
        {
            var profiler =
                new AiHardwareProfiler(
                    settings,
                    http);

            var profile =
                await profiler.Assess(
                    probeOllama: true,
                    progress: null,
                    ct);

            await ApplyHardwareProfile(
                profile,
                ct);

            report.Add(
                BuildHardwareReport(
                    profile));
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            report.Add(
                "AI HARDWARE — ASSESSMENT FAILED\n" +
                e.Message +
                "\nExisting AI settings have been left in place.");
        }
    }

    /// <summary>
    /// Public entry point for the Setup screen when the user explicitly asks
    /// Jenga to reassess this PC.
    /// </summary>
    public async Task<AiHardwareProfile> AssessAiHardware(
        IProgress<ProgressUpdate>? progress,
        CancellationToken ct)
    {
        var profiler =
            new AiHardwareProfiler(
                settings,
                http);

        var profile =
            await profiler.Assess(
                probeOllama: true,
                progress,
                ct);

        await ApplyHardwareProfile(
            profile,
            ct);

        return profile;
    }

    private async Task ApplyHardwareProfile(
        AiHardwareProfile profile,
        CancellationToken ct)
    {
        if (profile == null)
        {
            throw new ArgumentNullException(
                nameof(profile));
        }

        if (string.IsNullOrWhiteSpace(
                profile.RecommendedModel))
        {
            throw new InvalidDataException(
                "The AI hardware profiler did not select a model.");
        }

        settings.CpuOnly =
            profile.RecommendedCpuOnly;

        settings.Model =
            profile.RecommendedModel.Trim();

        await settings.Save();

        Directory.CreateDirectory(
            Settings.DataRoot);

        await Json.Save(
            Path.Combine(
                Settings.DataRoot,
                "ai-hardware-profile.json"),
            profile,
            ct);
    }

    private static string BuildHardwareReport(
        AiHardwareProfile profile)
    {
        var gpuText =
            profile.Gpus.Count == 0
                ? "None detected"
                : string.Join(
                    ", ",
                    profile.Gpus.Select(
                        gpu =>
                            gpu.VramGb > 0
                                ? $"{gpu.Name} ({gpu.VramGb:0.0} GB VRAM)"
                                : gpu.Name));

        var backend =
            profile.Backend switch
            {
                AiComputeBackend.Gpu =>
                    "GPU acceleration",

                AiComputeBackend.Cpu =>
                    "CPU-only",

                _ =>
                    "Unknown"
            };

        var benchmark =
            profile.BenchmarkSucceeded
                ? profile.BenchmarkTokensPerSecond.HasValue
                    ? $"{profile.BenchmarkTokensPerSecond.Value:0.0} tokens/sec"
                    : "Completed"
                : string.IsNullOrWhiteSpace(
                    profile.BenchmarkFailureReason)
                    ? "Not completed"
                    : "Failed — " +
                      Shorten(
                          profile.BenchmarkFailureReason,
                          300);

        var gpuTest =
            !profile.GpuAccelerationTested
                ? "Not tested"
                : profile.GpuAccelerationAvailable
                    ? "Passed"
                    : "Failed";

        var gpuFailure =
            !profile.GpuAccelerationAvailable &&
            !string.IsNullOrWhiteSpace(
                profile.GpuFailureReason)
                ? "\nGPU failure: " +
                  Shorten(
                      profile.GpuFailureReason,
                      500)
                : "";

        var notes =
            profile.Notes.Count == 0
                ? ""
                : "\nNotes:\n- " +
                  string.Join(
                      "\n- ",
                      profile.Notes);

        return
            "AI HARDWARE — PROFILE SELECTED\n" +
            $"CPU: {profile.CpuName}\n" +
            $"Logical processors: {profile.LogicalProcessors}\n" +
            $"RAM: {profile.TotalRamGb:0.0} GB total · " +
            $"{profile.AvailableRamGb:0.0} GB currently available\n" +
            $"GPU: {gpuText}\n" +
            $"GPU Ollama test: {gpuTest}{gpuFailure}\n" +
            $"Usable backend: {backend}\n" +
            $"Performance class: {profile.PerformanceClass}\n" +
            $"Recommended model: {profile.RecommendedModel}\n" +
            $"CPU-only: {(profile.RecommendedCpuOnly ? "yes" : "no")}\n" +
            $"Context: {profile.RecommendedContext:N0}\n" +
            $"Output limit: {profile.RecommendedPrediction:N0} tokens\n" +
            $"Batch: {profile.RecommendedBatch:N0}\n" +
            $"Benchmark model: " +
            $"{(string.IsNullOrWhiteSpace(profile.BenchmarkModel) ? "none" : profile.BenchmarkModel)}\n" +
            $"Benchmark: {benchmark}\n" +
            $"Free project-drive space: {profile.FreeDiskGb:0.0} GB" +
            notes;
    }

    // =====================================================================
    // STORAGE
    // =====================================================================

    private void DiagnoseStorage(
        List<string> report)
    {
        try
        {
            Directory.CreateDirectory(
                settings.ProjectRoot);

            var full =
                Path.GetFullPath(
                    settings.ProjectRoot);

            var root =
                Path.GetPathRoot(full);

            if (string.IsNullOrWhiteSpace(root))
            {
                throw new InvalidOperationException(
                    "Could not determine the project drive.");
            }

            var drive =
                new DriveInfo(root);

            var freeGb =
                drive.AvailableFreeSpace /
                1_000_000_000d;

            var status =
                freeGb switch
                {
                    < 2 => "CRITICAL",
                    < 10 => "LOW",
                    _ => "READY"
                };

            report.Add(
                $"PROJECT STORAGE — {status}\n" +
                $"Location: {full}\n" +
                $"Free space: {freeGb:0.0} GB");
        }
        catch (Exception e)
        {
            report.Add(
                "PROJECT STORAGE — ERROR\n" +
                e.Message);
        }
    }

    // =====================================================================
    // OLLAMA
    // =====================================================================

    /// <summary>
    /// Reports the Ollama/model state after DiagnoseAiHardware has selected
    /// and saved the machine-specific AI profile.
    /// </summary>
    private async Task DiagnoseOllama(
        List<string> report,
        CancellationToken ct)
    {
        try
        {
            Util.LocalEndpoint(
                settings.OllamaEndpoint);

            var endpoint =
                settings.OllamaEndpoint.TrimEnd('/');

            using var bound =
                CancellationTokenSource
                    .CreateLinkedTokenSource(ct);

            bound.CancelAfter(
                TimeSpan.FromSeconds(15));

            using var response =
                await http.GetAsync(
                    endpoint + "/api/tags",
                    bound.Token);

            var body =
                await response.Content
                    .ReadAsStringAsync(
                        bound.Token);

            if (!response.IsSuccessStatusCode)
            {
                report.Add(
                    "LOCAL AI — OLLAMA ERROR\n" +
                    $"HTTP {(int)response.StatusCode} " +
                    $"{response.ReasonPhrase}");

                return;
            }

            using var document =
                JsonDocument.Parse(body);

            if (!document.RootElement.TryGetProperty(
                    "models",
                    out var modelsElement)
                ||
                modelsElement.ValueKind !=
                JsonValueKind.Array)
            {
                report.Add(
                    "LOCAL AI — INVALID RESPONSE\n" +
                    "Ollama is responding, but /api/tags " +
                    "did not contain a models array.");

                return;
            }

            var models =
                modelsElement
                    .EnumerateArray()
                    .ToList();

            var configuredModel =
                FindModel(
                    models,
                    settings.Model);

            var mode =
                settings.CpuOnly
                    ? "CPU ONLY"
                    : "ACCELERATED / AUTOMATIC";

            if (configuredModel == null)
            {
                report.Add(
                    "LOCAL AI — RECOMMENDED MODEL NOT INSTALLED\n" +
                    "Ollama: online\n" +
                    $"Selected model: {settings.Model}\n" +
                    $"Execution mode: {mode}\n" +
                    "Use Download text model in Setup. " +
                    "Jenga will download the model selected by the " +
                    "hardware profiler.");
            }
            else
            {
                var size =
                    GetLong(
                        configuredModel.Value,
                        "size");

                report.Add(
                    "LOCAL AI — READY\n" +
                    $"Model: {settings.Model}\n" +
                    $"Size: {size / 1e9:0.00} GB\n" +
                    $"Execution mode: {mode}\n" +
                    "The selected model comes from the saved " +
                    "AI hardware profile.");
            }

            foreach (var model in models)
            {
                var name =
                    GetString(
                        model,
                        "name")
                    ?? "unknown";

                var size =
                    GetLong(
                        model,
                        "size");

                var digest =
                    GetString(
                        model,
                        "digest")
                    ?? "unknown";

                report.Add(
                    "OLLAMA MODEL\n" +
                    $"Name: {name}\n" +
                    $"Size: {size / 1e9:0.00} GB\n" +
                    $"Digest: {Shorten(digest, 24)}");
            }

            Directory.CreateDirectory(
                Settings.DataRoot);

            await File.WriteAllTextAsync(
                Path.Combine(
                    Settings.DataRoot,
                    "installed-models.json"),
                body,
                ct);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            report.Add(
                "LOCAL AI — TIMEOUT\n" +
                "Ollama did not respond within 15 seconds.");
        }
        catch (HttpRequestException e)
        {
            report.Add(
                "LOCAL AI — OFFLINE\n" +
                "Jenga could not connect to Ollama.\n" +
                e.Message);
        }
        catch (Exception e)
        {
            report.Add(
                "LOCAL AI — ERROR\n" +
                e.Message);
        }
    }

    private static JsonElement? FindModel(
        IEnumerable<JsonElement> models,
        string requested)
    {
        foreach (var model in models)
        {
            var name =
                GetString(
                    model,
                    "name");

            if (string.Equals(
                    name,
                    requested,
                    StringComparison.OrdinalIgnoreCase))
            {
                return model;
            }

            if (!requested.Contains(':')
                &&
                string.Equals(
                    name,
                    requested + ":latest",
                    StringComparison.OrdinalIgnoreCase))
            {
                return model;
            }
        }

        return null;
    }

    // =====================================================================
    // OLLAMA MODEL DOWNLOAD
    // =====================================================================

    /// <summary>
    /// Downloads the model selected by AiHardwareProfiler.
    ///
    /// A fresh assessment is performed first so an old saved 7B default
    /// cannot silently override the machine-specific recommendation.
    /// </summary>
    public async Task PullModel(
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        Util.LocalEndpoint(
            settings.OllamaEndpoint);

        progress.Report(
            new ProgressUpdate(
                "Installing AI",
                "Assessing this PC before selecting a model",
                0,
                true));

        var profiler =
            new AiHardwareProfiler(
                settings,
                http);

        var profile =
            await profiler.Assess(
                probeOllama: true,
                progress,
                ct);

        await ApplyHardwareProfile(
            profile,
            ct);

        progress.Report(
            new ProgressUpdate(
                "Installing AI",
                $"Selected {settings.Model} · " +
                $"{(settings.CpuOnly ? "CPU-only" : "accelerated")}",
                0,
                true));

        var endpoint =
            settings.OllamaEndpoint.TrimEnd('/');

        using var request =
            new HttpRequestMessage(
                HttpMethod.Post,
                endpoint + "/api/pull")
            {
                Content =
                    JsonContent.Create(
                        new
                        {
                            model =
                                settings.Model,

                            stream =
                                true
                        })
            };

        using var response =
            await http.SendAsync(
                request,
                HttpCompletionOption.ResponseHeadersRead,
                ct);

        if (!response.IsSuccessStatusCode)
        {
            var error =
                await response.Content
                    .ReadAsStringAsync(ct);

            throw new InvalidOperationException(
                $"Ollama model download failed: HTTP " +
                $"{(int)response.StatusCode}. " +
                Shorten(
                    error,
                    1000));
        }

        await using var stream =
            await response.Content
                .ReadAsStreamAsync(ct);

        using var reader =
            new StreamReader(stream);

        var layers =
            new Dictionary<
                string,
                (long Done, long Total)>();

        var success =
            false;

        while (await reader.ReadLineAsync(ct)
               is { } line)
        {
            if (string.IsNullOrWhiteSpace(line))
                continue;

            using var document =
                JsonDocument.Parse(line);

            var element =
                document.RootElement;

            if (element.TryGetProperty(
                    "error",
                    out var error))
            {
                throw new InvalidOperationException(
                    error.GetString()
                    ??
                    "Ollama reported an unknown download error.");
            }

            var status =
                GetString(
                    element,
                    "status")
                ?? "Downloading";

            var total =
                GetLong(
                    element,
                    "total");

            var completed =
                GetLong(
                    element,
                    "completed");

            var digest =
                GetString(
                    element,
                    "digest");

            if (!string.IsNullOrWhiteSpace(
                    digest))
            {
                layers[digest] =
                    (completed, total);
            }

            var allTotal =
                layers.Values.Sum(
                    x => x.Total);

            var allDone =
                layers.Values.Sum(
                    x => x.Done);

            var percentage =
                allTotal > 0
                    ? Math.Clamp(
                        100d * allDone / allTotal,
                        0,
                        100)
                    : 0;

            progress.Report(
                new ProgressUpdate(
                    "Installing AI",
                    allTotal > 0
                        ? $"{status} · " +
                          $"{allDone / 1e9:0.00} / " +
                          $"{allTotal / 1e9:0.00} GB · " +
                          $"{settings.Model}"
                        : $"{status} · {settings.Model}",
                    percentage,
                    allTotal == 0));

            if (string.Equals(
                    status,
                    "success",
                    StringComparison.OrdinalIgnoreCase))
            {
                success =
                    true;
            }
        }

        if (!success)
        {
            throw new IOException(
                "Ollama download ended without confirming success. " +
                "Retrying is safe because Ollama can reuse downloaded layers.");
        }

        await settings.Save();

        progress.Report(
            new ProgressUpdate(
                "Installing AI",
                $"{settings.Model} installed and selected · " +
                $"{(settings.CpuOnly ? "CPU-only" : "accelerated")}",
                100,
                false));
    }

    // =====================================================================
    // EXECUTABLE DIAGNOSTICS
    // =====================================================================

    private static async Task DiagnoseExecutable(
        List<string> report,
        string label,
        string executable,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(executable))
        {
            report.Add(
                $"{label.ToUpperInvariant()} — NOT CONFIGURED");

            return;
        }

        try
        {
            var version =
                await ChildProcess.Run(
                    executable,
                    ["-version"],
                    null,
                    null,
                    TimeSpan.FromSeconds(20),
                    ct);

            var firstLine =
                version
                    .Split(
                        ['\r', '\n'],
                        StringSplitOptions.RemoveEmptyEntries)
                    .FirstOrDefault()
                ?? "version detected";

            report.Add(
                $"{label.ToUpperInvariant()} — READY\n" +
                firstLine);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            report.Add(
                $"{label.ToUpperInvariant()} — MISSING / FAILED\n" +
                $"Configured path: {executable}\n" +
                e.Message);
        }
    }

    // =====================================================================
    // SPEECH DIAGNOSTICS
    // =====================================================================

    private async Task DiagnoseSpeech(
        List<string> report,
        CancellationToken ct)
    {
        if (!string.Equals(
                settings.SpeechProvider,
                "Piper",
                StringComparison.OrdinalIgnoreCase))
        {
            report.Add(
                "NARRATION — WINDOWS SPEECH SELECTED\n" +
                $"Voice: " +
                $"{(string.IsNullOrWhiteSpace(settings.WindowsVoice) ? "Windows default" : settings.WindowsVoice)}\n" +
                "Use Test narration to verify audio output.");

            return;
        }

        var problems =
            new List<string>();

        if (string.IsNullOrWhiteSpace(
                settings.PiperPython)
            ||
            !File.Exists(
                settings.PiperPython))
        {
            problems.Add(
                "Piper Python executable is missing.");
        }

        if (string.IsNullOrWhiteSpace(
                settings.PiperVoice)
            ||
            !File.Exists(
                settings.PiperVoice))
        {
            problems.Add(
                "Piper voice model is missing.");
        }

        if (!string.IsNullOrWhiteSpace(
                settings.PiperVoice)
            &&
            File.Exists(settings.PiperVoice))
        {
            var config1 =
                settings.PiperVoice + ".json";

            var config2 =
                Path.ChangeExtension(
                    settings.PiperVoice,
                    ".json");

            if (!File.Exists(config1)
                &&
                !File.Exists(config2))
            {
                problems.Add(
                    "Piper voice configuration JSON is missing.");
            }
        }

        if (problems.Count > 0)
        {
            report.Add(
                "NARRATION — PIPER INCOMPLETE\n" +
                string.Join(
                    "\n",
                    problems));

            return;
        }

        try
        {
            var result =
                await ChildProcess.Run(
                    settings.PiperPython,
                    [
                        "-c",
                        "import piper; print('PIPER_OK')"
                    ],
                    null,
                    null,
                    TimeSpan.FromSeconds(20),
                    ct);

            if (!result.Contains(
                    "PIPER_OK",
                    StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "Python did not confirm the Piper package.");
            }

            report.Add(
                "NARRATION — PIPER READY\n" +
                $"Python: {settings.PiperPython}\n" +
                $"Voice: {Path.GetFileName(settings.PiperVoice)}\n" +
                "Use Test narration for an end-to-end audio test.");
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            report.Add(
                "NARRATION — PIPER FAILED\n" +
                e.Message);
        }
    }

    // =====================================================================
    // COMFYUI DIAGNOSTICS
    // =====================================================================

    private async Task DiagnoseComfyUi(
        List<string> report,
        CancellationToken ct)
    {
        if (!settings.EnableImages
            &&
            !settings.EnableVideo)
        {
            report.Add(
                "AI VISUALS — OPTIONAL / DISABLED\n" +
                "Jenga will use deterministic graphics. " +
                "ComfyUI is not required.");

            return;
        }

        var problems =
            new List<string>();

        if (settings.EnableImages
            &&
            (
                string.IsNullOrWhiteSpace(
                    settings.ImageWorkflow)
                ||
                !File.Exists(
                    settings.ImageWorkflow)
            ))
        {
            problems.Add(
                "Image workflow is missing.");
        }

        if (settings.EnableVideo
            &&
            (
                string.IsNullOrWhiteSpace(
                    settings.VideoWorkflow)
                ||
                !File.Exists(
                    settings.VideoWorkflow)
            ))
        {
            problems.Add(
                "Video workflow is missing.");
        }

        if (problems.Count > 0)
        {
            report.Add(
                "AI VISUALS — CONFIGURATION INCOMPLETE\n" +
                string.Join(
                    "\n",
                    problems));

            return;
        }

        try
        {
            Util.LocalEndpoint(
                settings.ComfyEndpoint);

            using var bound =
                CancellationTokenSource
                    .CreateLinkedTokenSource(ct);

            bound.CancelAfter(
                TimeSpan.FromSeconds(10));

            using var response =
                await http.GetAsync(
                    settings.ComfyEndpoint.TrimEnd('/')
                    + "/system_stats",
                    bound.Token);

            if (!response.IsSuccessStatusCode)
            {
                report.Add(
                    "AI VISUALS — COMFYUI ERROR\n" +
                    $"HTTP {(int)response.StatusCode} " +
                    $"{response.ReasonPhrase}");

                return;
            }

            report.Add(
                "AI VISUALS — COMFYUI READY\n" +
                $"Endpoint: {settings.ComfyEndpoint}\n" +
                $"Images: {(settings.EnableImages ? "enabled" : "disabled")}\n" +
                $"Video: {(settings.EnableVideo ? "enabled" : "disabled")}");
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            report.Add(
                "AI VISUALS — COMFYUI TIMEOUT\n" +
                "ComfyUI did not respond within 10 seconds.");
        }
        catch (Exception e)
        {
            report.Add(
                "AI VISUALS — COMFYUI OFFLINE\n" +
                "Generated graphics remain available as the fallback.\n" +
                e.Message);
        }
    }

    // =====================================================================
    // GENERIC DOWNLOAD
    // =====================================================================

    public async Task Download(
        string url,
        string path,
        long maxBytes,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        if (maxBytes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(maxBytes));
        }

        if (!Uri.TryCreate(
                url,
                UriKind.Absolute,
                out var uri)
            ||
            uri.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException(
                "Setup downloads must use HTTPS.");
        }

        var directory =
            Path.GetDirectoryName(path)
            ?? throw new InvalidOperationException(
                "Download destination has no parent directory.");

        Directory.CreateDirectory(
            directory);

        var partial =
            path + ".part";

        var existing =
            File.Exists(partial)
                ? new FileInfo(partial).Length
                : 0;

        if (existing > maxBytes)
        {
            File.Delete(partial);
            existing = 0;
        }

        using var request =
            new HttpRequestMessage(
                HttpMethod.Get,
                uri);

        if (existing > 0)
        {
            request.Headers.Range =
                new RangeHeaderValue(
                    existing,
                    null);
        }

        using var response =
            await http.SendAsync(
                request,
                HttpCompletionOption.ResponseHeadersRead,
                ct);

        if (response.StatusCode ==
            HttpStatusCode.RequestedRangeNotSatisfiable)
        {
            TryDelete(partial);

            throw new IOException(
                "The previous partial download is stale. " +
                "It has been cleared; retry the download.");
        }

        response.EnsureSuccessStatusCode();

        if (response.StatusCode !=
            HttpStatusCode.PartialContent)
        {
            existing = 0;
        }
        else if (
            response.Content.Headers.ContentRange?.From
            != existing)
        {
            throw new IOException(
                "Server returned an unexpected resume position.");
        }

        var contentLength =
            response.Content.Headers.ContentLength;

        if (contentLength is long incoming
            &&
            incoming > maxBytes - existing)
        {
            throw new IOException(
                "Download exceeds the configured size limit.");
        }

        var total =
            contentLength.HasValue
                ? existing + contentLength.Value
                : 0;

        await using var source =
            await response.Content
                .ReadAsStreamAsync(ct);

        await using var target =
            new FileStream(
                partial,
                existing > 0
                    ? FileMode.Append
                    : FileMode.Create,
                FileAccess.Write,
                FileShare.None,
                65536,
                true);

        var buffer =
            new byte[65536];

        var done =
            existing;

        while (true)
        {
            var count =
                await source.ReadAsync(
                    buffer.AsMemory(),
                    ct);

            if (count == 0)
                break;

            done += count;

            if (done > maxBytes)
            {
                throw new IOException(
                    "Download exceeded the configured size limit.");
            }

            await target.WriteAsync(
                buffer.AsMemory(
                    0,
                    count),
                ct);

            progress.Report(
                new ProgressUpdate(
                    "Downloading",
                    total > 0
                        ? $"{Path.GetFileName(path)} · " +
                          $"{done / 1e6:0} / {total / 1e6:0} MB"
                        : $"{Path.GetFileName(path)} · " +
                          $"{done / 1e6:0} MB",
                    total > 0
                        ? Math.Clamp(
                            100d * done / total,
                            0,
                            100)
                        : 0,
                    total == 0));
        }

        await target.FlushAsync(ct);

        File.Move(
            partial,
            path,
            true);
    }

    // =====================================================================
    // FFMPEG INSTALLATION
    // =====================================================================

    public async Task InstallFfmpeg(
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        var root =
            Path.Combine(
                Settings.DataRoot,
                "tools",
                "ffmpeg");

        var downloads =
            Path.Combine(
                Settings.DataRoot,
                "downloads");

        Directory.CreateDirectory(root);
        Directory.CreateDirectory(downloads);

        var zip =
            Path.Combine(
                downloads,
                "ffmpeg.zip");

        const string url =
            "https://www.gyan.dev/ffmpeg/builds/" +
            "ffmpeg-release-essentials.zip";

        progress.Report(
            new ProgressUpdate(
                "Installing FFmpeg",
                "Checking upstream checksum",
                0,
                true));

        var checksum =
            await http.GetStringAsync(
                url + ".sha256",
                ct);

        var expected =
            Regex.Match(
                checksum,
                @"\b[0-9a-fA-F]{64}\b")
                .Value;

        if (expected.Length != 64)
        {
            throw new InvalidDataException(
                "FFmpeg upstream did not provide a valid SHA-256 checksum.");
        }

        await Download(
            url,
            zip,
            600_000_000,
            progress,
            ct);

        progress.Report(
            new ProgressUpdate(
                "Installing FFmpeg",
                "Verifying SHA-256",
                80,
                true));

        await using (var file =
                     File.OpenRead(zip))
        {
            var hash =
                await SHA256.HashDataAsync(
                    file,
                    ct);

            var actual =
                Convert.ToHexString(hash);

            if (!actual.Equals(
                    expected,
                    StringComparison.OrdinalIgnoreCase))
            {
                TryDelete(zip);

                throw new InvalidDataException(
                    "FFmpeg checksum mismatch. " +
                    "The downloaded archive was discarded.");
            }
        }

        progress.Report(
            new ProgressUpdate(
                "Installing FFmpeg",
                "Extracting tools",
                90,
                true));

        await Task.Run(
            () =>
            {
                ZipFile.ExtractToDirectory(
                    zip,
                    root,
                    true);
            },
            ct);

        var ffmpeg =
            Directory
                .GetFiles(
                    root,
                    "ffmpeg.exe",
                    SearchOption.AllDirectories)
                .FirstOrDefault();

        var ffprobe =
            Directory
                .GetFiles(
                    root,
                    "ffprobe.exe",
                    SearchOption.AllDirectories)
                .FirstOrDefault();

        if (ffmpeg == null
            ||
            ffprobe == null)
        {
            throw new InvalidDataException(
                "FFmpeg archive extracted, but ffmpeg.exe " +
                "or ffprobe.exe could not be found.");
        }

        var version =
            await ChildProcess.Run(
                ffmpeg,
                ["-version"],
                null,
                null,
                TimeSpan.FromSeconds(20),
                ct);

        settings.Ffmpeg =
            ffmpeg;

        settings.Ffprobe =
            ffprobe;

        await Json.Save(
            Path.Combine(
                root,
                "installed.json"),
            new
            {
                url,
                sha256 = expected,
                version,
                installed =
                    DateTimeOffset.UtcNow
            },
            ct);

        await settings.Save();

        progress.Report(
            new ProgressUpdate(
                "Installing FFmpeg",
                "FFmpeg and FFprobe are ready",
                100,
                false));
    }

    // =====================================================================
    // PIPER INSTALLATION
    // =====================================================================

    public async Task InstallPiper(
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        var root =
            Path.Combine(
                Settings.DataRoot,
                "piper");

        Directory.CreateDirectory(root);

        var environment =
            Path.Combine(
                root,
                "venv");

        var python =
            Path.Combine(
                environment,
                "Scripts",
                "python.exe");

        progress.Report(
            new ProgressUpdate(
                "Neural narration",
                "Creating private Python environment",
                10,
                true));

        if (!File.Exists(python))
        {
            if (string.IsNullOrWhiteSpace(
                    settings.Python))
            {
                throw new InvalidOperationException(
                    "Python is not configured.");
            }

            await ChildProcess.Run(
                settings.Python,
                [
                    "-m",
                    "venv",
                    environment
                ],
                root,
                null,
                TimeSpan.FromMinutes(5),
                ct);
        }

        if (!File.Exists(python))
        {
            throw new InvalidOperationException(
                "Python virtual environment was not created correctly.");
        }

        progress.Report(
            new ProgressUpdate(
                "Neural narration",
                $"Installing Piper {PiperVersion}",
                30,
                true));

        await ChildProcess.Run(
            python,
            [
                "-m",
                "pip",
                "install",
                "--disable-pip-version-check",
                $"piper-tts=={PiperVersion}"
            ],
            root,
            null,
            TimeSpan.FromMinutes(20),
            ct);

        progress.Report(
            new ProgressUpdate(
                "Neural narration",
                "Downloading Lessac neural voice",
                70,
                true));

        var voices =
            Path.Combine(
                root,
                "voices");

        Directory.CreateDirectory(
            voices);

        await ChildProcess.Run(
            python,
            [
                "-m",
                "piper.download_voices",
                PiperVoiceName,
                "--data-dir",
                voices
            ],
            root,
            null,
            TimeSpan.FromMinutes(20),
            ct);

        var voice =
            Path.Combine(
                voices,
                PiperVoiceName + ".onnx");

        var voiceConfig =
            voice + ".json";

        if (!File.Exists(voice))
        {
            throw new InvalidDataException(
                "Piper installation completed but the voice model " +
                "could not be found.");
        }

        if (!File.Exists(voiceConfig))
        {
            throw new InvalidDataException(
                "Piper voice model was downloaded, but its JSON " +
                "configuration file is missing.");
        }

        // Confirm Python can actually import Piper.
        var test =
            await ChildProcess.Run(
                python,
                [
                    "-c",
                    "import piper; print('PIPER_OK')"
                ],
                root,
                null,
                TimeSpan.FromSeconds(30),
                ct);

        if (!test.Contains(
                "PIPER_OK",
                StringComparison.Ordinal))
        {
            throw new InvalidOperationException(
                "Piper installed but failed its import test.");
        }

        settings.PiperPython =
            python;

        settings.PiperVoice =
            voice;

        settings.SpeechProvider =
            "Piper";

        var packages =
            await ChildProcess.Run(
                python,
                [
                    "-m",
                    "pip",
                    "freeze"
                ],
                root,
                null,
                TimeSpan.FromMinutes(1),
                ct);

        await File.WriteAllTextAsync(
            Path.Combine(
                root,
                "installed-packages.txt"),
            packages,
            ct);

        await settings.Save();

        progress.Report(
            new ProgressUpdate(
                "Neural narration",
                "Piper installed. Use Test narration to verify audio.",
                100,
                false));
    }

    // =====================================================================
    // JSON HELPERS
    // =====================================================================

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

    // =====================================================================
    // GENERAL HELPERS
    // =====================================================================

    private static string Shorten(
        string value,
        int maximum)
    {
        if (string.IsNullOrEmpty(value)
            ||
            value.Length <= maximum)
        {
            return value;
        }

        return value[..maximum] + "...";
    }

    private static void TryDelete(
        string path)
    {
        try
        {
            if (File.Exists(path))
                File.Delete(path);
        }
        catch
        {
            // Cleanup failure should not hide the original setup problem.
        }
    }
}