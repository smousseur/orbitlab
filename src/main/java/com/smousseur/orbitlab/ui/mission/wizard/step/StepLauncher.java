package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.vehicle.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.component.PopupList;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Launcher & payload wizard step. Launcher cards and the payload list are driven by the {@link
 * Launchers} and {@link Payloads} catalogs; {@code PAYLOAD_TYPE} carries the catalog id (not the
 * display name) so the mission factory can resolve it.
 */
public class StepLauncher implements StepValues {

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
  private final List<SelectableCard> launcherCards = new ArrayList<>();
  private final PopupList payloadType;
  private final TextField massField;
  private String selectedLauncher;

  public StepLauncher() {
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(
        new Vector3f(
            FormStyles.CONTENT_WIDTH,
            FormStyles.CONTENT_HEIGHT,
            0));

    Label title = root.addChild(new Label("LAUNCHER & PAYLOAD", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle =
        root.addChild(new Label("// vehicle configuration", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Container vRow = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
    vRow.setBackground(null);

    List<LauncherModel> launchers = Launchers.all();
    selectedLauncher = launchers.getFirst().id();
    for (int i = 0; i < launchers.size(); i++) {
      LauncherModel launcher = launchers.get(i);
      SelectableCard card =
          new SelectableCard(
              CARD_W,
              CARD_H,
              launcher.displayName().toUpperCase(Locale.ROOT),
              String.format(
                  Locale.ROOT,
                  "S1 thrust: %.1f MN",
                  launcher.stages().getFirst().propulsion().thrust() / 1e6),
              String.format(
                  Locale.ROOT,
                  "Isp S2: %.0fs",
                  launcher.stages().getLast().propulsion().isp()),
              null,
              i == 0 ? SelectableCard.State.SELECTED : SelectableCard.State.IDLE,
              iconFor(launcher.id()),
              LAUNCHER_ICON,
              SelectableCard.Variant.LAUNCHER);
      launcherCards.add(card);
      if (i > 0) {
        vRow.addChild(UiKit.hSpacer(COL_GAP));
      }
      vRow.addChild(card.getNode());

      // Mutual exclusion: clicking one card deselects the others.
      MouseEventControl.addListenersToSpatial(
          card.getNode(),
          new DefaultMouseListener() {
            @Override
            public void click(MouseButtonEvent e, Spatial t, Spatial c) {
              for (SelectableCard other : launcherCards) {
                if (other != card) {
                  other.applyState(SelectableCard.State.IDLE);
                }
              }
              selectedLauncher = launcher.id();
            }
          });
    }
    float vRowTrailing =
        FormStyles.CONTENT_WIDTH - launchers.size() * CARD_W - (launchers.size() - 1) * COL_GAP;
    if (vRowTrailing > 0) {
      vRow.addChild(UiKit.hSpacer(vRowTrailing));
    }

    root.addChild(UiKit.vSpacer(3 * ROW_GAP));

    Container payloadRow = new Container(new BoxLayout(Axis.X, FillMode.None));
    payloadRow.setBackground(null);

    root.addChild(UiKit.fieldLabelRow("PAYLOAD", "lbl-box"));
    root.addChild(UiKit.vSpacer(ROW_GAP));
    List<PayloadModel> payloads = Payloads.all();
    payloadType =
        new PopupList(
            PAYLOAD_POPUP_W,
            -24,
            12,
            payloads.stream().map(PayloadModel::displayName).toList(),
            payloads.getFirst().displayName());

    payloadRow.addChild(payloadType.getNode());

    massField = new TextField(defaultMassText(payloads.getFirst()), FormStyles.STYLE);
    massField.setFont(UiKit.ibmPlexMono(11));
    massField.setPreferredSize(new Vector3f(MASS_FIELD_W, 46, 0));
    massField.setInsets(new Insets3f(0, 0, 10, 0));
    payloadRow.addChild(massField);
    payloadType.setOnSelect(
        selectedName ->
            findByDisplayName(selectedName)
                .ifPresent(payload -> massField.setText(defaultMassText(payload))));

    payloadRow.addChild(UiKit.vSpacer(3 * LABEL_FIELD_GAP));
    Label kgLabel = payloadRow.addChild(new Label("kg", FormStyles.STYLE));
    kgLabel.setTextHAlignment(HAlignment.Center);
    kgLabel.setTextVAlignment(VAlignment.Center);
    kgLabel.setFont(UiKit.ibmPlexMono(11));
    kgLabel.setColor(FormStyles.TEXT_SECONDARY);
    kgLabel.setPreferredSize(new Vector3f(KG_LABEL_W, 0, 0));

    root.addChild(payloadRow);
  }

  public Container getNode() {
    return root;
  }

  @Override
  public Map<String, Object> getValues() {
    String payloadId =
        findByDisplayName(payloadType.getSelectedValue())
            .map(PayloadModel::id)
            .orElse(Payloads.all().getFirst().id());
    return Map.of(
        FormField.LAUNCHER_TYPE.key(), selectedLauncher,
        FormField.PAYLOAD_TYPE.key(), payloadId,
        FormField.PAYLOAD_MASS.key(), parseDoubleOrZero(massField.getText()));
  }

  private static java.util.Optional<PayloadModel> findByDisplayName(String displayName) {
    return Payloads.all().stream()
        .filter(payload -> payload.displayName().equals(displayName))
        .findFirst();
  }

  private static String defaultMassText(PayloadModel payload) {
    return String.valueOf((long) payload.defaultDryMass());
  }

  private static String iconFor(String launcherId) {
    return switch (launcherId) {
      case "ARIANE_5_ECA" -> "interface/wizard/icon-launcher-ariane.png";
      default -> "interface/wizard/icon-launcher-falcon.png";
    };
  }

  private static double parseDoubleOrZero(String text) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return 0d;
    }
  }
}
