package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;

public class StepParameters {

  private static final float COL_WIDTH = 400f;
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

    // Row 1 — Mission Name
    root.addChild(
        new LabeledField(
                "MISSION NAME",
                monoField("ORBITLAB-LEO-001"),
                null,
                "icons/wizard/field-pencil.png")
            .getNode());

    // Row 2 — Altitude + Tolerance
    Container row2 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);

    Container altCol = col(COL_WIDTH);
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
    row2.addChild(altCol);

    Container tolCol = col(COL_WIDTH);
    tolCol.addChild(
        new LabeledField(
                "ALTITUDE TOLERANCE",
                monoField("1"),
                "+/- km \u00b7 CMA-ES convergence",
                "icons/wizard/field-tolerance.png")
            .getNode());
    row2.addChild(tolCol);

    // Row 3 — Inclination + RAAN
    root.addChild(
        twoColRow(
            new LabeledField(
                "INCLINATION",
                monoField("51.6"),
                "degrees \u00b7 0\u00b0 = equatorial",
                "icons/wizard/field-inclination.png"),
            new LabeledField(
                "RAAN (\u03a9)",
                monoField("0.0"),
                "degrees \u00b7 ascending node",
                "icons/wizard/field-raan.png")));

    // Row 4 — Arg Perigee + Strategy
    Container row4 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row4.setBackground(null);

    Container perigeeCol = col(COL_WIDTH);
    perigeeCol.addChild(
        new LabeledField(
                "ARGUMENT OF PERIGEE (\u03c9)",
                monoField("0.0"),
                "degrees",
                "icons/wizard/field-perigee.png")
            .getNode());
    row4.addChild(perigeeCol);

    Container stratCol = col(COL_WIDTH);
    SegmentedControl strat =
        new SegmentedControl("2 BURNS", "DIRECT", "HOHMANN").select(0);
    stratCol.addChild(
        new LabeledField(
                "INSERTION STRATEGY",
                strat.getNode(),
                "2 burns: gravity turn + circularisation",
                "icons/wizard/field-strategy.png")
            .getNode());
    row4.addChild(stratCol);

    // Row 5 — Launch Date
    TextField dateField = monoField("2026-06-15T06:00:00Z");
    dateField.setPreferredSize(new Vector3f(320, 0, 0));
    root.addChild(
        new LabeledField(
                "LAUNCH DATE",
                dateField,
                "UTC \u00b7 Orekit epoch",
                "icons/wizard/field-clock.png")
            .getNode());

    // Info Banner
    root.addChild(
        new InfoBanner(
                "Iterative altitude correction enabled: the mean altitude "
                    + "over a full period (J2 filter) is used as reference. "
                    + "Convergence in 2-3 iterations \u00b7 tolerance 500 m.",
                InfoBanner.Variant.INFO)
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

  private Container col(float w) {
    Container c = new Container(new BoxLayout(Axis.Y, FillMode.None));
    c.setBackground(null);
    c.setPreferredSize(new Vector3f(w, 0, 0));
    return c;
  }

  private Container twoColRow(LabeledField left, LabeledField right) {
    Container row = new Container(new BoxLayout(Axis.X, FillMode.None));
    row.setBackground(null);
    Container l = col(COL_WIDTH);
    l.addChild(left.getNode());
    row.addChild(l);
    Container r = col(COL_WIDTH);
    r.addChild(right.getNode());
    row.addChild(r);
    return row;
  }
}
