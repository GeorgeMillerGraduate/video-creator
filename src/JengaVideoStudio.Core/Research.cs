using System.Globalization;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace JengaVideoStudio.Core;

// ============================================================================
// PUBLIC WEB
// ============================================================================
//
// Safe HTTP reader for research.
//
// - HTTPS only
// - no credentials in URLs
// - blocks private/loopback/link-local destinations
// - validates redirects
// - response size limit
// - text/HTML/JSON/XML only
// - useful HTTP diagnostics
// - bounded retry for genuine transient failures
// - NEVER automatically retries HTTP 429
// ============================================================================
public sealed class PublicWeb : IDisposable
{
    private const int MaxRedirects = 5;
    private const long MaxResponseBytes = 2_000_000;
    private const int MaxAttempts = 3;

    private readonly HttpClient http;

    public PublicWeb()
    {
        var handler = new SocketsHttpHandler
        {
            AllowAutoRedirect = false,
            AutomaticDecompression = DecompressionMethods.All,

            ConnectCallback = async (ctx, ct) =>
            {
                var host = ctx.DnsEndPoint.Host;

                IPAddress[] addresses;

                try
                {
                    addresses =
                        await Dns.GetHostAddressesAsync(host, ct);
                }
                catch (Exception e)
                    when (e is SocketException or HttpRequestException)
                {
                    throw new HttpRequestException(
                        $"DNS lookup failed for '{host}': {e.Message}",
                        e);
                }

                if (addresses.Length == 0)
                {
                    throw new HttpRequestException(
                        $"DNS lookup returned no addresses for '{host}'.");
                }

                if (addresses.Any(IsPrivate))
                {
                    throw new HttpRequestException(
                        $"Non-public research destination blocked: {host}");
                }

                Exception? last = null;

                // Try public DNS results in order rather than assuming
                // the first returned address is reachable.
                foreach (var address in addresses)
                {
                    var socket = new Socket(
                        address.AddressFamily,
                        SocketType.Stream,
                        ProtocolType.Tcp);

                    try
                    {
                        await socket.ConnectAsync(
                            address,
                            ctx.DnsEndPoint.Port,
                            ct);

                        return new NetworkStream(
                            socket,
                            ownsSocket: true);
                    }
                    catch (Exception e)
                    {
                        last = e;
                        socket.Dispose();
                    }
                }

                throw new HttpRequestException(
                    $"Could not connect to '{host}'.",
                    last);
            }
        };

        http = new HttpClient(handler)
        {
            Timeout = TimeSpan.FromSeconds(45)
        };

        // Wikimedia requests identifiable automated clients.
        http.DefaultRequestHeaders.UserAgent.ParseAdd(
            "JengaVideoStudio/0.2");

        http.DefaultRequestHeaders.UserAgent.Add(
            new ProductInfoHeaderValue(
                "(+https://jenga-code.com/)"));

        http.DefaultRequestHeaders.Accept.ParseAdd(
            "text/html,application/json,text/plain,application/xml;q=0.9,*/*;q=0.1");

        http.DefaultRequestHeaders.AcceptLanguage.ParseAdd(
            "en-GB,en;q=0.8");
    }

    // ========================================================================
    // URL / ADDRESS SAFETY
    // ========================================================================

    public static bool IsPrivate(IPAddress ip)
    {
        if (ip.IsIPv4MappedToIPv6)
            ip = ip.MapToIPv4();

        if (IPAddress.IsLoopback(ip))
            return true;

        var b = ip.GetAddressBytes();

        if (b.Length == 16)
        {
            return ip.IsIPv6LinkLocal
                   || ip.IsIPv6Multicast
                   || ip.Equals(IPAddress.IPv6Any)
                   || ip.Equals(IPAddress.IPv6None)
                   || (b[0] & 0xfe) == 0xfc;
        }

        return b[0] == 0
               || b[0] == 10
               || b[0] == 127
               || b[0] >= 224
               || (b[0] == 169 && b[1] == 254)
               || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
               || (b[0] == 192 && b[1] == 168)
               || (b[0] == 100 && b[1] >= 64 && b[1] <= 127);
    }

    public static string Canonical(string url)
    {
        if (!Uri.TryCreate(
                url,
                UriKind.Absolute,
                out var uri))
        {
            throw new InvalidDataException(
                $"Invalid research URL: {url}");
        }

        if (!string.Equals(
                uri.Scheme,
                Uri.UriSchemeHttps,
                StringComparison.OrdinalIgnoreCase)
            || !uri.IsDefaultPort
            || !string.IsNullOrEmpty(uri.UserInfo))
        {
            throw new InvalidDataException(
                "Research URLs must use public HTTPS without credentials.");
        }

        var builder = new UriBuilder(uri)
        {
            Fragment = ""
        };

        return builder.Uri.AbsoluteUri;
    }

    // ========================================================================
    // READ
    // ========================================================================

