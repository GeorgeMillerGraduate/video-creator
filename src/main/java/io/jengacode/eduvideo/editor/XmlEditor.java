/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.editor;

import java.util.*;
import java.util.regex.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.*;
import org.fxmisc.richtext.model.*;

/**
 * EduVideo XML editor with undo history, line numbers, lexical highlighting and literal
 * find/replace.
 */
public final class XmlEditor extends BorderPane {
  public final CodeArea code = new CodeArea();
  private final TextField find = new TextField(), replace = new TextField();
  private static final Pattern XML =
      Pattern.compile(
          "(?<COMMENT><!--[\\s\\S]*?-->)|(?<TAG></?[\\w-]+|/?>)|(?<STRING>\"[^\"]*\"|'[^']*')|(?<ATTRIBUTE>[\\w-]+(?=\\s*=))");

  /** Creates editor controls and highlights complete tokens even while the XML is incomplete. */
  public XmlEditor() {
    setMinSize(0, 0);
    find.setMinWidth(60);
    replace.setMinWidth(60);
    HBox.setHgrow(find, Priority.ALWAYS);
    HBox.setHgrow(replace, Priority.ALWAYS);
    code.setParagraphGraphicFactory(LineNumberFactory.get(code));
    code.setStyle("-fx-font-family: 'DejaVu Sans Mono';");
    setCenter(new VirtualizedScrollPane<>(code));
    find.setPromptText("Find text");
    replace.setPromptText("Replace with");
    var bar =
        new HBox(
            6,
            find,
            replace,
            io.jengacode.eduvideo.ui.Ui.button("Find", "Find next occurrence", this::findNext),
            io.jengacode.eduvideo.ui.Ui.button(
                "Replace",
                "Replace selection",
                () -> {
                  if (code.getSelectedText().equals(find.getText()))
                    code.replaceSelection(replace.getText());
                  findNext();
                }),
            io.jengacode.eduvideo.ui.Ui.button(
                "All",
                "Replace all literal matches",
                () -> {
                  if (!find.getText().isEmpty())
                    code.replaceText(code.getText().replace(find.getText(), replace.getText()));
                }));
    bar.getStyleClass().add("editor-search");
    setBottom(bar);
    code.textProperty().addListener((o, a, b) -> highlight());
  }

  /** Applies lexical XML styling without requiring a syntactically complete document. */
  private void highlight() {
    var m = XML.matcher(code.getText());
    var spans = new StyleSpansBuilder<Collection<String>>();
    int last = 0;
    while (m.find()) {
      spans.add(List.of(), m.start() - last);
      String kind =
          m.group("COMMENT") != null
              ? "xml-comment"
              : m.group("TAG") != null
                  ? "xml-tag"
                  : m.group("STRING") != null ? "xml-string" : "xml-attribute";
      spans.add(List.of(kind), m.end() - m.start());
      last = m.end();
    }
    spans.add(List.of(), code.getLength() - last);
    code.setStyleSpans(0, spans.create());
  }

  /** Loads a document and starts a fresh undo history. */
  public void load(String xml) {
    code.replaceText(xml);
    code.getUndoManager().forgetHistory();
    code.moveTo(0);
  }

  /** Focuses the search field without stealing the editor's selection. */
  public void focusFind() {
    find.requestFocus();
  }

  /** Selects the next literal match, wrapping once. */
  public void findNext() {
    String needle = find.getText();
    if (needle.isEmpty()) return;
    int i = code.getText().indexOf(needle, code.getSelection().getEnd());
    if (i < 0) i = code.getText().indexOf(needle);
    if (i >= 0) {
      code.selectRange(i, i + needle.length());
      code.requestFollowCaret();
    }
  }

  /** Navigates to a one-based parser error line, clamped to current text. */
  public void jump(int line) {
    code.moveTo(Math.max(0, Math.min(code.getParagraphs().size() - 1, line - 1)), 0);
    code.requestFollowCaret();
    code.requestFocus();
  }

  /** Marks a one-based parser error line without interfering with XML token highlighting. */
  public void problem(int line) {
    for (int i = 0; i < code.getParagraphs().size(); i++)
      code.setParagraphStyle(i, java.util.List.of());
    if (line > 0 && line <= code.getParagraphs().size())
      code.setParagraphStyle(line - 1, java.util.List.of("problem-line"));
  }
}
