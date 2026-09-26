/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import com.google.gson.*;
import io.jengacode.eduvideo.app.StudioSettings;
import io.jengacode.eduvideo.production.Cancellation;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * The sole production narration provider. Sends text to OpenAI's HTTPS Speech API and requests WAV.
 * Configuration is snapshotted per job; credentials never enter cache keys or diagnostics. No
 * automatic retries: a failed request must not silently create additional billable requests.
 */
public final class OpenAISpeechProvider implements SpeechProvider {
  private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/audio/speech");
  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final String key, model, defaultVoice;
  private final Cancellation cancellation;
  private final HttpClient client;
  private final URI endpoint;

  /** Captures the GUI configuration or OPENAI_API_KEY for one cancellable job. */
  public OpenAISpeechProvider(StudioSettings settings, Cancellation cancellation) {
    this(settings, cancellation, CLIENT, ENDPOINT);
  }

  /**
   * Injects the HTTP boundary for package-local tests without exposing endpoint redirection in the
   * GUI.
   */
  OpenAISpeechProvider(
      StudioSettings settings, Cancellation cancellation, HttpClient client, URI endpoint) {
    this.key = settings.apiKey();
    this.model = settings.speechModel();
    this.defaultVoice = settings.speechVoice();
    this.cancellation = cancellation;
    this.client = client;
    this.endpoint = endpoint;
  }

  /** Cache identity includes the model and WAV contract, never the API key. */
  public String id() {
    return "openai-speech/v1/" + model;
  }

  /** Returns official built-in voices for this model, independent of network or credentials. */
  public List<SpeechVoice> voices() {
    return OpenAIVoices.voices(model);
  }

  /** Resolves default XML voices and rejects obsolete local IDs or unsupported options clearly. */
  public VoiceSettings resolve(VoiceSettings requested) throws IOException {
    if (!OpenAIVoices.MODELS.contains(model))
      throw new IOException(
          "Invalid OpenAI speech model. Select a model in Preferences > Narration.");
    String name = requested.name().equals("default") ? defaultVoice : requested.name();
    if (voices().stream().noneMatch(v -> v.id().equals(name)))
      throw new IOException(
          "Invalid OpenAI voice for the selected model. Select a listed voice or remove the XML"
              + " voice override.");
    if (requested.ssml())
      throw new IOException(
          "OpenAI narration requires plain text; remove ssml from the narration cue.");
    if (requested.pitch() != 0)
      throw new IOException("Pitch is not supported by the Speech API; use pitch=0.");
    return new VoiceSettings(requested.language(), name, requested.rate(), 0, false);
  }

  /** Validates local settings without an HTTP request or charge. */
  public void preflight() throws IOException {
    resolve(VoiceSettings.defaults());
    if (key.isBlank())
      throw new IOException(
          "OpenAI API key is not configured. Open Preferences > Narration to configure it.");
    if (key.chars().anyMatch(c -> c < 33 || c > 126))
      throw new IOException(
          "OpenAI API key contains invalid characters. Paste the key again in Preferences >"
              + " Narration.");
  }

  /**
   * Synthesizes one cue; returned bytes have passed WAV validation and exact-length normalization.
   */
  public byte[] synthesize(String text, VoiceSettings requested) throws IOException {
    cancellation.check();
    preflight();
    VoiceSettings voice = resolve(requested);
    if (text == null || text.isBlank()) throw new IOException("Narration text is empty.");
    if (text.codePointCount(0, text.length()) > 4096)
      throw new IOException("Narration exceeds 4096 characters. Split it into shorter speak cues.");
    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.addProperty("voice", voice.name());
    body.addProperty("input", text);
    body.addProperty("response_format", "wav");
    body.addProperty("speed", voice.rate());
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
    HttpResponse<byte[]> response = exchange(request);
    if (response.body().length > 64 * 1024 * 1024)
      throw new IOException("OpenAI returned an unexpectedly large audio response.");
    return WavAudio.normalize(response.body()).bytes();
  }

