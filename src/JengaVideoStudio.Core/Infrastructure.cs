using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace JengaVideoStudio.Core;

// ============================================================================
// GENERAL UTILITIES
// ============================================================================

public static class Util
{
    private const int MaximumSlugLength = 48;

    public static string Hash(string value)
    {
        ArgumentNullException.ThrowIfNull(value);

        return Convert.ToHexString(
            SHA256.HashData(
                Encoding.UTF8.GetBytes(value)));
    }

    public static string Slug(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return "project";

        var slug =
            Regex.Replace(
                    value
                        .Trim()
                        .ToLowerInvariant(),
                    "[^a-z0-9]+",
                    "-")
                .Trim('-');

        if (string.IsNullOrWhiteSpace(slug))
            return "project";

        if (slug.Length > MaximumSlugLength)
        {
            slug =
                slug[..MaximumSlugLength]
                    .TrimEnd('-');
        }

        return string.IsNullOrWhiteSpace(slug)
            ? "project"
            : slug;
    }

    public static void Open(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new ArgumentException(
                "Cannot open an empty path.",
                nameof(path));
        }

        Process.Start(
            new ProcessStartInfo(path)
            {
                UseShellExecute = true
            });
    }

    public static string Prompt(string name)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            throw new ArgumentException(
                "Prompt name is empty.",
                nameof(name));
        }

        // Prevent a prompt name from escaping the prompts directory.
        if (name.IndexOfAny(
                [
                    Path.DirectorySeparatorChar,
                    Path.AltDirectorySeparatorChar
                ]) >= 0
            ||
            name.Contains("..", StringComparison.Ordinal))
        {
            throw new InvalidDataException(
                "Invalid prompt name.");
        }

        var path =
            Path.Combine(
                AppContext.BaseDirectory,
                "prompts",
                name + ".txt");

        if (!File.Exists(path))
        {
            throw new FileNotFoundException(
                $"Required AI prompt '{name}' is missing.",
                path);
        }

        var text =
            File.ReadAllText(path);

        if (string.IsNullOrWhiteSpace(text))
        {
            throw new InvalidDataException(
                $"AI prompt '{name}' is empty.");
        }

        return text;
    }

    /// <summary>
    /// Ensures an AI/service endpoint points only to this computer.
    ///
    /// This prevents configuration from accidentally sending private project
    /// material to an arbitrary remote HTTP service.
    /// </summary>
    public static void LocalEndpoint(string endpoint)
    {
        if (string.IsNullOrWhiteSpace(endpoint))
        {
            throw new InvalidDataException(
                "Local service endpoint is empty.");
        }

        if (!Uri.TryCreate(
                endpoint,
                UriKind.Absolute,
                out var uri))
        {
            throw new InvalidDataException(
                $"Invalid local service endpoint: {endpoint}");
        }

        if (!string.Equals(
                uri.Scheme,
                Uri.UriSchemeHttp,
                StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(
                "AI/service endpoints must use local HTTP.");
        }

        if (!uri.IsLoopback)
        {
            throw new InvalidDataException(
                "AI/service endpoints must point to this computer " +
                "(localhost or another loopback address).");
        }

        if (!string.IsNullOrEmpty(uri.UserInfo))
        {
            throw new InvalidDataException(
                "Local service endpoints must not contain credentials.");
        }
    }
}

// ============================================================================
// PROJECT STORAGE
// ============================================================================

public sealed class ProjectStore
{
    private const int CurrentProjectVersion = 1;
    private const int MaximumRecentProjects = 40;

    public Project Create(
        Settings settings,
        string idea,
        int seconds,
        string tone,
        string quality,
        string urls)
    {
        ArgumentNullException.ThrowIfNull(settings);

        if (string.IsNullOrWhiteSpace(idea))
        {
            throw new ArgumentException(
                "A project idea is required.",
                nameof(idea));
        }

        if (seconds <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(seconds),
                "Target duration must be greater than zero.");
        }

        if (string.IsNullOrWhiteSpace(
                settings.ProjectRoot))
        {
            throw new InvalidOperationException(
                "Project root is not configured.");
        }

        Directory.CreateDirectory(
            settings.ProjectRoot);

        var project =
            new Project
            {
                Idea = idea.Trim(),
                TargetSeconds = seconds,
                Tone = tone?.Trim() ?? "",
                Quality = quality?.Trim() ?? "",
                SuppliedUrls = urls ?? ""
            };

