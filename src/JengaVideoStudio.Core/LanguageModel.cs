using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace JengaVideoStudio.Core;

// ============================================================================
// LIVE AI PROGRESS
// ============================================================================

public sealed record AiProgress(
    DateTimeOffset Time,
    string Area,
    string Action,
    string Detail = "",
    int? Attempt = null,
    int? MaximumAttempts = null,
    long? GeneratedCharacters = null,
    long? GeneratedTokens = null,
    double? TokensPerSecond = null,
    bool IsWarning = false,
    bool IsError = false)
{
    public string JournalText
    {
        get
        {
            var text = string.IsNullOrWhiteSpace(Detail)
                ? Action
                : $"{Action} — {Detail}";

            if (Attempt.HasValue && MaximumAttempts.HasValue)
                text += $" · attempt {Attempt}/{MaximumAttempts}";

            return text;
        }
    }
}

// ============================================================================
// OLLAMA LANGUAGE MODEL
// ============================================================================

public sealed class OllamaLanguageModel : ILanguageModel
{
    private const int MaxAttempts = 3;

    // Quality/GPU profile.
    private const int QualityContext = 8192;
    private const int QualityPrediction = 2048;
    private const int QualityMaxPromptCharacters = 26000;
    private const int QualityMaxSourceCharacters = 6500;

    // Lightweight CPU profile. Keeping the context and requested output
    // smaller is far more important than simply setting num_gpu = 0.
    private const int CpuContext = 4096;
    private const int CpuPrediction = 1024;
    private const int CpuMaxPromptCharacters = 12000;
    private const int CpuMaxSourceCharacters = 2500;

    private static readonly TimeSpan CpuAttemptTimeout =
        TimeSpan.FromMinutes(6);

    private static readonly TimeSpan QualityAttemptTimeout =
        TimeSpan.FromMinutes(20);

    private readonly Settings settings;
    private readonly HttpClient http;

    /*
     * Once a GPU compatibility problem is detected, this model object remains
     * CPU-only for the rest of its lifetime.
     */
    private bool forceCpuOnly;

    /*
     * Pipeline / ViewModel can subscribe to this in the next stage of the
     * instrumentation work.
     */
    public event EventHandler<AiProgress>? Progress;

    public OllamaLanguageModel(
        Settings settings,
        HttpClient http)
    {
        this.settings =
            settings ?? throw new ArgumentNullException(nameof(settings));

        this.http =
            http ?? throw new ArgumentNullException(nameof(http));

        /*
         * Production can be entered without visiting the Setup screen first.
         * Apply the last hardware assessment here so an old settings.json
         * model (for example qwen2.5:7b) cannot override the model that the
         * hardware profiler selected for this PC.
         */
        ApplySavedHardwareProfile();

        forceCpuOnly = settings.CpuOnly;
    }

    /// <summary>
    /// Loads the profile written by SetupService/AiHardwareProfiler and applies
    /// its model/backend recommendation before the first production request.
    ///
    /// This is intentionally synchronous and tiny: it only reads a local JSON
    /// file during construction. The expensive hardware benchmark remains in
    /// SetupService and is not repeated for every video.
    /// </summary>
    private void ApplySavedHardwareProfile()
    {
        try
        {
            var path =
                Path.Combine(
                    Settings.DataRoot,
                    "ai-hardware-profile.json");

            if (!File.Exists(path))
                return;

            var json =
                File.ReadAllText(path);

            using var document =
                JsonDocument.Parse(json);

            var root =
                document.RootElement;

            string? recommendedModel =
                null;

            bool? recommendedCpuOnly =
                null;

            if (root.TryGetProperty(
                    "RecommendedModel",
                    out var modelElement)
                &&
                modelElement.ValueKind ==
                JsonValueKind.String)
            {
                recommendedModel =
                    modelElement.GetString();
            }

            if (root.TryGetProperty(
                    "RecommendedCpuOnly",
                    out var cpuElement)
                &&
                (cpuElement.ValueKind == JsonValueKind.True ||
                 cpuElement.ValueKind == JsonValueKind.False))
            {
                recommendedCpuOnly =
                    cpuElement.GetBoolean();
            }

            if (!string.IsNullOrWhiteSpace(
                    recommendedModel))
            {
                settings.Model =
                    recommendedModel.Trim();
            }

            if (recommendedCpuOnly.HasValue)
            {
                settings.CpuOnly =
                    recommendedCpuOnly.Value;
            }
        }
        catch
        {
            /*
             * A missing, old or damaged optional hardware-profile cache must
             * never prevent Jenga from starting. Existing Settings remain the
             * fallback. Setup diagnostics can regenerate the profile.
             */
        }
    }

    private bool CpuProfile =>
        forceCpuOnly || settings.CpuOnly;

    private int ContextSize =>
        CpuProfile ? CpuContext : QualityContext;

    private int PredictionLimit =>
        CpuProfile ? CpuPrediction : QualityPrediction;

    private int PromptCharacterLimit =>
        CpuProfile ? CpuMaxPromptCharacters : QualityMaxPromptCharacters;

    private int SourceCharacterLimit =>
        CpuProfile ? CpuMaxSourceCharacters : QualityMaxSourceCharacters;

