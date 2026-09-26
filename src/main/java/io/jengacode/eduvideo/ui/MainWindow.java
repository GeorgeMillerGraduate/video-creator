/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.app.*;
import io.jengacode.eduvideo.editor.XmlEditor;
import io.jengacode.eduvideo.production.*;
import io.jengacode.eduvideo.project.*;
import io.jengacode.eduvideo.xml.*;
import java.nio.file.*;
import java.util.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/**
 * Workspace coordinator. Components own their UI; parsing, preview, speech and production share
 * engine services.
 */
public final class MainWindow implements AutoCloseable {
  private final ApplicationContext context;
  private final Stage stage;
  private final ProjectState state = new ProjectState();
  private final XmlEditor editor = new XmlEditor();
  private final LogPane log = new LogPane();
  private final ProductionPane monitor = new ProductionPane(log);
  private final PreviewPane preview =
      new PreviewPane(t -> state.time.set(t), e -> Ui.error("Preview failed", e));
  private final TimelinePane timeline = new TimelinePane();
  private final VoicePane voice;
  private final TextArea inspector = new TextArea();
  private final ListView<String> problems = new ListView<>();
  private final TreeView<Path> explorer = new TreeView<>();
  private final TabPane workspace = new TabPane(), bottom = new TabPane();
  private final Tab previewTab, editorTab, productionTab, queueTab, partsTab;
  private final ProductionPartsPane partsPane;
  private final javafx.beans.property.ObjectProperty<ProductionProject> production =
      new javafx.beans.property.SimpleObjectProperty<>();
  private final BorderPane root = new BorderPane();
  private final RenderQueue queue;
  private final ListView<RenderQueue.Job> queueList;
  private final ProjectLibrary library = new ProjectLibrary();
  private final PauseTransition debounce = new PauseTransition(Duration.millis(500));
  private long generation;
  private boolean loading;
  private final Label title = new Label("No project open");
  private final Button render;
  private final Menu recent = new Menu("Recent projects");
  private final SplitPane horizontal, vertical;
  private final List<Path> recentPaths = new ArrayList<>();