        var idFragment =
            project.Id.Length >= 8
                ? project.Id[..8]
                : project.Id;

        var folderName =
            $"{DateTime.Now:yyyy-MM-dd_HHmmss}_" +
            $"{Util.Slug(project.Idea)}_" +
            $"{idFragment}";

        project.Folder =
            Path.Combine(
                settings.ProjectRoot,
                folderName);

        Directory.CreateDirectory(
            project.Folder);

        // Create the standard directories immediately. This makes the project
        // layout predictable for logging and interrupted productions.
        Directory.CreateDirectory(
            project.PathFor("logs"));

        Directory.CreateDirectory(
            project.PathFor("assets"));

        project.Metadata.Privacy =
            settings.DefaultPrivacy;

        return project;
    }

    public Task Save(
        Project project,
        CancellationToken ct = default)
    {
        ArgumentNullException.ThrowIfNull(project);

        if (string.IsNullOrWhiteSpace(
                project.Folder))
        {
            throw new InvalidOperationException(
                "Project has no storage folder.");
        }

        return Json.Save(
            project.PathFor("project.json"),
            project,
            ct);
    }

    public Project Load(string file)
    {
        if (string.IsNullOrWhiteSpace(file))
        {
            throw new ArgumentException(
                "Project filename is empty.",
                nameof(file));
        }

        var fullPath =
            Path.GetFullPath(file);

        if (!File.Exists(fullPath))
        {
            throw new FileNotFoundException(
                "Project file could not be found.",
                fullPath);
        }

        var text =
            File.ReadAllText(fullPath);

        if (string.IsNullOrWhiteSpace(text))
        {
            throw new InvalidDataException(
                "Project file is empty.");
        }

        var project =
            Json.Decode<Project>(text);

        if (project.Version !=
            CurrentProjectVersion)
        {
            throw new InvalidDataException(
                $"Unsupported project version {project.Version}. " +
                $"This build supports project version " +
                $"{CurrentProjectVersion}.");
        }

        project.Folder =
            Path.GetDirectoryName(fullPath)
            ?? throw new InvalidDataException(
                "Project file has no parent directory.");

        return project;
    }

    public List<Project> Recent(
        Settings settings)
    {
        ArgumentNullException.ThrowIfNull(settings);

        if (string.IsNullOrWhiteSpace(
                settings.ProjectRoot)
            ||
            !Directory.Exists(
                settings.ProjectRoot))
        {
            return [];
        }

        var result =
            new List<Project>();

        IEnumerable<string> files;

        try
        {
            files =
                Directory.EnumerateFiles(
                    settings.ProjectRoot,
                    "project.json",
                    SearchOption.AllDirectories);
        }
        catch (
            Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException)
        {
            return [];
        }

        try
        {
            foreach (var file in files)
            {
                try
                {
                    result.Add(
                        Load(file));
                }
                catch (Exception e)
                    when (
                        e is IOException
                        or UnauthorizedAccessException
                        or System.Text.Json.JsonException
                        or InvalidDataException)
                {
                    // A damaged/old project must not prevent Jenga from
                    // displaying all the other valid projects.
                }
            }
        }
        catch (Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException)
        {
            // Enumeration can fail lazily after it has already yielded some
            // projects. Preserve everything successfully discovered so far.
        }

        return result
            .OrderByDescending(
                p => p.CreatedAt)
            .Take(MaximumRecentProjects)
            .ToList();
    }

    public static async Task Log(
        Project project,
        string message)
    {
        ArgumentNullException.ThrowIfNull(project);

        if (string.IsNullOrWhiteSpace(
                project.Folder))
        {
            return;
        }

        var path =
            project.PathFor(
                "logs/production.log");

        var directory =
            Path.GetDirectoryName(path);

        if (!string.IsNullOrWhiteSpace(directory))
        {
            Directory.CreateDirectory(
                directory);
        }

        // Replace control characters that could corrupt the line-oriented log,
        // while preserving ordinary message text.
        var safe =
            (message ?? "")
                .Replace(
                    "\r\n",
                    "\n")
                .Replace(
                    '\r',
                    '\n');

        var entry =
            $"{DateTimeOffset.UtcNow:O} {safe}" +
            Environment.NewLine;

        await File.AppendAllTextAsync(
            path,
            entry,
            Encoding.UTF8);
    }
}

