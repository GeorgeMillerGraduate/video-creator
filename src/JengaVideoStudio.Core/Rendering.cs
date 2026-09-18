using System.Globalization;
using System.Text;
using System.Text.Json;

namespace JengaVideoStudio.Core;

/// <summary>
/// FFmpeg/FFprobe media renderer for Jenga Video Studio.
///
/// Responsibilities:
/// - measure narration duration
/// - render each scene independently
/// - reuse unchanged scene renders
/// - safely recover from interrupted renders
/// - concatenate scenes into the final portrait video
/// - create SRT captions and a thumbnail
/// - validate codecs, dimensions, duration and complete decodability
/// </summary>
public sealed class FfmpegRenderer(Settings settings) : IMediaRenderer
{
    private const int Width = 1080;
    private const int Height = 1920;
    private const int FrameRate = 30;

    // Change this whenever render behaviour changes in a way that should
    // invalidate cached scene renders.
    private const int RendererVersion = 3;

    private static string N(double value) =>
        value.ToString("0.000", CultureInfo.InvariantCulture);

    // ========================================================================
    // DURATION
    // ========================================================================

    public async Task<double> Duration(
        string path,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(path))
            throw new ArgumentException("Media path is empty.", nameof(path));

        if (!File.Exists(path))
            throw new FileNotFoundException(
                "Media file does not exist.",
                path);

        string value;

        try
        {
            value = await ChildProcess.Run(
                settings.Ffprobe,
                [
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    path
                ],
                null,
                null,
                TimeSpan.FromSeconds(45),
                ct);
        }
        catch (Exception e) when (
            e is InvalidOperationException
            or TimeoutException
            or IOException)
        {
            throw new InvalidDataException(
                $"Could not determine media duration for " +
                $"'{Path.GetFileName(path)}'. {e.Message}",
                e);
        }

        if (!double.TryParse(
                value.Trim(),
                NumberStyles.Float,
                CultureInfo.InvariantCulture,
                out var duration))
        {
            throw new InvalidDataException(
                $"FFprobe returned an invalid duration for " +
                $"'{Path.GetFileName(path)}': '{SafeSnippet(value)}'.");
        }

        if (double.IsNaN(duration)
            || double.IsInfinity(duration)
            || duration <= 0)
        {
            throw new InvalidDataException(
                $"FFprobe returned an unusable duration " +
                $"({duration}) for '{Path.GetFileName(path)}'.");
        }