  /** Assembles resizable workspace regions and binds controls to observable document state. */
  public MainWindow(Stage stage, ApplicationContext context) {
    this.stage = stage;
    this.context = context;
    voice = new VoicePane(context);
    inspector.setEditable(false);
    inspector.setWrapText(true);
    inspector.setPrefWidth(240);
    explorer.setShowRoot(false);
    explorer.setCellFactory(
        v ->
            new TreeCell<>() {
              /** Displays the filesystem name while allowing JavaFX to recycle explorer cells. */
              protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                setText(empty || path == null ? null : path.getFileName().toString());
              }
            });
    explorer.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            var item = explorer.getSelectionModel().getSelectedItem();
            if (item != null && item.getValue().toString().endsWith(".xml")) open(item.getValue());
          }
        });
    partsPane =
        new ProductionPartsPane(
            context,
            xml -> editor.code.replaceText(xml),
            this::open,
            t -> {
              preview.seek(t);
              workspace.getSelectionModel().select(previewTabRef());
            },
            part ->
                ProjectDialogs.render(stage, part.source(), part.project(), context)
                    .ifPresent(this::enqueue),
            this::render,
            () -> save(false, () -> {}));
    partsTab = tab("Production parts", partsPane);
    previewTab = tab("Preview", preview);
    editorTab = tab("XML source", editor);
    workspace.getTabs().addAll(previewTab, editorTab, tab("Welcome", welcome()));
    workspace.getSelectionModel().select(2);
    workspace.getTabs().add(partsTab);
    var narrationScroll = new ScrollPane(voice);
    narrationScroll.setFitToWidth(true);
    var right =
        new TabPane(
            tab("Inspector", Ui.panel("SELECTION", inspector)), tab("Narration", narrationScroll));
    right.setMinWidth(220);
    right.setPrefWidth(280);
    workspace.setMinWidth(300);
    workspace.setMinHeight(160);
    SplitPane.setResizableWithParent(right, false);
    right.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    var left = Ui.panel("PROJECT LIBRARY", explorer);
    left.setMinWidth(160);
    left.setPrefWidth(210);
    left.getChildren()
        .add(
            new HBox(
                5,
                Ui.button("Folder", "Choose XML library folder", this::chooseLibrary),
                Ui.button("Refresh", "Rescan XML files", this::refresh)));
    horizontal = new SplitPane(left, workspace, right);
    horizontal.setDividerPositions(.16, .80);
    SplitPane.setResizableWithParent(left, false);
    try {
      String[] d =
          context.settings.get("dividers", ".17,.78").replace("[", "").replace("]", "").split(",");
      double a = Double.parseDouble(d[0].trim()), b = Double.parseDouble(d[1].trim());
      if (a > .05 && b > a && b < .95) horizontal.setDividerPositions(a, b);
    } catch (RuntimeException ignored) {
    }
    horizontal.setMinHeight(180);
    queue =
        new RenderQueue(
            monitor,
            j -> {
              monitor.reset();
              showProduction();
              bottom.getSelectionModel().select(productionTabIndex());
              state.status.set("Rendering " + j.name);
            },
            this::completed,
            e -> {
              state.status.set("Render failed");
              log.add("ERROR", e.getMessage());
              Ui.error("Render failed", e);
            });
    monitor.onCancel(queue::cancel);
    monitor.onProgress(text -> state.status.set(text));
    queueList = new ListView<>(queue.jobs);
    productionTab = tab("Production", monitor);
    queueTab = tab("Render queue", queuePanel());
    bottom.getTabs().addAll(tab("Log", log), tab("Problems", problems), productionTab, queueTab);
    bottom.setMinHeight(100);
    bottom.setPrefHeight(210);
    SplitPane.setResizableWithParent(bottom, false);
    var timelinePanel = new VBox(4, new Label("TIMELINE · CLICK TO SEEK"), timeline);
    timelinePanel.getStyleClass().add("timeline-panel");
    timelinePanel.setMinHeight(112);
    timelinePanel.setPrefHeight(112);
    var timelineScroll = new ScrollPane(timelinePanel);
    timelineScroll.setFitToWidth(true);
    timelineScroll.setMinHeight(118);
    timelineScroll.setPrefHeight(118);
    var middle = new SplitPane(horizontal, timelineScroll);
    middle.setOrientation(javafx.geometry.Orientation.VERTICAL);
    middle.setDividerPositions(.81);
    middle.setMinHeight(250);
    SplitPane.setResizableWithParent(timelineScroll, false);
    vertical = new SplitPane(middle, bottom);
    vertical.setOrientation(javafx.geometry.Orientation.VERTICAL);
    vertical.setDividerPositions(.77);
    try {
      double d = Double.parseDouble(context.settings.get("divider.bottom", ".77"));
      if (d > .3 && d < .92) vertical.setDividerPositions(d);
    } catch (NumberFormatException ignored) {
    }
    root.setCenter(vertical);
    render = Ui.button("RENDER VIDEO", "Configure and queue production · Ctrl+R", this::render);
    render.getStyleClass().add("primary");
    render.disableProperty().bind(state.project.isNull().and(production.isNull()));
    var brand = new Label("JENGA-CODE  /  EDUVIDEO STUDIO");
    brand.getStyleClass().add("brand");
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var header = new HBox(18, brand, title, spacer, render);
    header.getStyleClass().add("app-header");
    var toolbar =
        new ToolBar(
            Ui.button("New", "New XML project · Ctrl+N", this::create),
            Ui.button("Open", "Open XML · Ctrl+O", this::chooseOpen),
            Ui.button("Save", "Save XML · Ctrl+S", () -> save(false, () -> {})),
            new Separator(),
            Ui.button("Validate", "Validate current XML", this::validate),
            Ui.button("Render frame", "Save displayed preview as PNG", this::saveFrame),
            Ui.button(
                "Preferences",
                "Configure OpenAI narration and FFmpeg",
                () -> {
                  PreferencesDialog.show(context);
                  voice.refresh();
                }));
    root.setTop(new VBox(header, menus(), toolbar));
    var status = new Label();
    status.textProperty().bind(state.status);
    status.getStyleClass().add("status-bar");
    status.setMaxWidth(Double.MAX_VALUE);
    root.setBottom(status);
    state.path.addListener((o, a, b) -> updateTitle());
    state.dirty.addListener((o, a, b) -> updateTitle());
    editor
        .code
        .textProperty()
        .addListener(
            (o, a, b) -> {
              if (!loading) {
                state.dirty.set(true);
                state.project.set(null);
                production.set(null);
                partsPane.project(null);
                generation++;
                debounce.playFromStart();
              }
            });
    debounce.setOnFinished(e -> validate());
    problems.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2 && problems.getSelectionModel().getSelectedItem() != null) {
            var m =
                java.util.regex.Pattern.compile("[Ll]ine (\\d+)")
                    .matcher(problems.getSelectionModel().getSelectedItem());
            if (m.find()) {
              workspace.getSelectionModel().select(editorTab);
              editor.jump(Integer.parseInt(m.group(1)));
            }
          }
        });
    for (String path : context.settings.get("recent", "").split("\\n"))
      if (!path.isBlank()) recentPaths.add(Path.of(path));
    updateRecent();
    refresh();
    stage.setOnCloseRequest(
        e -> {
          e.consume();
          guard(
              () -> {
                if (queue.busy.get() && !Ui.confirm("Cancel production and exit?")) return;
                close();
                stage.hide();
                Platform.exit();
              });
        });
  }

  /** Expands the lower workspace so production controls and filmstrip remain visible. */
  private void showProduction() {
    // Selecting the monitor must preserve the user's chosen panel dimensions.
  }

  /** Locates the monitor in the fixed lower tab group. */
  private int productionTabIndex() {
    return 2;
  }

  /** Creates a persistent workspace tab whose close operation is controlled by the application. */
  private static Tab tab(String name, Node content) {
    var t = new Tab(name, content);
    t.setClosable(false);
    return t;
  }

  /** Returns the fully assembled scene root. */
  public Parent root() {
    return root;
  }

  /** Installs editing-aware keyboard shortcuts after a Scene is available. */
  public void shortcuts(Scene scene) {
    scene.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.isControlDown()) {
            Runnable action =
                switch (e.getCode()) {
                  case N -> this::create;
                  case O -> this::chooseOpen;
                  case S -> () -> save(e.isShiftDown(), () -> {});
                  case F ->
                      () -> {
                        workspace.getSelectionModel().select(editorTab);
                        editor.focusFind();
                      };
                  case R -> this::render;
                  default -> null;
                };
            if (action != null) {
              action.run();
              e.consume();
            }
          } else if (e.getCode() == KeyCode.F11) {
            preview.fullscreen();
            e.consume();
          } else if (!inText(scene.getFocusOwner())
              && workspace.getSelectionModel().getSelectedItem() == previewTab) {
            switch (e.getCode()) {
              case SPACE -> preview.toggle();
              case LEFT -> preview.step(-1);
              case RIGHT -> preview.step(1);
              default -> {
                return;
              }
            }
            e.consume();
          }
        });
  }

  /** Detects editing focus so playback shortcuts never consume typing or cursor navigation. */
  private boolean inText(Node node) {
    for (Node n = node; n != null; n = n.getParent())
      if (n instanceof TextInputControl || n instanceof org.fxmisc.richtext.GenericStyledArea)
        return true;
    return false;
  }

  /** Connects desktop menus to the same actions used by toolbar and keyboard controls. */
  private MenuBar menus() {
    var file = new Menu("File");
    file.getItems()
        .addAll(
            item("New project", this::create),
            item("New production…", this::createProduction),
            item("Open production…", this::chooseOpen),
            item("Save production", () -> save(false, () -> {})),
            item("Open XML", this::chooseOpen),
            recent,
            item("Save", () -> save(false, () -> {})),
            item("Save as", () -> save(true, () -> {})),
            item("Close project", () -> guard(this::clear)),
            item(
                "Exit",
                () -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST))));
    var edit = new Menu("Edit");
    edit.getItems()
        .addAll(
            item("Undo", editor.code::undo),
            item("Redo", editor.code::redo),
            item(
                "Find / replace",
                () -> {
                  workspace.getSelectionModel().select(editorTab);
                  editor.focusFind();
                }));
    var project = new Menu("Project");
    project
        .getItems()
        .addAll(
            item("Validate", this::validate),
            item("Refresh library", this::refresh),
            item("Duplicate XML", this::duplicate),
            item("Rename XML", this::rename),
            item("Delete XML…", this::delete),
            item(
                "Reveal project folder",
                () -> {
                  if (state.path.get() != null) reveal(state.path.get().getParent());
                }));
    var playback = new Menu("Preview");
    playback
        .getItems()
        .addAll(
            item("Play / pause", preview::toggle),
            item("Previous frame", () -> preview.step(-1)),
            item("Next frame", () -> preview.step(1)),
            item("Save frame PNG", this::saveFrame),
            item("Fullscreen", preview::fullscreen));
    var rendering = new Menu("Render");
    rendering
        .getItems()
        .addAll(
            item("Configure render", this::render),
            item("Show queue", () -> bottom.getSelectionModel().select(queueTab)),
            item("Start queue", queue::start),
            item("Cancel current", queue::cancel));
    var tools = new Menu("Tools");
    tools
        .getItems()
        .addAll(
            item(
                "Preferences",
                () -> {
                  PreferencesDialog.show(context);
                  voice.refresh();
                }),
            item("Refresh narration settings", voice::refresh),
            item(
                "Clear speech cache…",
                () -> {
                  if (Ui.confirm("Delete cached speech? Narration will be regenerated."))
                    context.work(
                        () ->
                            new io.jengacode.eduvideo.audio.SpeechCache(context.settings.cache())
                                .clear(),
                        n -> log.add("INFO", "Removed " + n + " cached clips"),
                        e -> Ui.error("Cannot clear cache", e));
                }));
    var help = new Menu("Help");
    help.getItems()
        .add(
            item(
                "About",
                () -> {
                  var a =
                      new Alert(
                          Alert.AlertType.INFORMATION,
                          "Jenga-Code EduVideo Studio 3.2\n"
                              + "XML animation · JavaFX production workspace\n"
                              + "OpenAI narration · Local FFmpeg\n"
                              + "Copyright © 2026 George Miller\n"
                              + "See README.md for installation and workflows.");
                  a.setHeaderText("EDUVIDEO STUDIO");
                  Ui.theme(a);
                  a.show();
                }));
    return new MenuBar(file, edit, project, playback, rendering, tools, help);
  }

  /** Wraps a shared action as a menu item. */
  private MenuItem item(String text, Runnable run) {
    var i = new MenuItem(text);
    i.setOnAction(e -> run.run());
    return i;
  }

  /** Builds the initial project creation and opening surface. */
  private Node welcome() {
    var title = new Label("EDUVIDEO\nSTUDIO");
    title.getStyleClass().add("welcome-title");
    var tagline =
        new Label(
            "Mathematics in motion.\nCreate, inspect and produce XML-driven educational videos.");
    tagline.getStyleClass().add("welcome-copy");
    var recentList = new ListView<Path>();
    recentList.getItems().setAll(recentPaths);
    recentList.setPrefHeight(120);
    recentList.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2 && recentList.getSelectionModel().getSelectedItem() != null)
            open(recentList.getSelectionModel().getSelectedItem());
        });
    var box =
        new VBox(
            22,
            new Label("JENGA-CODE / PRODUCTION ENVIRONMENT"),
            title,
            tagline,
            new HBox(
                10,
                Ui.button("New video project", "Create from a template", this::create),
                Ui.button("Open XML project", "Browse a lesson", this::chooseOpen)),
            new Label("Browse the project library on the left, or File > Recent projects."));
    box.getStyleClass().add("welcome");
    return box;
  }

  /** Reflects the active filename and unsaved marker in both the workspace and native title. */
  private void updateTitle() {
    String name =
        state.path.get() == null ? "No project open" : state.path.get().getFileName().toString();
    title.setText(name + (state.dirty.get() ? " *" : ""));
    stage.setTitle(title.getText() + " — Jenga-Code EduVideo Studio");
  }

  /** Requests an XML path; opening still passes through unsaved-change protection. */
  private void chooseOpen() {
    var c = new FileChooser();
    c.getExtensionFilters().add(new FileChooser.ExtensionFilter("EduVideo XML", "*.xml"));
    var f = c.showOpenDialog(stage);
    if (f != null) open(f.toPath());
  }

  /** Opens disk XML after offering to preserve unsaved changes. */
  public void open(Path path) {
    guard(() -> load(path));
  }

  /**
   * Reads a new document asynchronously and ignores responses superseded by later document work.
   */
  private void load(Path path) {
    long ticket = ++generation;
    debounce.stop();
    context.work(
        () -> Files.readString(path),
        xml -> {
          if (ticket != generation) return;
          loading = true;
          state.path.set(path.toAbsolutePath());
          editor.load(xml);
          loading = false;
          state.dirty.set(false);
          recentPaths.remove(path.toAbsolutePath());
          recentPaths.add(0, path.toAbsolutePath());
          if (recentPaths.size() > 10) recentPaths.remove(10);
          context.settings.set(
              "recent", String.join("\n", recentPaths.stream().map(Path::toString).toList()));
          updateRecent();
          workspace.getSelectionModel().select(previewTab);
          validate();
        },
        e -> Ui.error("Cannot open XML", e));
  }

  /** Rebuilds the recent-files menu from the bounded most-recent-first history. */
  private void updateRecent() {
    recent.getItems().clear();
    for (Path path : recentPaths)
      recent.getItems().add(item(path.getFileName().toString(), () -> open(path)));
  }

  /** Invalidates pending validation and releases the current document model and preview. */
  private void clear() {
    generation++;
    debounce.stop();
    loading = true;
    editor.load("");
    loading = false;
    state.path.set(null);
    state.project.set(null);
    production.set(null);
    partsPane.project(null);
    state.dirty.set(false);
    preview.project(null);
    timeline.project(null, t -> {}, s -> {});
    problems.getItems().clear();
    inspector.clear();
    workspace.getSelectionModel().select(2);
    state.status.set("Ready");
  }

  /** Offers Save, Discard and Cancel before a transition that could abandon edits. */
  private void guard(Runnable action) {
    if (!state.dirty.get()) {
      action.run();
      return;
    }
    var save = new ButtonType("Save", ButtonBar.ButtonData.YES);
    var discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
    var a =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "This project contains unsaved changes.",
            save,
            discard,
            ButtonType.CANCEL);
    a.setHeaderText("Save changes before continuing?");
    Ui.theme(a);
    var result = a.showAndWait().orElse(ButtonType.CANCEL);
    if (result == save) save(false, action);
    else if (result == discard) action.run();
  }

  /**
   * Writes an editor snapshot atomically off-thread and preserves the dirty flag if typing
   * continued.
   */
  private void save(boolean as, Runnable after) {
    Path path = state.path.get();
    if (as || path == null) {
      var c = new FileChooser();
      c.setInitialFileName(path == null ? "lesson.xml" : path.getFileName().toString());
      var f = c.showSaveDialog(stage);
      if (f == null) return;
      path = f.toPath();
    }
    Path originalPath = state.path.get();
    Path target = path;
    String xml = editor.code.getText();
    context.work(
        () -> {
          String saved = xml;
          if (originalPath != null
              && !target
                  .toAbsolutePath()
                  .normalize()
                  .equals(originalPath.toAbsolutePath().normalize())) {
            var parser = new ProductionParser();
            if (parser.isProduction(xml)) {
              var manifest = parser.parse(xml, originalPath);
              saved =
                  ProductionWriter.write(
                      target,
                      manifest.title(),
                      manifest.settings(),
                      manifest.output(),
                      manifest.parts(),
                      manifest.music());
            }
          }
          library.save(target, saved);
          return saved;
        },
        saved -> {
          if (!java.util.Objects.equals(originalPath, state.path.get())) return;
          if (!xml.equals(editor.code.getText())) {
            log.add(
                "INFO", "Saved snapshot to " + target + "; newer editor changes remain unsaved.");
            return;
          }
          state.path.set(target.toAbsolutePath());
          if (!saved.equals(xml)) {
            loading = true;
            editor.load(saved);
            loading = false;
          }
          state.dirty.set(false);
          after.run();
          validate();
          refresh();
        },
        e -> Ui.error("Cannot save XML", e));
  }

  /**
   * Debounces semantic validation on a worker; generation numbers prevent stale results replacing
   * newer edits.
   */
  private void validate() {
    if (state.path.get() == null) return;
    long ticket = ++generation;
    String xml = editor.code.getText();
    Path path = state.path.get();
    state.status.set("Validating XML…");
    context.work(
        () -> {
          var parser = new ProductionParser();
          return parser.isProduction(xml)
              ? parser.parse(xml, path)
              : new ProjectParser().parse(xml, path);
        },
        value -> {
          if (ticket != generation) return;
          if (value instanceof ProductionProject complete) {
            state.project.set(null);
            production.set(complete);
            partsPane.project(complete);
            problems.getItems().clear();
            editor.problem(0);
            preview.production(complete);
            timeline.production(complete, preview::seek, inspector::setText);
            inspector.setText(
                "COMPLETE PRODUCTION\n"
                    + complete.title()
                    + "\n"
                    + complete.parts().size()
                    + " parts\n"
                    + complete.duration()
                    + " seconds\n"
                    + complete.settings().width()
                    + " × "
                    + complete.settings().height()
                    + " · "
                    + complete.settings().fps()
                    + " FPS"
                    + "\nMusic regions: "
                    + complete.audioTimeline().tracks().stream().filter(t -> t.music()).count());
            state.status.set(
                "Valid production | "
                    + complete.parts().size()
                    + " parts | "
                    + ProductionPartsPane.time(complete.duration())
                    + " | OpenAI TTS");
            log.add("INFO", "Validated production " + path.getFileName());
            return;
          }
          var p = (io.jengacode.eduvideo.video.VideoProject) value;
          production.set(null);
          partsPane.project(null);
          state.project.set(p);
          problems.getItems().clear();
          editor.problem(0);
          preview.project(p);
          timeline.project(p, preview::seek, inspector::setText);
          inspector.setText(
              "PROJECT\n"
                  + path.getFileName()
                  + "\n\n"
                  + p.settings().width()
                  + " × "
                  + p.settings().height()
                  + "\n"
                  + p.settings().fps()
                  + " FPS\n"
                  + p.duration()
                  + " seconds\n"
                  + p.scenes().size()
                  + " scenes\n"
                  + p.audioTimeline().narration().size()
                  + " narration cues\n\n"
                  + "XML is the source of truth.\n"
                  + "Edit values in XML source.\n"
                  + "Click timeline blocks to inspect timings.");
          state.status.set(
              "Valid XML | "
                  + p.settings().width()
                  + "×"
                  + p.settings().height()
                  + " | "
                  + p.settings().fps()
                  + " FPS | "
                  + p.duration()
                  + " s | OpenAI TTS");
          log.add("INFO", "Validated " + path.getFileName());
        },
        e -> {
          if (ticket != generation) return;
          state.project.set(null);
          production.set(null);
          partsPane.project(null);
          problems.getItems().setAll("ERROR  " + e.getMessage());
          var line = java.util.regex.Pattern.compile("[Ll]ine (\\d+)").matcher(e.getMessage());
          if (line.find()) editor.problem(Integer.parseInt(line.group(1)));
          state.status.set("Invalid XML · double-click the problem to locate it");
        });
  }

  /**
   * Expands a resource template, validates it through ProjectParser, and creates a new XML file.
   */
  private void create() {
    guard(
        () ->
            ProjectDialogs.create(stage)
                .ifPresent(
                    request ->
                        context.work(
                            () -> {
                              String xml =
                                  new ProjectTemplates()
                                      .create(
                                          request.template(),
                                          request.title(),
                                          request.width(),
                                          request.height(),
                                          request.fps(),
                                          request.duration());
                              new ProjectParser().parse(xml, request.path());
                              Files.createDirectories(request.path().toAbsolutePath().getParent());
                              Files.writeString(request.path(), xml, StandardOpenOption.CREATE_NEW);
                              return request.path();
                            },
                            p -> {
                              load(p);
                              refresh();
                            },
                            e -> Ui.error("Cannot create project", e))));
  }

  /** Selects the folder searched for ordinary XML lesson files. */
  private void chooseLibrary() {
    var f = new DirectoryChooser().showDialog(stage);
    if (f != null) {
      context.settings.set("projects", f.getAbsolutePath());
      refresh();
    }
  }

  /** Scans project folders on a worker and replaces the explorer tree on JavaFX. */
  private void refresh() {
    Path folder = Path.of(context.settings.get("projects", "examples"));
    context.work(
        () -> library.discover(folder),
        paths -> {
          var tree = new TreeItem<Path>(folder);
          var groups = new LinkedHashMap<Path, TreeItem<Path>>();
          for (Path path : paths) {
            var group =
                groups.computeIfAbsent(
                    path.getParent(),
                    p -> {
                      var node = new TreeItem<Path>(p);
                      node.setExpanded(true);
                      tree.getChildren().add(node);
                      return node;
                    });
            group.getChildren().add(new TreeItem<>(path));
          }
          explorer.setRoot(tree);
        },
        e -> Ui.error("Cannot scan projects", e));
  }

  /** Saves the current XML under another filename while retaining the original file. */
  private void duplicate() {
    if (state.path.get() == null) return;
    save(true, () -> {});
  }

  /** Renames only the XML within its current folder so relative asset references remain valid. */
  private void rename() {
    if (state.path.get() == null) return;
    guard(
        () -> {
          var d = new TextInputDialog(state.path.get().getFileName().toString());
          d.setHeaderText("Rename XML file in this folder");
          d.showAndWait()
              .ifPresent(
                  name -> {
                    if (name.isBlank() || !Path.of(name).getFileName().toString().equals(name)) {
                      Ui.error(
                          "Invalid filename",
                          new IllegalArgumentException("Enter a filename without directories"));
                      return;
                    }
                    Path source = state.path.get(),
                        target =
                            source.resolveSibling(name.endsWith(".xml") ? name : name + ".xml");
                    context.work(
                        () -> Files.move(source, target),
                        p -> {
                          load(p);
                          refresh();
                        },
                        e -> Ui.error("Cannot rename XML", e));
                  });
        });
  }

  /** Confirms before removing the active XML; referenced media are preserved. */
  private void delete() {
    if (state.path.get() == null) return;
    Path path = state.path.get();
    if (Ui.confirm("Delete " + path.getFileName() + "? This deletes the XML file only."))
      context.work(
          () -> {
            Files.delete(path);
            return true;
          },
          v -> {
            clear();
            refresh();
          },
          e -> Ui.error("Cannot delete XML", e));
  }

  /** Captures the validated document and real delivery settings as a waiting queue item. */
  private void render() {
    if (production.get() != null) {
      ProjectDialogs.renderProduction(stage, production.get(), context).ifPresent(this::enqueue);
      return;
    }
    if (state.project.get() == null) return;
    ProjectDialogs.render(stage, state.path.get(), state.project.get(), context)
        .ifPresent(this::enqueue);
  }

  /** Adds a validated snapshot to the sequential queue and honors the dialog start choice. */
  private void enqueue(RenderQueue.Job job) {
    queue.jobs.add(job);
    bottom.getSelectionModel().select(queueTab);
    if (job.startImmediately) queue.start();
  }

  /** Defers access to the preview tab until constructor-created callbacks are invoked. */
  private Tab previewTabRef() {
    return previewTab;
  }

  /** Creates a manifest from existing independently valid XML lessons on a background worker. */
  private void createProduction() {
    guard(
        () -> {
          var choose = new FileChooser();
          choose.setTitle("Select XML parts in order (reorder later)");
          choose
              .getExtensionFilters()
              .add(new FileChooser.ExtensionFilter("EduVideo XML", "*.xml"));
          var files = choose.showOpenMultipleDialog(stage);
          if (files == null || files.isEmpty()) return;
          var save = new FileChooser();
          save.setTitle("New production manifest");
          save.setInitialFileName("complete-production.xml");
          var destination = save.showSaveDialog(stage);
          if (destination == null) return;
          context.work(
              () -> {
                var parts = new ArrayList<ProductionProject.Part>();
                double offset = 0;
                for (var file : files) {
                  var p = new ProjectParser().parse(file.toPath());
                  parts.add(new ProductionProject.Part(file.toPath(), file.getName(), p, offset));
                  offset += p.duration();
                }
                Path path = destination.toPath();
                String xml =
                    ProductionWriter.write(
                        path,
                        "Complete production",
                        parts.get(0).project().settings(),
                        path.toAbsolutePath().getParent().resolve("output/complete-production.mp4"),
                        parts,
                        List.of());
                new ProductionParser().parse(xml, path);
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, xml, StandardOpenOption.CREATE_NEW);
                return path;
              },
              p -> {
                load(p);
                workspace.getSelectionModel().select(partsTab);
                refresh();
              },
              e -> Ui.error("Cannot create production", e));
        });
  }

  /** Connects sequential queue operations without introducing another rendering implementation. */
  private Node queuePanel() {
    var box = new BorderPane(queueList);
    box.setBottom(
        new FlowPane(
            6,
            6,
            Ui.button("Start queue", "Render waiting jobs sequentially", queue::start),
            Ui.button("Cancel current", "Stop active production", queue::cancel),
            Ui.button(
                "Remove",
                "Remove selected waiting job",
                () -> {
                  var j = queueList.getSelectionModel().getSelectedItem();
                  if (j != null && !j.status.get().equals("Rendering")) queue.jobs.remove(j);
                }),
            Ui.button("Up", "Move waiting job up", () -> moveJob(-1)),
            Ui.button("Down", "Move waiting job down", () -> moveJob(1)),
            Ui.button(
                "Clear finished",
                "Remove terminal jobs",
                () ->
                    queue.jobs.removeIf(
                        j -> !Set.of("Rendering", "Waiting").contains(j.status.get())))));
    return box;
  }

  /** Reorders waiting jobs only; an active render cannot be moved. */
  private void moveJob(int direction) {
    int i = queueList.getSelectionModel().getSelectedIndex(), j = i + direction;
    if (i < 0 || j < 0 || j >= queue.jobs.size()) return;
    if (!queue.jobs.get(i).status.get().equals("Waiting")
        || !queue.jobs.get(j).status.get().equals("Waiting")) return;
    Collections.swap(queue.jobs, i, j);
    queueList.getSelectionModel().select(j);
  }

  /** Shows measured output size and exposes local playback, folder opening and path copying. */
  private void completed(RenderQueue.Job job) {
    state.status.set("Render complete: " + job.output);
    context.work(
        () -> Files.size(job.output),
        size -> {
          ButtonType play = new ButtonType("Play video"),
              folder = new ButtonType("Open folder"),
              copy = new ButtonType("Copy path");
          var a =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  String.format(
                      Locale.ROOT,
                      "%s\n%.2f MB · %.2f seconds\n%s",
                      job.name,
                      size / 1048576.0,
                      job.to - job.from,
                      job.output.toAbsolutePath()),
                  play,
                  folder,
                  copy,
                  ButtonType.CLOSE);
          a.setHeaderText("RENDER COMPLETE");
          Ui.theme(a);
          a.showAndWait()
              .ifPresent(
                  b -> {
                    if (b == play) reveal(job.output);
                    else if (b == folder) reveal(job.output.toAbsolutePath().getParent());
                    else if (b == copy) {
                      var c = new ClipboardContent();
                      c.putString(job.output.toAbsolutePath().toString());
                      Clipboard.getSystemClipboard().setContent(c);
                    }
                  });
        },
        e -> Ui.error("Cannot inspect output", e));
  }

  /** Delegates file/folder opening to the operating system away from the UI thread. */
  private void reveal(Path path) {
    context.work(
        () -> {
          if (!java.awt.Desktop.isDesktopSupported())
            throw new IllegalStateException("Desktop file opening is unavailable");
          java.awt.Desktop.getDesktop().open(path.toFile());
          return true;
        },
        v -> {},
        e -> Ui.error("Cannot open file or folder", e));
  }

  /** Exports the displayed preview raster on a worker after a destination is selected. */
  private void saveFrame() {
    var frame = preview.current();
    if (frame == null) return;
    var c = new FileChooser();
    c.setInitialFileName("preview-frame.png");
    var f = c.showSaveDialog(stage);
    if (f != null)
      context.work(
          () -> javax.imageio.ImageIO.write(frame, "png", f),
          v -> log.add("INFO", "Saved preview PNG " + f),
          e -> Ui.error("Cannot save frame", e));
  }

  /** Stops owned jobs, previews and audio before persisting non-secret window preferences. */
  public void close() {
    debounce.stop();
    generation++;
    queue.close();
    preview.close();
    monitor.close();
    voice.close();
    if (!stage.isMaximized()) {
      context.settings.set("window.width", Double.toString(stage.getWidth()));
      context.settings.set("window.height", Double.toString(stage.getHeight()));
    }
    context.settings.set("dividers", Arrays.toString(horizontal.getDividerPositions()));
    context.settings.set("divider.bottom", Double.toString(vertical.getDividerPositions()[0]));
    try {
      context.settings.save();
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    context.close();
  }
}
