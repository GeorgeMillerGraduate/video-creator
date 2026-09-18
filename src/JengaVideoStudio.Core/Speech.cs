using System.Text;

namespace JengaVideoStudio.Core;

/// <summary>
/// Local narration provider.
///
/// Supports:
/// - Windows Speech through scripts/speak.ps1
/// - Piper neural TTS
///
/// Audio is always generated to a temporary file first. The final narration
/// file is replaced only after generation succeeds and the WAV passes basic
/// validation.
/// </summary>
public sealed class LocalSpeech(Settings settings) : ITextToSpeechProvider
{
    private static readonly TimeSpan SpeechTimeout =
        TimeSpan.FromMinutes(10);

    public string Identity =>
        $"{settings.SpeechProvider}|" +
        $"{settings.WindowsVoice}|" +
        $"{settings.PiperVoice}";

    // ========================================================================
    // SPEAK
    // ========================================================================

    public async Task Speak(
        string text,
        string output,
        CancellationToken ct)
    {
        ct.ThrowIfCancellationRequested();

        ValidateInput(text, output);

        var fullOutput =
            Path.GetFullPath(output);

        var directory =
            Path.GetDirectoryName(fullOutput)
            ?? throw new InvalidOperationException(
                "Narration output has no parent directory.");

        Directory.CreateDirectory(directory);

        var temporaryAudio =
            fullOutput + ".partial.wav";

        var temporaryText =
            fullOutput + ".speech-input.txt";

        // Never reuse leftovers from an interrupted previous attempt.
        TryDelete(temporaryAudio);
        TryDelete(temporaryText);

        try
        {
            if (IsPiper())
            {
                await SpeakWithPiper(
                    text,
                    temporaryAudio,
                    directory,
                    ct);
            }
            else
            {
                await SpeakWithWindows(
                    text,
                    temporaryAudio,
                    temporaryText,
                    ct);
            }

            ct.ThrowIfCancellationRequested();

            ValidateGeneratedWave(
                temporaryAudio);

            // Only now is the successfully generated file allowed to become
            // the project's real narration file.
            File.Move(
                temporaryAudio,
                fullOutput,
                true);
        }
        catch (OperationCanceledException)
        {
            TryDelete(temporaryAudio);
            throw;
        }
        catch (Exception e) when (
            e is InvalidOperationException
            or InvalidDataException
            or IOException
            or UnauthorizedAccessException
            or TimeoutException)
        {
            TryDelete(temporaryAudio);

            throw new InvalidOperationException(
                BuildSpeechFailureMessage(e),
                e);
        }
        finally
        {
            // Windows Speech uses this temporary text file.
            // Piper does not, but deleting a nonexistent file is harmless.
            TryDelete(temporaryText);
        }
    }

    // ========================================================================
    // PIPER
    // ========================================================================

    private async Task SpeakWithPiper(
        string text,
        string temporaryAudio,
        string workingDirectory,
        CancellationToken ct)
    {
        ValidatePiperConfiguration();

        try
        {
            await ChildProcess.Run(
                settings.PiperPython,
                [
                    "-m",
                    "piper",

                    "-m",
                    settings.PiperVoice,

                    "-f",
                    temporaryAudio
                ],
                workingDirectory,
                text,
                SpeechTimeout,
                ct);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            throw new InvalidOperationException(
                "Piper could not generate narration. " +
                $"Python: '{settings.PiperPython}'. " +
                $"Voice: '{settings.PiperVoice}'. " +
                e.Message,
                e);
        }
    }

    private void ValidatePiperConfiguration()
    {
        if (string.IsNullOrWhiteSpace(settings.PiperPython))
        {
            throw new InvalidOperationException(
                "Piper Python executable is not configured.");
        }

        if (!File.Exists(settings.PiperPython))
        {
            throw new InvalidOperationException(
                "The configured Piper Python executable does not exist: " +
                settings.PiperPython);
        }

        if (string.IsNullOrWhiteSpace(settings.PiperVoice))
        {
            throw new InvalidOperationException(
                "No Piper voice model is configured.");
        }

        if (!File.Exists(settings.PiperVoice))
        {
            throw new InvalidOperationException(
                "The configured Piper voice model does not exist: " +
                settings.PiperVoice);
        }

        // Piper voices normally have a neighbouring .json configuration.
        //
        // Example:
        //
        // en_US-lessac-medium.onnx
        // en_US-lessac-medium.onnx.json
        //
        // Some configurations may differ, so check both common forms.

        var json1 =
            settings.PiperVoice + ".json";

        var json2 =
            Path.ChangeExtension(
                settings.PiperVoice,
                ".json");

        if (!File.Exists(json1)
            && !File.Exists(json2))
        {
            throw new InvalidOperationException(
                "The Piper voice model exists, but its voice configuration " +
                "JSON file could not be found beside it.");
        }
    }

