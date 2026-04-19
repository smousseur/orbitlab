package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.LabeledField;

public class StepParameters {

  private static final float NAME_FIELD_W = 320f;
  private static final float SLIDER_W = 520f;
  private static final float DATE_FIELD_W = 320f;
  private static final float ROW_GAP = 12f;

  private final Container root;

  public StepParameters() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            MissionWizardStyles.WIZARD_CONTENT_WIDTH,
            MissionWizardStyles.WIZARD_CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("PARAMETERS \u2014 LEO", MissionWizardStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle =
        root.addChild(new Label("// target orbit configuration", MissionWizardStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // Mission Name
    TextField nameField = monoField("ORBITLAB-LEO-001");
    nameField.setPreferredSize(new Vector3f(NAME_FIELD_W, 0, 0));
    root.addChild(
        widthBoundedRow(
            new LabeledField("MISSION NAME", nameField, null, "lbl-edit")
                .getNode(),
            NAME_FIELD_W));

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // Target Altitude (slider + big value)
    Slider altSlider =
        new Slider(new DefaultRangedValueModel(160, 2000, 550), Axis.X, MissionWizardStyles.STYLE);
    altSlider.setPreferredSize(new Vector3f(SLIDER_W, 0, 0));

    Container altCol = new Container(new BoxLayout(Axis.Y, FillMode.None));
    altCol.setBackground(null);
    altCol.addChild(
        new LabeledField(
                "TARGET ALTITUDE",
                altSlider,
                "160 km                         2 000 km",
                "lbl-ruler")
            .getNode());
    Label altValue = altCol.addChild(new Label("550 km", MissionWizardStyles.STYLE));
    altValue.setFont(UiKit.orbitron(13));
    altValue.setColor(MissionWizardStyles.WIZARD_ACCENT);
    root.addChild(widthBoundedRow(altCol, SLIDER_W));

    root.addChild(UiKit.vSpacer(ROW_GAP));

    // Launch Date
    TextField dateField = monoField("2026-06-15T06:00:00Z");
    dateField.setPreferredSize(new Vector3f(DATE_FIELD_W, 0, 0));
    root.addChild(
        widthBoundedRow(
            new LabeledField(
                    "LAUNCH DATE",
                    dateField,
                    "UTC \u00b7 Orekit epoch",
                    "lbl-clock")
                .getNode(),
            DATE_FIELD_W));
  }

  public Container getNode() {
    return root;
  }

  private TextField monoField(String value) {
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(UiKit.mono(14));
    return f;
  }

  /** Wraps a child in an X-row with a trailing invisible spacer so it keeps its fixed width. */
  private Container widthBoundedRow(Container child, float childWidth) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    row.addChild(child);
    float trailing = MissionWizardStyles.WIZARD_CONTENT_WIDTH - childWidth;
    if (trailing > 0f) {
      row.addChild(UiKit.hSpacer(trailing));
    }
    return row;
  }
}
