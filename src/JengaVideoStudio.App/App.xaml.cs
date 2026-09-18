using System.Net.Http;
using System.Windows;
using JengaVideoStudio.Core;

namespace JengaVideoStudio.App;

public partial class App : Application
{
    private HttpClient? startupHttp;

    protected override async void OnStartup(
        StartupEventArgs e)
    {
        base.OnStartup(e);

        try
        {
            startupHttp =
                new HttpClient();

            var settings =
                Settings.Load();

            await EnsureAiHardwareProfile(
                settings,
                startupHttp);

            var window =
                new MainWindow();

            MainWindow =
                window;

            window.Show();
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "Jenga Video Studio could not start.\n\n" +
                ex.Message,
                "Startup error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);

            Shutdown(1);
        }
    }

    private static async Task EnsureAiHardwareProfile(
        Settings settings,
        HttpClient http)
    {
        var profilePath =
            Path.Combine(
                Settings.DataRoot,
                "ai-hardware-profile.json");

        /*
         * If a hardware assessment already exists, don't benchmark the
         * computer again every time Jenga starts.
         *
         * OllamaLanguageModel will read this profile before production.
         */
        if (File.Exists(profilePath))
            return;

        try
        {
            var profiler =
                new AiHardwareProfiler(
                    settings,
                    http);

            using var timeout =
                new CancellationTokenSource(
                    TimeSpan.FromMinutes(3));

            var profile =
                await profiler.Assess(
                    probeOllama: true,
                    progress: null,
                    timeout.Token);

            if (string.IsNullOrWhiteSpace(
                    profile.RecommendedModel))
            {
                throw new InvalidDataException(
                    "Hardware assessment did not select an AI model.");
            }

            settings.CpuOnly =
                profile.RecommendedCpuOnly;

            settings.Model =
                profile.RecommendedModel;

            await settings.Save();

            Directory.CreateDirectory(
                Settings.DataRoot);

            await Json.Save(
                profilePath,
                profile,
                CancellationToken.None);
        }
        catch
        {
            /*
             * Hardware assessment should never make the entire desktop
             * application unusable.
             *
             * If assessment cannot be completed, use the conservative
             * CPU profile instead of gambling on 7B + GPU acceleration.
             */
            settings.CpuOnly =
                true;

            settings.Model =
                Settings.CpuModel;

            await settings.Save();
        }
    }

    protected override void OnExit(
        ExitEventArgs e)
    {
        startupHttp?.Dispose();
        startupHttp =
            null;

        base.OnExit(e);
    }
}