    public async Task<string> Read(
        string url,
        CancellationToken ct,
        bool robots = false)
    {
        url = Canonical(url);

        for (var redirect = 0;
             redirect < MaxRedirects;
             redirect++)
        {
            ct.ThrowIfCancellationRequested();

            var uri = new Uri(url);

            if (robots && !await Allowed(uri, ct))
            {
                throw new HttpRequestException(
                    $"Site research access restricted by robots.txt: {uri.Host}");
            }

            using var response =
                await SendWithRetry(uri, ct);

            // ------------------------------------------------------------
            // REDIRECT
            // ------------------------------------------------------------

            if ((int)response.StatusCode is >= 300 and < 400)
            {
                if (response.Headers.Location == null)
                {
                    throw new HttpRequestException(
                        $"HTTP {(int)response.StatusCode} " +
                        $"{response.ReasonPhrase} from {SafeUrl(uri)} " +
                        "with no redirect destination.",
                        null,
                        response.StatusCode);
                }

                var redirected =
                    new Uri(uri, response.Headers.Location);

                url =
                    Canonical(redirected.AbsoluteUri);

                continue;
            }

            // ------------------------------------------------------------
            // HTTP ERROR
            // ------------------------------------------------------------

            if (!response.IsSuccessStatusCode)
            {
                var snippet =
                    await ReadErrorSnippet(response, ct);

                var retryAfter =
                    GetRetryAfterDescription(response);

                var message =
                    $"HTTP {(int)response.StatusCode} " +
                    $"{response.ReasonPhrase} from {SafeUrl(uri)}";

                if (!string.IsNullOrWhiteSpace(retryAfter))
                    message += $" | Retry-After: {retryAfter}";

                if (!string.IsNullOrWhiteSpace(snippet))
                    message += $" | Response: {snippet}";

                throw new HttpRequestException(
                    message,
                    null,
                    response.StatusCode);
            }

            // ------------------------------------------------------------
            // CONTENT TYPE
            // ------------------------------------------------------------

            var mediaType =
                response.Content.Headers.ContentType?.MediaType ?? "";

            if (mediaType.Length > 0
                && !mediaType.Contains(
                    "text",
                    StringComparison.OrdinalIgnoreCase)
                && !mediaType.Contains(
                    "json",
                    StringComparison.OrdinalIgnoreCase)
                && !mediaType.Contains(
                    "xml",
                    StringComparison.OrdinalIgnoreCase))
            {
                throw new HttpRequestException(
                    "Research only reads text/HTML/JSON/XML. " +
                    $"{SafeUrl(uri)} returned '{mediaType}'.");
            }

            // ------------------------------------------------------------
            // DECLARED SIZE
            // ------------------------------------------------------------

            if (response.Content.Headers.ContentLength
                    is long length
                && length > MaxResponseBytes)
            {
                throw new HttpRequestException(
                    "Research page exceeds the 2 MB limit: " +
                    SafeUrl(uri));
            }

            // ------------------------------------------------------------
            // STREAM WITH HARD LIMIT
            // ------------------------------------------------------------

            using var stream =
                await response.Content.ReadAsStreamAsync(ct);

            using var memory =
                new MemoryStream();

            var buffer =
                new byte[16_384];

            while (true)
            {
                var count =
                    await stream.ReadAsync(
                        buffer.AsMemory(),
                        ct);

                if (count == 0)
                    break;

                if (memory.Length + count > MaxResponseBytes)
                {
                    throw new HttpRequestException(
                        "Research page exceeds the 2 MB limit: " +
                        SafeUrl(uri));
                }

                await memory.WriteAsync(
                    buffer.AsMemory(0, count),
                    ct);
            }

            // ------------------------------------------------------------
            // ENCODING
            // ------------------------------------------------------------

            var charset =
                response.Content.Headers.ContentType?.CharSet;

            Encoding encoding =
                Encoding.UTF8;

            if (!string.IsNullOrWhiteSpace(charset))
            {
                try
                {
                    encoding =
                        Encoding.GetEncoding(
                            charset.Trim('"', '\''));
                }
                catch (ArgumentException)
                {
                    // UTF-8 fallback.
                }
            }

            return encoding.GetString(
                memory.ToArray());
        }

        throw new HttpRequestException(
            "Too many research redirects while requesting " +
            SafeUrl(new Uri(url)));
    }

    // ========================================================================
    // RETRIES
    // ========================================================================

    private async Task<HttpResponseMessage> SendWithRetry(
        Uri uri,
        CancellationToken ct)
    {
        for (var attempt = 1;
             attempt <= MaxAttempts;
             attempt++)
        {
            ct.ThrowIfCancellationRequested();

            HttpResponseMessage response;

            try
            {
                response =
                    await http.GetAsync(
                        uri,
                        HttpCompletionOption.ResponseHeadersRead,
                        ct);
            }
            catch (OperationCanceledException)
                when (!ct.IsCancellationRequested
                      && attempt < MaxAttempts)
            {
                await Task.Delay(
                    TimeSpan.FromMilliseconds(
                        750 * attempt),
                    ct);

                continue;
            }
            catch (HttpRequestException)
                when (attempt < MaxAttempts)
            {
                await Task.Delay(
                    TimeSpan.FromMilliseconds(
                        750 * attempt),
                    ct);

                continue;
            }

            // NEVER immediately retry a rate limit.
            if (response.StatusCode ==
                HttpStatusCode.TooManyRequests)
            {
                return response;
            }

            var transient =
                response.StatusCode ==
                    HttpStatusCode.RequestTimeout
                || response.StatusCode ==
                    HttpStatusCode.BadGateway
                || response.StatusCode ==
                    HttpStatusCode.ServiceUnavailable
                || response.StatusCode ==
                    HttpStatusCode.GatewayTimeout
                || (int)response.StatusCode >= 500;

            if (!transient
                || attempt == MaxAttempts)
            {
                return response;
            }

            response.Dispose();

            await Task.Delay(
                TimeSpan.FromMilliseconds(
                    1000 * attempt),
                ct);
        }

        throw new HttpRequestException(
            "Research request failed after retries: " +
            SafeUrl(uri));
    }

    private static string GetRetryAfterDescription(
        HttpResponseMessage response)
    {
        var retry =
            response.Headers.RetryAfter;

        if (retry == null)
            return "";

        if (retry.Delta.HasValue)
        {
            return
                $"{Math.Ceiling(retry.Delta.Value.TotalSeconds)} seconds";
        }

        if (retry.Date.HasValue)
        {
            return retry.Date.Value.ToString(
                "O",
                CultureInfo.InvariantCulture);
        }

        return "";
    }

