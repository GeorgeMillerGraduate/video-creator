/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Validates RIFF/WAVE and rewrites complete audio as signed 16-bit PCM with exact chunk sizes.
 * Duration uses actual data bytes, never an untrusted Java Sound frame-count/header estimate.
 * Streaming data lengths 0xffffffff and 0x7fffffff are resolved using received payload length.
 * Sample rate and channel count are retained; AudioMixer performs the single 48 kHz conversion.
 */
public final class WavAudio {
  /** Validated, canonical WAV and duration based on its real complete sample frames. */
  public record Clip(byte[] bytes, double seconds) {}

  /** Static audio boundary only. */
  private WavAudio() {}

  /** Validates container, chunk lengths and PCM/float format, then creates an ordinary PCM WAV. */
  public static Clip normalize(byte[] source) throws IOException {
    if (source == null || source.length < 44 || !tag(source, 0, "RIFF") || !tag(source, 8, "WAVE"))
      throw new IOException("Expected a nonempty WAV audio response, not JSON or another format.");
    ByteBuffer b = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
    long riff = Integer.toUnsignedLong(b.getInt(4));
    if (riff != 0xffffffffL && riff != 0x7fffffffL && riff + 8 != source.length)
      throw new IOException("WAV container length does not match the received audio.");
    int format = 0, channels = 0, rate = 0, bits = 0, alignment = 0;
    byte[] data = null;
    for (int offset = 12; offset < source.length; ) {
      if (source.length - offset < 8) throw new IOException("Truncated WAV chunk header.");
      long declared = Integer.toUnsignedLong(b.getInt(offset + 4));
      int begin = offset + 8;
      boolean streamed =
          tag(source, offset, "data") && (declared == 0xffffffffL || declared == 0x7fffffffL);
      long size = streamed ? source.length - begin : declared;
      if (size > source.length - begin) throw new IOException("Truncated WAV audio chunk.");
      int n = (int) size;
      if (tag(source, offset, "fmt ")) {
        if (n < 16 || format != 0) throw new IOException("Invalid WAV format chunk.");
        format = Short.toUnsignedInt(b.getShort(begin));
        channels = Short.toUnsignedInt(b.getShort(begin + 2));
        rate = b.getInt(begin + 4);
        alignment = Short.toUnsignedInt(b.getShort(begin + 12));
        bits = Short.toUnsignedInt(b.getShort(begin + 14));
        if (format == 0xfffe) {
          if (n < 40
              || Short.toUnsignedInt(b.getShort(begin + 16)) < 22
              || Short.toUnsignedInt(b.getShort(begin + 18)) != bits)
            throw new IOException("Unsupported extensible WAV format.");
          byte[] guidTail = {
            0, 0, 0, 0, 16, 0, (byte) 128, 0, 0, (byte) 170, 0, 56, (byte) 155, 113
          };
          if (!Arrays.equals(Arrays.copyOfRange(source, begin + 26, begin + 40), guidTail))
            throw new IOException("Unsupported WAV audio subformat.");
          format = Short.toUnsignedInt(b.getShort(begin + 24));
        }
      } else if (tag(source, offset, "data")) {
        if (data != null) throw new IOException("Multiple WAV data chunks are not supported.");
        data = Arrays.copyOfRange(source, begin, begin + n);
      }
      long next = (long) begin + n + (n & 1);
      if (next > source.length && !streamed) throw new IOException("Missing WAV chunk padding.");
      offset = (int) Math.min(next, source.length);
    }
    if (data == null
        || data.length == 0
        || channels < 1
        || channels > 2
        || rate < 8000
        || rate > 192000
        || !(format == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32)
            || format == 3 && (bits == 32 || bits == 64))
        || alignment != channels * (bits / 8)
        || data.length % alignment != 0)
      throw new IOException("Invalid or unsupported WAV sample format or incomplete audio frames.");
    int samples = data.length / (bits / 8);
    ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
    ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < samples; i++) {
      double value;
      if (format == 3) value = bits == 32 ? input.getFloat() : input.getDouble();
      else
        value =
            switch (bits) {
              case 8 -> (Byte.toUnsignedInt(input.get()) - 128) / 128.0;
              case 16 -> input.getShort() / 32768.0;
              case 24 -> {
                int v =
                    Byte.toUnsignedInt(input.get())
                        | (Byte.toUnsignedInt(input.get()) << 8)
                        | (input.get() << 16);
                yield v / 8388608.0;
              }
              default -> input.getInt() / 2147483648.0;
            };
      if (!Double.isFinite(value))
        throw new IOException("WAV contains invalid floating-point samples.");
      pcm.putShort((short) Math.max(-32768, Math.min(32767, Math.round(value * 32768))));
    }
    ByteBuffer out = ByteBuffer.allocate(44 + pcm.capacity()).order(ByteOrder.LITTLE_ENDIAN);
    out.put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + pcm.capacity())
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) channels)
        .putInt(rate)
        .putInt(rate * channels * 2)
        .putShort((short) (channels * 2))
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(pcm.capacity())
        .put(pcm.array());
    return new Clip(out.array(), data.length / (double) alignment / rate);
  }

  /** Compares a four-byte RIFF identifier without depending on the machine's default charset. */
  private static boolean tag(byte[] bytes, int at, String text) {
    if (at + 4 > bytes.length) return false;
    for (int i = 0; i < 4; i++) if (bytes[at + i] != text.charAt(i)) return false;
    return true;
  }
}
