package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.mission.window.LaunchWindow;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters;
import com.smousseur.orbitlab.ui.timeline.mission.TimeAxis;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.orekit.time.AbsoluteDate;

/**
 * The launch window timeline of the planning page, drawn at two stacked scales.
 *
 * <p><b>Why two scales and not one.</b> The measured Earth slot is 232 s wide and recurs every 86
 * 164 s — a ratio of one to 371. On the 752 px this wizard can give an axis, three days of span
 * make the slot 0.4 px: it is not small, it is invisible, and no tuning saves a single axis (spec
 * {@code docs/mission-window/02-timeline-wizard.md} §2). So the day scale draws an opportunity as
 * an <em>instant</em> — a marker, whose width nobody reads as a duration, so it lies about nothing
 * — and the true width appears only in the zoom pane, where it means what it says: the operational
 * margin. That pane's own scale is not fixed either, because the slot is not: {@link ZoomScale}
 * picks the rung that shows the selected slot without the captions colliding, and the note beside
 * the heading names it.
 *
 * <p><b>No cost curve.</b> A V-shaped trace in the zoom pane was considered and dropped: Lemur has
 * no line primitive and a custom mesh is out of proportion here. The three cost-bearing figures of
 * the readout carry the same information.
 *
 * <p><b>Rebuilt, not mutated.</b> {@code MissionPanelWidget.refresh()} is the house precedent, and
 * an axis whose marker count changes holds nothing worth preserving. The page polls this on every
 * frame, so {@link #render(PlanningState)} returns at once when the state has not changed; {@link
 * PlanningState}'s three cases are records and {@code PlanningModel} hands back the same instance
 * while its inputs stand still, so the common frame costs one comparison.
 *
 * <p>Fractional placement is impossible under a {@code BoxLayout}, so every element whose x is a
 * fraction of the track is attached raw to a fixed-size surface and positioned by its local
 * translation, on {@code ScrubberTrack}'s and {@code PhaseMarkers}' idiom: x grows rightward from
 * the surface's left edge, y grows <em>downward as negative</em> from its top edge.
 */
public final class LaunchWindowTimeline {

  /**
   * Track width, taken from the wizard's own full-width field so the two frames line up with the
   * fields of the main page rather than floating over them — and so widening the step moves the
   * axis with it instead of unaligning it silently.
   */
  static final float TRACK_W = StepParameters.FIELD_W;

  /** Height of the opportunities frame: room for an 18 px marker and its air. */
  private static final float AXIS_FRAME_H = 30f;

  /** Height of the zoom frame, tall enough for the open interval to read as a band. */
  private static final float ZOOM_FRAME_H = 46f;

  /** Height of a graduation strip, sized on {@code ibmPlexMono(10)}'s 15 px line box, trimmed. */
  private static final float GRADUATION_H = 13f;

  /** Height of the readout strip, on the same font. */
  private static final float READOUT_H = 14f;

  /** Height of a section-label row, sized on {@code ibmPlexMono(11)}. */
  private static final float SECTION_LABEL_H = 14f;

  /** Gap between a section label and the frame it names. */
  private static final float LABEL_FRAME_GAP = 6f;

  /** Gap between a frame and the graduations that read it. */
  private static final float FRAME_GRADUATION_GAP = 4f;

  /** Gap between the two sections, and before the readout. */
  private static final float SECTION_GAP = 14f;

  /** Thickness of a frame border and of the open interval's own edges. */
  private static final float EDGE = 1f;

  /** Width of the two rules that stand for an instant: the requested date, and the optimum. */
  private static final float RULE_W = 2f;

  /** Width of an opportunity marker. */
  private static final float MARKER_W = 8f;

  /** Height of an opportunity marker, leaving 6 px of frame above and below it. */
  private static final float MARKER_H = 18f;

  /**
   * Right-hand padding of the opportunities axis, as a fraction of the floor-to-last-closing
   * interval. A twelfth, so the last marker sits inside the frame instead of on its edge.
   */
  private static final double AXIS_TAIL_FRACTION = 1.0 / 12.0;

  /**
   * Upper bound on day graduations. The axis span comes from the planner, which derives it from the
   * problem's recurrence, so it is three days here and could be three months on another problem;
   * the stride keeps the strip readable either way instead of writing a terrestrial horizon down.
   */
  private static final int MAX_DAY_LABELS = 6;

  /**
   * Every caption below is sized in characters, because {@code ibmPlexMono} is monospaced and
   * advances 6 px at the size they are drawn at ({@code ibmplexmono-regular-10.fnt}). A caption
   * narrower than its text is clipped, not wrapped, so each carries a couple of characters of air.
   */
  static final float CAPTION_CHAR_W = 6f;

