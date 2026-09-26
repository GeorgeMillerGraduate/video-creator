/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Hardened SAX loader with DTD/entity denial and retained line numbers.
 *
 * <p>Disallows DTDs, external general/parameter entities and XInclude. A SAX handler preserves
 * source line numbers so semantic validation can identify the originating element.
 */
final class LocatedXml {
  /**
   * Loads XML securely while retaining source lines and denying external entities.
   *
   * @param path input file path
   * @return loads XML securely while retaining source lines and denying external entities
   * @throws IOException if file access or encoding fails
   */
  static XmlElement read(Path path) throws IOException {
    return read(new InputSource(path.toUri().toString()));
  }

  /** Parses an in-memory source under the same hardened SAX policy. */
  static XmlElement read(InputSource source) throws IOException {
    try {
      var factory = SAXParserFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
      var handler = new Handler();
      factory.newSAXParser().parse(source, handler);
      return handler.root;
    } catch (SAXParseException e) {
      throw new ProjectFormatException("Line " + e.getLineNumber() + ": " + e.getMessage(), e);
    } catch (SAXException | ParserConfigurationException e) {
      throw new ProjectFormatException("Invalid XML: " + e.getMessage(), e);
    }
  }

  /**
   * Internal SAX stack builder. Character callbacks append to the current element and start
   * callbacks retain the locator line before pushing a new node.
   */
  private static final class Handler extends DefaultHandler {

    private Locator locator;

    private XmlElement root;

    private final Deque<XmlElement> stack = new ArrayDeque<>();

    /**
     * Retains the SAX source locator for subsequent element positions.
     *
     * @param l SAX source locator
     */
    public void setDocumentLocator(Locator l) {
      locator = l;
    }

    /**
     * Builds and pushes a located XML element with copied attributes.
     *
     * @param uri SAX namespace URI
     * @param local SAX local name
     * @param name qualified XML element name
     * @param attrs SAX attributes
     */
    public void startElement(String uri, String local, String name, Attributes attrs) {
      Map<String, String> map = new HashMap<>();
      for (int i = 0; i < attrs.getLength(); i++) map.put(attrs.getQName(i), attrs.getValue(i));
      var node = new XmlElement(name, locator.getLineNumber(), map);
      if (stack.isEmpty()) root = node;
      else stack.peek().children.add(node);
      stack.push(node);
    }

    /**
     * Appends a SAX character span to the active element text.
     *
     * @param chars SAX character buffer
     * @param start first valid offset in the SAX character buffer
     * @param length number of valid characters
     */
    public void characters(char[] chars, int start, int length) {
      if (!stack.isEmpty()) stack.peek().text.append(chars, start, length);
    }

    /**
     * Pops the completed XML element from the parse stack.
     *
     * @param uri SAX namespace URI
     * @param local SAX local name
     * @param name qualified XML element name
     */
    public void endElement(String uri, String local, String name) {
      stack.pop();
    }

    /**
     * Propagates a SAX validation failure without losing its location.
     *
     * @param e source parser or window event
     * @throws SAXException if parsing, rendering or output processing fails
     */
    public void error(SAXParseException e) throws SAXException {
      throw e;
    }

    /**
     * Propagates a fatal SAX parse error with its source location.
     *
     * @param e source parser or window event
     * @throws SAXException if parsing, rendering or output processing fails
     */
    public void fatalError(SAXParseException e) throws SAXException {
      throw e;
    }
  }
}
