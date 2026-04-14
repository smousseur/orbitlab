package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.LabeledField;

public class StepParameters {

  private final Container root;

  public StepParameters() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Label title =
        root.addChild(
            new Label("PARAMETERS \u2014 LEO", MissionWizardStyles.STYLE));
    title.setFont(MissionWizardStyles.rajdhani(20));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label subtitle =
        root.addChild(
            new Label(
                "// target orbit configuration", MissionWizardStyles.STYLE));
    subtitle.setFont(MissionWizardStyles.mono(12));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    // Mission Name
    root.addChild(
        new LabeledField(
                "MISSION NAME",
                monoField("ORBITLAB-LEO-001"),
                null,
                "icons/wizard/field-pencil.png")
            .getNode());

    // Target Altitude (slider + big value)
    Container altCol =
        root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None)));
    altCol.setBackground(null);
    Slider altSlider =
        new Slider(
            new DefaultRangedValueModel(160, 2000, 550),
            Axis.X,
            MissionWizardStyles.STYLE);
    altCol.addChild(
        new LabeledField(
                "TARGET ALTITUDE",
                altSlider,
                "160 km                         2 000 km",
                "icons/wizard/field-altitude.png")
            .getNode());
    Label altValue =
        altCol.addChild(new Label("550 km", MissionWizardStyles.STYLE));
    altValue.setFont(MissionWizardStyles.rajdhani(28));
    altValue.setColor(MissionWizardStyles.WIZARD_ACCENT);

    // Launch Date
    TextField dateField = monoField("2026-06-15T06:00:00Z");
    dateField.setPreferredSize(new Vector3f(320, 0, 0));
    root.addChild(
        new LabeledField(
                "LAUNCH DATE",
                dateField,
                "UTC \u00b7 Orekit epoch",
                "icons/wizard/field-clock.png")
            .getNode());
  }

  public Container getNode() {
    return root;
  }

  private TextField monoField(String value) {
    TextField f = new TextField(value, MissionWizardStyles.STYLE);
    f.setFont(MissionWizardStyles.mono(14));
    return f;
  }
}