    private TimeSpan AttemptTimeout =>
        CpuProfile ? CpuAttemptTimeout : QualityAttemptTimeout;

    // ========================================================================
    // GENERATE
    // ========================================================================

    public async Task<T> Generate<T>(
        string template,
        object input,
        CancellationToken ct)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(template);
        ArgumentNullException.ThrowIfNull(input);

        Util.LocalEndpoint(settings.OllamaEndpoint);

        var endpoint =
            settings.OllamaEndpoint.TrimEnd('/') +
            "/api/generate";

        Report(
            "AI",
            "Preparing local AI request",
            $"Template: {template}");

        var systemPrompt =
            Util.Prompt(template);

        Report(
            "AI",
            "System instructions loaded",
            $"{systemPrompt.Length:N0} characters");

        var originalInput =
            Json.Encode(input);

        var prompt =
            PreparePrompt(input);

        if (prompt.Length < originalInput.Length)
        {
            Report(
                "AI",
                "Prompt reduced for local model",
                $"{originalInput.Length:N0} → {prompt.Length:N0} characters");
        }
        else
        {
            Report(
                "AI",
                "Prompt prepared",
                $"{prompt.Length:N0} characters");
        }

        var correction = "";
        Exception? lastFailure = null;
        var attempt = 1;

        while (attempt <= MaxAttempts)
        {
            ct.ThrowIfCancellationRequested();

            Report(
                "AI",
                "Starting generation",
                $"{settings.Model} · {ComputeDescription()}",
                attempt,
                MaxAttempts);

            try
            {
                var result =
                    await GenerateAttempt<T>(
                        endpoint,
                        systemPrompt + correction,
                        prompt,
                        attempt,
                        ct);

                Report(
                    "AI",
                    "Generation accepted",
                    $"Structured {typeof(T).Name} response is valid",
                    attempt,
                    MaxAttempts);

                return result;
            }

            // ----------------------------------------------------------------
            // MALFORMED MODEL JSON
            // ----------------------------------------------------------------

            catch (MalformedModelJsonException e)
            {
                lastFailure = e;

                Report(
                    "AI",
                    "Structured response rejected",
                    e.Message,
                    attempt,
                    MaxAttempts,
                    warning: true);

                if (!string.IsNullOrWhiteSpace(e.ResponseSnippet))
                {
                    Report(
                        "AI",
                        "Rejected response preview",
                        e.ResponseSnippet,
                        attempt,
                        MaxAttempts,
                        warning: true);
                }

                if (attempt >= MaxAttempts)
                    break;

                correction =
                    """

                    IMPORTANT CORRECTION:

                    Your previous response could not be parsed as the required
                    JSON structure.

                    Return ONLY valid JSON.
                    Do not use Markdown.
                    Do not use ``` fences.
                    Do not add commentary before or after the JSON.
                    Match the requested schema and property types exactly.
                    Ensure every required collection and property is present.
                    """;

                Report(
                    "AI",
                    "Preparing corrective retry",
                    "The next request will explicitly require schema-valid JSON",
                    attempt + 1,
                    MaxAttempts);

                attempt++;
            }

            // ----------------------------------------------------------------
            // OLLAMA ERROR
            // ----------------------------------------------------------------

            catch (OllamaRequestException e)
            {
                lastFailure = e;

                Report(
                    "OLLAMA",
                    "Ollama request failed",
                    BuildOllamaProgressDetail(e),
                    attempt,
                    MaxAttempts,
                    warning: true);

                if (!forceCpuOnly &&
                    IsGpuCompatibilityFailure(e))
                {
                    forceCpuOnly = true;

                    /*
                     * The accelerator has now failed in a real production
                     * request. Persist the conservative CPU profile immediately
                     * so the next stage/run does not repeat the same bad GPU
                     * attempt or retain an oversized 7B default.
                     */
                    settings.CpuOnly = true;

                    if (string.Equals(
                            settings.Model,
                            Settings.QualityModel,
                            StringComparison.OrdinalIgnoreCase)
                        ||
                        string.Equals(
                            settings.Model,
                            "qwen2.5:7b",
                            StringComparison.OrdinalIgnoreCase))
                    {
                        settings.Model =
                            Settings.CpuModel;
                    }

                    try
                    {
                        await settings.Save();
                    }
                    catch
                    {
                        // Production fallback must still continue even if the
                        // preference could not be persisted to disk.
                    }

                    Report(
                        "OLLAMA",
                        "GPU compatibility failure detected",
                        $"Switching to {settings.Model} in CPU-only mode",
                        attempt,
                        MaxAttempts,
                        warning: true);

                    await DelayWithProgress(
                        TimeSpan.FromSeconds(1),
                        "Restarting request on CPU",
                        ct);

                    /*
                     * Backend failure does not consume a logical model attempt.
                     */
                    continue;
                }

                if (attempt >= MaxAttempts ||
                    !e.Retryable)
                {
                    Report(
                        "OLLAMA",
                        "Local AI stopped",
                        BuildOllamaProgressDetail(e),
                        attempt,
                        MaxAttempts,
                        error: true);

                    throw BuildFinalOllamaException(e);
                }

                var delay =
                    TimeSpan.FromSeconds(attempt * 3);

                await DelayWithProgress(
                    delay,
                    "Waiting before Ollama retry",
                    ct);

                attempt++;
            }

            // ----------------------------------------------------------------
            // TIMEOUT
            // ----------------------------------------------------------------

            catch (TimeoutException e)
            {
                lastFailure = e;

                Report(
                    "OLLAMA",
                    "Local AI timed out",
                    e.Message,
                    attempt,
                    MaxAttempts,
                    warning: true);

                if (attempt >= MaxAttempts)
                    break;

                var delay =
                    TimeSpan.FromSeconds(attempt * 2);

                await DelayWithProgress(
                    delay,
                    "Waiting before timeout retry",
                    ct);

                attempt++;
            }

            // ----------------------------------------------------------------
            // CONNECTION FAILURE
            // ----------------------------------------------------------------

            catch (HttpRequestException e)
            {
                lastFailure = e;

                Report(
                    "OLLAMA",
                    "Connection to Ollama failed",
                    e.Message,
                    attempt,
                    MaxAttempts,
                    warning: true);

                if (attempt >= MaxAttempts)
                    break;

                var delay =
                    TimeSpan.FromSeconds(attempt * 3);

                await DelayWithProgress(
                    delay,
                    "Waiting before connection retry",
                    ct);

                attempt++;
            }
        }

