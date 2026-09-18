using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace JengaVideoStudio.Core;

// ============================================================================
// CAPTION DATA
// ============================================================================

public sealed record Cue(
    double Start,
    double End,
    string Text);

// ============================================================================
// CAPTIONS
// ============================================================================

public static class Captions
{
    private const int DefaultCaptionWidth = 28;
    private const int MaximumCaptionLines = 2;

    // Avoid effectively zero-length subtitle events.
    private const double MinimumCueDuration = 0.12;

    // ========================================================================
    // TEXT WRAPPING
    // ========================================================================

    public static List<string> Wrap(
        string text,
        int width = DefaultCaptionWidth)
    {
        if (width <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(width),
                "Caption width must be greater than zero.");
        }

        if (string.IsNullOrWhiteSpace(text))
            return [];

        var words =
            Regex.Split(
                text.Trim(),
                @"\s+")
                .Where(x => !string.IsNullOrWhiteSpace(x));

        var lines =
            new List<string>();

        var current =
            new StringBuilder();

        foreach (var word in words)
        {
            // Very long tokens such as URLs need splitting so they cannot
            // create an enormous subtitle line.
            if (word.Length > width)
            {
                FlushLine(
                    current,
                    lines);

                for (var offset = 0;
                     offset < word.Length;
                     offset += width)
                {
                    var length =
                        Math.Min(
                            width,
                            word.Length - offset);

                    lines.Add(
                        word.Substring(
                            offset,
                            length));
                }

                continue;
            }

            var required =
                current.Length == 0
                    ? word.Length
                    : current.Length + 1 + word.Length;

            if (required > width)
            {
                FlushLine(
                    current,
                    lines);
            }

            if (current.Length > 0)
                current.Append(' ');

            current.Append(word);
        }

        FlushLine(
            current,
            lines);