    private static async Task<string> ReadErrorSnippet(
        HttpResponseMessage response,
        CancellationToken ct)
    {
        try
        {
            var body =
                await response.Content
                    .ReadAsStringAsync(ct);

            body =
                Regex.Replace(
                    body,
                    @"\s+",
                    " ")
                .Trim();

            if (body.Length > 600)
                body = body[..600] + "...";

            return body;
        }
        catch
        {
            return "";
        }
    }

    // ========================================================================
    // ROBOTS.TXT
    // ========================================================================

    private async Task<bool> Allowed(
        Uri uri,
        CancellationToken ct)
    {
        string content;

        try
        {
            content =
                await Read(
                    new Uri(
                        uri,
                        "/robots.txt")
                    .AbsoluteUri,
                    ct,
                    robots: false);
        }
        catch (HttpRequestException e)
            when (e.StatusCode ==
                  HttpStatusCode.NotFound)
        {
            return true;
        }
        catch (HttpRequestException)
        {
            // Conservative fallback.
            return false;
        }

        foreach (var line in content.Split('\n'))
        {
            var cleaned =
                line.Split('#')[0].Trim();

            if (!cleaned.StartsWith(
                    "Disallow:",
                    StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            var path =
                cleaned["Disallow:".Length..]
                    .Trim();

            if (path.Length == 0)
                continue;

            var pattern =
                "^"
                + Regex.Escape(path)
                    .Replace("\\*", ".*")
                    .Replace("\\$", "$");

            if (Regex.IsMatch(
                    uri.PathAndQuery,
                    pattern))
            {
                return false;
            }
        }

        return true;
    }

    // ========================================================================
    // HTML -> TEXT
    // ========================================================================

    public static string Text(string html)
    {
        if (string.IsNullOrWhiteSpace(html))
            return "";

        html =
            Regex.Replace(
                html,
                @"<(script|style|nav|footer|header|noscript)\b[^>]*>.*?</\1>",
                " ",
                RegexOptions.IgnoreCase
                | RegexOptions.Singleline);

        html =
            Regex.Replace(
                html,
                @"<[^>]+>",
                " ");

        html =
            WebUtility.HtmlDecode(html);

        return Regex.Replace(
                html,
                @"\s+",
                " ")
            .Trim();
    }

    private static string SafeUrl(Uri uri)
    {
        return uri.GetLeftPart(
                   UriPartial.Path)
               + (string.IsNullOrEmpty(uri.Query)
                   ? ""
                   : uri.Query);
    }

    public void Dispose()
    {
        http.Dispose();
    }
}


// ============================================================================
// RESEARCH SERVICE
// ============================================================================
//
// Strategy:
//
// 1. Read supplied URLs.
// 2. Ask local AI for research queries.
// 3. Search Wikipedia.
// 4. Rank discovered pages for relevance.
// 5. Attempt one economical batch extraction.
// 6. If a batch extract is empty, retry the relevant page individually.
// 7. If the individual MediaWiki extract is still empty, use the Wikimedia
//    REST summary endpoint.
// 8. Collect a restrained number of external references.
// 9. Stop Wikimedia work immediately on HTTP 429.
// 10. Require at least two readable sources.
// ============================================================================
public sealed class ResearchService(
    ILanguageModel llm,
    PublicWeb web) : IResearchService
{
    private const string Api =
        "https://en.wikipedia.org/w/api.php?format=json&formatversion=2&";

    private const string RestSummaryApi =
        "https://en.wikipedia.org/api/rest_v1/page/summary/";

    private const int MaxSources = 7;
    private const int TargetWikipediaSources = 4;
    private const int MaxQueries = 3;
    private const int MaxCandidates = 9;
    private const int MaxExternalAttempts = 3;
    private const int MinimumReadableCharacters = 500;

    private static readonly TimeSpan WikimediaDelay =
        TimeSpan.FromMilliseconds(1500);

    private DateTimeOffset lastWikimediaRequest =
        DateTimeOffset.MinValue;

    private sealed record WikipediaCandidate(
        int PageId,
        string Title,
        string Query,
        int SearchPosition,
        int Score);

    // ========================================================================
    // MAIN ENTRY
    // ========================================================================

    public async Task<List<Source>> Research(
        Project p,
        IProgress<ProgressUpdate> progress,
        CancellationToken ct)
    {
        var sources =
            new List<Source>();

        var visited =
            new HashSet<string>(
                StringComparer.OrdinalIgnoreCase);

        var wikipediaRateLimited = false;

        await ProjectStore.Log(
            p,
            $"Research started. Idea: {p.Idea}");

        // ====================================================================
        // LOGGING
        // ====================================================================

        async Task LogFailure(
            string operation,
            string? url,
            Exception e)
        {
            var status =
                e is HttpRequestException hre
                && hre.StatusCode.HasValue
                    ? $" HTTP {(int)hre.StatusCode.Value} {hre.StatusCode.Value};"
                    : "";

            await ProjectStore.Log(
                p,
                $"RESEARCH WARNING | {operation};{status} " +
                $"URL={url ?? "(none)"}; " +
                $"Exception={e.GetType().Name}; " +
                $"Message={e.Message}");
        }

        // ====================================================================
        // EXTERNAL URL READER
        // ====================================================================

        async Task FetchExternal(
            string rawUrl)
        {
            if (sources.Count >= MaxSources)
                return;

            string? url = null;

            try
            {
                url =
                    PublicWeb.Canonical(rawUrl);

                if (!visited.Add(url))
                    return;

                await ProjectStore.Log(
                    p,
                    $"Fetching external source: {url}");

                var html =
                    await web.Read(
                        url,
                        ct,
                        robots: true);

                var text =
                    PublicWeb.Text(html);

                if (text.Length <
                    MinimumReadableCharacters)
                {
                    await ProjectStore.Log(
                        p,
                        $"Skipped short external source " +
                        $"({text.Length} chars): {url}");

                    return;
                }

                var titleMatch =
                    Regex.Match(
                        html,
                        @"<title[^>]*>(.*?)</title>",
                        RegexOptions.IgnoreCase
                        | RegexOptions.Singleline);

                var title =
                    WebUtility.HtmlDecode(
                            titleMatch.Groups[1].Value)
                        .Trim();

                var uri =
                    new Uri(url);

                sources.Add(
                    new Source
                    {
                        Url = url,

                        Title =
                            title.Length > 0
                                ? title
                                : uri.Host,

                        Publisher =
                            uri.Host,

                        Text =
                            Clip(text)
                    });

                await ProjectStore.Log(
                    p,
                    $"Accepted external source: {url} " +
                    $"({text.Length} readable chars)");
            }
            catch (OperationCanceledException)
                when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception e)
                when (e is HttpRequestException
                    or InvalidDataException
                    or UriFormatException)
            {
                await LogFailure(
                    "External source skipped",
                    url ?? rawUrl,
                    e);
            }
        }

        // ====================================================================
        // 1. USER-SUPPLIED SOURCES
        // ====================================================================

        foreach (var rawUrl in
                 p.SuppliedUrls.Split(
                     ['\r', '\n'],
                     StringSplitOptions.RemoveEmptyEntries
                     | StringSplitOptions.TrimEntries))
        {
            ct.ThrowIfCancellationRequested();

            await FetchExternal(rawUrl);

            if (sources.Count >= MaxSources)
                break;
        }

        // ====================================================================
        // 2. LOCAL AI QUERY GENERATION
        // ====================================================================

        progress.Report(
            new ProgressUpdate(
                "Researching",
                "Asking the local AI for research queries",
                7,
                true));

        ResearchQueries queries;

        try
        {
            queries =
                await llm.Generate<ResearchQueries>(
                    "queries",
                    new
                    {
                        idea = p.Idea
                    },
                    ct);
        }
        catch (Exception e)
        {
            await ProjectStore.Log(
                p,
                "QUERY GENERATION FAILED | " +
                $"{e.GetType().Name}: {e.Message}");

            throw new InvalidOperationException(
                "The local AI could not create research queries. " +
                $"Details: {e.Message}",
                e);
        }

        var usableQueries =
            queries.Queries
                .Where(q =>
                    !string.IsNullOrWhiteSpace(q))
                .Select(q => q.Trim())
                .Distinct(
                    StringComparer.OrdinalIgnoreCase)
                .Take(MaxQueries)
                .ToList();

        await ProjectStore.Log(
            p,
            $"Local AI produced {usableQueries.Count} " +
            $"research queries: " +
            string.Join(" | ", usableQueries));

        if (usableQueries.Count == 0)
        {
            throw new InvalidOperationException(
                "The local AI returned no usable research queries.");
        }

        // ====================================================================
        // 3. WIKIPEDIA SEARCH
        // ====================================================================

        var candidateMap =
            new Dictionary<int, WikipediaCandidate>();

        foreach (var query in usableQueries)
        {
            ct.ThrowIfCancellationRequested();

            if (wikipediaRateLimited
                || sources.Count >= MaxSources)
            {
                break;
            }

            progress.Report(
                new ProgressUpdate(
                    "Researching",
                    $"Discovering sources: {query}",
                    10,
                    true));

            var searchUrl =
                BuildWikipediaSearchUrl(query);

            try
            {
                await ProjectStore.Log(
                    p,
                    $"Wikipedia search: {query}");

                var json =
                    await ReadWikipedia(
                        searchUrl,
                        p,
                        ct);

                using var document =
                    JsonDocument.Parse(json);

                if (!document.RootElement
                        .TryGetProperty(
                            "query",
                            out var queryNode)
                    || !queryNode.TryGetProperty(
                        "search",
                        out var results)
                    || results.ValueKind !=
                        JsonValueKind.Array)
                {
                    await ProjectStore.Log(
                        p,
                        $"Wikipedia search returned unexpected JSON " +
                        $"for '{query}'.");

                    continue;
                }

                var position = 0;

                foreach (var item in
                         results.EnumerateArray())
                {
                    position++;

                    if (!item.TryGetProperty(
                            "pageid",
                            out var idNode)
                        || !idNode.TryGetInt32(
                            out var pageId))
                    {
                        continue;
                    }

                    var title =
                        item.TryGetProperty(
                            "title",
                            out var titleNode)
                            ? titleNode.GetString() ?? ""
                            : "";

                    if (title.Length == 0)
                        continue;

                    var score =
                        ScoreCandidate(
                            p.Idea,
                            query,
                            title,
                            position);

                    var candidate =
                        new WikipediaCandidate(
                            pageId,
                            title,
                            query,
                            position,
                            score);

                    if (!candidateMap.TryGetValue(
                            pageId,
                            out var existing)
                        || candidate.Score >
                           existing.Score)
                    {
                        candidateMap[pageId] =
                            candidate;
                    }
                }

                await ProjectStore.Log(
                    p,
                    $"Wikipedia search '{query}' produced " +
                    $"{results.GetArrayLength()} result(s). " +
                    $"Unique candidates: {candidateMap.Count}");
            }
            catch (OperationCanceledException)
                when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (HttpRequestException e)
                when (e.StatusCode ==
                      HttpStatusCode.TooManyRequests)
            {
                wikipediaRateLimited = true;

                await LogFailure(
                    $"Wikipedia rate-limited search '{query}'",
                    searchUrl,
                    e);

                await ProjectStore.Log(
                    p,
                    "WIKIPEDIA RATE LIMIT | " +
                    "Stopping Wikimedia requests for this run.");

                break;
            }
            catch (Exception e)
                when (e is HttpRequestException
                    or JsonException
                    or InvalidDataException)
            {
                await LogFailure(
                    $"Wikipedia search failed for '{query}'",
                    searchUrl,
                    e);
            }
        }

        // ====================================================================
        // 4. RANK CANDIDATES
        // ====================================================================

        var candidates =
            candidateMap.Values
                .OrderByDescending(x => x.Score)
                .ThenBy(x => x.SearchPosition)
                .Take(MaxCandidates)
                .ToList();

        if (candidates.Count > 0)
        {
            await ProjectStore.Log(
                p,
                "Wikipedia candidate ranking: " +
                string.Join(
                    " | ",
                    candidates.Select(
                        x =>
                            $"{x.Title} [{x.Score}]")));
        }

        // ====================================================================
        // 5. BATCH EXTRACTION
        // ====================================================================

        var batchResults =
            new Dictionary<int, (
                string Title,
                string Url,
                string Text,
                List<string> ExternalLinks)>();

        if (!wikipediaRateLimited
            && candidates.Count > 0
            && sources.Count < MaxSources)
        {
            progress.Report(
                new ProgressUpdate(
                    "Researching",
                    "Reading discovered reference material",
                    12,
                    true));

            var batchUrl =
                BuildWikipediaBatchUrl(
                    candidates.Select(x => x.PageId));

            try
            {
                await ProjectStore.Log(
                    p,
                    "Wikipedia batch request: pageids=" +
                    string.Join(
                        "|",
                        candidates.Select(x => x.PageId)));

                var json =
                    await ReadWikipedia(
                        batchUrl,
                        p,
                        ct);

                batchResults =
                    ParseWikipediaBatch(json);

                await ProjectStore.Log(
                    p,
                    $"Wikipedia batch parsed " +
                    $"{batchResults.Count} page record(s).");
            }
            catch (OperationCanceledException)
                when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (HttpRequestException e)
                when (e.StatusCode ==
                      HttpStatusCode.TooManyRequests)
            {
                wikipediaRateLimited = true;

                await LogFailure(
                    "Wikipedia batch request rate-limited",
                    batchUrl,
                    e);

                await ProjectStore.Log(
                    p,
                    "WIKIPEDIA RATE LIMIT | " +
                    "Stopping Wikimedia requests for this run.");
            }
            catch (Exception e)
                when (e is HttpRequestException
                    or JsonException
                    or InvalidDataException)
            {
                await LogFailure(
                    "Wikipedia batch request failed",
                    batchUrl,
                    e);
            }
        }

        // ====================================================================
        // 6. ACCEPT BATCH RESULTS / INDIVIDUAL FALLBACK
        // ====================================================================

        var externalCandidates =
            new List<string>();

        foreach (var candidate in candidates)
        {
            ct.ThrowIfCancellationRequested();

            if (sources.Count >= MaxSources)
                break;

            if (CountWikipediaSources(sources) >=
                TargetWikipediaSources)
            {
                break;
            }

            if (batchResults.TryGetValue(
                    candidate.PageId,
                    out var batch)
                && batch.Text.Length >=
                   MinimumReadableCharacters)
            {
                if (TryAddWikipediaSource(
                        sources,
                        visited,
                        batch.Url,
                        batch.Title,
                        batch.Text))
                {
                    await ProjectStore.Log(
                        p,
                        $"Accepted Wikipedia source via BATCH: " +
                        $"'{batch.Title}' | " +
                        $"pageid={candidate.PageId} | " +
                        $"score={candidate.Score} | " +
                        $"chars={batch.Text.Length}");

                    AddExternalCandidates(
                        externalCandidates,
                        batch.ExternalLinks);
                }

                continue;
            }

            var batchLength =
                batchResults.TryGetValue(
                    candidate.PageId,
                    out var emptyBatch)
                    ? emptyBatch.Text.Length
                    : -1;

            await ProjectStore.Log(
                p,
                $"Wikipedia batch extract insufficient for " +
                $"'{candidate.Title}' | " +
                $"pageid={candidate.PageId} | " +
                $"chars={(batchLength < 0 ? "missing" : batchLength)} | " +
                "trying individual extraction.");

            if (wikipediaRateLimited)
                break;

            // ------------------------------------------------------------
            // INDIVIDUAL MEDIAWIKI FALLBACK
            // ------------------------------------------------------------

            var individual =
                await TryReadWikipediaPageIndividually(
                    candidate,
                    p,
                    ct);

            if (individual.RateLimited)
            {
                wikipediaRateLimited = true;
                break;
            }

            if (individual.Text.Length >=
                MinimumReadableCharacters)
            {
                if (TryAddWikipediaSource(
                        sources,
                        visited,
                        individual.Url,
                        individual.Title,
                        individual.Text))
                {
                    await ProjectStore.Log(
                        p,
                        $"Accepted Wikipedia source via INDIVIDUAL API: " +
                        $"'{individual.Title}' | " +
                        $"pageid={candidate.PageId} | " +
                        $"score={candidate.Score} | " +
                        $"chars={individual.Text.Length}");

                    AddExternalCandidates(
                        externalCandidates,
                        individual.ExternalLinks);
                }

                continue;
            }

            if (wikipediaRateLimited)
                break;

            // ------------------------------------------------------------
            // REST SUMMARY FALLBACK
            // ------------------------------------------------------------

            await ProjectStore.Log(
                p,
                $"Individual extract still insufficient for " +
                $"'{candidate.Title}' | " +
                $"pageid={candidate.PageId} | " +
                $"chars={individual.Text.Length} | " +
                "trying REST summary fallback.");

            var summary =
                await TryReadWikipediaSummary(
                    candidate,
                    p,
                    ct);

            if (summary.RateLimited)
            {
                wikipediaRateLimited = true;
                break;
            }

            if (summary.Text.Length >=
                MinimumReadableCharacters)
            {
                if (TryAddWikipediaSource(
                        sources,
                        visited,
                        summary.Url,
                        summary.Title,
                        summary.Text))
                {
                    await ProjectStore.Log(
                        p,
                        $"Accepted Wikipedia source via REST SUMMARY: " +
                        $"'{summary.Title}' | " +
                        $"pageid={candidate.PageId} | " +
                        $"score={candidate.Score} | " +
                        $"chars={summary.Text.Length}");
                }
            }
            else
            {
                await ProjectStore.Log(
                    p,
                    $"Wikipedia candidate exhausted: " +
                    $"'{candidate.Title}' | " +
                    $"batchChars={Math.Max(0, batchLength)} | " +
                    $"individualChars={individual.Text.Length} | " +
                    $"summaryChars={summary.Text.Length}");
            }
        }

        // ====================================================================
        // 7. EXTERNAL REFERENCES
        // ====================================================================

        var externalAttempts = 0;

        foreach (var external in
                 externalCandidates.Distinct(
                     StringComparer.OrdinalIgnoreCase))
        {
            ct.ThrowIfCancellationRequested();

            if (sources.Count >= MaxSources)
                break;

            if (externalAttempts >= MaxExternalAttempts)
                break;

            externalAttempts++;

            await FetchExternal(external);
        }

        // ====================================================================
        // 8. RESULT
        // ====================================================================

        await ProjectStore.Log(
            p,
            $"Research discovery finished with " +
            $"{sources.Count} readable source(s).");

        if (sources.Count < 2)
        {
            if (wikipediaRateLimited)
            {
                throw new InvalidOperationException(
                    $"Research found only {sources.Count} readable " +
                    "source(s) because Wikipedia temporarily " +
                    "rate-limited automated research. Jenga stopped " +
                    "sending Wikimedia requests rather than hammering " +
                    "the service. Wait before trying again, or add " +
                    "2–4 public HTTPS article URLs on the Create tab. " +
                    "See logs/production.log for details.");
            }

            throw new InvalidOperationException(
                $"Research found only {sources.Count} readable " +
                "source(s). At least two are required. " +
                "Jenga attempted Wikipedia batch extraction, " +
                "individual article extraction and REST-summary " +
                "fallbacks. Open logs/production.log to see exactly " +
                "which method failed for each candidate. You can also " +
                "add 2–4 public HTTPS article URLs on the Create tab.");
        }

        // ====================================================================
        // 9. SAVE
        // ====================================================================

        for (var i = 0;
             i < sources.Count;
             i++)
        {
            ct.ThrowIfCancellationRequested();

            sources[i].Id =
                $"S{i + 1}";

            await File.WriteAllTextAsync(
                p.PathFor(
                    $"sources/extracted/S{i + 1}.txt"),
                sources[i].Text,
                ct);
        }

        await Json.Save(
            p.PathFor("sources/sources.json"),
            sources,
            ct);

        await ProjectStore.Log(
            p,
            $"Research completed successfully with " +
            $"{sources.Count} source(s).");

        return sources;
    }

    // ========================================================================
    // INDIVIDUAL WIKIPEDIA EXTRACTION
    // ========================================================================

    private async Task<WikipediaReadResult>
        TryReadWikipediaPageIndividually(
            WikipediaCandidate candidate,
            Project p,
            CancellationToken ct)
    {
        var url =
            BuildWikipediaIndividualUrl(
                candidate.PageId);

        try
        {
            var json =
                await ReadWikipedia(
                    url,
                    p,
                    ct);

            using var document =
                JsonDocument.Parse(json);

            if (!document.RootElement.TryGetProperty(
                    "query",
                    out var queryNode)
                || !queryNode.TryGetProperty(
                    "pages",
                    out var pages)
                || pages.ValueKind !=
                    JsonValueKind.Array
                || pages.GetArrayLength() == 0)
            {
                await ProjectStore.Log(
                    p,
                    $"Individual Wikipedia response had no page data: " +
                    $"{candidate.PageId}");

                return WikipediaReadResult.Empty;
            }

            var page =
                pages[0];

            var title =
                GetString(page, "title")
                ?? candidate.Title;

            var pageUrl =
                GetString(page, "fullurl")
                ?? BuildWikipediaArticleUrl(title);

            var text =
                GetString(page, "extract")
                ?? "";

            var links =
                ReadExternalLinks(page);

            await ProjectStore.Log(
                p,
                $"Individual Wikipedia extract: " +
                $"'{title}' | pageid={candidate.PageId} | " +
                $"chars={text.Length}");

            return new WikipediaReadResult(
                title,
                pageUrl,
                text,
                links,
                false);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (HttpRequestException e)
            when (e.StatusCode ==
                  HttpStatusCode.TooManyRequests)
        {
            await ProjectStore.Log(
                p,
                $"WIKIPEDIA RATE LIMIT | Individual page " +
                $"{candidate.PageId} was rate-limited: {e.Message}");

            return WikipediaReadResult.RateLimit;
        }
        catch (Exception e)
            when (e is HttpRequestException
                or JsonException
                or InvalidDataException)
        {
            await ProjectStore.Log(
                p,
                $"RESEARCH WARNING | Individual Wikipedia " +
                $"page failed; pageid={candidate.PageId}; " +
                $"{e.GetType().Name}: {e.Message}");

            return WikipediaReadResult.Empty;
        }
    }

    // ========================================================================
    // REST SUMMARY FALLBACK
    // ========================================================================

    private async Task<WikipediaReadResult>
        TryReadWikipediaSummary(
            WikipediaCandidate candidate,
            Project p,
            CancellationToken ct)
    {
        var url =
            RestSummaryApi
            + Uri.EscapeDataString(
                candidate.Title.Replace(' ', '_'));

        try
        {
            var json =
                await ReadWikipedia(
                    url,
                    p,
                    ct);

            using var document =
                JsonDocument.Parse(json);

            var root =
                document.RootElement;

            var title =
                GetString(root, "title")
                ?? candidate.Title;

            var extract =
                GetString(root, "extract")
                ?? "";

            var pageUrl =
                BuildWikipediaArticleUrl(title);

            if (root.TryGetProperty(
                    "content_urls",
                    out var contentUrls)
                && contentUrls.TryGetProperty(
                    "desktop",
                    out var desktop)
                && desktop.TryGetProperty(
                    "page",
                    out var pageNode)
                && pageNode.GetString()
                    is { Length: > 0 } suppliedUrl)
            {
                pageUrl = suppliedUrl;
            }

            await ProjectStore.Log(
                p,
                $"Wikipedia REST summary: " +
                $"'{title}' | pageid={candidate.PageId} | " +
                $"chars={extract.Length}");

            return new WikipediaReadResult(
                title,
                pageUrl,
                extract,
                [],
                false);
        }
        catch (OperationCanceledException)
            when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (HttpRequestException e)
            when (e.StatusCode ==
                  HttpStatusCode.TooManyRequests)
        {
            await ProjectStore.Log(
                p,
                $"WIKIPEDIA RATE LIMIT | REST summary " +
                $"for '{candidate.Title}' was rate-limited: " +
                e.Message);

            return WikipediaReadResult.RateLimit;
        }
        catch (Exception e)
            when (e is HttpRequestException
                or JsonException
                or InvalidDataException)
        {
            await ProjectStore.Log(
                p,
                $"RESEARCH WARNING | Wikipedia REST summary " +
                $"failed for '{candidate.Title}'; " +
                $"{e.GetType().Name}: {e.Message}");

            return WikipediaReadResult.Empty;
        }
    }

    // ========================================================================
    // WIKIMEDIA THROTTLING
    // ========================================================================

    private async Task<string> ReadWikipedia(
        string url,
        Project p,
        CancellationToken ct)
    {
        var elapsed =
            DateTimeOffset.UtcNow
            - lastWikimediaRequest;

        if (lastWikimediaRequest !=
                DateTimeOffset.MinValue
            && elapsed < WikimediaDelay)
        {
            var wait =
                WikimediaDelay - elapsed;

            await ProjectStore.Log(
                p,
                $"Wikipedia throttle: waiting " +
                $"{Math.Ceiling(wait.TotalMilliseconds)} ms");

            await Task.Delay(
                wait,
                ct);
        }

        lastWikimediaRequest =
            DateTimeOffset.UtcNow;

        return await web.Read(
            url,
            ct);
    }

    // ========================================================================
    // BATCH PARSER
    // ========================================================================

    private static Dictionary<int, (
        string Title,
        string Url,
        string Text,
        List<string> ExternalLinks)>
        ParseWikipediaBatch(string json)
    {
        using var document =
            JsonDocument.Parse(json);

        if (!document.RootElement.TryGetProperty(
                "query",
                out var queryNode)
            || !queryNode.TryGetProperty(
                "pages",
                out var pages)
            || pages.ValueKind !=
                JsonValueKind.Array)
        {
            throw new InvalidDataException(
                "Wikipedia batch response contained no page array.");
        }

        var result =
            new Dictionary<int, (
                string Title,
                string Url,
                string Text,
                List<string> ExternalLinks)>();

        foreach (var page in
                 pages.EnumerateArray())
        {
            if (page.TryGetProperty(
                    "missing",
                    out _))
            {
                continue;
            }

            if (!page.TryGetProperty(
                    "pageid",
                    out var idNode)
                || !idNode.TryGetInt32(
                    out var pageId))
            {
                continue;
            }

            var title =
                GetString(page, "title")
                ?? $"Wikipedia page {pageId}";

            var url =
                GetString(page, "fullurl")
                ?? BuildWikipediaArticleUrl(title);

            var text =
                GetString(page, "extract")
                ?? "";

            var external =
                ReadExternalLinks(page);

            result[pageId] =
                (
                    title,
                    url,
                    text,
                    external
                );
        }

        return result;
    }

    // ========================================================================
    // RELEVANCE
    // ========================================================================

    private static int ScoreCandidate(
        string idea,
        string query,
        string title,
        int searchPosition)
    {
        var score =
            Math.Max(0, 40 - (searchPosition - 1) * 8);

        var ideaWords =
            ImportantWords(idea);

        var queryWords =
            ImportantWords(query);

        var titleWords =
            ImportantWords(title);

        foreach (var word in ideaWords)
        {
            if (titleWords.Contains(word))
                score += 25;
        }

        foreach (var word in queryWords)
        {
            if (titleWords.Contains(word))
                score += 15;
        }

        var normalizedTitle =
            Normalize(title);

        var normalizedIdea =
            Normalize(idea);

        var normalizedQuery =
            Normalize(query);

        if (normalizedIdea.Contains(
                normalizedTitle,
                StringComparison.OrdinalIgnoreCase)
            || normalizedTitle.Contains(
                normalizedIdea,
                StringComparison.OrdinalIgnoreCase))
        {
            score += 40;
        }

        if (normalizedQuery.Contains(
                normalizedTitle,
                StringComparison.OrdinalIgnoreCase)
            || normalizedTitle.Contains(
                normalizedQuery,
                StringComparison.OrdinalIgnoreCase))
        {
            score += 30;
        }

        // Mild penalty for list/episode-style pages.
        if (title.StartsWith(
                "List of ",
                StringComparison.OrdinalIgnoreCase))
        {
            score -= 25;
        }

        if (title.Contains(
                "episode",
                StringComparison.OrdinalIgnoreCase))
        {
            score -= 20;
        }

        return score;
    }

    private static HashSet<string> ImportantWords(
        string value)
    {
        var stopWords =
            new HashSet<string>(
                new[]
                {
                    "the", "and", "for", "with", "from",
                    "why", "did", "does", "how", "what",
                    "when", "where", "were", "was", "are",
                    "into", "that", "this", "have", "has",
                    "long", "historical", "history"
                },
                StringComparer.OrdinalIgnoreCase);

        return Regex.Matches(
                value.ToLowerInvariant(),
                @"[a-z0-9]+")
            .Select(x => x.Value)
            .Where(x =>
                x.Length >= 3
                && !stopWords.Contains(x))
            .ToHashSet(
                StringComparer.OrdinalIgnoreCase);
    }

    private static string Normalize(
        string value)
    {
        return Regex.Replace(
                value.ToLowerInvariant(),
                @"[^a-z0-9]+",
                " ")
            .Trim();
    }

    // ========================================================================
    // SOURCE HELPERS
    // ========================================================================

    private static bool TryAddWikipediaSource(
        List<Source> sources,
        HashSet<string> visited,
        string rawUrl,
        string title,
        string text)
    {
        if (text.Length <
            MinimumReadableCharacters)
        {
            return false;
        }

        string url;

        try
        {
            url =
                PublicWeb.Canonical(rawUrl);
        }
        catch
        {
            return false;
        }

        if (!visited.Add(url))
            return false;

        sources.Add(
            new Source
            {
                Url = url,
                Title = title,
                Publisher =
                    "Wikipedia (secondary reference)",
                Text = Clip(text)
            });

        return true;
    }

    private static int CountWikipediaSources(
        IEnumerable<Source> sources)
    {
        return sources.Count(
            x =>
                x.Publisher.StartsWith(
                    "Wikipedia",
                    StringComparison.OrdinalIgnoreCase));
    }

    private static string Clip(
        string text)
    {
        text =
            text.Trim();

        return text[
            ..Math.Min(
                text.Length,
                4500)];
    }

    private static void AddExternalCandidates(
        List<string> destination,
        IEnumerable<string> links)
    {
        foreach (var link in links)
        {
            if (!link.StartsWith(
                    "https://",
                    StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            if (!destination.Contains(
                    link,
                    StringComparer.OrdinalIgnoreCase))
            {
                destination.Add(link);
            }
        }
    }

    private static List<string> ReadExternalLinks(
        JsonElement page)
    {
        var result =
            new List<string>();

        if (!page.TryGetProperty(
                "extlinks",
                out var links)
            || links.ValueKind !=
                JsonValueKind.Array)
        {
            return result;
        }

        foreach (var link in
                 links.EnumerateArray())
        {
            if (link.TryGetProperty(
                    "url",
                    out var node)
                && node.GetString()
                    is { Length: > 0 } url
                && url.StartsWith(
                    "https://",
                    StringComparison.OrdinalIgnoreCase))
            {
                result.Add(url);
            }
        }

        return result;
    }

    private static string? GetString(
        JsonElement element,
        string property)
    {
        if (!element.TryGetProperty(
                property,
                out var node))
        {
            return null;
        }

        if (node.ValueKind !=
            JsonValueKind.String)
        {
            return null;
        }

        return node.GetString();
    }

    // ========================================================================
    // URL BUILDERS
    // ========================================================================

    private static string BuildWikipediaSearchUrl(
        string query)
    {
        return Api
               + "action=query"
               + "&list=search"
               + "&srlimit=3"
               + "&srsearch="
               + Uri.EscapeDataString(query);
    }

    private static string BuildWikipediaBatchUrl(
        IEnumerable<int> pageIds)
    {
        var ids =
            string.Join(
                "|",
                pageIds.Distinct());

        return Api
               + "action=query"
               + "&pageids="
               + Uri.EscapeDataString(ids)
               + "&prop=extracts%7Cinfo%7Cextlinks"
               + "&explaintext=1"
               + "&exsectionformat=plain"
               + "&inprop=url"
               + "&ellimit=10";
    }

    private static string BuildWikipediaIndividualUrl(
        int pageId)
    {
        return Api
               + "action=query"
               + "&pageids="
               + pageId.ToString(
                   CultureInfo.InvariantCulture)
               + "&prop=extracts%7Cinfo%7Cextlinks"
               + "&explaintext=1"
               + "&exsectionformat=plain"
               + "&inprop=url"
               + "&ellimit=10";
    }

    private static string BuildWikipediaArticleUrl(
        string title)
    {
        return
            "https://en.wikipedia.org/wiki/"
            + Uri.EscapeDataString(
                title.Replace(' ', '_'));
    }

    // ========================================================================
    // FALLBACK RESULT
    // ========================================================================

    private sealed record WikipediaReadResult(
        string Title,
        string Url,
        string Text,
        List<string> ExternalLinks,
        bool RateLimited)
    {
        public static WikipediaReadResult Empty =>
            new(
                "",
                "",
                "",
                [],
                false);

        public static WikipediaReadResult RateLimit =>
            new(
                "",
                "",
                "",
                [],
                true);
    }
}