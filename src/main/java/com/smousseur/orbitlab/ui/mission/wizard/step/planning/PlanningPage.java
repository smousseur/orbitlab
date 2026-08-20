package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;
import static com.smousseur.orbitlab.ui.mission.wizard.step.StepParameters.*;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.VAlignment;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.smousseur.orbitlab.app.converters.TimeConverter;
import com.smousseur.orbitlab.simulation.mission.window.problem.EarthLaunchWindowRequest;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.orekit.time.AbsoluteDate;

/**
 * The parameters step's second page: what decides <em>when</em> the mission leaves.
 *
 * <p>It owns the target node, which used to sit on the inclination row, and it is the only producer
 * of {@link FormField#TARGET_RAAN} — {@code StepParameters} merges what this page publishes exactly
 * as it merges the dynamic parameters' own map, so the wizard's duplicate-key guard stays satisfied.
 *
 * <p>The page exists because the parameters step has no vertical room left: its root is pinned to
 * {@link FormStyles#CONTENT_HEIGHT} and nothing in this wizard clips, so an overflow lands on the
 * footer. Spec {@code docs/mission-window/02-timeline-wizard.md} §1.
 */
public final class PlanningPage {

  private static final float RAAN_FIELD_W = 110f;
  private static final float BACK_BTN_W = 90f;
  private static final float BACK_BTN_H = 22f;

  /**
   * Room for the 19 characters of {@code yyyy-MM-dd HH:mm:ss} at {@code ibmPlexMono(13)}, and air.
   */
  private static final float DATE_ECHO_W = 240f;

  private static final String RAAN_HELPER = "empty - no plane is waited for";
  private static final String RAAN_FORMAT_HELPER = "RAAN - expected degrees";

  private static final String DATE_PLANNED_HELPER = "UTC - read as a floor by the planner";
  private static final String DATE_TYPED_HELPER = "UTC - typed on the fields page";

  /** Shown when the floor does not parse; the axis says why on its own line. */
  private static final String NO_DATE = "--";

  private final Container root;
  private final TextField raanField;
  private final Label raanHelper;
  private final LaunchWindowTimeline timeline;
  private final TextField dateEcho;
  private final Label dateEchoHelper;

  private final PlanningModel model = new PlanningModel();

  /** Entry that was refused, kept so the error state clears as soon as it is edited. */
  private String rejectedRaan;

  private Runnable onBack = () -> {};
  private Consumer<AbsoluteDate> onDateChosen = date -> {};

