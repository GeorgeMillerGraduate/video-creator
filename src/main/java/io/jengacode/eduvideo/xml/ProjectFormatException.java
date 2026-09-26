/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

/**
 * Human-readable source location for invalid lesson input.
 *
 * <p>An IllegalArgumentException carrying human-readable XML location and validation details.
 * Optional causes preserve parser/I/O diagnostics for callers and preview dialogs.
 */
public final class ProjectFormatException extends IllegalArgumentException {
  /**
   * Creates a configured ProjectFormatException instance.
   *
   * @param message human-readable failure explanation
   */
  public ProjectFormatException(String message) {
    super(message);
  }

  /**
   * Creates a configured ProjectFormatException instance.
   *
   * @param message human-readable failure explanation
   * @param cause underlying failure
   */
  public ProjectFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
