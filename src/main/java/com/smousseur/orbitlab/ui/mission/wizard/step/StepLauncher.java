package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.mission.wizard.MissionWizardStyles;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.List;

public class StepLauncher {

  private static final float CARD_W = 264;
  private static final float CARD_H = 112f;
  private static final float LAUNCHER_ICON = 40f;
  private static final float PAYLOAD_POPUP_W = 520f;
  private static final float MASS_FIELD_W = 140f;
  private static final float KG_LABEL_W = 40f;
  private static final float ROW_GAP = 12f;
  private static final float COL_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

  private final Container root;

  private record PayloadData(String name, String mass) {}

  private static final List<PayloadData> payloads =
      List.of(
          new PayloadData("Cargo module", "15000"),
          new PayloadData("Earth observation satellite", "10000"));

  public StepLauncher() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            MissionWizardStyles.WIZARD_CONTENT_WIDTH,
            MissionWizardStyles.WIZARD_CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("LAUNCHER & PAYLOAD", MissionWizardStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(MissionWizardStyles.WIZARD_TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle =
        root.addChild(new Label("// vehicle configuration", MissionWizardStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    SelectableCard falcon =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "FALCON HEAVY",
            "S1 thrust: 22.8 MN",
            "Isp S2: 348s",
            null,
            SelectableCard.State.SELECTED,
            "interface/wizard/v2/icon-launcher-falcon.png",
            LAUNCHER_ICON,
            SelectableCard.Variant.LAUNCHER);
    SelectableCard ariane =
        new SelectableCard(
            CARD_W,
            CARD_H,
            "ARIANE 5 ECA",
            "S1 thrust: 7.6 MN",
            "Isp S2: 431s",
            null,
            SelectableCard.State.IDLE,
            "interface/wizard/v2/icon-launcher-ariane.png",
            LAUNCHER_ICON,
            SelectableCard.Variant.LAUNCHER);

    Container vRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    vRow.setBackground(null);
    vRow.addChild(falcon.getNode());
    vRow.addChild(UiKit.hSpacer(COL_GAP));
    vRow.addChild(ariane.getNode());
    float vRowTrailing = MissionWizardStyles.WIZARD_CONTENT_WIDTH - 2 * CARD_W - COL_GAP;
    vRow.addChild(UiKit.hSpacer(vRowTrailing));

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

    root.addChild(UiKit.vSpacer(3 * ROW_GAP));

    Container payloadRow = new Container(new BoxLayout(Axis.X, FillMode.None));
    payloadRow.setBackground(null);

    root.addChild(UiKit.fieldLabelRow("PAYLOAD", "lbl-box"));
    root.addChild(UiKit.vSpacer(ROW_GAP));
    PopupList payloadType =
        new PopupList(
            PAYLOAD_POPUP_W,
            -24,
            12,
            payloads.stream().map(PayloadData::name).toList(),
            payloads.getFirst().name());

    payloadRow.addChild(payloadType.getNode());

    TextField massField = new TextField("15000", MissionWizardStyles.STYLE);
    massField.setFont(UiKit.ibmPlexMono(11));
    massField.setPreferredSize(new Vector3f(MASS_FIELD_W, 46, 0));
    massField.setInsets(new Insets3f(0, 0, 10, 0));
    payloadRow.addChild(massField);
    payloadType.setOnSelect(
        selectedName ->
            payloads.stream()
                .filter(payload -> payload.name.equals(selectedName))
                .findFirst()
                .ifPresent(payload -> massField.setText(payload.mass)));

    payloadRow.addChild(UiKit.vSpacer(3 * LABEL_FIELD_GAP));
    Label kgLabel = payloadRow.addChild(new Label("kg", MissionWizardStyles.STYLE));
    kgLabel.setTextHAlignment(HAlignment.Center);
    kgLabel.setTextVAlignment(VAlignment.Center);
    kgLabel.setFont(UiKit.ibmPlexMono(11));
    kgLabel.setColor(MissionWizardStyles.WIZARD_TEXT_SECONDARY);
    kgLabel.setPreferredSize(new Vector3f(KG_LABEL_W, 0, 0));

    root.addChild(payloadRow);
  }

  public Container getNode() {
    return root;
  }
}
