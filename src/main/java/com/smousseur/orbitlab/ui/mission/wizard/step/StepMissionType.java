package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.Map;

public class StepMissionType implements StepValues {

  private static final float CARD_W = 256f;
  private static final float CARD_H = 176f;
  private static final float ICON_SIZE = 48f;
  private static final float CARD_GAP = 16f;
  private static final float ROW_GAP = 12f;

  private final Container root;
  private final SelectableCard leoCard;
  private final SelectableCard gtoCard;
  private boolean missionTypeSelected = true;
  private String selectedMissionType = "LEO";

  public StepMissionType() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            FormStyles.CONTENT_WIDTH,
            FormStyles.CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("MISSION TYPE", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle =
        root.addChild(new Label("// select the target orbit", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Container row = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    row.setBackground(null);

    leoCard =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "LEO",
            "Low Earth Orbit",
            "160 - 2 000 km",
            new Badge("AVAILABLE", Badge.Variant.SUCCESS),
            SelectableCard.State.SELECTED,
            "interface/wizard/icon-mission-leo.png",
            ICON_SIZE,
            SelectableCard.Variant.MISSION);
    gtoCard =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "GTO",
            "Geostationary Transfer",
            "200 x 35 786 km",
            new Badge("IN PROGRESS", Badge.Variant.WARNING),
            SelectableCard.State.DISABLED,
            "interface/wizard/icon-mission-gto.png",
            ICON_SIZE,
            SelectableCard.Variant.MISSION);

    MouseEventControl.addListenersToSpatial(
        leoCard.getNode(),
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent e, Spatial t, Spatial c) {
            selectedMissionType = "LEO";
            missionTypeSelected = true;
          }
        });

    row.addChild(leoCard.getNode());
    row.addChild(UiKit.hSpacer(CARD_GAP));
    row.addChild(gtoCard.getNode());
    // Reserve the 3rd column (no mission yet) so the grid stays on a 3-column layout.
    row.addChild(UiKit.hSpacer(CARD_GAP));
    row.addChild(UiKit.spacer(CARD_W, CARD_H));
    // Trailing spacer fills remaining row width so cards stay at their fixed size.
    float trailing = FormStyles.CONTENT_WIDTH - 3 * CARD_W - 2 * CARD_GAP;
    if (trailing > 0f) {
      row.addChild(UiKit.hSpacer(trailing));
    }
  }

  public Container getNode() {
    return root;
  }

  public boolean isMissionTypeSelected() {
    return missionTypeSelected;
  }

  @Override
  public Map<String, Object> getValues() {
    return Map.of(FormField.MISSION_TYPE.key(), selectedMissionType);
  }
}
