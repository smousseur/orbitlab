package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;

public class StepMissionType {

  private static final float CARD_W = 256f;
  private static final float CARD_H = 152f;

  private final Container root;

  public StepMissionType() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Label title =
        root.addChild(new Label("MISSION TYPE", MissionWizardStyles.STYLE));
    title.setFont(MissionWizardStyles.rajdhani(20));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label subtitle =
        root.addChild(
            new Label("// select the target orbit", MissionWizardStyles.STYLE));
    subtitle.setFont(MissionWizardStyles.mono(12));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    Container row1 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row1.setBackground(null);

    row1.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "LEO",
                "Low Earth Orbit",
                "160 - 2 000 km",
                new Badge("v AVAILABLE", Badge.Variant.SUCCESS),
                SelectableCard.State.SELECTED)
            .getNode());
    row1.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "GTO",
                "Geostationary Transfer",
                "200 x 35 786 km",
                new Badge("o IN PROGRESS", Badge.Variant.WARNING),
                SelectableCard.State.IDLE)
            .getNode());
    row1.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "SSO",
                "Sun-Synchronous Orbit",
                "600 - 800 km",
                new Badge("SOON", Badge.Variant.MUTED),
                SelectableCard.State.DISABLED)
            .getNode());

    Container row2 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row2.setBackground(null);

    row2.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "MEO",
                "Medium Earth Orbit",
                "2 000 - 35 786 km",
                new Badge("SOON", Badge.Variant.MUTED),
                SelectableCard.State.DISABLED)
            .getNode());
    row2.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "GEO",
                "Geostationary Orbit",
                "35 786 km",
                new Badge("SOON", Badge.Variant.MUTED),
                SelectableCard.State.DISABLED)
            .getNode());
    row2.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "TLI",
                "Trans-Lunar Injection",
                "Cislunar",
                new Badge("SOON", Badge.Variant.MUTED),
                SelectableCard.State.DISABLED)
            .getNode());
  }

  public Container getNode() {
    return root;
  }
}