  /** Checks authentication and selected-model access without generating billable audio. */
  public void testConnection() throws IOException {
    preflight();
    URI models = endpoint.resolve("/v1/models/" + model);
    exchange(
        HttpRequest.newBuilder(models)
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + key)
            .GET()
            .build());
  }

  /** Polls job cancellation and suppresses potentially credential-bearing transport diagnostics. */
  private HttpResponse<byte[]> exchange(HttpRequest request) throws IOException {
    CompletableFuture<HttpResponse<byte[]>> pending =
        client.sendAsync(request, ignored -> new BoundedBody());
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(125);
    try {
      HttpResponse<byte[]> response;
      while (true) {
        cancellation.check();
        if (System.nanoTime() > deadline)
          throw new IOException("OpenAI narration timed out. Check your connection and try again.");
        try {
          response = pending.get(100, TimeUnit.MILLISECONDS);
          break;
        } catch (TimeoutException waiting) {
          /* Poll cancellation while awaiting the complete audio. */
        }
      }
      cancellation.check();
      if (response.statusCode() != 200) throw httpFailure(response.statusCode(), response.body());

      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("OpenAI narration interrupted.");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof HttpTimeoutException)
        throw new IOException("OpenAI narration timed out. Check your connection and try again.");
      throw new IOException(
          "Could not connect to OpenAI. Check your internet connection, firewall and proxy"
              + " settings.");
    } finally {
      if (!pending.isDone()) pending.cancel(true);
    }
  }

  /**
   * Bounded response accumulator prevents a malformed response from exhausting the desktop heap.
   */
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private java.util.concurrent.Flow.Subscription subscription;

    /** Publishes only the complete response body. */
    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return body;
    }

    /** Requests chunks as they arrive from the Java HTTP client. */
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    /** Enforces a 64 MiB per-cue response bound before allocating another chunk. */
    public void onNext(List<java.nio.ByteBuffer> chunks) {
      for (var chunk : chunks) {
        if ((long) bytes.size() + chunk.remaining() > 64 * 1024 * 1024) {
          subscription.cancel();
          body.completeExceptionally(new IOException("Audio response exceeded 64 MiB."));
          return;
        }
        byte[] part = new byte[chunk.remaining()];
        chunk.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    /** Completes a failed transfer without exposing request details. */
    public void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    /** Returns the complete response once transport has finished. */
    public void onComplete() {
      body.complete(bytes.toByteArray());
    }
  }

  /** Converts HTTP failures to safe operational messages; never repeats arbitrary server text. */
  private static IOException httpFailure(int status, byte[] bytes) {
    String code = "", param = "";
    try {
      JsonObject e =
          JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
              .getAsJsonObject()
              .getAsJsonObject("error");
      if (e.has("code") && !e.get("code").isJsonNull()) code = e.get("code").getAsString();
      if (e.has("param") && !e.get("param").isJsonNull()) param = e.get("param").getAsString();
    } catch (RuntimeException ignored) {
      /* A non-JSON service error still has an HTTP status. */
    }
    String message;
    if (status == 401) message = "OpenAI rejected the API key. Check Preferences > Narration.";
    else if (code.equals("insufficient_quota") || code.equals("billing_hard_limit_reached"))
      message =
          "OpenAI API credit or quota is exhausted. Check your API billing and project limits.";
    else if (status == 429) message = "OpenAI rate limit reached. Wait before trying again.";
    else if (status == 403)
      message = "OpenAI denied access. Check the API key's project permissions and model access.";
    else if (param.equals("voice"))
      message = "OpenAI rejected the selected voice. Choose a voice supported by this model.";
    else if (param.equals("model") || code.equals("model_not_found"))
      message =
          "OpenAI rejected the model or your project lacks access. Check Preferences > Narration.";
    else if (status >= 500) message = "OpenAI is temporarily unavailable. Try again later.";
    else message = "OpenAI rejected the speech request. Check model, voice and narration text.";
    return new IOException(message + " (HTTP " + status + ")");
  }
}