        var finalMessage =
            BuildFailureMessage(lastFailure);

        Report(
            "AI",
            "Local AI could not complete this operation",
            finalMessage,
            MaxAttempts,
            MaxAttempts,
            error: true);

        throw new InvalidOperationException(
            finalMessage,
            lastFailure);
    }

    // ========================================================================
    // SINGLE STREAMING OLLAMA REQUEST
    // ========================================================================

    private async Task<T> GenerateAttempt<T>(
        string endpoint,
        string systemPrompt,
        string prompt,
        int attempt,
        CancellationToken ct)
    {
        using var bound =
            CancellationTokenSource.CreateLinkedTokenSource(ct);

        var timeout = AttemptTimeout;

        bound.CancelAfter(timeout);

        var cpuOnly =
            forceCpuOnly ||
            settings.CpuOnly;

        var options =
            new Dictionary<string, object>
            {
                ["temperature"] = 0.25,
                ["num_ctx"] = ContextSize,
                ["num_predict"] = PredictionLimit,
                ["num_batch"] = cpuOnly ? 32 : 128
            };

        if (cpuOnly)
            options["num_gpu"] = 0;

        var request =
            new
            {
                model = settings.Model,
                system = systemPrompt,
                prompt,
                format = "json",

                /*
                 * IMPORTANT:
                 *
                 * Streaming lets the UI see that Ollama is alive instead of
                 * waiting twenty minutes for one giant HTTP response.
                 */
                stream = true,

                /*
                 * Release the model after the operation on memory-constrained
                 * machines.
                 */
                keep_alive = 0,

                options
            };

        Report(
            "OLLAMA",
            "Sending request",
            $"{settings.Model} · {ComputeDescription()} · " +
            $"ctx {ContextSize:N0} · output ≤ {PredictionLimit:N0} tokens · " +
            $"{prompt.Length:N0} prompt characters",
            attempt,
            MaxAttempts);

        var requestWatch =
            Stopwatch.StartNew();

        HttpResponseMessage response;

        try
        {
            using var message =
                new HttpRequestMessage(
                    HttpMethod.Post,
                    endpoint)
                {
                    Content =
                        JsonContent.Create(request)
                };

            response =
                await http.SendAsync(
                    message,
                    HttpCompletionOption.ResponseHeadersRead,
                    bound.Token);
        }
        catch (OperationCanceledException)
            when (!ct.IsCancellationRequested)
        {
            throw new TimeoutException(
                $"Local AI attempt {attempt} timed out after " +
                $"{timeout.TotalMinutes:0} minutes.");
        }
        catch (HttpRequestException e)
        {
            throw new OllamaRequestException(
                "Jenga could not communicate with Ollama. " +
                "Check that Ollama is still running.",
                null,
                e.Message,
                retryable: true,
                e);
        }

        using (response)
        {
            /*
             * For HTTP failures we still need the complete body because Ollama
             * commonly puts CUDA / runner diagnostics in it.
             */
            if (!response.IsSuccessStatusCode)
            {
                string body;

                try
                {
                    body =
                        await response.Content.ReadAsStringAsync(
                            bound.Token);
                }
                catch (Exception e)
                    when (
                        e is IOException
                        or HttpRequestException)
                {
                    throw new OllamaRequestException(
                        "Ollama returned an error response that could not be read.",
                        response.StatusCode,
                        e.Message,
                        retryable: true,
                        e);
                }

                var detail =
                    ExtractOllamaError(body);

                throw new OllamaRequestException(
                    $"Ollama returned HTTP {(int)response.StatusCode} " +
                    $"({response.ReasonPhrase}).",
                    response.StatusCode,
                    detail,
                    IsRetryable(response.StatusCode),
                    null);
            }

            Report(
                "OLLAMA",
                "Connected to local model",
                $"HTTP {(int)response.StatusCode} · waiting for generated output",
                attempt,
                MaxAttempts);

            await using var stream =
                await response.Content.ReadAsStreamAsync(
                    bound.Token);

            using var reader =
                new StreamReader(stream);

            var generated =
                new StringBuilder();

            var firstChunkReceived =
                false;

            long estimatedTokens =
                0;

            long lastReportedCharacters =
                0;

            var lastProgressReport =
                Stopwatch.StartNew();

            long? finalEvalCount =
                null;

            long? finalEvalDuration =
                null;

            long? finalPromptEvalCount =
                null;

            string? finalDoneReason =
                null;

            while (true)
            {
                ct.ThrowIfCancellationRequested();

                string? line;

                try
                {
                    line =
                        await reader.ReadLineAsync(
                            bound.Token);
                }
                catch (OperationCanceledException)
                    when (!ct.IsCancellationRequested)
                {
                    throw new TimeoutException(
                        $"Reading streamed Ollama output for attempt {attempt} " +
                        $"timed out after {timeout.TotalMinutes:0} minutes.");
                }

                if (line == null)
                    break;

                if (string.IsNullOrWhiteSpace(line))
                    continue;

                JsonDocument packet;

                try
                {
                    packet =
                        JsonDocument.Parse(line);
                }
                catch (JsonException e)
                {
                    throw new OllamaRequestException(
                        "Ollama returned malformed streaming JSON.",
                        response.StatusCode,
                        SafeSnippet(line),
                        retryable: true,
                        e);
                }

                using (packet)
                {
                    var root =
                        packet.RootElement;

                    if (root.TryGetProperty(
                            "error",
                            out var error))
                    {
                        var detail =
                            error.ValueKind == JsonValueKind.String
                                ? error.GetString()
                                : error.ToString();

                        throw new OllamaRequestException(
                            "Ollama reported a model error.",
                            response.StatusCode,
                            detail ?? "Unknown Ollama error.",
                            retryable: true,
                            null);
                    }

                    if (root.TryGetProperty(
                            "response",
                            out var responseElement)
                        &&
                        responseElement.ValueKind ==
                        JsonValueKind.String)
                    {
                        var chunk =
                            responseElement.GetString() ?? "";

                        if (chunk.Length > 0)
                        {
                            generated.Append(chunk);

                            if (!firstChunkReceived)
                            {
                                firstChunkReceived = true;

                                Report(
                                    "OLLAMA",
                                    "First generated output received",
                                    $"After {requestWatch.Elapsed.TotalSeconds:N1} seconds",
                                    attempt,
                                    MaxAttempts);
                            }
                        }
                    }

                    if (root.TryGetProperty(
                            "eval_count",
                            out var evalCountElement)
                        &&
                        evalCountElement.TryGetInt64(
                            out var evalCount))
                    {
                        finalEvalCount =
                            evalCount;

                        estimatedTokens =
                            evalCount;
                    }
                    else
                    {
                        /*
                         * Ollama generally provides the exact eval_count on the
                         * final packet. Until then this is explicitly only an
                         * approximation for UI activity.
                         */
                        estimatedTokens =
                            Math.Max(
                                estimatedTokens,
                                generated.Length / 4);
                    }

                    if (root.TryGetProperty(
                            "eval_duration",
                            out var evalDurationElement)
                        &&
                        evalDurationElement.TryGetInt64(
                            out var evalDuration))
                    {
                        finalEvalDuration =
                            evalDuration;
                    }

                    if (root.TryGetProperty(
                            "prompt_eval_count",
                            out var promptEvalElement)
                        &&
                        promptEvalElement.TryGetInt64(
                            out var promptEvalCount))
                    {
                        finalPromptEvalCount =
                            promptEvalCount;
                    }

                    if (root.TryGetProperty(
                            "done_reason",
                            out var doneReasonElement)
                        &&
                        doneReasonElement.ValueKind ==
                        JsonValueKind.String)
                    {
                        finalDoneReason =
                            doneReasonElement.GetString();
                    }

                    var done =
                        root.TryGetProperty(
                            "done",
                            out var doneElement)
                        &&
                        doneElement.ValueKind ==
                        JsonValueKind.True;

                    /*
                     * Do not spam the journal on every tiny streamed fragment.
                     * Send a useful update roughly every two seconds or after a
                     * significant increase in generated output.
                     */
                    var charactersSinceReport =
                        generated.Length -
                        lastReportedCharacters;

                    if (!done &&
                        firstChunkReceived &&
                        (
                            lastProgressReport.Elapsed >=
                            TimeSpan.FromSeconds(2)
                            ||
                            charactersSinceReport >= 800
                        ))
                    {
                        Report(
                            "OLLAMA",
                            "Generating response",
                            BuildStreamingDetail(
                                generated.Length,
                                estimatedTokens,
                                requestWatch.Elapsed),
                            attempt,
                            MaxAttempts,
                            generatedCharacters: generated.Length,
                            generatedTokens: estimatedTokens);

                        lastReportedCharacters =
                            generated.Length;

                        lastProgressReport.Restart();
                    }

                    if (done)
                        break;
                }
            }

            requestWatch.Stop();

            if (generated.Length == 0)
            {
                throw new OllamaRequestException(
                    "The local AI returned an empty generated response.",
                    response.StatusCode,
                    "The model completed without usable output.",
                    retryable: true,
                    null);
            }

            var tokensPerSecond =
                CalculateTokensPerSecond(
                    finalEvalCount,
                    finalEvalDuration);

            Report(
                "OLLAMA",
                "Generation complete",
                BuildCompletionDetail(
                    generated.Length,
                    finalEvalCount ?? estimatedTokens,
                    tokensPerSecond,
                    requestWatch.Elapsed,
                    finalPromptEvalCount,
                    finalDoneReason),
                attempt,
                MaxAttempts,
                generatedCharacters: generated.Length,
                generatedTokens: finalEvalCount ?? estimatedTokens,
                tokensPerSecond: tokensPerSecond);

            var modelText =
                CleanModelJson(
                    generated.ToString());

            Report(
                "AI",
                "Parsing structured response",
                $"{modelText.Length:N0} generated characters",
                attempt,
                MaxAttempts);

            try
            {
                var result =
                    Json.Decode<T>(modelText);

                Report(
                    "AI",
                    "Structured response parsed",
                    typeof(T).Name,
                    attempt,
                    MaxAttempts);

                return result;
            }
            catch (JsonException e)
            {
                throw new MalformedModelJsonException(
                    "The local AI returned JSON that did not match " +
                    "the required Jenga data structure.",
                    SafeSnippet(modelText),
                    e);
            }
            catch (InvalidDataException e)
            {
                throw new MalformedModelJsonException(
                    "The local AI returned incomplete structured data.",
                    SafeSnippet(modelText),
                    e);
            }
        }
    }

    // ========================================================================
    // PROGRESS
    // ========================================================================

    private void Report(
        string area,
        string action,
        string detail = "",
        int? attempt = null,
        int? maximumAttempts = null,
        long? generatedCharacters = null,
        long? generatedTokens = null,
        double? tokensPerSecond = null,
        bool warning = false,
        bool error = false)
    {
        var handler =
            Progress;

        if (handler == null)
            return;

        var progress =
            new AiProgress(
                DateTimeOffset.Now,
                area,
                action,
                detail,
                attempt,
                maximumAttempts,
                generatedCharacters,
                generatedTokens,
                tokensPerSecond,
                warning,
                error);

        try
        {
            handler(
                this,
                progress);
        }
        catch
        {
            /*
             * UI diagnostics must never be capable of breaking production.
             */
        }
    }

    private async Task DelayWithProgress(
        TimeSpan delay,
        string reason,
        CancellationToken ct)
    {
        if (delay <= TimeSpan.Zero)
            return;

        Report(
            "AI",
            reason,
            $"{Math.Ceiling(delay.TotalSeconds):N0} seconds");

        await Task.Delay(
            delay,
            ct);
    }

    private string ComputeDescription()
    {
        return forceCpuOnly || settings.CpuOnly
            ? "CPU-only"
            : "GPU acceleration allowed";
    }

    private static string BuildStreamingDetail(
        int characters,
        long tokens,
        TimeSpan elapsed)
    {
        return
            $"{characters:N0} chars · ~{tokens:N0} tokens · " +
            $"{FormatElapsed(elapsed)} elapsed";
    }

    private static string BuildCompletionDetail(
        int characters,
        long tokens,
        double? tokensPerSecond,
        TimeSpan elapsed,
        long? promptTokens,
        string? doneReason)
    {
        var parts =
            new List<string>
            {
                $"{characters:N0} chars",
                $"{tokens:N0} tokens",
                FormatElapsed(elapsed)
            };

        if (tokensPerSecond.HasValue)
        {
            parts.Add(
                $"{tokensPerSecond.Value:N2} tok/s");
        }

        if (promptTokens.HasValue)
        {
            parts.Add(
                $"{promptTokens.Value:N0} prompt tokens");
        }

        if (!string.IsNullOrWhiteSpace(doneReason))
        {
            parts.Add(
                $"finish: {doneReason}");
        }

        return string.Join(
            " · ",
            parts);
    }

    private static double? CalculateTokensPerSecond(
        long? evalCount,
        long? evalDurationNanoseconds)
    {
        if (!evalCount.HasValue ||
            !evalDurationNanoseconds.HasValue ||
            evalCount.Value <= 0 ||
            evalDurationNanoseconds.Value <= 0)
        {
            return null;
        }

        var seconds =
            evalDurationNanoseconds.Value /
            1_000_000_000d;

        if (seconds <= 0)
            return null;

        return
            evalCount.Value /
            seconds;
    }

    private static string FormatElapsed(
        TimeSpan elapsed)
    {
        if (elapsed.TotalHours >= 1)
        {
            return
                $"{(int)elapsed.TotalHours:00}:" +
                $"{elapsed.Minutes:00}:" +
                $"{elapsed.Seconds:00}";
        }

        return
            $"{(int)elapsed.TotalMinutes:00}:" +
            $"{elapsed.Seconds:00}";
    }

    // ========================================================================
    // GPU -> CPU FALLBACK
    // ========================================================================

    private static bool IsGpuCompatibilityFailure(
        OllamaRequestException error)
    {
        var text =
            (
                error.Message +
                " " +
                error.Detail +
                " " +
                error.InnerException?.Message
            )
            .ToLowerInvariant();

        string[] strongIndicators =
        [
            "unsupported toolchain",
            "provided ptx",
            "ptx was compiled",
            "cuda error",
            "cuda driver",
            "cuda initialization",
            "cuda initialization error",
            "cuda out of memory",
            "cublas",
            "cudnn",
            "ggml_cuda",
            "ggml-cuda",
            "vulkan error",
            "gpu runner",
            "gpu memory",
            "failed to load cuda",
            "failed to initialize cuda",
            "no kernel image is available",
            "invalid device function"
        ];

        if (strongIndicators.Any(
                indicator =>
                    text.Contains(
                        indicator,
                        StringComparison.Ordinal)))
        {
            return true;
        }

        var runnerDied =
            text.Contains(
                "llama-server process has terminated",
                StringComparison.Ordinal)
            ||
            text.Contains(
                "runner process has terminated",
                StringComparison.Ordinal);

        var mentionsGpuBackend =
            text.Contains(
                "cuda",
                StringComparison.Ordinal)
            ||
            text.Contains(
                "ptx",
                StringComparison.Ordinal)
            ||
            text.Contains(
                "vulkan",
                StringComparison.Ordinal)
            ||
            text.Contains(
                "gpu",
                StringComparison.Ordinal);

        return
            runnerDied &&
            mentionsGpuBackend;
    }

    // ========================================================================
    // INPUT PREPARATION
    // ========================================================================

    private string PreparePrompt(
        object input)
    {
        var original =
            Json.Encode(input);

        var promptLimit =
            PromptCharacterLimit;

        var sourceLimit =
            SourceCharacterLimit;

        if (original.Length <=
            promptLimit)
        {
            return original;
        }

        try
        {
            using var doc =
                JsonDocument.Parse(original);

            var reduced =
                ReduceElement(
                    doc.RootElement,
                    propertyName: null,
                    sourceTextLimit: sourceLimit);

            var result =
                JsonSerializer.Serialize(
                    reduced,
                    Json.Options);

            if (result.Length <=
                promptLimit)
            {
                return result;
            }

            var tighterLimits =
                CpuProfile
                    ? new[] { 1800, 1200, 800 }
                    : new[] { 4500, 3000, 2000 };

            foreach (var tighterLimit in tighterLimits)
            {
                var tighter =
                    ReduceElement(
                        doc.RootElement,
                        propertyName: null,
                        sourceTextLimit: tighterLimit);

                result =
                    JsonSerializer.Serialize(
                        tighter,
                        Json.Options);

                if (result.Length <=
                    promptLimit)
                {
                    return result;
                }
            }

            var minimal =
                ReduceElement(
                    doc.RootElement,
                    propertyName: null,
                    sourceTextLimit: CpuProfile ? 600 : 1500);

            return
                JsonSerializer.Serialize(
                    minimal,
                    Json.Options);
        }
        catch (JsonException)
        {
            return original;
        }
    }

    // ========================================================================
    // JSON TREE REDUCTION
    // ========================================================================

    private static object? ReduceElement(
        JsonElement element,
        string? propertyName,
        int sourceTextLimit)
    {
        switch (element.ValueKind)
        {
            case JsonValueKind.Object:
                {
                    var dictionary =
                        new Dictionary<string, object?>();

                    foreach (var property in
                             element.EnumerateObject())
                    {
                        dictionary[property.Name] =
                            ReduceElement(
                                property.Value,
                                property.Name,
                                sourceTextLimit);
                    }

                    return dictionary;
                }

            case JsonValueKind.Array:
                {
                    return element
                        .EnumerateArray()
                        .Select(
                            item =>
                                ReduceElement(
                                    item,
                                    propertyName,
                                    sourceTextLimit))
                        .ToList();
                }

            case JsonValueKind.String:
                {
                    var value =
                        element.GetString() ?? "";

                    if (string.Equals(
                            propertyName,
                            "Text",
                            StringComparison.OrdinalIgnoreCase)
                        &&
                        value.Length >
                        sourceTextLimit)
                    {
                        return
                            SmartExcerpt(
                                value,
                                sourceTextLimit);
                    }

                    return value;
                }

            case JsonValueKind.Number:
                {
                    if (element.TryGetInt64(
                            out var integer))
                    {
                        return integer;
                    }

                    if (element.TryGetDouble(
                            out var number))
                    {
                        return number;
                    }

                    return element.GetRawText();
                }

            case JsonValueKind.True:
                return true;

            case JsonValueKind.False:
                return false;

            case JsonValueKind.Null:
            case JsonValueKind.Undefined:
                return null;

            default:
                return element.GetRawText();
        }
    }

    // ========================================================================
    // SOURCE EXCERPT
    // ========================================================================

    private static string SmartExcerpt(
        string text,
        int maximum)
    {
        text =
            Regex.Replace(
                    text,
                    @"\s+",
                    " ")
                .Trim();

        if (text.Length <= maximum)
            return text;

        const string marker =
            "\n\n[... source shortened by Jenga for local AI context ...]\n\n";

        var available =
            Math.Max(
                500,
                maximum -
                marker.Length);

        var first =
            (int)(
                available *
                0.70);

        var last =
            available -
            first;

        return
            text[..first] +
            marker +
            text[^last..];
    }

    // ========================================================================
    // MODEL JSON CLEANUP
    // ========================================================================

    private static string CleanModelJson(
        string text)
    {
        text =
            text.Trim();

        if (text.StartsWith(
                "```",
                StringComparison.Ordinal))
        {
            var firstNewLine =
                text.IndexOf('\n');

            if (firstNewLine >= 0)
            {
                text =
                    text[
                        (firstNewLine + 1)..];
            }

            var finalFence =
                text.LastIndexOf(
                    "```",
                    StringComparison.Ordinal);

            if (finalFence >= 0)
            {
                text =
                    text[..finalFence];
            }

            text =
                text.Trim();
        }

        return text;
    }

    // ========================================================================
    // OLLAMA ERROR EXTRACTION
    // ========================================================================

    private static string ExtractOllamaError(
        string body)
    {
        if (string.IsNullOrWhiteSpace(body))
        {
            return
                "Ollama supplied no additional error information.";
        }

        try
        {
            using var doc =
                JsonDocument.Parse(body);

            if (doc.RootElement.TryGetProperty(
                    "error",
                    out var error))
            {
                if (error.ValueKind ==
                    JsonValueKind.String)
                {
                    return
                        error.GetString() ??
                        "Unknown Ollama error.";
                }

                return error.ToString();
            }

            if (doc.RootElement.TryGetProperty(
                    "message",
                    out var message))
            {
                if (message.ValueKind ==
                    JsonValueKind.String)
                {
                    return
                        message.GetString() ??
                        "Unknown Ollama error.";
                }

                return message.ToString();
            }
        }
        catch (JsonException)
        {
            // Fall through to safe body snippet.
        }

        return
            SafeSnippet(body);
    }

    // ========================================================================
    // SAFE ERROR SNIPPET
    // ========================================================================

    private static string SafeSnippet(
        string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return "";

        value =
            value
                .Replace(
                    "\r",
                    " ")
                .Replace(
                    "\n",
                    " ")
                .Trim();

        return
            value.Length <= 1200
                ? value
                : value[..1200] +
                  "...";
    }

    // ========================================================================
    // RETRY POLICY
    // ========================================================================

    private static bool IsRetryable(
        HttpStatusCode status)
    {
        var code =
            (int)status;

        return
            code == 408 ||
            code == 429 ||
            code >= 500;
    }

    // ========================================================================
    // ERRORS
    // ========================================================================

    private InvalidOperationException BuildFinalOllamaException(
        OllamaRequestException error)
    {
        var message =
            "Local AI failed while processing this stage.";

        if (error.StatusCode != null)
        {
            message +=
                $" Ollama returned HTTP " +
                $"{(int)error.StatusCode.Value}.";
        }

        if (!string.IsNullOrWhiteSpace(
                error.Detail))
        {
            message +=
                " Ollama says: " +
                error.Detail;
        }

        if (forceCpuOnly ||
            settings.CpuOnly)
        {
            message +=
                " Jenga was already using CPU-only mode.";
        }
        else
        {
            message +=
                " Try CPU-only mode or select the smaller local model.";
        }

        return
            new InvalidOperationException(
                message,
                error);
    }

    private string BuildFailureMessage(
        Exception? failure)
    {
        if (failure is TimeoutException)
        {
            return
                "Local AI timed out repeatedly. The model may be too large " +
                "for this computer or Ollama may have stalled. Try the " +
                "smaller model.";
        }

        if (failure is
            MalformedModelJsonException malformed)
        {
            return
                "Local AI returned malformed structured data three times. " +
                "Last response: " +
                malformed.ResponseSnippet;
        }

        if (failure is
            OllamaRequestException ollama)
        {
            return
                BuildFinalOllamaException(
                    ollama)
                .Message;
        }

        if (failure is
            HttpRequestException)
        {
            return
                "Jenga repeatedly lost communication with Ollama. " +
                "Check that Ollama is running and that the configured " +
                "local AI endpoint is correct.";
        }

        return
            "Local AI could not complete the request after three attempts. " +
            (
                failure?.Message ??
                "No additional error information was available."
            );
    }

    private static string BuildOllamaProgressDetail(
        OllamaRequestException error)
    {
        var parts =
            new List<string>();

        if (error.StatusCode.HasValue)
        {
            parts.Add(
                $"HTTP {(int)error.StatusCode.Value}");
        }

        if (!string.IsNullOrWhiteSpace(
                error.Detail))
        {
            parts.Add(
                SafeSnippet(error.Detail));
        }

        if (parts.Count == 0)
            parts.Add(error.Message);

        return
            string.Join(
                " · ",
                parts);
    }

    // ========================================================================
    // PRIVATE EXCEPTIONS
    // ========================================================================

    private sealed class OllamaRequestException :
        Exception
    {
        public HttpStatusCode? StatusCode { get; }

        public string Detail { get; }

        public bool Retryable { get; }

        public OllamaRequestException(
            string message,
            HttpStatusCode? statusCode,
            string? detail,
            bool retryable,
            Exception? inner)
            : base(
                message,
                inner)
        {
            StatusCode =
                statusCode;

            Detail =
                detail ?? "";

            Retryable =
                retryable;
        }
    }

    private sealed class MalformedModelJsonException :
        Exception
    {
        public string ResponseSnippet { get; }

        public MalformedModelJsonException(
            string message,
            string responseSnippet,
            Exception inner)
            : base(
                message,
                inner)
        {
            ResponseSnippet =
                responseSnippet;
        }
    }
}

