package com.smousseur.orbitlab.ui.mission.wizard.step.planning;

import static com.smousseur.orbitlab.ui.UiKit.fieldLabelRow;
import static com.smousseur.orbitlab.ui.UiKit.newInputField;

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
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
  private static final float FIELD_H = 36f;
  private static final float LABEL_FIELD_GAP = 6f;
  private static final float LABEL_ICON_SIZE = 14f;
  private static final float ROW_GAP = 16f;
  private static final float BACK_BTN_W = 90f;
  private static final float BACK_BTN_H = 22f;

  private static final String RAAN_HELPER = "empty — no plane is waited for";
  private static final String RAAN_FORMAT_HELPER = "RAAN — expected degrees";

  private final Container root;
  private final TextField raanField;
  private final Label raanHelper;

  private Runnable onBack = () -> {};

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
   * Publishes the node, and only when it reads as a number.
   *
   * <p>Absent when blank, and that absence is the mission saying it waits for no plane. Publishing a
   * default here would make every mission sit through a launch window it never asked for.
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
   * @return the reason the node was refused, or empty when it is usable
   */
  public Optional<String> validateTargetNode() {
    String text = raanField.getText().trim();
    if (text.isEmpty()) {
      clearRejection();
      return Optional.empty();
    }
    try {
      Double.parseDouble(text);
    } catch (NumberFormatException e) {
      raanField.setColor(FormStyles.DANGER);
      raanHelper.setText(RAAN_FORMAT_HELPER);
      raanHelper.setColor(FormStyles.DANGER);
      return Optional.of("Target RAAN is not a number: " + text);
    }
    clearRejection();
    return Optional.empty();
  }

  /**
   * @return the node the field holds, or empty when it is blank or unreadable — {@link
   *     #validateTargetNode()} is what refuses the unreadable case, this only declines to publish it
   */
  public Optional<Double> parsedRaanDeg() {
    String text = raanField.getText().trim();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Double.parseDouble(text));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private void clearRejection() {
    raanField.setColor(FormStyles.TEXT_PRIMARY);
    raanHelper.setText(RAAN_HELPER);
    raanHelper.setColor(FormStyles.TEXT_LO);
  }
}