// ============================================================================
// CHILD PROCESS EXECUTION
// ============================================================================

public static class ChildProcess
{
    private const int MaximumCapturedCharacters =
        1_000_000;

    private const int MaximumErrorCharacters =
        8_000;

    /// <summary>
    /// Executes a local process without opening a console window.
    ///
    /// stdout and stderr are consumed concurrently to prevent pipe deadlocks.
    /// The entire process tree is terminated on timeout or cancellation.
    /// </summary>
    public static async Task<string> Run(
        string executable,
        IEnumerable<string> arguments,
        string? cwd,
        string? input,
        TimeSpan timeout,
        CancellationToken ct,
        Action<string>? line = null)
    {
        if (string.IsNullOrWhiteSpace(executable))
        {
            throw new ArgumentException(
                "Executable is empty.",
                nameof(executable));
        }

        ArgumentNullException.ThrowIfNull(arguments);

        if (timeout <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(
                nameof(timeout),
                "Process timeout must be greater than zero.");
        }

        ct.ThrowIfCancellationRequested();

        var info =
            new ProcessStartInfo
            {
                FileName = executable,
                UseShellExecute = false,
                CreateNoWindow = true,

                RedirectStandardOutput = true,
                RedirectStandardError = true,

                RedirectStandardInput =
                    input != null
            };

        if (!string.IsNullOrWhiteSpace(cwd))
        {
            var workingDirectory =
                Path.GetFullPath(cwd);

            if (!Directory.Exists(
                    workingDirectory))
            {
                throw new DirectoryNotFoundException(
                    $"Process working directory does not exist: " +
                    $"{workingDirectory}");
            }

            info.WorkingDirectory =
                workingDirectory;
        }

        foreach (var argument in arguments)
        {
            info.ArgumentList.Add(
                argument ?? "");
        }

        using var process =
            new Process
            {
                StartInfo = info,

                EnableRaisingEvents =
                    true
            };

        try
        {
            if (!process.Start())
            {
                throw new InvalidOperationException(
                    $"Windows could not start " +
                    $"'{Path.GetFileName(executable)}'.");
            }
        }
        catch (Exception e)
            when (
                e is System.ComponentModel.Win32Exception
                or InvalidOperationException)
        {
            throw new InvalidOperationException(
                $"Could not start '{executable}'. " +
                "Check that it is installed and that the configured " +
                "path is correct.",
                e);
        }

        using var timeoutSource =
            new CancellationTokenSource(
                timeout);

        using var linked =
            CancellationTokenSource
                .CreateLinkedTokenSource(
                    ct,
                    timeoutSource.Token);

        // Begin reading immediately. If either pipe fills while we're waiting
        // for the process, the child could otherwise deadlock.
        var stdoutTask =
            ReadStream(
                process.StandardOutput,
                true,
                line);

        var stderrTask =
            ReadStream(
                process.StandardError,
                false,
                null);

        Exception? inputFailure = null;

        try
        {
            if (input != null)
            {
                try
                {
                    await process.StandardInput
                        .WriteAsync(
                            input.AsMemory(),
                            linked.Token);

                    await process.StandardInput
                        .FlushAsync(
                            linked.Token);
                }
                catch (IOException e)
                {
                    // Some programs can terminate before consuming all stdin.
                    // Save this so a successful process isn't automatically
                    // converted into a failure.
                    inputFailure = e;
                }
                finally
                {
                    try
                    {
                        process.StandardInput.Close();
                    }
                    catch
                    {
                        // Best effort.
                    }
                }
            }

            try
            {
                await process.WaitForExitAsync(
                    linked.Token);
            }
            catch (OperationCanceledException)
            {
                KillProcessTree(process);

                // Give Windows a short opportunity to reap the process after
                // Kill(). This is deliberately not tied to the cancelled token.
                try
                {
                    using var cleanup =
                        new CancellationTokenSource(
                            TimeSpan.FromSeconds(5));

                    await process.WaitForExitAsync(
                        cleanup.Token);
                }
                catch
                {
                    // Nothing more useful to do here.
                }

                if (ct.IsCancellationRequested)
                {
                    throw new OperationCanceledException(
                        $"'{Path.GetFileName(executable)}' was cancelled.",
                        ct);
                }

                throw new TimeoutException(
                    BuildTimeoutMessage(
                        executable,
                        timeout));
            }

            // Wait for the asynchronous readers after process termination so
            // the final buffered stdout/stderr is not lost.
            var output =
                await stdoutTask;

            var errors =
                await stderrTask;

            if (process.ExitCode != 0)
            {
                throw new ChildProcessException(
                    executable,
                    process.ExitCode,
                    output,
                    errors);
            }

            if (inputFailure != null
                &&
                string.IsNullOrWhiteSpace(output))
            {
                throw new InvalidOperationException(
                    $"{Path.GetFileName(executable)} closed its input " +
                    $"before Jenga could finish writing to it.",
                    inputFailure);
            }

            return output;
        }
        catch
        {
            if (!process.HasExited)
            {
                KillProcessTree(process);
            }

            // Observe the stream tasks if they have already completed/faulted.
            // Do not allow cleanup exceptions to conceal the actual error.
            await ObserveQuietly(stdoutTask);
            await ObserveQuietly(stderrTask);

            throw;
        }
    }

