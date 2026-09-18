namespace JengaVideoStudio.Core;

/// <summary>
/// Coordinates the complete video-production workflow.
///
/// Expensive completed work is checkpointed as soon as possible so Resume
/// does not unnecessarily repeat research, AI generation, narration, visuals
/// or rendering.
///
/// This version also forwards detailed local-AI telemetry to the normal
/// ProgressUpdate channel so the WPF production journal can show what the
/// application is actually doing during long-running operations.
/// </summary>
public sealed class ProductionPipeline(
    ProjectStore store,
    ILanguageModel llm,
    IResearchService research,
    ITextToSpeechProvider speech,
    IVisualProvider visuals,
    IMediaRenderer renderer)
{
    private const int AiValidationAttempts = 3;

    // ========================================================================
    // RUN
    // ========================================================================

    public async Task Run(
        Project p,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        ArgumentNullException.ThrowIfNull(p);
        ArgumentNullException.ThrowIfNull(progress);

        ProductionState currentStage = p.State;
        double currentPercent = 0;

        EventHandler<AiProgress>? aiProgressHandler = null;

        // ====================================================================
        // LIVE PROGRESS HELPERS
        // ====================================================================

        void Live(
            string area,
            string action,
            string detail = "",
            bool indefinite = true)
        {
            var message =
                string.IsNullOrWhiteSpace(detail)
                    ? $"{area}: {action}"
                    : $"{area}: {action} — {detail}";

            progress.Report(
                new ProgressUpdate(
                    currentStage.ToString(),
                    message,
                    currentPercent,
                    indefinite));
        }

        async Task Journal(
            string area,
            string action,
            string detail = "")
        {
            ct.ThrowIfCancellationRequested();

            var message =
                string.IsNullOrWhiteSpace(detail)
                    ? $"{area.ToUpperInvariant()} | {action}"
                    : $"{area.ToUpperInvariant()} | {action} — {detail}";

            await ProjectStore.Log(
                p,
                message);

            Live(
                area,
                action,
                detail);
        }

        async Task Stage(
            ProductionState state,
            string detail,
            double percent,
            bool indefinite = false)
        {
            ct.ThrowIfCancellationRequested();

            currentStage = state;
            currentPercent = percent;

            p.State = state;
            p.Error = "";

            await store.Save(
                p,
                ct);

            await ProjectStore.Log(
                p,
                $"{state}: {detail}");

            progress.Report(
                new ProgressUpdate(
                    state.ToString(),
                    detail,
                    percent,
                    indefinite));
        }

        async Task Checkpoint(
            string description)
        {
            ct.ThrowIfCancellationRequested();

            await store.Save(
                p,
                ct);

            await ProjectStore.Log(
                p,
                $"CHECKPOINT | {description}");
        }

        // ====================================================================
        // FORWARD OLLAMA TELEMETRY
        // ====================================================================

        if (llm is OllamaLanguageModel ollama)
        {
            aiProgressHandler =
                (_, e) =>
                {
                    var detail = e.Detail;

                    if (e.Attempt.HasValue &&
                        e.MaximumAttempts.HasValue)
                    {
                        detail =
                            string.IsNullOrWhiteSpace(detail)
                                ? $"attempt {e.Attempt}/{e.MaximumAttempts}"
                                : $"{detail} · attempt " +
                                  $"{e.Attempt}/{e.MaximumAttempts}";
                    }

                    Live(
                        e.Area,
                        e.Action,
                        detail,
                        true);
                };

            ollama.Progress +=
                aiProgressHandler;
        }

        try
        {
            // ================================================================
            // START
            // ================================================================

            p.EditorialReviewed = false;

            await Checkpoint(
                "Production run started.");

            // ================================================================
            // 1. RESEARCH
            // ================================================================

            if (p.Sources.Count == 0)
            {
                await Stage(
                    ProductionState.Researching,
                    "Finding readable sources",
                    5,
                    true);

                await Journal(
                    "Research",
                    "Starting source discovery",
                    $"Topic: {p.Idea}");

                p.Sources =
                    await research.Research(
                        p,
                        progress,
                        ct);

                await Journal(
                    "Research",
                    "Source discovery returned",
                    $"{p.Sources.Count} readable source(s)");

                if (p.Sources.Count < 2)
                {
                    throw new InvalidDataException(
                        "Research completed without enough usable sources.");
                }

                var totalCharacters =
                    p.Sources.Sum(
                        source =>
                            source.Text?.Length ?? 0);

                await Journal(
                    "Research",
                    "Sources accepted",
                    $"{p.Sources.Count} source(s) · " +
                    $"{totalCharacters:N0} characters");

                await Checkpoint(
                    $"Research complete: " +
                    $"{p.Sources.Count} source(s).");
            }
            else
            {
                await ProjectStore.Log(
                    p,
                    $"RESUME | Reusing " +
                    $"{p.Sources.Count} saved research source(s).");

                Live(
                    "Research",
                    "Reusing saved research",
                    $"{p.Sources.Count} source(s)");
            }

            // ================================================================
            // 2. EVIDENCE / BRIEF
            // ================================================================

            if (p.Brief == null)
            {
                await Stage(
                    ProductionState.AnalysingSources,
                    "Checking source quotations and preparing claims",
                    20,
                    true);

                await Journal(
                    "Evidence",
                    "Preparing source material",
                    $"{p.Sources.Count} source(s)");

                p.Brief =
                    await GenerateValidatedBrief(
                        p,
                        progress,
                        ct);

                await Journal(
                    "Evidence",
                    "Saving validated evidence",
                    $"{p.Brief.Claims.Count} supported claim(s)");

                await SaveBrief(
                    p,
                    ct);

                await Checkpoint(
                    $"Evidence analysis complete: " +
                    $"{p.Brief.Claims.Count} supported claim(s).");
            }
            else
            {
                Live(
                    "Evidence",
                    "Checking saved brief",
                    $"{p.Brief.Claims.Count} claim(s)");

                Grounding.Validate(
                    p.Brief,
                    p.Sources);

                await ProjectStore.Log(
                    p,
                    $"RESUME | Reusing validated research brief with " +
                    $"{p.Brief.Claims.Count} claim(s).");
            }

            // ================================================================
            // 3. SCRIPT + SCENE PLAN
            // ================================================================

            if (p.Scenes.Count == 0)
            {
                await Stage(
                    ProductionState.WritingScript,
                    "Writing a sourced script and scene directions",
                    30,
                    true);

                await Journal(
                    "Script",
                    "Preparing script generation",
                    $"{p.Brief.Claims.Count} evidence claim(s) · " +
                    $"target {p.TargetSeconds}s · tone: {p.Tone}");

                var plan =
                    await GenerateValidatedScript(
                        p,
                        progress,
                        ct);

                p.Scenes =
                    plan.Scenes;

                for (var i = 0;
                     i < p.Scenes.Count;
                     i++)
                {
                    p.Scenes[i].Number =
                        i + 1;
                }

                p.Metadata.Title =
                    plan.Title;

                p.Metadata.Description =
                    plan.Description;

                await Journal(
                    "Script",
                    "Script accepted",
                    $"{p.Scenes.Count} scene(s) · " +
                    $"title: {plan.Title}");

                await SaveScriptAndScenes(
                    p,
                    ct);

                await Checkpoint(
                    $"Script complete: " +
                    $"{p.Scenes.Count} scene(s).");

                await Stage(
                    ProductionState.PlanningScenes,
                    $"Planned {p.Scenes.Count} scenes",
                    38);
            }
            else
            {
                for (var i = 0;
                     i < p.Scenes.Count;
                     i++)
                {
                    p.Scenes[i].Number =
                        i + 1;
                }

                Grounding.ValidateScenes(
                    p.Scenes,
                    p.Brief);

                await ProjectStore.Log(
                    p,
                    $"RESUME | Reusing " +
                    $"{p.Scenes.Count} validated scene(s).");

                Live(
                    "Script",
                    "Reusing saved scene plan",
                    $"{p.Scenes.Count} scene(s)");
            }

            // ================================================================
            // FINAL STRUCTURAL VALIDATION
            // ================================================================

            Live(
                "Validation",
                "Checking complete scene plan",
                $"{p.Scenes.Count} scene(s)");

            Grounding.ValidateScenes(
                p.Scenes,
                p.Brief);

            BuildWarnings(
                p);

            await Checkpoint(
                "Pre-production validation complete.");

            // ================================================================
            // 4. NARRATION
            // ================================================================

            for (var i = 0;
                 i < p.Scenes.Count;
                 i++)
            {
                ct.ThrowIfCancellationRequested();

                var scene =
                    p.Scenes[i];

                scene.Number =
                    i + 1;

                var percent =
                    40 +
                    15.0 *
                    i /
                    p.Scenes.Count;

                var hash =
                    Util.Hash(
                        scene.Narration +
                        "|" +
                        speech.Identity);

                await Stage(
                    ProductionState.GeneratingNarration,
                    $"Narration {i + 1} of {p.Scenes.Count}",
                    percent,
                    true);

                var audioIsReusable =
                    scene.AudioHash == hash
                    &&
                    !string.IsNullOrWhiteSpace(
                        scene.Audio)
                    &&
                    File.Exists(
                        p.PathFor(scene.Audio));

                if (audioIsReusable)
                {
                    Live(
                        "Narration",
                        $"Scene {scene.Number}/{p.Scenes.Count}",
                        "Reusing existing audio");

                    if (scene.Duration < 0.2)
                    {
                        Live(
                            "Narration",
                            "Measuring saved audio",
                            $"Scene {scene.Number}");

                        scene.Duration =
                            await renderer.Duration(
                                p.PathFor(scene.Audio),
                                ct);

                        ValidateNarrationDuration(
                            scene);
                    }

                    await ProjectStore.Log(
                        p,
                        $"RESUME | Scene " +
                        $"{scene.Number} narration reused.");

                    continue;
                }

                var relativeAudio =
                    $"audio/narration/" +
                    $"{i + 1:00}-{hash[..12]}.wav";

                var finalAudio =
                    p.PathFor(
                        relativeAudio);

                EnsureParentDirectory(
                    finalAudio);

                Live(
                    "Narration",
                    $"Generating scene {scene.Number}/{p.Scenes.Count}",
                    $"{scene.Narration.Length:N0} characters · " +
                    $"{speech.Identity}");

                await speech.Speak(
                    scene.Narration,
                    finalAudio,
                    ct);

                if (!File.Exists(
                        finalAudio))
                {
                    throw new InvalidDataException(
                        $"Narration for scene " +
                        $"{scene.Number} completed without " +
                        "producing an audio file.");
                }

                Live(
                    "Narration",
                    "Audio generated",
                    $"Scene {scene.Number} · measuring duration");

                scene.Duration =
                    await renderer.Duration(
                        finalAudio,
                        ct);

                ValidateNarrationDuration(
                    scene);

                scene.Audio =
                    relativeAudio;

                scene.AudioHash =
                    hash;

                Live(
                    "Narration",
                    $"Scene {scene.Number} complete",
                    $"{scene.Duration:0.00}s");

                await Checkpoint(
                    $"Narration scene " +
                    $"{scene.Number} complete " +
                    $"({scene.Duration:0.00}s).");
            }

            // ================================================================
            // 5. VISUALS
            // ================================================================

            for (var i = 0;
                 i < p.Scenes.Count;
                 i++)
            {
                ct.ThrowIfCancellationRequested();

                var scene =
                    p.Scenes[i];

                var percent =
                    55 +
                    15.0 *
                    i /
                    p.Scenes.Count;

                await Stage(
                    ProductionState.GeneratingVisuals,
                    $"Visual {i + 1} of {p.Scenes.Count}",
                    percent,
                    true);

                var hash =
                    Util.Hash(
                        Json.Encode(
                            new
                            {
                                scene.VisualType,
                                scene.VisualPrompt,
                                scene.Revision,
                                p.Quality
                            }));

                var visualIsReusable =
                    scene.VisualHash == hash
                    &&
                    (
                        string.IsNullOrWhiteSpace(
                            scene.Visual)
                        ||
                        File.Exists(
                            p.PathFor(scene.Visual))
                    );

                if (visualIsReusable)
                {
                    Live(
                        "Visuals",
                        $"Scene {scene.Number}/{p.Scenes.Count}",
                        "Reusing existing visual");

                    await ProjectStore.Log(
                        p,
                        $"RESUME | Scene " +
                        $"{scene.Number} visual reused.");

                    continue;
                }

                Live(
                    "Visuals",
                    $"Generating scene {scene.Number}/{p.Scenes.Count}",
                    $"{scene.VisualType} · " +
                    SafeVisualPrompt(scene.VisualPrompt));

                try
                {
                    scene.Visual =
                        await visuals.Create(
                            p,
                            scene,
                            ct);

                    Live(
                        "Visuals",
                        $"Scene {scene.Number} generated",
                        string.IsNullOrWhiteSpace(
                            scene.Visual)
                            ? "Using generated graphic"
                            : scene.Visual);
                }
                catch (OperationCanceledException)
                    when (ct.IsCancellationRequested)
                {
                    throw;
                }
                catch (Exception e)
                    when (IsRecoverableVisualFailure(e))
                {
                    scene.Visual = "";

                    AddWarningOnce(
                        p,
                        $"Scene {scene.Number}: " +
                        "visual generation failed; " +
                        "using a generated graphic instead. " +
                        e.Message);

                    Live(
                        "Visuals",
                        $"Scene {scene.Number} fallback",
                        e.Message);

                    await ProjectStore.Log(
                        p,
                        $"VISUAL FALLBACK | " +
                        $"Scene {scene.Number} | " +
                        $"{e.GetType().Name}: {e.Message}");
                }

                scene.VisualHash =
                    hash;

                await Checkpoint(
                    $"Visual scene " +
                    $"{scene.Number} complete.");
            }

            // ================================================================
            // 6. SAVE FINAL PRE-RENDER SCRIPT
            // ================================================================

            Live(
                "Project",
                "Saving pre-render state",
                $"{p.Scenes.Count} scene(s)");

            await SaveScriptAndScenes(
                p,
                ct);

            // ================================================================
            // 7. RENDER
            // ================================================================

            await Stage(
                ProductionState.Rendering,
                "Encoding portrait video",
                70,
                true);

            await Journal(
                "Render",
                "Starting FFmpeg render",
                $"{p.Scenes.Count} scene(s)");

            await renderer.Render(
                p,
                progress,
                ct);

            Live(
                "Render",
                "Video encoding complete",
                "Saving render checkpoint");

            await Checkpoint(
                "Video render completed.");

            // ================================================================
            // 8. QUALITY CHECK
            // ================================================================

            await Stage(
                ProductionState.QualityChecking,
                "Checking codecs, dimensions, duration and full decode",
                95,
                true);

            await Journal(
                "Quality",
                "Starting final validation",
                "Checking rendered media");

            await renderer.Validate(
                p,
                ct);

            Live(
                "Quality",
                "Media validation passed",
                "Calculating final content identity");

            p.FinalHash =
                ContentHash(
                    p);

            await Json.Save(
                p.PathFor(
                    "youtube/metadata.json"),
                p.Metadata,
                ct);

            await Checkpoint(
                "Quality checks completed.");

            // ================================================================
            // 9. READY FOR HUMAN REVIEW
            // ================================================================

            await Stage(
                ProductionState.ReadyForReview,
                "Video ready. Review sources, narration and captions before uploading.",
                100);

            await ProjectStore.Log(
                p,
                "PRODUCTION COMPLETE | " +
                "Ready for editorial review.");
        }

        // ====================================================================
        // CANCELLATION
        // ====================================================================

        catch (OperationCanceledException)
        {
            p.State =
                ProductionState.Cancelled;

            p.Error =
                "Cancelled. Completed stages are saved; " +
                "use Resume / Render.";

            await ProjectStore.Log(
                p,
                $"CANCELLED | Stage={currentStage}");

            await store.Save(
                p);

            progress.Report(
                new ProgressUpdate(
                    "Cancelled",
                    p.Error,
                    0,
                    false));

            throw;
        }

        // ====================================================================
        // FAILURE
        // ====================================================================

        catch (Exception e)
        {
            var failedStage =
                currentStage;

            p.State =
                ProductionState.Failed;

            p.Error =
                FriendlyStageError(
                    failedStage,
                    e);

            await ProjectStore.Log(
                p,
                $"PIPELINE FAILED | " +
                $"Stage={failedStage} | " +
                $"{e.GetType().Name}: {e.Message}");

            await ProjectStore.Log(
                p,
                e.ToString());

            await store.Save(
                p);

            progress.Report(
                new ProgressUpdate(
                    "Needs attention",
                    p.Error,
                    0,
                    false));

            throw;
        }

        // ====================================================================
        // CLEANUP
        // ====================================================================

        finally
        {
            if (llm is OllamaLanguageModel ollamaModel &&
                aiProgressHandler != null)
            {
                ollamaModel.Progress -=
                    aiProgressHandler;
            }
        }
    }

    // ========================================================================
    // EVIDENCE GENERATION
    // ========================================================================

    private async Task<Brief> GenerateValidatedBrief(
        Project p,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        InvalidDataException? lastValidationFailure =
            null;

        for (var attempt = 1;
             attempt <= AiValidationAttempts;
             attempt++)
        {
            ct.ThrowIfCancellationRequested();

            var sourceCharacters =
                p.Sources.Sum(
                    source =>
                        source.Text?.Length ?? 0);

            progress.Report(
                new ProgressUpdate(
                    ProductionState.AnalysingSources.ToString(),
                    $"Evidence: preparing attempt " +
                    $"{attempt}/{AiValidationAttempts} — " +
                    $"{p.Sources.Count} sources · " +
                    $"{sourceCharacters:N0} source characters",
                    20,
                    true));

            await ProjectStore.Log(
                p,
                $"EVIDENCE | " +
                $"Attempt {attempt}/{AiValidationAttempts} | " +
                $"Sources={p.Sources.Count} | " +
                $"SourceChars={sourceCharacters:N0}");

            if (lastValidationFailure != null)
            {
                progress.Report(
                    new ProgressUpdate(
                        ProductionState.AnalysingSources.ToString(),
                        $"Evidence: corrective retry — " +
                        lastValidationFailure.Message,
                        20,
                        true));

                await ProjectStore.Log(
                    p,
                    $"EVIDENCE CORRECTION | " +
                    lastValidationFailure.Message);
            }

            var brief =
                await llm.Generate<Brief>(
                    "brief",
                    new
                    {
                        idea = p.Idea,

                        sources = p.Sources,

                        validationFeedback =
                            lastValidationFailure?.Message ?? "",

                        instruction =
                            lastValidationFailure == null
                                ? "Create a grounded research brief. " +
                                  "Every evidence quotation must exactly " +
                                  "support the claim it is attached to."
                                : "Repair the previous validation problem. " +
                                  "Do not repeat it. Use only the supplied " +
                                  "sources and exact quotations."
                    },
                    ct);

            progress.Report(
                new ProgressUpdate(
                    ProductionState.AnalysingSources.ToString(),
                    $"Evidence: AI returned " +
                    $"{brief.Claims.Count} claim(s) — " +
                    "checking source IDs and quotations",
                    20,
                    true));

            try
            {
                Grounding.Validate(
                    brief,
                    p.Sources);

                progress.Report(
                    new ProgressUpdate(
                        ProductionState.AnalysingSources.ToString(),
                        $"Evidence: validation passed — " +
                        $"{brief.Claims.Count} supported claim(s)",
                        20,
                        true));

                await ProjectStore.Log(
                    p,
                    $"EVIDENCE VALID | " +
                    $"Attempt={attempt} | " +
                    $"Claims={brief.Claims.Count}");

                return brief;
            }
            catch (InvalidDataException e)
            {
                lastValidationFailure =
                    e;

                progress.Report(
                    new ProgressUpdate(
                        ProductionState.AnalysingSources.ToString(),
                        $"Evidence: attempt " +
                        $"{attempt}/{AiValidationAttempts} rejected — " +
                        e.Message,
                        20,
                        true));

                await ProjectStore.Log(
                    p,
                    $"EVIDENCE VALIDATION FAILED | " +
                    $"Attempt={attempt} | {e.Message}");

                await SaveRejectedObject(
                    p,
                    $"research/rejected-brief-{attempt:00}.json",
                    brief,
                    ct);

                if (attempt >=
                    AiValidationAttempts)
                {
                    break;
                }

                await ProjectStore.Log(
                    p,
                    "EVIDENCE | Corrective retry will include " +
                    "the exact validation failure.");
            }
        }

        throw new InvalidDataException(
            "The local AI produced an invalid research brief " +
            "three times. The source quotations or claim " +
            "references could not be verified. " +
            $"Last validation problem: " +
            $"{lastValidationFailure?.Message ?? "unknown"}.",
            lastValidationFailure);
    }

    // ========================================================================
    // SCRIPT GENERATION
    // ========================================================================

    private async Task<ScriptPlan> GenerateValidatedScript(
        Project p,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        InvalidDataException? lastValidationFailure =
            null;

        for (var attempt = 1;
             attempt <= AiValidationAttempts;
             attempt++)
        {
            ct.ThrowIfCancellationRequested();

            var claimCount =
                p.Brief?.Claims.Count ?? 0;

            progress.Report(
                new ProgressUpdate(
                    ProductionState.WritingScript.ToString(),
                    $"Script: preparing attempt " +
                    $"{attempt}/{AiValidationAttempts} — " +
                    $"{claimCount} claims · " +
                    $"target {p.TargetSeconds}s · " +
                    $"tone: {p.Tone}",
                    30,
                    true));

            await ProjectStore.Log(
                p,
                $"SCRIPT | " +
                $"Attempt {attempt}/{AiValidationAttempts} | " +
                $"Claims={claimCount} | " +
                $"TargetSeconds={p.TargetSeconds} | " +
                $"Tone={p.Tone}");

            if (lastValidationFailure != null)
            {
                progress.Report(
                    new ProgressUpdate(
                        ProductionState.WritingScript.ToString(),
                        $"Script: corrective retry — " +
                        lastValidationFailure.Message,
                        30,
                        true));

                await ProjectStore.Log(
                    p,
                    $"SCRIPT CORRECTION | " +
                    lastValidationFailure.Message);
            }

            var plan =
                await llm.Generate<ScriptPlan>(
                    "script",
                    new
                    {
                        idea = p.Idea,
                        tone = p.Tone,
                        targetSeconds = p.TargetSeconds,
                        brief = p.Brief,

                        validationFeedback =
                            lastValidationFailure?.Message ?? "",

                        instruction =
                            lastValidationFailure == null
                                ? "Write a sourced script and scene plan. " +
                                  "Every scene must reference at least one " +
                                  "claim ID that exists in the brief."
                                : "Repair the previous validation problem. " +
                                  "Use only claim IDs that exist in the brief. " +
                                  "Return a complete corrected script plan."
                    },
                    ct);

            progress.Report(
                new ProgressUpdate(
                    ProductionState.WritingScript.ToString(),
                    $"Script: AI returned " +
                    $"{plan.Scenes.Count} scene(s) — " +
                    "checking structure and claim references",
                    30,
                    true));

            try
            {
                Grounding.ValidateScenes(
                    plan.Scenes,
                    p.Brief!);

                ValidateScriptPlan(
                    plan);

                progress.Report(
                    new ProgressUpdate(
                        ProductionState.WritingScript.ToString(),
                        $"Script: validation passed — " +
                        $"{plan.Scenes.Count} scene(s) · " +
                        $"title: {plan.Title}",
                        30,
                        true));

                await ProjectStore.Log(
                    p,
                    $"SCRIPT VALID | " +
                    $"Attempt={attempt} | " +
                    $"Scenes={plan.Scenes.Count} | " +
                    $"Title={plan.Title}");

                return plan;
            }
            catch (InvalidDataException e)
            {
                lastValidationFailure =
                    e;

                progress.Report(
                    new ProgressUpdate(
                        ProductionState.WritingScript.ToString(),
                        $"Script: attempt " +
                        $"{attempt}/{AiValidationAttempts} rejected — " +
                        e.Message,
                        30,
                        true));

                await ProjectStore.Log(
                    p,
                    $"SCRIPT VALIDATION FAILED | " +
                    $"Attempt={attempt} | {e.Message}");

                await SaveRejectedObject(
                    p,
                    $"script/rejected-attempt-{attempt:00}.json",
                    plan,
                    ct);

                if (attempt >=
                    AiValidationAttempts)
                {
                    break;
                }

                await ProjectStore.Log(
                    p,
                    "SCRIPT | Corrective retry will include " +
                    "the exact validation failure.");
            }
        }

        throw new InvalidDataException(
            "The local AI produced an invalid script three times. " +
            "No media generation was started. " +
            $"Last validation problem: " +
            $"{lastValidationFailure?.Message ?? "unknown"}.",
            lastValidationFailure);
    }

    // ========================================================================
    // SCRIPT VALIDATION
    // ========================================================================

    private static void ValidateScriptPlan(
        ScriptPlan plan)
    {
        ArgumentNullException.ThrowIfNull(
            plan);

        if (string.IsNullOrWhiteSpace(
                plan.Title))
        {
            throw new InvalidDataException(
                "Generated script has no video title.");
        }

        if (plan.Title.Length > 100)
        {
            throw new InvalidDataException(
                $"Generated video title exceeds 100 characters " +
                $"({plan.Title.Length}).");
        }

        if (string.IsNullOrWhiteSpace(
                plan.Description))
        {
            throw new InvalidDataException(
                "Generated script has no video description.");
        }

        if (plan.Scenes.Count == 0)
        {
            throw new InvalidDataException(
                "Generated script contains no scenes.");
        }
    }

    // ========================================================================
    // NARRATION VALIDATION
    // ========================================================================

    private static void ValidateNarrationDuration(
        Scene scene)
    {
        if (double.IsNaN(
                scene.Duration)
            ||
            double.IsInfinity(
                scene.Duration)
            ||
            scene.Duration < 0.2
            ||
            scene.Duration > 120)
        {
            throw new InvalidDataException(
                $"Narration segment " +
                $"{scene.Number} has an unusable " +
                $"duration ({scene.Duration:0.00}s).");
        }
    }

    // ========================================================================
    // WARNINGS
    // ========================================================================

    private static void BuildWarnings(
        Project p)
    {
        p.Warnings.Clear();

        AddWarningOnce(
            p,
            "Review factual accuracy: matching source quotations " +
            "does not prove the AI's interpretation or scene narration.");

        AddWarningOnce(
            p,
            "Caption timing is estimated within measured narration " +
            "segments; it is not word-aligned.");

        var distinctHosts =
            p.Sources
                .Select(
                    source =>
                    {
                        try
                        {
                            return new Uri(
                                source.Url).Host;
                        }
                        catch
                        {
                            return "";
                        }
                    })
                .Where(
                    host =>
                        !string.IsNullOrWhiteSpace(
                            host))
                .Distinct(
                    StringComparer.OrdinalIgnoreCase)
                .Count();

        if (distinctHosts < 2)
        {
            AddWarningOnce(
                p,
                "All sources share one website. Add independent " +
                "sources before relying on this research.");
        }

        AddWarningOnce(
            p,
            "Research does not guarantee publication dates or " +
            "current-news coverage. Check time-sensitive claims manually.");
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

    // ========================================================================
    // SAVE GENERATED TEXT
    // ========================================================================

    private static async Task SaveBrief(
        Project p,
        CancellationToken ct)
    {
        if (p.Brief == null)
        {
            throw new InvalidOperationException(
                "Cannot save a missing research brief.");
        }

        var claimsPath =
            p.PathFor(
                "research/claims.json");

        var briefPath =
            p.PathFor(
                "research/brief.md");

        EnsureParentDirectory(
            claimsPath);

        EnsureParentDirectory(
            briefPath);

        await Json.Save(
            claimsPath,
            p.Brief.Claims,
            ct);

        await WriteTextAtomically(
            briefPath,
            p.Brief.Summary,
            ct);
    }

    private static async Task SaveScriptAndScenes(
        Project p,
        CancellationToken ct)
    {
        var scenesPath =
            p.PathFor(
                "scenes/scenes.json");

        var scriptPath =
            p.PathFor(
                "script/script.md");

        EnsureParentDirectory(
            scenesPath);

        EnsureParentDirectory(
            scriptPath);

        await Json.Save(
            scenesPath,
            p.Scenes,
            ct);

        var script =
            string.Join(
                Environment.NewLine +
                Environment.NewLine,
                p.Scenes.Select(
                    scene =>
                        scene.Narration));

        await WriteTextAtomically(
            scriptPath,
            script,
            ct);
    }

    // ========================================================================
    // SAVE REJECTED AI OUTPUT
    // ========================================================================

    private static async Task SaveRejectedObject<T>(
        Project p,
        string relativePath,
        T value,
        CancellationToken ct)
    {
        try
        {
            var path =
                p.PathFor(
                    relativePath);

            EnsureParentDirectory(
                path);

            await Json.Save(
                path,
                value,
                ct);

            await ProjectStore.Log(
                p,
                $"AI REJECTED OUTPUT SAVED | {relativePath}");
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception e)
            when (
                e is IOException
                or UnauthorizedAccessException
                or InvalidDataException)
        {
            // Diagnostic persistence must not replace the real
            // validation error.
            await ProjectStore.Log(
                p,
                $"AI REJECTED OUTPUT SAVE WARNING | " +
                $"{relativePath} | {e.Message}");
        }
    }

    // ========================================================================
    // ATOMIC TEXT WRITE
    // ========================================================================

    private static async Task WriteTextAtomically(
        string path,
        string content,
        CancellationToken ct)
    {
        EnsureParentDirectory(
            path);

        var temporary =
            path + ".tmp";

        try
        {
            await File.WriteAllTextAsync(
                temporary,
                content,
                ct);

            File.Move(
                temporary,
                path,
                true);
        }
        finally
        {
            try
            {
                if (File.Exists(
                        temporary))
                {
                    File.Delete(
                        temporary);
                }
            }
            catch
            {
                // Cleanup failure must not mask the real result.
            }
        }
    }

    // ========================================================================
    // PATH HELPERS
    // ========================================================================

    private static void EnsureParentDirectory(
        string path)
    {
        var directory =
            Path.GetDirectoryName(
                path);

        if (!string.IsNullOrWhiteSpace(
                directory))
        {
            Directory.CreateDirectory(
                directory);
        }
    }

    // ========================================================================
    // VISUAL FAILURE CLASSIFICATION
    // ========================================================================

    private static bool IsRecoverableVisualFailure(
        Exception e)
    {
        return
            e is HttpRequestException
            or InvalidOperationException
            or InvalidDataException
            or TimeoutException
            or IOException
            or OperationCanceledException;
    }

    // ========================================================================
    // HUMAN-FRIENDLY FAILURE
    // ========================================================================

    private static string FriendlyStageError(
        ProductionState stage,
        Exception e)
    {
        var prefix =
            stage switch
            {
                ProductionState.Researching =>
                    "Research failed.",

                ProductionState.AnalysingSources =>
                    "Evidence analysis failed.",

                ProductionState.WritingScript =>
                    "Script generation failed.",

                ProductionState.PlanningScenes =>
                    "Scene planning failed.",

                ProductionState.GeneratingNarration =>
                    "Narration generation failed.",

                ProductionState.GeneratingVisuals =>
                    "Visual generation failed.",

                ProductionState.Rendering =>
                    "Video rendering failed.",

                ProductionState.QualityChecking =>
                    "Video quality checking failed.",

                _ =>
                    "Production failed."
            };

        return
            $"{prefix} {e.Message}";
    }

    // ========================================================================
    // SAFE DISPLAY TEXT
    // ========================================================================

    private static string SafeVisualPrompt(
        string? value)
    {
        if (string.IsNullOrWhiteSpace(
                value))
        {
            return "No custom prompt";
        }

        var text =
            value
                .Replace(
                    "\r",
                    " ")
                .Replace(
                    "\n",
                    " ")
                .Trim();

        return
            text.Length <= 100
                ? text
                : text[..100] + "...";
    }

    // ========================================================================
    // CONTENT IDENTITY
    // ========================================================================

    public static string ContentHash(
        Project p)
    {
        return
            Util.Hash(
                Json.Encode(
                    p.Scenes.Select(
                        scene =>
                            new
                            {
                                scene.Narration,
                                scene.Heading,
                                scene.Points,
                                scene.ClaimIds,
                                scene.VisualType,
                                scene.VisualPrompt,
                                scene.Revision
                            })));
    }
}