package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadModel;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.FormValues;
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
 *
 * <p>The payload list is narrowed to the models the selected mission type can actually fly — a GEO
 * mission delegates its apogee circularization to the payload's kick motor, so offering an inert
 * payload there would only fail later, during propagation.
 */
public class StepLauncher implements StepValues {

  private static final String SUBTITLE = "// vehicle configuration";
  private static final String SUBTITLE_PROPELLED =
      SUBTITLE + " - this mission requires a payload with an apogee kick motor";

  private static final float CARD_W = 264;
  private static final float CARD_H = 112f;
  private static final float LAUNCHER_ICON = 40f;
  private static final float PAYLOAD_POPUP_W = 520f;
  private static final float MASS_FIELD_W = 140f;
  private static final float KG_LABEL_W = 40f;
  private static final float ROW_GAP = 12f;
  private static final float COL_GAP = 16f;
  private static final float LABEL_FIELD_GAP = 6f;

  /** Characters per line of the refusal message, sized on the step's width at 11 px monospace. */
  private static final int REFUSAL_WRAP_COLUMNS = 96;

  private final Container root;
  private final MissionContext missionContext;
  private final List<SelectableCard> launcherCards = new ArrayList<>();
  private final Label subtitle;
  private Label refusalLabel;
  private final PopupList payloadType;
  private final TextField massField;
  private String selectedLauncher;
  private MissionType shownMissionType;

  public StepLauncher(MissionContext missionContext) {
    this.missionContext = missionContext;
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    Label title = root.addChild(new Label("LAUNCHER & PAYLOAD", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    subtitle = root.addChild(new Label(SUBTITLE, FormStyles.STYLE));
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
                  Locale.ROOT, "Isp S2: %.0fs", launcher.stages().getLast().propulsion().isp()),
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
              // A refusal named this vehicle; picking another one is the user answering it.
              clearRefusal();
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
    List<PayloadModel> payloads = eligiblePayloads(missionContext.getSelectedMissionType());
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
        selectedName -> {
          findByDisplayName(selectedName)
              .ifPresent(payload -> massField.setText(defaultMassText(payload)));
          // Same reasoning as the launcher cards: a kick motor is one of the two ways out.
          clearRefusal();
        });

    payloadRow.addChild(UiKit.vSpacer(3 * LABEL_FIELD_GAP));
    Label kgLabel = payloadRow.addChild(new Label("kg", FormStyles.STYLE));
    kgLabel.setTextHAlignment(HAlignment.Center);
    kgLabel.setTextVAlignment(VAlignment.Center);
    kgLabel.setFont(UiKit.ibmPlexMono(11));
    kgLabel.setColor(FormStyles.TEXT_SECONDARY);
    kgLabel.setPreferredSize(new Vector3f(KG_LABEL_W, 0, 0));

    root.addChild(payloadRow);

    root.addChild(UiKit.vSpacer(ROW_GAP));
    refusalLabel = root.addChild(new Label("", FormStyles.STYLE));
    refusalLabel.setFont(UiKit.ibmPlexMono(11));
    refusalLabel.setColor(FormStyles.DANGER);

    applyMissionType(missionContext.getSelectedMissionType());
  }

  /**
   * Shows why the mission cannot be composed, on the step where the answer is (spec {@code
   * docs/earth-orbit/02-wizard-orbites-terrestres.md} §6).
   *
   * <p>A target beyond the ascent's reach — a MEO — is only refutable once the vehicle is known, and
   * the vehicle is picked here, last. The message comes from {@code MissionComposer} unchanged: it
   * names the stage, the coast the transfer needs and the coast the stage declares, so the way out
   * ("fly Ariane 62, or a payload with a kick motor") is in the refusal itself. Until P2 it reached
   * a log line and the user saw the wizard close on no mission at all.
   *
   * @param message the refusal, as the model worded it
   */
  public void showRefusal(String message) {
    refusalLabel.setText(wrap(message, REFUSAL_WRAP_COLUMNS));
  }

  /** Clears any refusal on display; harmless when there is none. */
  public void clearRefusal() {
    if (!refusalLabel.getText().isEmpty()) {
      refusalLabel.setText("");
    }
  }