    // ========================================================================
    // STREAM READING
    // ========================================================================

    private static async Task<string> ReadStream(
        StreamReader reader,
        bool reportLines,
        Action<string>? line)
    {
        var captured =
            new StringBuilder();

        while (true)
        {
            var text =
                await reader.ReadLineAsync();

            if (text == null)
                break;

            if (captured.Length <
                MaximumCapturedCharacters)
            {
                var remaining =
                    MaximumCapturedCharacters -
                    captured.Length;

                if (text.Length + 1 <= remaining)
                {
                    captured.AppendLine(text);
                }
                else if (remaining > 0)
                {
                    captured.Append(
                        text.AsSpan(
                            0,
                            Math.Min(
                                text.Length,
                                remaining)));

                    captured.AppendLine();
                }
            }

            if (reportLines
                &&
                line != null)
            {
                try
                {
                    line(text);
                }
                catch
                {
                    // A UI/progress callback must never crash the process
                    // reader or cause stdout to stop being drained.
                }
            }
        }

        return captured.ToString();
    }

    // ========================================================================
    // PROCESS TERMINATION
    // ========================================================================

    private static void KillProcessTree(
        Process process)
    {
        try
        {
            if (!process.HasExited)
            {
                process.Kill(
                    entireProcessTree: true);
            }
        }
        catch (
            Exception e)
            when (
                e is InvalidOperationException
                or System.ComponentModel.Win32Exception
                or NotSupportedException)
        {
            // The process may already have disappeared.
        }
    }

    // ========================================================================
    // ERRORS
    // ========================================================================

    private static string BuildTimeoutMessage(
        string executable,
        TimeSpan timeout)
    {
        var duration =
            timeout.TotalMinutes >= 1
                ? $"{timeout.TotalMinutes:0.#} minute(s)"
                : $"{timeout.TotalSeconds:0.#} second(s)";

        return
            $"{Path.GetFileName(executable)} timed out after {duration}.";
    }

    private static async Task ObserveQuietly(
        Task task)
    {
        try
        {
            await task;
        }
        catch
        {
            // Cleanup only.
        }
    }

    public sealed class ChildProcessException
        : InvalidOperationException
    {
        public string Executable { get; }

        public int ExitCode { get; }

        public string StandardOutput { get; }

        public string StandardError { get; }

        public ChildProcessException(
            string executable,
            int exitCode,
            string standardOutput,
            string standardError)
            : base(
                BuildMessage(
                    executable,
                    exitCode,
                    standardOutput,
                    standardError))
        {
            Executable =
                executable;

            ExitCode =
                exitCode;

            StandardOutput =
                standardOutput;

            StandardError =
                standardError;
        }

        private static string BuildMessage(
            string executable,
            int exitCode,
            string stdout,
            string stderr)
        {
            var diagnostic =
                !string.IsNullOrWhiteSpace(stderr)
                    ? stderr.Trim()
                    : stdout.Trim();

            if (diagnostic.Length >
                MaximumErrorCharacters)
            {
                diagnostic =
                    diagnostic[..MaximumErrorCharacters]
                    + "...";
            }

            var message =
                $"{Path.GetFileName(executable)} exited with code " +
                $"{exitCode}.";

            if (!string.IsNullOrWhiteSpace(
                    diagnostic))
            {
                message +=
                    Environment.NewLine +
                    diagnostic;
            }

            return message;
        }
    }
}