  /** The 19 characters of {@code yyyy-MM-dd HH:mm:ss}, plus air. */
  private static final float FLOOR_LABEL_W = 21 * CAPTION_CHAR_W;

  /** The 5 characters of {@code MM-dd}, plus air. */
  private static final float DAY_LABEL_W = 7 * CAPTION_CHAR_W;

  /**
   * The 10 characters of {@code span 99.9 d}, plus air — and the widest note the zoom heading can
   * produce is {@code +/- 30 min}, one character narrower still.
   */
  static final float SPAN_NOTE_W = 13 * CAPTION_CHAR_W;

  /** Gap kept clear between the floor caption and the first day graduation drawn after it. */
  private static final float GRADUATION_MIN_GAP = 10f;

  /** One readout cell per quarter of the track. */
  private static final float READOUT_CELL_W = TRACK_W / 4f;

  /** Width of a readout key: the 10 characters of {@code PLANE COST}, plus air. */
  private static final float READOUT_KEY_W = 13 * CAPTION_CHAR_W;

  /** Local z of the frame borders, above the frame's own background. */
  private static final float Z_EDGE = 1f;

  /** Local z of the content of a frame, above its borders. */
  private static final float Z_CONTENT = 2f;

  /**
   * Local z of what must never be hidden by what it sits on: the clickable markers, which may land
   * on the requested-date rule, and the accent rules of the zoom pane, which sit on its tint.
   */
  private static final float Z_OVERLAY = 3f;

  /** Tint of the open interval: the accent, laid flat enough to read the edges through it. */
  private static final ColorRGBA SLOT_FILL =
      new ColorRGBA(
          FormStyles.ACCENT_BRIGHT.r,
          FormStyles.ACCENT_BRIGHT.g,
          FormStyles.ACCENT_BRIGHT.b,
          0.18f);

  private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** The em dash and the ± sign are not in the bundled font pack, which stops at ASCII. */
  private static final String NO_VALUE = "--";

  private static final String IDLE_NOTE = "no plane is being waited for";

  private final Container root;
  private final Label spanNote;
  private final Label zoomNote;
  private final Container axisFrame;
  private final Container axisGraduations;
  private final Container zoomFrame;
  private final Container zoomGraduations;
  private final Container readout;

  /** Everything the last render attached, detached whole on the next one. */
  private final List<Spatial> painted = new ArrayList<>();

  private IntConsumer onSelected = index -> {};

  /** The state the frames currently show, so an unchanged frame rebuilds nothing. */
  private PlanningState rendered;

  public LaunchWindowTimeline() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    spanNote = dimNote();
    root.addChild(sectionHeader("OPPORTUNITIES", spanNote));

    root.addChild(UiKit.vSpacer(LABEL_FRAME_GAP));
    axisFrame = root.addChild(frame(AXIS_FRAME_H));
    root.addChild(UiKit.vSpacer(FRAME_GRADUATION_GAP));
    axisGraduations = root.addChild(surface(GRADUATION_H));

    root.addChild(UiKit.vSpacer(SECTION_GAP));

    zoomNote = dimNote();
    root.addChild(sectionHeader("SELECTED WINDOW", zoomNote));

    root.addChild(UiKit.vSpacer(LABEL_FRAME_GAP));
    zoomFrame = root.addChild(frame(ZOOM_FRAME_H));
    root.addChild(UiKit.vSpacer(FRAME_GRADUATION_GAP));
    zoomGraduations = root.addChild(surface(GRADUATION_H));