    // ========================================================================
    // WINDOWS SPEECH
    // ========================================================================

    private async Task SpeakWithWindows(
        string text,
        string temporaryAudio,
        string temporaryText,
        CancellationToken ct)
    {
        var script =
            Path.Combine(
                AppContext.BaseDirectory,
                "scripts",
                "speak.ps1");

        if (!File.Exists(script))
        {
            throw new FileNotFoundException(
                "The Windows speech script is missing.",
                script);
        }

        await File.WriteAllTextAsync(
            temporaryText,
            text,
            Encoding.UTF8,
            ct);

        var arguments =
            new List<string>
            {
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script,
                "-TextPath",
                temporaryText,
                "-OutputPath",
                temporaryAudio
            };

        if (!string.IsNullOrWhiteSpace(
                settings.WindowsVoice))
        {
            arguments.Add("-Voice");
            arguments.Add(settings.WindowsVoice);
        }

        try
        {
            await ChildProcess.Run(
                "powershell.exe",
                arguments,
                null,
                null,
                SpeechTimeout,
                ct);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
        {
            var voice =
                string.IsNullOrWhiteSpace(settings.WindowsVoice)
                    ? "Windows default"
                    : settings.WindowsVoice;

            throw new InvalidOperationException(
                $"Windows Speech could not generate narration " +
                $"using voice '{voice}'. {e.Message}",
                e);
        }
    }

    // ========================================================================
    // INPUT VALIDATION
    // ========================================================================

    private static void ValidateInput(
        string text,
        string output)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            throw new InvalidDataException(
                "Cannot generate narration from empty text.");
        }

        if (text.Length > 10_000)
        {
            throw new InvalidDataException(
                "Narration segment is unexpectedly large. " +
                "Split it into smaller scenes before speech generation.");
        }

        if (string.IsNullOrWhiteSpace(output))
        {
            throw new ArgumentException(
                "Narration output path is empty.",
                nameof(output));
        }

        if (!string.Equals(
                Path.GetExtension(output),
                ".wav",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                "Narration output must be a WAV file.");
        }
    }

    // ========================================================================
    // WAV VALIDATION
    // ========================================================================

    private static void ValidateGeneratedWave(
        string path)
    {
        if (!File.Exists(path))
        {
            throw new InvalidDataException(
                "Speech generation completed without producing an audio file.");
        }

        var info =
            new FileInfo(path);

        // A WAV header alone is roughly 44 bytes. Requiring substantially
        // more prevents empty/header-only output from being accepted.
        if (info.Length < 256)
        {
            throw new InvalidDataException(
                $"Narration produced an unusably small audio file " +
                $"({info.Length} bytes).");
        }

        using var stream =
            new FileStream(
                path,
                FileMode.Open,
                FileAccess.Read,
                FileShare.Read);

        if (stream.Length < 12)
        {
            throw new InvalidDataException(
                "Narration output is not a valid WAV file.");
        }

        Span<byte> header =
            stackalloc byte[12];

        var read =
            stream.Read(header);

        if (read != 12)
        {
            throw new InvalidDataException(
                "Narration WAV header could not be read.");
        }

        var riff =
            Encoding.ASCII.GetString(
                header[..4]);

        var wave =
            Encoding.ASCII.GetString(
                header[8..12]);

        if (!string.Equals(
                riff,
                "RIFF",
                StringComparison.Ordinal)
            ||
            !string.Equals(
                wave,
                "WAVE",
                StringComparison.Ordinal))
        {
            throw new InvalidDataException(
                "Speech provider produced a file that is not a valid " +
                "RIFF/WAVE audio file.");
        }
    }

    // ========================================================================
    // PROVIDER
    // ========================================================================

    private bool IsPiper()
    {
        return string.Equals(
            settings.SpeechProvider,
            "Piper",
            StringComparison.OrdinalIgnoreCase);
    }

    // ========================================================================
    // ERRORS
    // ========================================================================

    private string BuildSpeechFailureMessage(
        Exception error)
    {
        var provider =
            IsPiper()
                ? "Piper"
                : "Windows Speech";

        if (error is TimeoutException)
        {
            return
                $"{provider} narration timed out. " +
                "The speech process may have stalled.";
        }

        return
            $"{provider} narration failed. {error.Message}";
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

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
            // Cleanup failure must not conceal the real speech error.
        }
    }
}