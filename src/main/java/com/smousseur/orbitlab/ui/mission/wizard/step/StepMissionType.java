package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;

public class StepMissionType {

  private static final float CARD_W = 264f;
  private static final float CARD_H = 152f;
  private static final float ICON_SIZE = 16f;
  private static final float CARD_GAP = 16f;
  private static final float ROW_GAP = 12f;

  private final Container root;
  private boolean missionTypeSelected = true;

  public StepMissionType() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            MissionWizardStyles.WIZARD_CONTENT_WIDTH,
            MissionWizardStyles.WIZARD_CONTENT_HEIGHT,
            0));

    Label title =
        root.addChild(new Label("MISSION TYPE", MissionWizardStyles.STYLE));
    title.setFont(MissionWizardStyles.rajdhani(20));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    root.addChild(MissionWizardStyles.vSpacer(ROW_GAP));

    Label subtitle =
        root.addChild(
            new Label("// select the target orbit", MissionWizardStyles.STYLE));
    subtitle.setFont(MissionWizardStyles.mono(12));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(MissionWizardStyles.vSpacer(ROW_GAP));

    Container row =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row.setBackground(null);

    row.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "LEO",
                "Low Earth Orbit",
                "160 - 2 000 km",
                new Badge("v AVAILABLE", Badge.Variant.SUCCESS),
                SelectableCard.State.SELECTED,
                "icons/wizard/mission-leo.png",
                ICON_SIZE)
            .getNode());
    row.addChild(MissionWizardStyles.hSpacer(CARD_GAP));
    row.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "GTO",
                "Geostationary Transfer",
                "200 x 35 786 km",
                new Badge("o IN PROGRESS", Badge.Variant.WARNING),
                SelectableCard.State.DISABLED,
                "icons/wizard/mission-gto.png",
                ICON_SIZE)
            .getNode());
    // Reserve the 3rd column (no mission yet) so the grid stays on a 3-column layout.
    row.addChild(MissionWizardStyles.hSpacer(CARD_GAP));
    row.addChild(MissionWizardStyles.spacer(CARD_W, CARD_H));
    // Trailing spacer fills remaining row width so cards stay at their fixed size.
    float trailing =
        MissionWizardStyles.WIZARD_CONTENT_WIDTH - 3 * CARD_W - 2 * CARD_GAP;
    if (trailing > 0f) {
      row.addChild(MissionWizardStyles.hSpacer(trailing));
    }
  }

  public Container getNode() {
    return root;
  }

  public boolean isMissionTypeSelected() {
    return missionTypeSelected;
  }
}
