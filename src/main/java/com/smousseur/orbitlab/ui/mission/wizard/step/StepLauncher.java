package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.math.Vector3f;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.*;
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

    Container vRow1 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    vRow1.setBackground(null);
    vRow1.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "FALCON HEAVY",
                "S1 thrust: 22.8 MN",
                "Isp S2: 348 s \u00b7 LEO payload: 63.8 t",
                null,
                SelectableCard.State.SELECTED,
                "icons/wizard/launcher-falcon-heavy.png",
                LAUNCHER_ICON)
            .getNode());
    vRow1.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "ARIANE 5 ECA",
                "S1 thrust: 7.6 MN",
                "Isp S2: 431 s \u00b7 LEO payload: 21 t",
                null,
                SelectableCard.State.IDLE,
                "icons/wizard/launcher-ariane5.png",
                LAUNCHER_ICON)
            .getNode());

    Container vRow2 =
        root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    vRow2.setBackground(null);
    vRow2.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "ORBITLAB CUSTOM",
                "S1: ~8.4 MN \u00b7 S2: ~980 kN",
                "Isp S2: 348 s \u00b7 project config",
                null,
                SelectableCard.State.IDLE,
                "icons/wizard/launcher-custom.png",
                LAUNCHER_ICON)
            .getNode());
    vRow2.addChild(
        new SelectableCard(
                CARD_W,
                CARD_H,
                "CUSTOM",
                "Define S1 & S2 parameters",
                "manually",
                null,
                SelectableCard.State.IDLE,
                "icons/wizard/launcher-wrench.png",
                LAUNCHER_ICON)
            .getNode());

    // Payload label with icon
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

    Button removeBtn =
        payloadRow.addChild(new Button("x", MissionWizardStyles.STYLE));
    removeBtn.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_DANGER));
    removeBtn.setPreferredSize(new Vector3f(32, 32, 0));

    Button addPayloadBtn =
        root.addChild(
            new Button("+ Add payload", MissionWizardStyles.STYLE));
    addPayloadBtn.setBackground(
        MissionWizardStyles.createGradient(MissionWizardStyles.WIZARD_BG_CARD));
    addPayloadBtn.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    addPayloadBtn.setFont(MissionWizardStyles.rajdhani(14));

    root.addChild(
        new InfoBanner(
                "Tsiolkovsky check: required \u0394v \u2248 9 500 m/s \u00b7 "
                    + "available \u0394v S2 \u2248 11 200 m/s \u00b7 v Feasibility confirmed",
                InfoBanner.Variant.WARNING)
            .getNode());
  }

  public Container getNode() {
    return root;
  }
}