        return lines;
    }

    private static void FlushLine(
        StringBuilder current,
        List<string> lines)
    {
        if (current.Length == 0)
            return;

        lines.Add(
            current.ToString());

        current.Clear();
    }

    // ========================================================================
    // CUE GENERATION
    // ========================================================================

    public static List<Cue> ForScene(
        Scene scene)
    {
        ArgumentNullException.ThrowIfNull(scene);

        if (string.IsNullOrWhiteSpace(
                scene.Narration))
        {
            return [];
        }

        if (!double.IsFinite(scene.Duration)
            ||
            scene.Duration <= 0)
        {
            return [];
        }

        var lines =
            Wrap(
                scene.Narration);

        if (lines.Count == 0)
            return [];

        var chunks =
            new List<string>();

        for (var i = 0;
             i < lines.Count;
             i += MaximumCaptionLines)
        {
            chunks.Add(
                string.Join(
                    "\n",
                    lines
                        .Skip(i)
                        .Take(MaximumCaptionLines)));
        }

        if (chunks.Count == 0)
            return [];

        /*
         * Timing is proportional to the amount of visible text in each cue.
         *
         * Ignore whitespace when calculating weight so a line break does not
         * artificially receive speaking time.
         */

        var weights =
            chunks
                .Select(TextWeight)
                .ToArray();

        var totalWeight =
            weights.Sum();

        if (totalWeight <= 0)
            return [];

        var result =
            new List<Cue>(
                chunks.Count);

        var at =
            0d;

        for (var i = 0;
             i < chunks.Count;
             i++)
        {
            double end;

            if (i == chunks.Count - 1)
            {
                // Force the final cue to end exactly with the scene.
                end = scene.Duration;
            }
            else
            {
                end =
                    at +
                    scene.Duration *
                    weights[i] /
                    totalWeight;

                // Prevent pathological tiny cues while never exceeding
                // the scene boundary.
                end =
                    Math.Min(
                        scene.Duration,
                        Math.Max(
                            end,
                            at + MinimumCueDuration));
            }

            if (end <= at)
                continue;

            result.Add(
                new Cue(
                    at,
                    end,
                    chunks[i]));

            at = end;
        }

        // Numerical rounding or minimum-duration correction can leave an
        // unusual final boundary. Normalise it.
        if (result.Count > 0)
        {
            var last =
                result[^1];

            result[^1] =
                last with
                {
                    End = scene.Duration
                };
        }

        return result;
    }

    private static int TextWeight(
        string text)
    {
        var count =
            text.Count(
                c => !char.IsWhiteSpace(c));

        return Math.Max(
            1,
            count);
    }

    // ========================================================================
    // TIMESTAMPS
    // ========================================================================

    public static string Stamp(
        double seconds,
        bool ass = false)
    {
        if (!double.IsFinite(seconds))
            seconds = 0;

        seconds =
            Math.Max(
                0,
                seconds);

        /*
         * Round explicitly rather than relying on TimeSpan's fractional
         * behaviour. This avoids timestamps such as a cue ending a fraction
         * below the intended millisecond boundary.
         */

        if (ass)
        {
            // ASS timestamps use centiseconds.
            var centiseconds =
                (long)Math.Round(
                    seconds * 100,
                    MidpointRounding.AwayFromZero);

            var hours =
                centiseconds / 360000;

            var remainder =
                centiseconds % 360000;

            var minutes =
                remainder / 6000;

            remainder %=
                6000;

            var secs =
                remainder / 100;

            var cs =
                remainder % 100;

            return string.Create(
                CultureInfo.InvariantCulture,
                $"{hours}:{minutes:00}:{secs:00}.{cs:00}");
        }

        // SRT uses milliseconds.
        var milliseconds =
            (long)Math.Round(
                seconds * 1000,
                MidpointRounding.AwayFromZero);

        var srtHours =
            milliseconds / 3_600_000;

        var srtRemainder =
            milliseconds % 3_600_000;

        var srtMinutes =
            srtRemainder / 60_000;

        srtRemainder %=
            60_000;

        var srtSeconds =
            srtRemainder / 1000;

        var ms =
            srtRemainder % 1000;

        return string.Create(
            CultureInfo.InvariantCulture,
            $"{srtHours:00}:{srtMinutes:00}:{srtSeconds:00},{ms:000}");
    }

    // ========================================================================
    // ASS TEXT SAFETY
    // ========================================================================

    public static string Safe(
        string text)
    {
        if (string.IsNullOrEmpty(text))
            return "";

        /*
         * ASS uses:
         *   { ... }  for override commands
         *   \N       for explicit new lines
         *
         * Do not permit source/narration text to inject formatting commands.
         */

        return text
            .Replace(
                "\\",
                "/",
                StringComparison.Ordinal)
            .Replace(
                "{",
                "(",
                StringComparison.Ordinal)
            .Replace(
                "}",
                ")",
                StringComparison.Ordinal)
            .Replace(
                "\r\n",
                "\n",
                StringComparison.Ordinal)
            .Replace(
                '\r',
                '\n')
            .Replace(
                "\n",
                @"\N",
                StringComparison.Ordinal);
    }

    // ========================================================================
    // SRT
    // ========================================================================

    public static string Srt(
        Project project)
    {
        ArgumentNullException.ThrowIfNull(project);

        var builder =
            new StringBuilder();

        var index =
            1;

        var sceneStart =
            0d;

        foreach (var scene in project.Scenes)
        {
            if (!double.IsFinite(scene.Duration)
                ||
                scene.Duration <= 0)
            {
                continue;
            }

            foreach (var cue in ForScene(scene))
            {
                var start =
                    sceneStart + cue.Start;

                var end =
                    sceneStart + cue.End;

                if (end <= start)
                    continue;

                builder.AppendLine(
                    index
                        .ToString(
                            CultureInfo.InvariantCulture));

                builder.Append(
                    Stamp(start));

                builder.Append(
                    " --> ");

                builder.AppendLine(
                    Stamp(end));

                /*
                 * SRT does not use ASS escape sequences. Preserve the actual
                 * line break generated by ForScene().
                 */
                builder.AppendLine(
                    NormaliseSrtText(
                        cue.Text));

                builder.AppendLine();

                index++;
            }

            sceneStart +=
                scene.Duration;
        }

        return builder.ToString();
    }

    private static string NormaliseSrtText(
        string text)
    {
        if (string.IsNullOrWhiteSpace(text))
            return "";

        return text
            .Replace(
                "\r\n",
                "\n",
                StringComparison.Ordinal)
            .Replace(
                '\r',
                '\n')
            .Trim();
    }

    // ========================================================================
    // ASS
    // ========================================================================

    public static string Ass(
        Scene scene,
        string source)
    {
        ArgumentNullException.ThrowIfNull(scene);

        if (!double.IsFinite(scene.Duration)
            ||
            scene.Duration <= 0)
        {
            throw new InvalidDataException(
                $"Scene {scene.Number} has an invalid duration " +
                $"({scene.Duration}).");
        }

        var builder =
            new StringBuilder(
                """
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1080
                PlayResY: 1920
                WrapStyle: 2
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: Caption,Arial,58,&H00FFFFFF,&H00FFFFFF,&H00120E09,&HAA120E09,-1,0,0,0,100,100,0,0,3,12,0,2,100,140,330,1
                Style: Heading,Arial,84,&H00FFFFFF,&H00FFFFFF,&H00231C13,&H00231C13,-1,0,0,0,100,100,0,0,1,2,0,7,95,140,280,1
                Style: Label,Arial,43,&H00E0BA65,&H00FFFFFF,&H00231C13,&H00231C13,0,0,0,0,100,100,0,0,1,2,0,7,95,140,160,1
                Style: Point,Arial,56,&H00FFFFFF,&H00FFFFFF,&H00231C13,&H00231C13,0,0,0,0,100,100,0,0,1,3,0,7,95,140,700,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """);

        builder.AppendLine();

        void AddEvent(
            double start,
            double end,
            string style,
            string text)
        {
            start =
                Math.Clamp(
                    start,
                    0,
                    scene.Duration);

            end =
                Math.Clamp(
                    end,
                    0,
                    scene.Duration);

            if (end <= start)
                return;

            builder.Append(
                "Dialogue: 0,");

            builder.Append(
                Stamp(
                    start,
                    true));

            builder.Append(',');

            builder.Append(
                Stamp(
                    end,
                    true));

            builder.Append(',');

            builder.Append(style);

            builder.Append(
                ",,0,0,0,,");

            builder.AppendLine(text);
        }

        // --------------------------------------------------------------------
        // BRAND LABEL
        // --------------------------------------------------------------------

        AddEvent(
            0,
            scene.Duration,
            "Label",
            "JENGA  /  EXPLAINED");

        // --------------------------------------------------------------------
        // HEADING
        // --------------------------------------------------------------------

        if (!string.IsNullOrWhiteSpace(
                scene.Heading))
        {
            var heading =
                string.Join(
                    "\n",
                    Wrap(
                            scene.Heading,
                            18)
                        .Take(3));

            AddEvent(
                0,
                scene.Duration,
                "Heading",
                @"{\fad(180,120)}"
                + Safe(heading));
        }

        // --------------------------------------------------------------------
        // DETERMINISTIC GRAPHICS
        // --------------------------------------------------------------------

        if (string.IsNullOrWhiteSpace(
                scene.Visual))
        {
            var points =
                scene.Points
                    .Where(
                        x => !string.IsNullOrWhiteSpace(x))
                    .Take(3)
                    .ToList();

            if (points.Count == 0
                &&
                !string.IsNullOrWhiteSpace(
                    scene.Heading))
            {
                points.Add(
                    scene.Heading);
            }

            for (var i = 0;
                 i < points.Count;
                 i++)
            {
                var prefix =
                    scene.VisualType switch
                    {
                        "Timeline" =>
                            $"{i + 1:00}  /  ",

                        "Diagram" =>
                            $"{i + 1}  →  ",

                        _ =>
                            ""
                    };

                var text =
                    prefix + points[i];

                var wrapped =
                    string.Join(
                        "\n",
                        Wrap(
                                text,
                                24)
                            .Take(2));

                var start =
                    Math.Min(
                        i * 0.35,
                        scene.Duration / 4);

                var y =
                    740 + i * 185;

                AddEvent(
                    start,
                    scene.Duration,
                    "Point",
                    $@"{{\pos(110,{y})\fad(250,120)}}"
                    + Safe(wrapped));
            }
        }

        // --------------------------------------------------------------------
        // SOURCE LABEL
        // --------------------------------------------------------------------

        var safeSource =
            BuildSourceLabel(source);

        if (!string.IsNullOrWhiteSpace(
                safeSource))
        {
            AddEvent(
                0,
                scene.Duration,
                "Label",
                @"{\pos(95,1740)\fs28}"
                + Safe(safeSource));
        }

        // --------------------------------------------------------------------
        // GENERATED-MEDIA LABEL
        // --------------------------------------------------------------------

        if (!string.IsNullOrWhiteSpace(
                scene.Visual))
        {
            AddEvent(
                0,
                scene.Duration,
                "Label",
                @"{\pos(95,1660)\fs28}"
                + "AI-generated illustration");
        }

        // --------------------------------------------------------------------
        // SPOKEN CAPTIONS
        // --------------------------------------------------------------------

        foreach (var cue in ForScene(scene))
        {
            AddEvent(
                cue.Start,
                cue.End,
                "Caption",
                Safe(cue.Text));
        }

        return builder.ToString();
    }

    // ========================================================================
    // SOURCE LABEL
    // ========================================================================

    private static string BuildSourceLabel(
        string source)
    {
        if (string.IsNullOrWhiteSpace(source))
            return "";

        var cleaned =
            Regex.Replace(
                    source.Trim(),
                    @"\s+",
                    " ")
                .Trim();

        const int maximum =
            80;

        if (cleaned.Length <= maximum)
            return cleaned;

        // Avoid chopping halfway through a Unicode surrogate pair.
        var length =
            maximum;

        if (length > 0
            &&
            length < cleaned.Length
            &&
            char.IsHighSurrogate(
                cleaned[length - 1])
            &&
            char.IsLowSurrogate(
                cleaned[length]))
        {
            length--;
        }

        return cleaned[..length]
            .TrimEnd()
            + "…";
    }
}