    root.addChild(UiKit.vSpacer(SECTION_GAP));
    readout = root.addChild(surface(READOUT_H));
  }

  public Container getNode() {
    return root;
  }

  /**
   * @param action what a click on the opportunity of that index does
   */
  public void setOnSelected(IntConsumer action) {
    this.onSelected = action != null ? action : index -> {};
  }

  /**
   * Draws the state, rebuilding the two frames from scratch.
   *
   * <p>The frames keep their size in all three states: the page fills in when a node is typed, it
   * does not reorganise itself (spec §6).
   *
   * @param state what to draw
   */
  public void render(PlanningState state) {
    Objects.requireNonNull(state, "state");
    if (state.equals(rendered)) {
      return;
    }
    rendered = state;
    clearPainted();

    switch (state) {
      case PlanningState.Idle ignored -> renderEmpty(IDLE_NOTE, FormStyles.TEXT_LO);
      case PlanningState.Unavailable unavailable ->
          renderEmpty(unavailable.reason(), FormStyles.TEXT_SECONDARY);
      case PlanningState.Windows windows -> renderWindows(windows);
    }
  }

  private void renderEmpty(String note, ColorRGBA color) {
    spanNote.setText(NO_VALUE);
    zoomNote.setText(NO_VALUE);
    attach(axisGraduations, caption(note, TRACK_W, color, HAlignment.Left), 0f, 0f, Z_CONTENT);
    paintReadout(NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
  }

  private void renderWindows(PlanningState.Windows state) {
    AbsoluteDate floor = state.floor();
    List<LaunchWindow> windows = state.windows();
    double span = axisSpanSeconds(floor, windows);

    spanNote.setText(
        String.format(Locale.ROOT, "span %.1f d", span / Duration.ofDays(1).toSeconds()));
    LaunchWindow selected = windows.get(state.selected());
    double halfSpan = ZoomScale.halfSpanSeconds(zoomSpanSeconds(selected));
    zoomNote.setText(ZoomScale.formatHalfSpan(halfSpan));

    paintAxis(floor, windows, state.selected(), span);
    paintDayGraduations(floor, span);
    paintZoom(selected, halfSpan);
    paintZoomGraduations(selected, halfSpan);
    paintSelectedReadout(state);
  }

  /**
   * The width the zoom pane has to show: twice the greater distance from the optimum to a bound.
   *
   * <p>The optimum is the cheapest candidate of the slot and not its midpoint: the solver bisects
   * outward from it, so the two halves are usually equal, but a fusion of two slots keeps one
   * optimum and a slot truncated by the end of the search range keeps none of its symmetry. Sizing
   * on the greater half is what keeps the far bound off the pane's edge. It cannot do more: a slot
   * leaning hard one way brings its near bound's caption toward the centre, and no pane centred on
   * the optimum can push it back out.
   */
  private static double zoomSpanSeconds(LaunchWindow window) {
    double before = window.date().durationFrom(window.opening());
    double after = window.closing().durationFrom(window.date());
    return 2.0 * Math.max(before, after);
  }

  /**
   * The interval the axis stretches over: the floor to the last slot's closing, plus its tail.
   *
   * <p>Guarded against a non-positive reach, which the planner cannot produce — its opportunities
   * are at or after the floor — but which would divide every marker position by zero if it ever
   * did.
   */
  private static double axisSpanSeconds(AbsoluteDate floor, List<LaunchWindow> windows) {
    double reach = windows.get(windows.size() - 1).closing().durationFrom(floor);
    return reach > 0.0 ? reach * (1.0 + AXIS_TAIL_FRACTION) : 1.0;
  }

  private void paintAxis(
      AbsoluteDate floor, List<LaunchWindow> windows, int selected, double span) {
    float inner = AXIS_FRAME_H - 2f * EDGE;
    attach(axisFrame, rule(RULE_W, inner, FormStyles.WARNING), EDGE, EDGE, Z_CONTENT);

    float markerTop = (AXIS_FRAME_H - MARKER_H) / 2f;
    for (int i = 0; i < windows.size(); i++) {
      float centre = trackX(windows.get(i).date().durationFrom(floor) / span);
      centre = clamp(centre, MARKER_W / 2f, TRACK_W - MARKER_W / 2f);
      ColorRGBA tint = i == selected ? FormStyles.ACCENT_BRIGHT : FormStyles.BORDER;
      Panel marker = rule(MARKER_W, MARKER_H, tint);
      attach(axisFrame, marker, centre - MARKER_W / 2f, markerTop, Z_OVERLAY);
      bindClick(marker, i);
    }
  }

  private void paintDayGraduations(AbsoluteDate floor, double span) {
    attach(
        axisGraduations,
        caption(
            TimeConverter.formatDate(floor), FLOOR_LABEL_W, FormStyles.WARNING, HAlignment.Left),
        0f,
        0f,
        Z_CONTENT);

    double days = span / Duration.ofDays(1).toSeconds();
    int stride = Math.max(1, (int) Math.ceil(days / MAX_DAY_LABELS));
    LocalDateTime boundary =
        TimeConverter.toUtcLocalDateTime(floor).toLocalDate().plusDays(1).atStartOfDay();
    while (true) {
      double offset = TimeConverter.fromUtcLocalDateTime(boundary).durationFrom(floor);
      if (offset >= span) {
        return;
      }
      float left = trackX(offset / span) - DAY_LABEL_W / 2f;
      if (left >= FLOOR_LABEL_W + GRADUATION_MIN_GAP) {
        attach(
            axisGraduations,
            caption(
                boundary.format(DAY_FORMAT), DAY_LABEL_W, FormStyles.TEXT_LO, HAlignment.Center),
            Math.min(left, TRACK_W - DAY_LABEL_W),
            0f,
            Z_CONTENT);
      }
      boundary = boundary.plusDays(stride);
    }
  }

  private void paintZoom(LaunchWindow window, double halfSpan) {
    float inner = ZOOM_FRAME_H - 2f * EDGE;
    float openX = trackX(zoomFraction(window, window.opening(), halfSpan));
    float closeX = trackX(zoomFraction(window, window.closing(), halfSpan));
    float width = Math.max(EDGE, closeX - openX);

    attach(zoomFrame, rule(width, inner, SLOT_FILL), openX, EDGE, Z_CONTENT);
    attach(zoomFrame, rule(EDGE, inner, FormStyles.ACCENT_BRIGHT), openX, EDGE, Z_OVERLAY);
    attach(
        zoomFrame,
        rule(EDGE, inner, FormStyles.ACCENT_BRIGHT),
        openX + width - EDGE,
        EDGE,
        Z_OVERLAY);
    attach(
        zoomFrame,
        rule(RULE_W, inner, FormStyles.ACCENT_BRIGHT),
        trackX(0.5) - RULE_W / 2f,
        EDGE,
        Z_OVERLAY);
  }

  private void paintZoomGraduations(LaunchWindow window, double halfSpan) {
    AbsoluteDate optimum = window.date();
    ZoomScale.ZoomCaptions captions =
        ZoomScale.captions(
            window.opening().durationFrom(optimum),
            window.closing().durationFrom(optimum),
            halfSpan);

    attachCaption(
        captions.paneStart(),
        time(optimum.shiftedBy(-halfSpan)),
        FormStyles.TEXT_LO,
        HAlignment.Left);
    attachCaption(
        captions.paneEnd(),
        time(optimum.shiftedBy(halfSpan)),
        FormStyles.TEXT_LO,
        HAlignment.Right);

    attachCaption(
        captions.opens(),
        "opens " + time(window.opening()),
        FormStyles.ACCENT_BRIGHT,
        HAlignment.Center);
    attachCaption(captions.optimum(), time(optimum), FormStyles.ACCENT_BRIGHT, HAlignment.Center);
    attachCaption(
        captions.closes(),
        "closes " + time(window.closing()),
        FormStyles.ACCENT_BRIGHT,
        HAlignment.Center);
  }

  private void attachCaption(
      ZoomScale.CaptionSpan span, String text, ColorRGBA color, HAlignment alignment) {
    attach(
        zoomGraduations, caption(text, span.width(), color, alignment), span.left(), 0f, Z_CONTENT);
  }

  private void paintSelectedReadout(PlanningState.Windows state) {
    LaunchWindow window = state.current();
    paintReadout(
        TimeAxis.formatGap(window.closing().durationFrom(window.opening())),
        String.format(Locale.ROOT, "%.1f m/s", window.best().deltaV()),
        TimeAxis.formatGap(window.date().durationFrom(state.floor())),
        recurrence(state.windows()));
  }

  /**
   * The gap between the first two opportunities, which is the recurrence as this search measured it
   * rather than a period written down here — the horizon belongs to the problem, not to the widget
   * (spec §2). A lone opportunity measures nothing.
   */
  private static String recurrence(List<LaunchWindow> windows) {
    if (windows.size() < 2) {
      return NO_VALUE;
    }
    return TimeAxis.formatGap(windows.get(1).date().durationFrom(windows.get(0).date()));
  }

  private void paintReadout(String slot, String cost, String delay, String recurrence) {
    paintReadoutCell(0, "SLOT", slot);
    paintReadoutCell(1, "PLANE COST", cost);
    paintReadoutCell(2, "DELAY", delay);
    paintReadoutCell(3, "RECURS", recurrence);
  }

  private void paintReadoutCell(int column, String key, String value) {
    float left = column * READOUT_CELL_W;
    attach(
        readout,
        caption(key, READOUT_KEY_W, FormStyles.TEXT_LO, HAlignment.Left),
        left,
        0f,
        Z_CONTENT);
    attach(
        readout,
        caption(value, READOUT_CELL_W - READOUT_KEY_W, FormStyles.TEXT_PRIMARY, HAlignment.Left),
        left + READOUT_KEY_W,
        0f,
        Z_CONTENT);
  }

  private void bindClick(Spatial marker, int index) {
    MouseEventControl.addListenersToSpatial(
        marker,
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent event, Spatial target, Spatial capture) {
            onSelected.accept(index);
            event.setConsumed();
          }
        });
  }

  private void clearPainted() {
    for (Spatial spatial : painted) {
      spatial.removeFromParent();
    }
    painted.clear();
  }

  /**
   * Attaches an element at a place a layout cannot express, and records it for the next rebuild.
   *
   * @param host the fixed-size surface the element belongs to
   * @param element the element to place
   * @param x distance from the surface's left edge
   * @param top distance from the surface's top edge, downward
   * @param z local depth
   */
  private void attach(Container host, Spatial element, float x, float top, float z) {
    element.setLocalTranslation(x, -top, z);
    host.attachChild(element);
    painted.add(element);
  }

  private static float trackX(double fraction) {
    return TRACK_W * (float) clamp01(fraction);
  }

  private static double zoomFraction(LaunchWindow window, AbsoluteDate date, double halfSpan) {
    return ZoomScale.fraction(date.durationFrom(window.date()), halfSpan);
  }

  private static String time(AbsoluteDate date) {
    return TimeConverter.toUtcLocalDateTime(date).format(TIME_FORMAT);
  }

  /** A fixed-size transparent surface whose children are placed by local translation. */
  private static Container surface(float height) {
    Container container = new Container(new BoxLayout(Axis.Y, FillMode.None));
    container.setBackground(null);
    container.setPreferredSize(new Vector3f(TRACK_W, height, 0));
    return container;
  }

  /** A surface with the header tint and a one-pixel border, on the wizard's framed-area look. */
  private static Container frame(float height) {
    Container container = surface(height);
    container.setBackground(FormStyles.headerBg());
    edge(container, TRACK_W, EDGE, 0f, 0f);
    edge(container, TRACK_W, EDGE, 0f, height - EDGE);
    edge(container, EDGE, height, 0f, 0f);
    edge(container, EDGE, height, TRACK_W - EDGE, 0f);
    return container;
  }

  private static void edge(Container host, float width, float height, float x, float top) {
    Panel border = rule(width, height, FormStyles.BORDER);
    border.setLocalTranslation(x, -top, Z_EDGE);
    host.attachChild(border);
  }

  private static Panel rule(float width, float height, ColorRGBA color) {
    Panel panel = new Panel(width, height, FormStyles.STYLE);
    panel.setBackground(new QuadBackgroundComponent(color));
    panel.setPreferredSize(new Vector3f(width, height, 0));
    panel.setSize(panel.getPreferredSize());
    return panel;
  }

  private static Label caption(String text, float width, ColorRGBA color, HAlignment alignment) {
    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.ibmPlexMono(10));
    label.setColor(color);
    label.setBackground(null);
    label.setTextHAlignment(alignment);
    label.setTextVAlignment(VAlignment.Center);
    label.setPreferredSize(new Vector3f(width, GRADUATION_H, 0));
    label.setSize(label.getPreferredSize());
    return label;
  }

  /** A section label with its note pushed against the track's right edge. */
  private static Container sectionHeader(String text, Label note) {
    Container header = new Container(new BoxLayout(Axis.X, FillMode.None));
    header.setBackground(null);

    Label label = new Label(text, FormStyles.STYLE);
    label.setFont(UiKit.ibmPlexMono(11));
    label.setColor(FormStyles.TEXT_SECONDARY);
    // Read after the font is set: the preferred width is measured from the glyphs, so the spacer
    // that follows can only be sized once the label knows how wide it is.
    float labelWidth = label.getPreferredSize().x;
    label.setPreferredSize(new Vector3f(labelWidth, SECTION_LABEL_H, 0));

    header.addChild(label);
    header.addChild(UiKit.hSpacer(Math.max(0f, TRACK_W - labelWidth - SPAN_NOTE_W)));
    header.addChild(note);
    return header;
  }

  private static Label dimNote() {
    Label label = new Label("", FormStyles.STYLE);
    label.setFont(UiKit.ibmPlexMono(10));
    label.setColor(FormStyles.TEXT_LO);
    label.setTextHAlignment(HAlignment.Right);
    label.setTextVAlignment(VAlignment.Center);
    label.setPreferredSize(new Vector3f(SPAN_NOTE_W, SECTION_LABEL_H, 0));
    return label;
  }

  private static float clamp(float value, float low, float high) {
    return Math.max(low, Math.min(high, value));
  }

  private static double clamp01(double value) {
    if (value < 0.0 || Double.isNaN(value)) {
      return 0.0;
    }
    return Math.min(value, 1.0);
  }
}