  /**
   * Breaks a message onto lines short enough for the step's width. Lemur's labels do not wrap, and
   * this one carries a full sentence rather than a field helper.
   */
  private static String wrap(String message, int columns) {
    StringBuilder wrapped = new StringBuilder();
    int lineLength = 0;
    for (String word : message.split(" ")) {
      if (lineLength > 0 && lineLength + 1 + word.length() > columns) {
        wrapped.append('\n');
        lineLength = 0;
      } else if (lineLength > 0) {
        wrapped.append(' ');
        lineLength++;
      }
      wrapped.append(word);
      lineLength += word.length();
    }
    return wrapped.toString();
  }

  public Container getNode() {
    return root;
  }

  /** Keeps the payload list in step with the mission type picked on the first wizard step. */
  public void update() {
    applyMissionType(missionContext.getSelectedMissionType());
  }

  /**
   * Narrows the payload list to the models the given mission type can fly, keeping the current
   * selection when it survives the filter so a round trip through the stepper does not silently
   * discard the user's payload and the mass they typed.
   */
  private void applyMissionType(MissionType type) {
    if (type == shownMissionType) {
      return;
    }
    List<PayloadModel> eligible = eligiblePayloads(type);
    String current = payloadType.getSelectedValue();
    PayloadModel selection =
        eligible.stream()
            .filter(payload -> payload.displayName().equals(current))
            .findFirst()
            .orElse(null);
    if (selection == null) {
      // The selected payload cannot fly this mission type. The mass on screen belonged to it, so it
      // goes back to the default of the payload taking its place.
      selection = eligible.getFirst();
      massField.setText(defaultMassText(selection));
    }
    payloadType.setOptions(
        eligible.stream().map(PayloadModel::displayName).toList(), selection.displayName());
    subtitle.setText(type.requiresPayloadPropulsion() ? SUBTITLE_PROPELLED : SUBTITLE);
    shownMissionType = type;
  }

  private static List<PayloadModel> eligiblePayloads(MissionType type) {
    List<PayloadModel> eligible = Payloads.forMissionType(type);
    if (eligible.isEmpty()) {
      throw new OrbitlabException("No payload in the catalog can fly a " + type + " mission");
    }
    return eligible;
  }

  @Override
  public Map<String, Object> getValues() {
    MissionType type = missionContext.getSelectedMissionType();
    String payloadId =
        findByDisplayName(payloadType.getSelectedValue())
            .map(PayloadModel::id)
            .orElseGet(() -> eligiblePayloads(type).getFirst().id());
    return Map.of(
        FormField.LAUNCHER_TYPE.key(), selectedLauncher,
        FormField.PAYLOAD_TYPE.key(), payloadId,
        FormField.PAYLOAD_MASS.key(), parseDoubleOrZero(massField.getText()));
  }

  @Override
  public void applyValues(Map<String, Object> values) {
    String launcherId = FormValues.string(values, FormField.LAUNCHER_TYPE);
    if (launcherId != null) {
      selectLauncher(launcherId);
    }
    String payloadId = FormValues.string(values, FormField.PAYLOAD_TYPE);
    if (payloadId != null) {
      // Looked up in the narrowed list, not the whole catalog: a payload absent from it is one this
      // mission type cannot fly, and the eligible default already on screen is the better offer.
      eligiblePayloads(missionContext.getSelectedMissionType()).stream()
          .filter(payload -> payload.id().equals(payloadId))
          .findFirst()
          .ifPresent(payload -> payloadType.setSelectedValue(payload.displayName()));
    }
    double payloadMass = FormValues.number(values, FormField.PAYLOAD_MASS, 0d);
    if (payloadMass > 0) {
      massField.setText(Long.toString(Math.round(payloadMass)));
    }
  }

  /** Moves the card selection to the given launcher, ignoring an id the catalog does not offer. */
  private void selectLauncher(String launcherId) {
    List<LauncherModel> launchers = Launchers.all();
    if (launchers.stream().noneMatch(launcher -> launcher.id().equals(launcherId))) {
      return;
    }
    selectedLauncher = launcherId;
    for (int i = 0; i < launchers.size() && i < launcherCards.size(); i++) {
      boolean selected = launchers.get(i).id().equals(launcherId);
      launcherCards
          .get(i)
          .applyState(selected ? SelectableCard.State.SELECTED : SelectableCard.State.IDLE);
    }
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
    String icon = "interface/wizard/icon-launcher-falcon.png";
    if (launcherId.equals("ARIANE_5_ECA")) {
      icon = "interface/wizard/icon-launcher-ariane.png";
    }
    return icon;
  }

  private static double parseDoubleOrZero(String text) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return 0d;
    }
  }
}
