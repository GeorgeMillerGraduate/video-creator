using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Nodes;

namespace JengaVideoStudio.Core;

/// <summary>
/// Optional local AI visual generator using ComfyUI.
///
/// If visual generation is disabled or inappropriate for the selected
/// quality level, Create returns an empty string. The renderer then uses
/// Jenga's deterministic graphic fallback.
///
/// Actual ComfyUI failures are reported to ProductionPipeline, which can
/// also fall back to graphics without destroying the whole production.
/// </summary>
public sealed class ComfyVisuals(
    Settings settings,
    HttpClient http) : IVisualProvider
{
    private static readonly TimeSpan ImageTimeout =
        TimeSpan.FromMinutes(15);

    private static readonly TimeSpan VideoTimeout =
        TimeSpan.FromMinutes(45);

    private static readonly TimeSpan PollInterval =
        TimeSpan.FromSeconds(2);

    private const long MaximumImageBytes =
        100L * 1024 * 1024;

    private const long MaximumVideoBytes =
        1_000L * 1024 * 1024;

    // ========================================================================
    // CREATE
    // ========================================================================

    public async Task<string> Create(
        Project project,
        Scene scene,
        CancellationToken ct)
    {
        ct.ThrowIfCancellationRequested();

        var mode =
            DetermineMode(
                project,
                scene);

        // Empty means "use deterministic Jenga graphics".
        if (mode == VisualMode.None)
            return "";

        ValidateScene(
            scene);

        var workflowPath =
            mode == VisualMode.Video
                ? settings.VideoWorkflow
                : settings.ImageWorkflow;

        ValidateWorkflowPath(
            workflowPath,
            mode);

        Util.LocalEndpoint(
            settings.ComfyEndpoint);

        var endpoint =
            settings.ComfyEndpoint.TrimEnd('/');

        var workflow =
            await LoadWorkflow(
                workflowPath,
                ct);

        PrepareWorkflow(
            workflow,
            project,
            scene);

        using var timeout =
            CancellationTokenSource.CreateLinkedTokenSource(ct);

        timeout.CancelAfter(
            mode == VisualMode.Video
                ? VideoTimeout
                : ImageTimeout);

        string? promptId = null;

        try
        {
            await EnsureComfyAvailable(
                endpoint,
                timeout.Token);

            promptId =
                await SubmitWorkflow(
                    endpoint,
                    workflow,
                    timeout.Token);

            var asset =
                await WaitForResult(
                    endpoint,
                    promptId,
                    mode,
                    timeout.Token);

            var relative =
                await DownloadAsset(
                    endpoint,
                    asset,
                    project,
                    scene,
                    mode,
                    timeout.Token);

            await SaveProvenance(
                project,
                scene,
                workflowPath,
                relative,
                promptId,
                mode,
                ct);

            return relative;
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            if (promptId != null)
            {
                await TryDeleteQueuedJob(
                    endpoint,
                    promptId);
            }

            throw;
        }
        catch (OperationCanceledException e)
        {
            if (promptId != null)
            {
                await TryDeleteQueuedJob(
                    endpoint,
                    promptId);
            }

            throw new TimeoutException(
                mode == VisualMode.Video
                    ? "ComfyUI video generation timed out."
                    : "ComfyUI image generation timed out.",
                e);
        }
        catch
        {
            if (promptId != null)
            {
                await TryDeleteQueuedJob(
                    endpoint,
                    promptId);
            }

            throw;
        }
    }

    // ========================================================================
    // MODE
    // ========================================================================

    private VisualMode DetermineMode(
        Project project,
        Scene scene)
    {
        var wantsVideo =
            string.Equals(
                scene.VisualType,
                "GeneratedVideo",
                StringComparison.OrdinalIgnoreCase);

        var wantsImage =
            string.Equals(
                scene.VisualType,
                "GeneratedImage",
                StringComparison.OrdinalIgnoreCase);

        // Light quality deliberately avoids expensive AI visuals.
        if (string.Equals(
                project.Quality,
                "Light",
                StringComparison.OrdinalIgnoreCase))
        {
            return VisualMode.None;
        }

        if (wantsVideo
            && settings.EnableVideo)
        {
            return VisualMode.Video;
        }

        if (wantsImage
            && settings.EnableImages)
        {
            return VisualMode.Image;
        }

        // If the script requests generated video but video generation is
        // disabled, allow an AI still image instead when image generation
        // is enabled. This gives a better degradation path than immediately
        // dropping all the way to graphics.
        if (wantsVideo
            && !settings.EnableVideo
            && settings.EnableImages)
        {
            return VisualMode.Image;
        }

        return VisualMode.None;
    }

    // ========================================================================
    // WORKFLOW
    // ========================================================================

    private static async Task<JsonNode> LoadWorkflow(
        string path,
        CancellationToken ct)
    {
        string json;

        try
        {
            json =
                await File.ReadAllTextAsync(
                    path,
                    ct);
        }
        catch (Exception e) when (
            e is IOException
            or UnauthorizedAccessException)
        {
            throw new InvalidOperationException(
                $"Could not read ComfyUI workflow '{path}'. {e.Message}",
                e);
        }

        if (string.IsNullOrWhiteSpace(json))
        {
            throw new InvalidDataException(
                "The configured ComfyUI workflow is empty.");
        }

        try
        {
            return JsonNode.Parse(json)
                ?? throw new InvalidDataException(
                    "The configured ComfyUI workflow contains no JSON.");
        }
        catch (System.Text.Json.JsonException e)
        {
            throw new InvalidDataException(
                "The configured ComfyUI workflow is not valid JSON. " +
                "Export the workflow in API format.",
                e);
        }
    }

    private static void PrepareWorkflow(
        JsonNode workflow,
        Project project,
        Scene scene)
    {
        var positive =
            BuildPositivePrompt(
                scene.VisualPrompt);

        const string negative =
            "text, watermark, watermarks, logo, signature, " +
            "illegible labels, distorted text";

        var prefix =
            $"jenga_{SafeToken(project.Id.ToString())}_" +
            $"{scene.Number:00}_{scene.Revision}";

        ReplaceWorkflowTokens(
            workflow,
            positive,
            negative,
            prefix);
    }

    private static void ReplaceWorkflowTokens(
        JsonNode node,
        string positive,
        string negative,
        string prefix)
    {
        if (node is JsonObject obj)
        {
            foreach (var key in
                     obj.Select(x => x.Key).ToArray())
            {
                var value =
                    obj[key];

                if (value is JsonValue jsonValue
                    && jsonValue.TryGetValue<string>(
                        out var text))
                {
                    obj[key] =
                        text
                            .Replace(
                                "{{PROMPT}}",
                                positive,
                                StringComparison.Ordinal)
                            .Replace(
                                "{{NEGATIVE}}",
                                negative,
                                StringComparison.Ordinal)
                            .Replace(
                                "{{PREFIX}}",
                                prefix,
                                StringComparison.Ordinal);
                }
                else if (value != null)
                {
                    ReplaceWorkflowTokens(
                        value,
                        positive,
                        negative,
                        prefix);
                }

                // Seeds deliberately change when a visual really needs
                // regenerating. Pipeline caching prevents needless calls.
                if (string.Equals(
                        key,
                        "seed",
                        StringComparison.OrdinalIgnoreCase)
                    ||
                    string.Equals(
                        key,
                        "noise_seed",
                        StringComparison.OrdinalIgnoreCase))
                {
                    obj[key] =
                        Random.Shared.NextInt64(
                            0,
                            int.MaxValue);
                }
            }
        }
        else if (node is JsonArray array)
        {
            foreach (var child in array)
            {
                if (child != null)
                {
                    ReplaceWorkflowTokens(
                        child,
                        positive,
                        negative,
                        prefix);
                }
            }
        }
    }

    // ========================================================================
    // CONNECTION CHECK
    // ========================================================================

    private async Task EnsureComfyAvailable(
        string endpoint,
        CancellationToken ct)
    {
        try
        {
            // /system_stats is a lightweight ComfyUI endpoint and lets us
            // distinguish "Comfy isn't running" from a bad workflow.
            using var response =
                await http.GetAsync(
                    endpoint + "/system_stats",
                    HttpCompletionOption.ResponseHeadersRead,
                    ct);

            if (!response.IsSuccessStatusCode)
            {
                throw new InvalidOperationException(
                    $"ComfyUI responded with HTTP " +
                    $"{(int)response.StatusCode} " +
                    $"{response.ReasonPhrase}.");
            }
        }
        catch (HttpRequestException e)
        {
            throw new InvalidOperationException(
                "Could not connect to local ComfyUI. " +
                "Start ComfyUI or disable AI visuals in Setup.",
                e);
        }
    }

    // ========================================================================
    // SUBMIT
    // ========================================================================

    private async Task<string> SubmitWorkflow(
        string endpoint,
        JsonNode workflow,
        CancellationToken ct)
    {
        using var response =
            await http.PostAsJsonAsync(
                endpoint + "/prompt",
                new
                {
                    prompt = workflow,
                    client_id =
                        Guid.NewGuid().ToString("N")
                },
                ct);

        var body =
            await response.Content.ReadAsStringAsync(
                ct);

        if (!response.IsSuccessStatusCode)
        {
            throw new InvalidOperationException(
                BuildComfyHttpError(
                    "ComfyUI rejected the workflow",
                    response.StatusCode,
                    body));
        }

        JsonNode? submitted;

        try
        {
            submitted =
                JsonNode.Parse(body);
        }
        catch (System.Text.Json.JsonException e)
        {
            throw new InvalidDataException(
                "ComfyUI returned malformed JSON after workflow submission.",
                e);
        }

        var promptId =
            submitted?["prompt_id"]
                ?.GetValue<string>();

        if (string.IsNullOrWhiteSpace(promptId))
        {
            var nodeErrors =
                submitted?["node_errors"]
                    ?.ToJsonString();

            throw new InvalidDataException(
                "ComfyUI did not return a prompt ID. " +
                "The workflow may not be valid API-format JSON." +
                (
                    string.IsNullOrWhiteSpace(nodeErrors)
                        ? ""
                        : " Node errors: " +
                          Limit(nodeErrors, 1000)
                ));
        }

        return promptId;
    }

    // ========================================================================
    // POLL RESULT
    // ========================================================================

    private async Task<GeneratedAsset> WaitForResult(
        string endpoint,
        string promptId,
        VisualMode mode,
        CancellationToken ct)
    {
        var escapedId =
            Uri.EscapeDataString(promptId);

        while (true)
        {
            ct.ThrowIfCancellationRequested();

            await Task.Delay(
                PollInterval,
                ct);

            string json;

            try
            {
                using var response =
                    await http.GetAsync(
                        endpoint +
                        "/history/" +
                        escapedId,
                        ct);

                if (!response.IsSuccessStatusCode)
                {
                    // A transient history lookup failure should not instantly
                    // destroy a long generation unless Comfy gives us a real
                    // client-side error.
                    if ((int)response.StatusCode >= 500)
                        continue;

                    var error =
                        await response.Content.ReadAsStringAsync(ct);

                    throw new InvalidOperationException(
                        BuildComfyHttpError(
                            "Could not read ComfyUI generation history",
                            response.StatusCode,
                            error));
                }

                json =
                    await response.Content.ReadAsStringAsync(
                        ct);
            }
            catch (HttpRequestException e)
            {
                throw new InvalidOperationException(
                    "Connection to ComfyUI was lost while generating a visual.",
                    e);
            }

            JsonNode? history;

            try
            {
                history =
                    JsonNode.Parse(json);
            }
            catch (System.Text.Json.JsonException)
            {
                // A malformed/transient response should not hammer the server.
                continue;
            }

            var result =
                history?[promptId];

            if (result == null)
                continue;

            var status =
                result["status"];

            var statusString =
                status?["status_str"]
                    ?.GetValue<string>();

            if (string.Equals(
                    statusString,
                    "error",
                    StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException(
                    ExtractComfyFailure(result));
            }

            var asset =
                FindGeneratedAsset(
                    result,
                    mode);

            if (asset != null)
                return asset;

            var completed =
                TryGetBoolean(
                    status?["completed"]);

            if (completed)
            {
                throw new InvalidDataException(
                    mode == VisualMode.Video
                        ? "ComfyUI completed the workflow but produced no " +
                          "supported saved video. Add an appropriate " +
                          "video-saving output node to the API workflow."
                        : "ComfyUI completed the workflow but produced no " +
                          "supported saved image. Add a SaveImage output " +
                          "node to the API workflow.");
            }
        }
    }

    // ========================================================================
    // FIND OUTPUT
    // ========================================================================

    private static GeneratedAsset? FindGeneratedAsset(
        JsonNode result,
        VisualMode mode)
    {
        if (result["outputs"]
            is not JsonObject outputs)
        {
            return null;
        }

        string[] kinds =
            mode == VisualMode.Video
                ? ["videos", "gifs", "images"]
                : ["images"];

        foreach (var output in outputs)
        {
            foreach (var kind in kinds)
            {
                if (output.Value?[kind]
                    is not JsonArray assets)
                {
                    continue;
                }

                foreach (var asset in assets)
                {
                    var filename =
                        asset?["filename"]
                            ?.GetValue<string>();

                    if (string.IsNullOrWhiteSpace(
                            filename))
                    {
                        continue;
                    }

                    var extension =
                        Path.GetExtension(filename)
                            .ToLowerInvariant();

                    if (!IsSupportedOutput(
                            extension,
                            mode))
                    {
                        continue;
                    }

                    return new GeneratedAsset(
                        filename,
                        asset?["subfolder"]
                            ?.GetValue<string>() ?? "",
                        asset?["type"]
                            ?.GetValue<string>() ?? "output",
                        extension);
                }
            }
        }

        return null;
    }

    // ========================================================================
    // DOWNLOAD
    // ========================================================================

    private async Task<string> DownloadAsset(
        string endpoint,
        GeneratedAsset asset,
        Project project,
        Scene scene,
        VisualMode mode,
        CancellationToken ct)
    {
        var url =
            endpoint +
            "/view?filename=" +
            Uri.EscapeDataString(asset.Filename) +
            "&subfolder=" +
            Uri.EscapeDataString(asset.Subfolder) +
            "&type=" +
            Uri.EscapeDataString(asset.Type);

        var relative =
            $"assets/generated/" +
            $"{scene.Number:00}-{scene.Revision}" +
            asset.Extension;

        var destination =
            project.PathFor(relative);

        var partial =
            destination + ".part";

        Directory.CreateDirectory(
            Path.GetDirectoryName(destination)!);

        TryDelete(partial);

        try
        {
            using var response =
                await http.GetAsync(
                    url,
                    HttpCompletionOption.ResponseHeadersRead,
                    ct);

            if (!response.IsSuccessStatusCode)
            {
                var error =
                    await response.Content.ReadAsStringAsync(
                        ct);

                throw new InvalidOperationException(
                    BuildComfyHttpError(
                        "Could not download generated visual",
                        response.StatusCode,
                        error));
            }

            var maximum =
                mode == VisualMode.Video
                    ? MaximumVideoBytes
                    : MaximumImageBytes;

            if (response.Content.Headers.ContentLength
                is long declaredLength
                && declaredLength > maximum)
            {
                throw new IOException(
                    $"Generated visual is too large " +
                    $"({declaredLength:N0} bytes).");
            }

            await using var input =
                await response.Content.ReadAsStreamAsync(
                    ct);

            await using var target =
                new FileStream(
                    partial,
                    FileMode.CreateNew,
                    FileAccess.Write,
                    FileShare.None,
                    65536,
                    true);

            var buffer =
                new byte[65536];

            long total = 0;

            while (true)
            {
                var count =
                    await input.ReadAsync(
                        buffer.AsMemory(),
                        ct);

                if (count == 0)
                    break;

                total += count;

                if (total > maximum)
                {
                    throw new IOException(
                        $"Generated visual exceeds the " +
                        $"{maximum / (1024 * 1024):N0} MB safety limit.");
                }

                await target.WriteAsync(
                    buffer.AsMemory(
                        0,
                        count),
                    ct);
            }

            await target.FlushAsync(ct);

            if (total < 100)
            {
                throw new InvalidDataException(
                    "ComfyUI returned an empty or unusably small visual.");
            }
        }
        catch
        {
            TryDelete(partial);
            throw;
        }

        ValidateDownloadedFile(
            partial,
            asset.Extension,
            mode);

        File.Move(
            partial,
            destination,
            true);

        return relative;
    }

    // ========================================================================
    // BASIC FILE VALIDATION
    // ========================================================================

    private static void ValidateDownloadedFile(
        string path,
        string extension,
        VisualMode mode)
    {
        if (!File.Exists(path))
        {
            throw new InvalidDataException(
                "Generated visual was not downloaded.");
        }

        var length =
            new FileInfo(path).Length;

        if (length < 100)
        {
            throw new InvalidDataException(
                "Generated visual is empty or incomplete.");
        }

        // Perform lightweight magic-byte validation for the formats where
        // doing so is simple and reliable. FFmpeg performs deeper validation
        // when the visual is actually rendered.

        using var stream =
            File.OpenRead(path);

        Span<byte> header =
            stackalloc byte[16];

        var read =
            stream.Read(header);

        if (read < 4)
        {
            throw new InvalidDataException(
                "Generated visual has an invalid file header.");
        }

        switch (extension)
        {
            case ".png":
                if (!(header[0] == 0x89
                      && header[1] == 0x50
                      && header[2] == 0x4E
                      && header[3] == 0x47))
                {
                    throw new InvalidDataException(
                        "ComfyUI returned a file labelled PNG that is not PNG.");
                }

                break;

            case ".jpg":
            case ".jpeg":
                if (!(header[0] == 0xFF
                      && header[1] == 0xD8))
                {
                    throw new InvalidDataException(
                        "ComfyUI returned a file labelled JPEG that is not JPEG.");
                }

                break;

            case ".webp":
                if (read < 12
                    || header[0] != (byte)'R'
                    || header[1] != (byte)'I'
                    || header[2] != (byte)'F'
                    || header[3] != (byte)'F'
                    || header[8] != (byte)'W'
                    || header[9] != (byte)'E'
                    || header[10] != (byte)'B'
                    || header[11] != (byte)'P')
                {
                    throw new InvalidDataException(
                        "ComfyUI returned a file labelled WebP that is not WebP.");
                }

                break;

            case ".mp4":
                if (read < 12
                    || header[4] != (byte)'f'
                    || header[5] != (byte)'t'
                    || header[6] != (byte)'y'
                    || header[7] != (byte)'p')
                {
                    throw new InvalidDataException(
                        "ComfyUI returned a file labelled MP4 that is not MP4.");
                }

                break;
        }

        if (mode == VisualMode.Image
            && !IsImageExtension(extension))
        {
            throw new InvalidDataException(
                "Image workflow returned a non-image file.");
        }
    }

    // ========================================================================
    // PROVENANCE
    // ========================================================================

    private static async Task SaveProvenance(
        Project project,
        Scene scene,
        string workflow,
        string relative,
        string promptId,
        VisualMode mode,
        CancellationToken ct)
    {
        await Json.Save(
            project.PathFor(
                relative + ".provenance.json"),
            new
            {
                provider = "Local ComfyUI",

                mediaType =
                    mode == VisualMode.Video
                        ? "video"
                        : "image",

                workflow =
                    Path.GetFileName(workflow),

                prompt =
                    scene.VisualPrompt,

                scene =
                    scene.Number,

                revision =
                    scene.Revision,

                promptId,

                created =
                    DateTimeOffset.UtcNow,

                illustration =
                    true
            },
            ct);
    }

    // ========================================================================
    // CANCELLATION CLEANUP
    // ========================================================================

    private async Task TryDeleteQueuedJob(
        string endpoint,
        string promptId)
    {
        try
        {
            using var cleanup =
                new CancellationTokenSource(
                    TimeSpan.FromSeconds(5));

            using var response =
                await http.PostAsJsonAsync(
                    endpoint + "/queue",
                    new
                    {
                        delete =
                            new[]
                            {
                                promptId
                            }
                    },
                    cleanup.Token);
        }
        catch
        {
            // Best-effort cleanup only.
            //
            // Do NOT interrupt the whole ComfyUI server. It may be shared
            // with another local task.
        }
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    private static void ValidateScene(
        Scene scene)
    {
        if (scene.Number <= 0)
        {
            throw new InvalidDataException(
                "Cannot generate a visual for an unnumbered scene.");
        }

        if (string.IsNullOrWhiteSpace(
                scene.VisualPrompt))
        {
            throw new InvalidDataException(
                $"Scene {scene.Number} requests an AI visual but has no " +
                "visual prompt.");
        }

        if (scene.VisualPrompt.Length > 4000)
        {
            throw new InvalidDataException(
                $"Scene {scene.Number} visual prompt is unexpectedly large.");
        }
    }

    private static void ValidateWorkflowPath(
        string path,
        VisualMode mode)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new InvalidOperationException(
                mode == VisualMode.Video
                    ? "No ComfyUI video workflow is configured."
                    : "No ComfyUI image workflow is configured.");
        }

        if (!File.Exists(path))
        {
            throw new InvalidOperationException(
                $"ComfyUI workflow does not exist: {path}");
        }

        if (!string.Equals(
                Path.GetExtension(path),
                ".json",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException(
                "ComfyUI workflow must be a JSON API workflow.");
        }
    }

    // ========================================================================
    // ERROR DETAILS
    // ========================================================================

    private static string ExtractComfyFailure(
        JsonNode result)
    {
        // Different ComfyUI versions/workflows expose slightly different
        // status structures, so preserve a bounded diagnostic fragment.

        var status =
            result["status"]
                ?.ToJsonString();

        if (string.IsNullOrWhiteSpace(status))
        {
            return
                "ComfyUI generation failed. Check the ComfyUI console " +
                "for the failing node.";
        }

        return
            "ComfyUI generation failed. Status: " +
            Limit(status, 1500);
    }

    private static string BuildComfyHttpError(
        string prefix,
        HttpStatusCode status,
        string body)
    {
        var message =
            $"{prefix}: HTTP {(int)status} {status}.";

        if (!string.IsNullOrWhiteSpace(body))
        {
            message +=
                " " +
                Limit(
                    body.Replace(
                        "\r",
                        " ")
                        .Replace(
                            "\n",
                            " "),
                    1500);
        }

        return message;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static string BuildPositivePrompt(
        string prompt)
    {
        return
            prompt.Trim()
            + ", portrait composition, vertical 9:16 framing"
            + ", consistent editorial illustration"
            + ", visually clear focal subject"
            + ", no written text"
            + ", no logos"
            + ", no watermarks";
    }

    private static bool IsSupportedOutput(
        string extension,
        VisualMode mode)
    {
        if (mode == VisualMode.Image)
            return IsImageExtension(extension);

        return extension
            is ".mp4"
            or ".webm"
            or ".gif"
            or ".png"
            or ".jpg"
            or ".jpeg"
            or ".webp";
    }

    private static bool IsImageExtension(
        string extension)
    {
        return extension
            is ".png"
            or ".jpg"
            or ".jpeg"
            or ".webp";
    }

    private static bool TryGetBoolean(
        JsonNode? node)
    {
        if (node is JsonValue value
            && value.TryGetValue<bool>(
                out var result))
        {
            return result;
        }

        return false;
    }

    private static string SafeToken(
        string value)
    {
        return new string(
            value
                .Where(
                    c =>
                        char.IsLetterOrDigit(c)
                        || c == '-'
                        || c == '_')
                .ToArray());
    }

    private static string Limit(
        string value,
        int maximum)
    {
        if (value.Length <= maximum)
            return value;

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
            // Cleanup must never conceal the actual generation error.
        }
    }

    private enum VisualMode
    {
        None,
        Image,
        Video
    }

    private sealed record GeneratedAsset(
        string Filename,
        string Subfolder,
        string Type,
        string Extension);
}