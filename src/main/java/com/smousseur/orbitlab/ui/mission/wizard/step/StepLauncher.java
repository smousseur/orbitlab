package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.List;

public class StepLauncher {

  private static final float CARD_W = 400f;
  private static final float CARD_H = 112f;
  private static final float LAUNCHER_ICON = 40f;

  private final Container root;

  public StepLauncher() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);

    Label title =
        root.addChild(
            new Label("LAUNCHER & PAYLOAD", MissionWizardStyles.STYLE));
    title.setFont(MissionWizardStyles.rajdhani(20));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    Label subtitle =
        root.addChild(
            new Label(
                "// vehicle configuration", MissionWizardStyles.STYLE));
    subtitle.setFont(MissionWizardStyles.mono(12));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    SelectableCard falcon =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "FALCON HEAVY",
            "S1 thrust: 22.8 MN",
            "Isp S2: 348 s \u00b7 LEO payload: 63.8 t",
            null,
            SelectableCard.State.SELECTED,
            "icons/wizard/launcher-falcon-heavy.png",
            LAUNCHER_ICON);
    SelectableCard ariane =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "ARIANE 5 ECA",
            "S1 thrust: 7.6 MN",
            "Isp S2: 431 s \u00b7 LEO payload: 21 t",
            null,
            SelectableCard.State.IDLE,
            "icons/wizard/launcher-ariane5.png",
            LAUNCHER_ICON);

    Container vRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    vRow.setBackground(null);
    vRow.addChild(falcon.getNode());
    vRow.addChild(ariane.getNode());

    // Mutual exclusion: clicking one deselects the other.
    MouseEventControl.addListenersToSpatial(
        falcon.getNode(),
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent e, Spatial t, Spatial c) {
            ariane.applyState(SelectableCard.State.IDLE);
          }
        });
    MouseEventControl.addListenersToSpatial(
        ariane.getNode(),
        new DefaultMouseListener() {
          @Override
          public void click(MouseButtonEvent e, Spatial t, Spatial c) {
            falcon.applyState(SelectableCard.State.IDLE);
          }
        });

    // Payload label
    Container payloadLabelRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    payloadLabelRow.setBackground(null);
    payloadLabelRow.addChild(
        MissionWizardStyles.iconPlaceholder("icons/wizard/payload.png", 14, 14));
    Label payloadSpacer =
        payloadLabelRow.addChild(new Label(" ", MissionWizardStyles.STYLE));
    payloadSpacer.setBackground(null);
    Label payloadLabel =
        payloadLabelRow.addChild(new Label("PAYLOAD", MissionWizardStyles.STYLE));
    payloadLabel.setFont(MissionWizardStyles.rajdhani(12));
    payloadLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    Container payloadRow =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    payloadRow.setBackground(null);

    PopupList payloadType =
        new PopupList(
            520f,
            List.of(
                "Communication satellite",
                "Earth observation satellite",
                "Scientific probe",
                "Cargo module"),
            "Communication satellite");
    payloadRow.addChild(payloadType.getNode());

    TextField massField =
        new TextField("15000", MissionWizardStyles.STYLE);
    massField.setFont(MissionWizardStyles.mono(14));
    massField.setPreferredSize(new Vector3f(140, 0, 0));
    payloadRow.addChild(massField);

    Label kgLabel =
        payloadRow.addChild(new Label("kg", MissionWizardStyles.STYLE));
    kgLabel.setFont(MissionWizardStyles.mono(14));
    kgLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    kgLabel.setPreferredSize(new Vector3f(40, 0, 0));
  }

  public Container getNode() {
    return root;
  }
}