// ============================================================================
// LANGUAGE MODEL RESPONSE TYPES
// ============================================================================

public class ResearchQueries
{
    public List<string> Queries { get; set; } =
        [];
}

public class Ideas
{
    public List<string> Suggestions { get; set; } =
        [];
}

// ============================================================================
// GROUNDING
// ============================================================================

public static class Grounding
{
    // ========================================================================
    // RESEARCH BRIEF VALIDATION
    // ========================================================================

    public static void Validate(
        Brief brief,
        IReadOnlyList<Source> sources)
    {
        ArgumentNullException.ThrowIfNull(brief);
        ArgumentNullException.ThrowIfNull(sources);

        if (brief.Claims.Count < 3 ||
            string.IsNullOrWhiteSpace(
                brief.Summary))
        {
            throw new InvalidDataException(
                "Research has too few supported claims. " +
                "Add relevant source URLs and try again.");
        }

        var ids =
            new HashSet<string>(
                StringComparer.Ordinal);

        foreach (var claim in
                 brief.Claims)
        {
            if (string.IsNullOrWhiteSpace(
                    claim.Id)
                ||
                !ids.Add(claim.Id)
                ||
                string.IsNullOrWhiteSpace(
                    claim.Text)
                ||
                claim.Evidence.Count == 0)
            {
                throw new InvalidDataException(
                    $"Research claim '{claim.Id}' is incomplete or duplicated.");
            }

            foreach (var evidence in
                     claim.Evidence)
            {
                var source =
                    sources.FirstOrDefault(
                        candidate =>
                            string.Equals(
                                candidate.Id,
                                evidence.SourceId,
                                StringComparison.Ordinal));

                if (source == null)
                {
                    throw new InvalidDataException(
                        $"Claim {claim.Id} references unknown source " +
                        $"'{evidence.SourceId}'.");
                }

                if (string.IsNullOrWhiteSpace(
                        evidence.Quote)
                    ||
                    evidence.Quote.Length < 15)
                {
                    throw new InvalidDataException(
                        $"Claim {claim.Id} contains an evidence quotation " +
                        "that is too short.");
                }

                if (!Normal(source.Text)
                    .Contains(
                        Normal(evidence.Quote),
                        StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidDataException(
                        $"Claim {claim.Id} contains a quotation that could not " +
                        $"be found verbatim in source {source.Id} " +
                        $"('{source.Title}').");
                }
            }
        }
    }

    // ========================================================================
    // SCENE VALIDATION
    // ========================================================================

    public static void ValidateScenes(
        IEnumerable<Scene> scenes,
        Brief brief)
    {
        ArgumentNullException.ThrowIfNull(scenes);
        ArgumentNullException.ThrowIfNull(brief);

        var list =
            scenes.ToList();

        if (list.Count is < 2 or > 24)
        {
            throw new InvalidDataException(
                $"Expected 2–24 scenes, but the local AI returned " +
                $"{list.Count}.");
        }

        var validClaims =
            brief.Claims
                .Where(
                    claim =>
                        !string.IsNullOrWhiteSpace(
                            claim.Id))
                .Select(
                    claim =>
                        claim.Id)
                .ToHashSet(
                    StringComparer.Ordinal);

        for (var index = 0;
             index < list.Count;
             index++)
        {
            var scene =
                list[index];

            var sceneNumber =
                index + 1;

            if (string.IsNullOrWhiteSpace(
                    scene.Narration))
            {
                throw new InvalidDataException(
                    $"Scene {sceneNumber} has no narration.");
            }

            if (scene.Narration.Length > 1600)
            {
                throw new InvalidDataException(
                    $"Scene {sceneNumber} narration is too long " +
                    $"({scene.Narration.Length:N0} characters; maximum 1,600).");
            }

            if (string.IsNullOrWhiteSpace(
                    scene.Heading))
            {
                throw new InvalidDataException(
                    $"Scene {sceneNumber} has no heading.");
            }

            if (scene.ClaimIds.Count == 0)
            {
                throw new InvalidDataException(
                    $"Scene {sceneNumber} does not reference any research claim.");
            }

            var invalidClaims =
                scene.ClaimIds
                    .Where(
                        id =>
                            !validClaims.Contains(id))
                    .Distinct(
                        StringComparer.Ordinal)
                    .ToArray();

            if (invalidClaims.Length > 0)
            {
                throw new InvalidDataException(
                    $"Scene {sceneNumber} references unknown research " +
                    $"claim(s): {string.Join(", ", invalidClaims)}. " +
                    $"Available claims: {string.Join(", ", validClaims)}.");
            }
        }
    }

    // ========================================================================
    // NORMALISE TEXT
    // ========================================================================

    private static string Normal(
        string value)
    {
        return
            Regex.Replace(
                    value ?? "",
                    @"\s+",
                    " ")
                .Trim();
    }
}