        return duration;
    }

    // ========================================================================
    // SCENE ARGUMENTS
    // ========================================================================

    public static List<string> SceneArguments(
        Scene scene,
        string audio,
        string? visual,
        string ass,
        string output)
    {
        if (scene.Duration <= 0
            || double.IsNaN(scene.Duration)
            || double.IsInfinity(scene.Duration))
        {
            throw new InvalidDataException(
                $"Scene {scene.Number} has an invalid duration.");
        }

        var hasVisual =
            !string.IsNullOrWhiteSpace(visual);

        var isImage =
            hasVisual && IsImageFile(visual!);

        var isVideo =
            hasVisual && IsVideoFile(visual!);

        if (hasVisual && !isImage && !isVideo)
        {
            throw new InvalidDataException(
                $"Scene {scene.Number} has an unsupported visual format: " +
                $"{Path.GetExtension(visual!)}");
        }

        List<string> args =
        [
            "-y",
            "-hide_banner",
            "-loglevel", "error"
        ];

        // --------------------------------------------------------------------
        // VIDEO INPUT
        // --------------------------------------------------------------------

        if (!hasVisual)
        {
            // Deterministic fallback graphic.
            args.AddRange(
            [
                "-f", "lavfi",
                "-i",
                $"color=c=0x101c2c:s={Width}x{Height}:r={FrameRate}"
            ]);
        }
        else if (isImage)
        {
            args.AddRange(
            [
                "-loop", "1",
                "-framerate", FrameRate.ToString(CultureInfo.InvariantCulture),
                "-i", visual!
            ]);
        }
        else
        {
            // Repeat generated video if it is shorter than narration.
            args.AddRange(
            [
                "-stream_loop", "-1",
                "-i", visual!
            ]);
        }

        // Narration input.
        args.AddRange(
        [
            "-i", audio
        ]);

        // --------------------------------------------------------------------
        // VIDEO FILTER
        // --------------------------------------------------------------------

        var filters = new List<string>
        {
            $"scale={Width}:{Height}:force_original_aspect_ratio=increase",
            $"crop={Width}:{Height}",
            "setsar=1"
        };

        if (isImage)
        {
            // Very gentle motion for still images.
            filters.Add(
                $"zoompan=" +
                $"z='min(zoom+0.00008,1.06)':" +
                $"x='iw/2-iw/zoom/2':" +
                $"y='ih/2-ih/zoom/2':" +
                $"d=1:" +
                $"s={Width}x{Height}:" +
                $"fps={FrameRate}");
        }

        if (hasVisual)
        {
            // Dark overlay behind heading/caption area.
            filters.Add(
                "drawbox=" +
                "x=0:y=0:w=iw:h=600:" +
                "color=0x101c2c@0.75:t=fill");
        }
        else
        {
            // Decorative deterministic fallback layout.
            filters.Add(
                "drawbox=" +
                "x=85:y=610:w=810:h=5:" +
                "color=0x64b5e2:t=fill");

            filters.Add(
                "drawbox=" +
                "x=85:y=680:w=6:h=560:" +
                "color=0x64b5e2@0.5:t=fill");
        }

        // ASS files are generated internally inside the controlled render
        // directory. Escape characters that FFmpeg filter syntax treats
        // specially.
        filters.Add(
            "ass=" + EscapeFilterPath(ass));

        var videoFilter =
            string.Join(",", filters);

        // --------------------------------------------------------------------
        // OUTPUT
        // --------------------------------------------------------------------

        args.AddRange(
        [
            "-vf", videoFilter,

            "-map", "0:v:0",
            "-map", "1:a:0",

            "-af",
            "loudnorm=I=-16:TP=-1.5:LRA=11",

            "-t", N(scene.Duration),

            "-r", FrameRate.ToString(CultureInfo.InvariantCulture),

            "-c:v", "libx264",
            "-preset", "veryfast",
            "-crf", "21",
            "-pix_fmt", "yuv420p",

            "-c:a", "aac",
            "-ar", "48000",
            "-ac", "2",
            "-b:a", "192k",

            "-movflags", "+faststart",

            // Machine-readable progress for the WPF UI.
            "-progress", "pipe:1",
            "-nostats",

            output
        ]);

        return args;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    public async Task Render(
        Project p,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        ValidateProjectForRendering(p);

        var renderDir =
            Path.GetDirectoryName(
                p.PathFor("renders/scenes/list.txt"))!;

        Directory.CreateDirectory(renderDir);

        var finalDirectory =
            Path.GetDirectoryName(
                p.PathFor("renders/final/final.mp4"))!;

        Directory.CreateDirectory(finalDirectory);

        // ====================================================================
        // SCENE RENDERING
        // ====================================================================

        for (var i = 0; i < p.Scenes.Count; i++)
        {
            ct.ThrowIfCancellationRequested();

            var scene = p.Scenes[i];

            scene.Number = i + 1;

            var sceneName =
                $"scene-{i + 1:00}";

            var assPath =
                Path.Combine(
                    renderDir,
                    sceneName + ".ass");

            var outputPath =
                Path.Combine(
                    renderDir,
                    sceneName + ".mp4");

            var partialPath =
                Path.Combine(
                    renderDir,
                    sceneName + ".partial.mp4");

            var hashPath =
                outputPath + ".hash";

            // ----------------------------------------------------------------
            // SOURCE REFERENCES
            // ----------------------------------------------------------------

            var references =
                ResolveSourceReferences(
                    p,
                    scene);

            var sourceLabel =
                references.Count == 0
                    ? "Sources: review project research"
                    : "Sources: " + string.Join(", ", references);

            await WriteTextAtomically(
                assPath,
                Captions.Ass(
                    scene,
                    sourceLabel),
                ct);

            // ----------------------------------------------------------------
            // INPUT VALIDATION
            // ----------------------------------------------------------------

            if (string.IsNullOrWhiteSpace(scene.Audio))
            {
                throw new InvalidDataException(
                    $"Scene {scene.Number} has no narration audio.");
            }

            var audioPath =
                p.PathFor(scene.Audio);

            if (!File.Exists(audioPath))
            {
                throw new FileNotFoundException(
                    $"Narration audio for scene {scene.Number} is missing.",
                    audioPath);
            }

            string? visualPath = null;

            if (!string.IsNullOrWhiteSpace(scene.Visual))
            {
                visualPath =
                    p.PathFor(scene.Visual);

                if (!File.Exists(visualPath))
                {
                    // Visual generation is optional. A missing generated
                    // visual should degrade to Jenga's graphic rather than
                    // destroy an otherwise usable production.
                    visualPath = null;
                }
            }

            // ----------------------------------------------------------------
            // CACHE IDENTITY
            // ----------------------------------------------------------------

            var renderHash =
                BuildSceneRenderHash(
                    scene,
                    visualPath != null);

            var reusable =
                await IsReusableScene(
                    outputPath,
                    hashPath,
                    renderHash,
                    ct);

            if (reusable)
            {
                progress.Report(
                    new ProgressUpdate(
                        "Rendering",
                        $"Scene {i + 1} of {p.Scenes.Count} · cached",
                        70 + 22.0 * (i + 1) / p.Scenes.Count,
                        false));

                continue;
            }

            // Never trust a stale partial file from a crashed/interrupted
            // FFmpeg process.
            TryDelete(partialPath);

            // ----------------------------------------------------------------
            // FFMPEG
            // ----------------------------------------------------------------

            var args =
                SceneArguments(
                    scene,
                    audioPath,
                    visualPath,
                    sceneName + ".ass",
                    sceneName + ".partial.mp4");

            await Json.Save(
                Path.Combine(
                    renderDir,
                    sceneName + ".arguments.json"),
                args,
                ct);

            try
            {
                await ChildProcess.Run(
                    settings.Ffmpeg,
                    args,
                    renderDir,
                    null,
                    TimeSpan.FromHours(1),
                    ct,
                    line =>
                    {
                        ReportRenderProgress(
                            line,
                            i,
                            p.Scenes.Count,
                            scene.Duration,
                            progress);
                    });
            }
            catch
            {
                TryDelete(partialPath);
                throw;
            }

            if (!File.Exists(partialPath))
            {
                throw new InvalidDataException(
                    $"FFmpeg reported success but scene {scene.Number} " +
                    "did not produce an output file.");
            }

            if (new FileInfo(partialPath).Length < 1024)
            {
                TryDelete(partialPath);

                throw new InvalidDataException(
                    $"FFmpeg produced an empty or unusable file for " +
                    $"scene {scene.Number}.");
            }

            // Validate each scene before placing it into the cache.
            await ValidateSceneFile(
                partialPath,
                scene,
                ct);

            File.Move(
                partialPath,
                outputPath,
                true);

            await WriteTextAtomically(
                hashPath,
                renderHash,
                ct);

            progress.Report(
                new ProgressUpdate(
                    "Rendering",
                    $"Scene {i + 1} of {p.Scenes.Count} complete",
                    70 + 22.0 * (i + 1) / p.Scenes.Count,
                    false));
        }

        // ====================================================================
        // CONCAT LIST
        // ====================================================================

        var listPath =
            Path.Combine(
                renderDir,
                "list.txt");

        var concatLines =
            Enumerable.Range(
                    1,
                    p.Scenes.Count)
                .Select(
                    i =>
                        $"file 'scene-{i:00}.mp4'")
                .ToArray();

        await WriteLinesAtomically(
            listPath,
            concatLines,
            ct);

        // ====================================================================
        // FINAL VIDEO
        // ====================================================================

        var final =
            p.PathFor(
                "renders/final/final.mp4");

        var finalPartial =
            final + ".partial.mp4";

        TryDelete(finalPartial);

        try
        {
            await ChildProcess.Run(
                settings.Ffmpeg,
                [
                    "-y",
                    "-hide_banner",
                    "-loglevel", "error",

                    "-f", "concat",
                    "-safe", "1",
                    "-i", "list.txt",

                    // Scene files are deliberately encoded identically, so
                    // stream-copy concatenation avoids another lossy encode.
                    "-c", "copy",

                    "-movflags", "+faststart",

                    finalPartial
                ],
                renderDir,
                null,
                TimeSpan.FromMinutes(15),
                ct);
        }
        catch
        {
            TryDelete(finalPartial);
            throw;
        }

        if (!File.Exists(finalPartial)
            || new FileInfo(finalPartial).Length < 1024)
        {
            TryDelete(finalPartial);

            throw new InvalidDataException(
                "FFmpeg did not produce a usable final video.");
        }

        File.Move(
            finalPartial,
            final,
            true);

        p.FinalVideo =
            "renders/final/final.mp4";

        // ====================================================================
        // SUBTITLES
        // ====================================================================

        await WriteTextAtomically(
            p.PathFor(
                "subtitles/captions.srt"),
            Captions.Srt(p),
            ct);

        // ====================================================================
        // THUMBNAIL
        // ====================================================================

        await CreateThumbnail(
            p,
            final,
            ct);

        progress.Report(
            new ProgressUpdate(
                "Rendering",
                "Final video assembled",
                92,
                false));
    }

    // ========================================================================
    // VALIDATE COMPLETE VIDEO
    // ========================================================================

    public async Task Validate(
        Project p,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(p.FinalVideo))
        {
            throw new InvalidDataException(
                "Project has no final video to validate.");
        }

        var finalPath =
            p.PathFor(p.FinalVideo);

        if (!File.Exists(finalPath))
        {
            throw new FileNotFoundException(
                "The final video file is missing.",
                finalPath);
        }

        if (new FileInfo(finalPath).Length < 1024)
        {
            throw new InvalidDataException(
                "The final video file is empty or incomplete.");
        }

        string output;

        try
        {
            output =
                await ProbeJson(
                    finalPath,
                    ct);
        }
        catch (Exception e) when (
            e is InvalidOperationException
            or TimeoutException
            or IOException)
        {
            throw new InvalidDataException(
                "FFprobe could not inspect the final video. " +
                e.Message,
                e);
        }

        await WriteTextAtomically(
            p.PathFor(
                "renders/final/probe.json"),
            output,
            ct);

        ValidateProbe(
            output,
            p);

        // --------------------------------------------------------------------
        // COMPLETE DECODE
        // --------------------------------------------------------------------
        //
        // Checking the MP4 header alone is insufficient. A damaged file can
        // have perfectly valid metadata and still fail halfway through.
        // Decode the complete result without producing another output file.

        try
        {
            await ChildProcess.Run(
                settings.Ffmpeg,
                [
                    "-v", "error",
                    "-xerror",
                    "-i", finalPath,
                    "-map", "0:v:0",
                    "-map", "0:a:0",
                    "-f", "null",
                    "-"
                ],
                null,
                null,
                TimeSpan.FromMinutes(30),
                ct);
        }
        catch (Exception e) when (
            e is InvalidOperationException
            or TimeoutException)
        {
            throw new InvalidDataException(
                "The final video exists but failed a complete decode test. " +
                "The render may be damaged or incomplete. " +
                e.Message,
                e);
        }
    }

    // ========================================================================
    // PROBE VALIDATION
    // ========================================================================

    private static void ValidateProbe(
        string json,
        Project p)
    {
        JsonDocument document;

        try
        {
            document =
                JsonDocument.Parse(json);
        }
        catch (JsonException e)
        {
            throw new InvalidDataException(
                "FFprobe returned malformed JSON.",
                e);
        }

        using (document)
        {
            var root =
                document.RootElement;

            if (!root.TryGetProperty(
                    "streams",
                    out var streamsElement)
                || streamsElement.ValueKind != JsonValueKind.Array)
            {
                throw new InvalidDataException(
                    "FFprobe output contains no media streams.");
            }

            var streams =
                streamsElement
                    .EnumerateArray()
                    .ToList();

            var video =
                streams.FirstOrDefault(
                    IsVideoStream);

            if (video.ValueKind == JsonValueKind.Undefined)
            {
                throw new InvalidDataException(
                    "Final output contains no video stream.");
            }

            var audio =
                streams.FirstOrDefault(
                    IsAudioStream);

            if (audio.ValueKind == JsonValueKind.Undefined)
            {
                throw new InvalidDataException(
                    "Final output contains no audio stream.");
            }

            var videoCodec =
                GetString(
                    video,
                    "codec_name");

            var width =
                GetInt(
                    video,
                    "width");

            var height =
                GetInt(
                    video,
                    "height");

            var audioCodec =
                GetString(
                    audio,
                    "codec_name");

            if (!string.Equals(
                    videoCodec,
                    "h264",
                    StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException(
                    $"Output validation failed: expected H.264 video, " +
                    $"found '{videoCodec}'.");
            }

            if (width != Width
                || height != Height)
            {
                throw new InvalidDataException(
                    $"Output validation failed: expected " +
                    $"{Width}x{Height} portrait video, " +
                    $"found {width}x{height}.");
            }

            if (!string.Equals(
                    audioCodec,
                    "aac",
                    StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException(
                    $"Output validation failed: expected AAC audio, " +
                    $"found '{audioCodec}'.");
            }

            if (!root.TryGetProperty(
                    "format",
                    out var format))
            {
                throw new InvalidDataException(
                    "FFprobe output contains no format information.");
            }

            var durationText =
                GetString(
                    format,
                    "duration");

            if (!double.TryParse(
                    durationText,
                    NumberStyles.Float,
                    CultureInfo.InvariantCulture,
                    out var actual))
            {
                throw new InvalidDataException(
                    "FFprobe returned an invalid final-video duration.");
            }

            if (actual < 1
                || double.IsNaN(actual)
                || double.IsInfinity(actual))
            {
                throw new InvalidDataException(
                    "Final video duration is unusable.");
            }

            var expected =
                p.Scenes.Sum(
                    s => s.Duration);

            // Allow a small amount for container/audio timestamp rounding.
            var timelineTolerance =
                Math.Max(
                    1.0,
                    p.Scenes.Count * 0.15);

            if (Math.Abs(actual - expected)
                > timelineTolerance)
            {
                throw new InvalidDataException(
                    $"Output duration does not match the scene timeline. " +
                    $"Expected approximately {expected:0.00}s, " +
                    $"found {actual:0.00}s.");
            }

            if (p.TargetSeconds > 0
                && Math.Abs(actual - p.TargetSeconds)
                    > p.TargetSeconds * 0.25)
            {
                AddWarningOnce(
                    p,
                    $"Actual duration {actual:0}s differs from target " +
                    $"{p.TargetSeconds}s. Edit narration if needed.");
            }
        }
    }

    // ========================================================================
    // VALIDATE INDIVIDUAL SCENE
    // ========================================================================

    private async Task ValidateSceneFile(
        string path,
        Scene scene,
        CancellationToken ct)
    {
        var json =
            await ProbeJson(
                path,
                ct);

        using var document =
            JsonDocument.Parse(json);

        var root =
            document.RootElement;

        if (!root.TryGetProperty(
                "streams",
                out var streamsElement))
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} has no media streams.");
        }

        var streams =
            streamsElement
                .EnumerateArray()
                .ToList();

        var video =
            streams.FirstOrDefault(
                IsVideoStream);

        var audio =
            streams.FirstOrDefault(
                IsAudioStream);

        if (video.ValueKind == JsonValueKind.Undefined
            || audio.ValueKind == JsonValueKind.Undefined)
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} is missing video or audio.");
        }

        if (GetInt(video, "width") != Width
            || GetInt(video, "height") != Height)
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} has incorrect dimensions.");
        }

        if (!string.Equals(
                GetString(video, "codec_name"),
                "h264",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} is not H.264.");
        }

        if (!string.Equals(
                GetString(audio, "codec_name"),
                "aac",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} does not contain AAC audio.");
        }

        var actual =
            await Duration(
                path,
                ct);

        if (Math.Abs(actual - scene.Duration) > 0.75)
        {
            throw new InvalidDataException(
                $"Rendered scene {scene.Number} duration is incorrect. " +
                $"Expected approximately {scene.Duration:0.00}s, " +
                $"found {actual:0.00}s.");
        }
    }

    // ========================================================================
    // THUMBNAIL
    // ========================================================================

    private async Task CreateThumbnail(
        Project p,
        string final,
        CancellationToken ct)
    {
        var thumbnail =
            p.PathFor(
                "renders/final/thumbnail.png");

        var partial =
            thumbnail + ".partial.png";

        TryDelete(partial);

        try
        {
            await ChildProcess.Run(
                settings.Ffmpeg,
                [
                    "-y",
                    "-hide_banner",
                    "-loglevel", "error",

                    // Pick a frame slightly into the video rather than
                    // potentially grabbing a completely blank first frame.
                    "-ss", "0.25",
                    "-i", final,

                    "-frames:v", "1",

                    partial
                ],
                null,
                null,
                TimeSpan.FromMinutes(2),
                ct);

            if (!File.Exists(partial)
                || new FileInfo(partial).Length < 100)
            {
                throw new InvalidDataException(
                    "FFmpeg did not create a usable thumbnail.");
            }

            File.Move(
                partial,
                thumbnail,
                true);
        }
        catch
        {
            TryDelete(partial);
            throw;
        }
    }

    // ========================================================================
    // FFPROBE
    // ========================================================================

    private async Task<string> ProbeJson(
        string path,
        CancellationToken ct)
    {
        return await ChildProcess.Run(
            settings.Ffprobe,
            [
                "-v", "error",
                "-show_streams",
                "-show_format",
                "-of", "json",
                path
            ],
            null,
            null,
            TimeSpan.FromMinutes(1),
            ct);
    }

    // ========================================================================
    // CACHE
    // ========================================================================

    private static string BuildSceneRenderHash(
        Scene scene,
        bool visualExists)
    {
        return Util.Hash(
            Json.Encode(
                new
                {
                    scene.Narration,
                    scene.Heading,
                    scene.Points,
                    scene.ClaimIds,

                    scene.AudioHash,
                    scene.VisualHash,

                    scene.Duration,
                    scene.VisualType,
                    scene.Revision,

                    VisualAvailable = visualExists,

                    Width,
                    Height,
                    FrameRate,

                    Renderer = RendererVersion
                }));
    }

    private static async Task<bool> IsReusableScene(
        string output,
        string hashPath,
        string expectedHash,
        CancellationToken ct)
    {
        if (!File.Exists(output)
            || !File.Exists(hashPath))
        {
            return false;
        }

        if (new FileInfo(output).Length < 1024)
            return false;

        string savedHash;

        try
        {
            savedHash =
                (await File.ReadAllTextAsync(
                    hashPath,
                    ct))
                .Trim();
        }
        catch (IOException)
        {
            return false;
        }

        return string.Equals(
            savedHash,
            expectedHash,
            StringComparison.Ordinal);
    }

    // ========================================================================
    // SOURCE LABELS
    // ========================================================================

    private static List<string> ResolveSourceReferences(
        Project p,
        Scene scene)
    {
        if (p.Brief == null)
            return [];

        var claims =
            p.Brief.Claims
                .ToDictionary(
                    c => c.Id,
                    StringComparer.Ordinal);

        var references =
            new HashSet<string>(
                StringComparer.Ordinal);

        foreach (var claimId in scene.ClaimIds)
        {
            if (!claims.TryGetValue(
                    claimId,
                    out var claim))
            {
                continue;
            }

            foreach (var evidence in claim.Evidence)
            {
                if (!string.IsNullOrWhiteSpace(
                        evidence.SourceId))
                {
                    references.Add(
                        evidence.SourceId);
                }
            }
        }

        return references
            .OrderBy(x => x)
            .ToList();
    }

    // ========================================================================
    // PROGRESS
    // ========================================================================

    private static void ReportRenderProgress(
        string line,
        int sceneIndex,
        int sceneCount,
        double sceneDuration,
        IProgress<ProgressUpdate> progress)
    {
        if (sceneCount <= 0
            || sceneDuration <= 0)
        {
            return;
        }

        double seconds;

        if (line.StartsWith(
                "out_time_us=",
                StringComparison.Ordinal)
            && long.TryParse(
                line["out_time_us=".Length..],
                NumberStyles.Integer,
                CultureInfo.InvariantCulture,
                out var microseconds))
        {
            seconds =
                microseconds / 1_000_000.0;
        }
        else if (line.StartsWith(
                     "out_time_ms=",
                     StringComparison.Ordinal)
                 && long.TryParse(
                     line["out_time_ms=".Length..],
                     NumberStyles.Integer,
                     CultureInfo.InvariantCulture,
                     out var value))
        {
            // FFmpeg historically labels this field out_time_ms even when
            // reporting microseconds on some versions.
            seconds =
                value / 1_000_000.0;
        }
        else
        {
            return;
        }

        var sceneProgress =
            Math.Clamp(
                seconds / sceneDuration,
                0,
                1);

        var overall =
            70
            + 22
            * (sceneIndex + sceneProgress)
            / sceneCount;

        progress.Report(
            new ProgressUpdate(
                "Rendering",
                $"Scene {sceneIndex + 1} of {sceneCount}",
                overall,
                false));
    }

    // ========================================================================
    // PROJECT VALIDATION
    // ========================================================================

    private static void ValidateProjectForRendering(
        Project p)
    {
        if (p.Scenes.Count == 0)
        {
            throw new InvalidDataException(
                "Project contains no scenes to render.");
        }

        if (p.Brief == null)
        {
            throw new InvalidDataException(
                "Project contains no research brief.");
        }

        for (var i = 0; i < p.Scenes.Count; i++)
        {
            var scene =
                p.Scenes[i];

            if (scene.Duration <= 0
                || double.IsNaN(scene.Duration)
                || double.IsInfinity(scene.Duration))
            {
                throw new InvalidDataException(
                    $"Scene {i + 1} has no valid narration duration.");
            }

            if (string.IsNullOrWhiteSpace(
                    scene.Audio))
            {
                throw new InvalidDataException(
                    $"Scene {i + 1} has no narration audio.");
            }
        }
    }

    // ========================================================================
    // FILE TYPES
    // ========================================================================

    private static bool IsImageFile(
        string path)
    {
        return Path.GetExtension(path)
            .ToLowerInvariant()
            is ".png"
            or ".jpg"
            or ".jpeg"
            or ".webp";
    }

    private static bool IsVideoFile(
        string path)
    {
        return Path.GetExtension(path)
            .ToLowerInvariant()
            is ".mp4"
            or ".webm"
            or ".mov"
            or ".mkv";
    }

    // ========================================================================
    // JSON HELPERS
    // ========================================================================

    private static bool IsVideoStream(
        JsonElement stream)
    {
        return string.Equals(
            GetString(
                stream,
                "codec_type"),
            "video",
            StringComparison.OrdinalIgnoreCase);
    }

    private static bool IsAudioStream(
        JsonElement stream)
    {
        return string.Equals(
            GetString(
                stream,
                "codec_type"),
            "audio",
            StringComparison.OrdinalIgnoreCase);
    }

    private static string GetString(
        JsonElement element,
        string property)
    {
        if (!element.TryGetProperty(
                property,
                out var value))
        {
            return "";
        }

        return value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? ""
            : value.ToString();
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

        if (value.ValueKind == JsonValueKind.Number
            && value.TryGetInt32(out var number))
        {
            return number;
        }

        return int.TryParse(
            value.ToString(),
            NumberStyles.Integer,
            CultureInfo.InvariantCulture,
            out number)
            ? number
            : 0;
    }

    // ========================================================================
    // ATOMIC FILE WRITING
    // ========================================================================

    private static async Task WriteTextAtomically(
        string path,
        string content,
        CancellationToken ct)
    {
        Directory.CreateDirectory(
            Path.GetDirectoryName(
                Path.GetFullPath(path))!);

        var temp =
            path + ".tmp";

        try
        {
            await File.WriteAllTextAsync(
                temp,
                content,
                Encoding.UTF8,
                ct);

            File.Move(
                temp,
                path,
                true);
        }
        finally
        {
            TryDelete(temp);
        }
    }

    private static async Task WriteLinesAtomically(
        string path,
        IEnumerable<string> lines,
        CancellationToken ct)
    {
        await WriteTextAtomically(
            path,
            string.Join(
                Environment.NewLine,
                lines)
            + Environment.NewLine,
            ct);
    }

    // ========================================================================
    // SMALL HELPERS
    // ========================================================================

    private static string EscapeFilterPath(
        string path)
    {
        // The renderer normally supplies a simple relative filename such as
        // scene-01.ass. These replacements also keep the method safe if that
        // changes later.
        return path
            .Replace("\\", "/")
            .Replace(":", "\\:")
            .Replace("'", "\\'");
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
            // Cleanup must not hide the original rendering failure.
        }
    }

    private static string SafeSnippet(
        string value)
    {
        value =
            value
                .Replace("\r", " ")
                .Replace("\n", " ")
                .Trim();

        return value.Length <= 500
            ? value
            : value[..500] + "...";
    }

    private static void AddWarningOnce(
        Project p,
        string warning)
    {
        if (!p.Warnings.Contains(
                warning,
                StringComparer.OrdinalIgnoreCase))
        {
            p.Warnings.Add(
                warning);
        }
    }
}