  public PlanningPage() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0)));
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    root.addChild(buildHeader());
    root.addChild(UiKit.vSpacer(6));

    Label subtitle = root.addChild(new Label("// launch window", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    root.addChild(
        fieldLabelRow("TARGET NODE (RAAN)", "lbl-globe", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));

    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    raanField = newInputField("", RAAN_FIELD_W, FIELD_H);
    row.addChild(raanField);
    row.addChild(UiKit.hSpacer(8f));

    Label unit = new Label("deg", FormStyles.STYLE);
    unit.setFont(UiKit.ibmPlexMono(11));
    unit.setColor(FormStyles.TEXT_LO);
    row.addChild(unit);
    root.addChild(row);

    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    raanHelper = new Label(RAAN_HELPER, FormStyles.STYLE);
    raanHelper.setFont(UiKit.ibmPlexMono(11));
    raanHelper.setColor(FormStyles.TEXT_LO);
    root.addChild(raanHelper);

    root.addChild(UiKit.vSpacer(ROW_GAP));
    timeline = new LaunchWindowTimeline();
    timeline.setOnSelected(this::onWindowSelected);
    root.addChild(timeline.getNode());

    root.addChild(UiKit.vSpacer(ROW_GAP));
    root.addChild(fieldLabelRow("LAUNCH DATE", "lbl-clock", LABEL_ICON_SIZE, LABEL_FIELD_GAP));
    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    dateEcho = newInputField("", DATE_ECHO_W, FIELD_H);
    UiKit.makeReadOnly(dateEcho);
    root.addChild(dateEcho);

    root.addChild(UiKit.vSpacer(LABEL_FIELD_GAP));
    dateEchoHelper = new Label(DATE_TYPED_HELPER, FormStyles.STYLE);
    dateEchoHelper.setFont(UiKit.ibmPlexMono(11));
    dateEchoHelper.setColor(FormStyles.TEXT_LO);
    root.addChild(dateEchoHelper);
  }

  /**
   * Points the zoom pane at the opportunity that was clicked, then hands its optimal instant to
   * whoever owns the launch date.
   *
   * <p><b>The axis re-anchors on the next frame, and that is the intended image.</b> The instant
   * just written becomes the floor, so the chosen opportunity moves to the head of the axis, as
   * many later ones come into view as were skipped, and the floor's rule ends up under the selected
   * marker — which is exactly what has just happened. Remembering the original date to hold the
   * axis still would be the second source of truth about "when" that spec {@code
   * docs/mission-window/02-timeline-wizard.md} §4 exists to refuse.
   *
   * @param index the opportunity clicked on the axis
   */
  private void onWindowSelected(int index) {
    model.select(index);
    timeline.render(model.state());
    if (model.state() instanceof PlanningState.Windows windows) {
      onDateChosen.accept(windows.current().date());
    }
  }

  /** The back band, on {@code MissionDetailView}'s recipe: accent text, no chrome, no insets. */
  private Container buildHeader() {
    Container header = new Container(new BoxLayout(Axis.X, FillMode.None));
    header.setBackground(null);

    Button back = new Button("< BACK", FormStyles.STYLE);
    back.setFont(UiKit.ibmPlexMono(11));
    back.setColor(FormStyles.ACCENT_BRIGHT);
    back.setBackground(null);
    back.setInsetsComponent(new InsetsComponent(new Insets3f(0, 0, 0, 0)));
    back.setTextVAlignment(VAlignment.Center);
    back.setPreferredSize(new Vector3f(BACK_BTN_W, BACK_BTN_H, 0));
    back.addClickCommands(source -> onBack.run());
    header.addChild(back);

    Label title = new Label("PLANNING", FormStyles.STYLE);
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);
    // Read after the font is set: the preferred width is measured from the glyphs. Grown to the
    // button's height so the two sit on one baseline, as MissionDetailView's header does.
    title.setPreferredSize(new Vector3f(title.getPreferredSize().x, BACK_BTN_H, 0));
    title.setTextVAlignment(VAlignment.Center);
    header.addChild(title);

    return header;
  }

  public Container getNode() {
    return root;
  }

  public void setOnBack(Runnable action) {
    this.onBack = action != null ? action : () -> {};
  }

  /**
   * What a click on an opportunity writes. The page holds no date of its own: the launch date field
   * on the main page is the single channel between the wizard and the planner (spec {@code
   * docs/mission-window/02-timeline-wizard.md} §4).
   *
   * @param action the sink for the chosen instant
   */
  public void setOnDateChosen(Consumer<AbsoluteDate> action) {
    this.onDateChosen = action != null ? action : date -> {};
  }

  /**
   * Clears the refusal as soon as the node is edited, so the red does not outlive the mistake — the
   * same contract every other refusable field of this wizard holds, {@code
   * StepParameters.rejectedLaunchDate} being the reference.
   *
   * @param tpf the frame time, unused: nothing on this page animates
   */
  public void update(float tpf) {
    if (rejectedRaan != null && !rejectedRaan.equals(raanField.getText())) {
      clearRejection();
    }
  }

  /**
   * Recomputes the timeline, at most once per change of inputs.
   *
   * <p>The page supplies the third argument itself: it owns the node field, so it is the only one
   * that knows whether a node was asked for at all — which is what separates "no plane is waited
   * for", the quiet common case, from "a plane was asked for and could not be served".
   *
   * @param request the window inputs, or null when the form cannot describe them
   * @param floor the launch date read as a floor, or null when the field does not parse
   */
  public void refresh(EarthLaunchWindowRequest request, AbsoluteDate floor) {
    model.refresh(request, floor, parsedRaanDeg().isPresent());
    timeline.render(model.state());
    showFloor(floor);
  }

  /**
   * Echoes the launch date the step is working from, and says which of the two things it is: the
   * floor a window is being searched from, or the instant the mission simply leaves at.
   *
   * <p><b>Read-only on purpose</b> ({@link UiKit#makeReadOnly}, the recipe the derived inclination
   * already uses). The floor is edited on the fields page: changing it is changing the question
   * asked, while this page only answers it. Made editable here the field would have two owners, and
   * the page that recomputes from it on every frame would be fighting the page that holds it (spec
   * {@code docs/mission-window/02-timeline-wizard.md} §3).
   *
   * @param floor the launch date read as a floor, or null when the field does not parse
   */
  private void showFloor(AbsoluteDate floor) {
    String text = floor != null ? TimeConverter.formatDate(floor) : NO_DATE;
    if (!text.equals(dateEcho.getText())) {
      dateEcho.setText(text);
    }
    // Windows, and not merely a node being typed: until opportunities are actually found, nothing
    // is moving this date and calling it a floor would promise a planning that is not happening.
    boolean planned = model.state() instanceof PlanningState.Windows;
    String helper = planned ? DATE_PLANNED_HELPER : DATE_TYPED_HELPER;
    if (!helper.equals(dateEchoHelper.getText())) {
      dateEchoHelper.setText(helper);
    }
  }

  /**
   * Publishes the node, and only when it reads as a number.
   *
   * <p>Absent when blank, and that absence is the mission saying it waits for no plane. Publishing a
   * default here would make every mission sit through a launch window it never asked for.
   *
   * <p><b>Not scoped to a profile.</b> This page is shared by every card, so a GEO submit can carry
   * the key; it stays inert only because {@code MissionFactory.specFromWizardValues} reads it in its
   * {@code case LEO} branch alone and {@code MissionSpec.Geo} has no node component. That scoping is
   * the factory's, not this page's — a node field added elsewhere would not inherit it.
   *
   * @return the values this page owns
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    parsedRaanDeg().ifPresent(raan -> values.put(FormField.TARGET_RAAN.key(), raan));
    return values;
  }

  /**
   * Restores the node. The key's absence means the mission waits for no plane, so the field goes
   * back to blank rather than keeping whatever the previous edit showed.
   *
   * @param values the raw wizard values
   */
  public void applyValues(Map<String, Object> values) {
    Object raan = values.get(FormField.TARGET_RAAN.key());
    raanField.setText(raan == null ? "" : raan.toString().trim());
    clearRejection();
  }

  /**
   * Refuses a node that is not a number. Blank passes: it is how the field says "no plane to wait
   * for", and it is the default state of every mission that does not meet something in orbit.
   *
   * <p>Refused rather than ignored, unlike the inclination's unreadable case, because there is no
   * value to fall back on: an inclination has a derived default, a node does not, so degrading would
   * silently turn "meet this plane" into "launch whenever".
   *
   * <p>What counts as usable is {@link RaanEntry}'s to say; this only paints the answer.
   *
   * @return the reason the node was refused, or empty when it is usable
   */
  public Optional<String> validateTargetNode() {
    String text = raanField.getText();
    Optional<String> refusal = RaanEntry.refusal(text);
    if (refusal.isEmpty()) {
      clearRejection();
      return refusal;
    }
    // The raw text, not the trimmed one: update() compares it against the field as typed, and a
    // trimmed copy would never match, clearing the refusal a frame after it was raised.
    rejectedRaan = text;
    raanField.setColor(FormStyles.DANGER);
    raanHelper.setText(RAAN_FORMAT_HELPER);
    raanHelper.setColor(FormStyles.DANGER);
    return refusal;
  }

  /**
   * @return the node the field holds, or empty when it is blank or unreadable — {@link
   *     #validateTargetNode()} is what refuses the unreadable case, this only declines to publish it
   */
  public Optional<Double> parsedRaanDeg() {
    return RaanEntry.parse(raanField.getText());
  }

  /**
   * @return whether the node currently carries a refusal, so the step can mount the page holding it
   *     without running the check a second time
   */
  public boolean hasRejection() {
    return rejectedRaan != null;
  }

  /**
   * Takes the refusal off the node. Public because the node does not exist on every card: the step
   * skips the check where there is no node to check, and a mark left standing from another card
   * would outlive the field able to show it.
   */
  public void clearRejection() {
    rejectedRaan = null;
    raanField.setColor(FormStyles.TEXT_PRIMARY);
    raanHelper.setText(RAAN_HELPER);
    raanHelper.setColor(FormStyles.TEXT_LO);
  }
}
