package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The wizard's first step: one card per {@link MissionProfile}.
 *
 * <p>Four of the five cards are backed by the same {@code MissionType} and the same spec record —
 * they are <b>presets</b>, not types (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md}
 * §1). What a card actually decides is the parameter panel the next step shows and the inclination
 * it starts from.
 */
public class StepMissionType implements StepValues {

  private static final float CARD_W = 256f;
  private static final float CARD_H = 176f;
  private static final float ICON_SIZE = 48f;
  private static final float CARD_GAP = 16f;
  private static final float ROW_GAP = 12f;

  /** Cards per row; five profiles therefore lay out as 3 + 2. */
  private static final int CARDS_PER_ROW = 3;

  private final Container root;
  private final Map<MissionProfile, SelectableCard> cards = new EnumMap<>(MissionProfile.class);
  private MissionProfile selectedProfile;
  private Consumer<MissionProfile> onProfileSelected = profile -> {};

  /**
   * Builds the mission-type step.
   *
   * <p>When {@code locked}, the wizard is editing an existing mission and the cards of the
   * <em>other</em> mission type are inert: a mission's stages, propellant budget and payload
   * eligibility derive from its type, and {@code MissionEntry.applySpec} refuses a spec that would
   * change it. The cards sharing the edited mission's type stay live, because switching between
   * them only changes the target — which is precisely what editing a mission means.
   *
   * @param missionContext the context whose selected type drives the downstream steps
   * @param initialProfile the profile to show as selected
   * @param locked {@code true} to forbid changing the mission type (wizard edit mode)
   */
  public StepMissionType(
      MissionContext missionContext, MissionProfile initialProfile, boolean locked) {
    selectedProfile = initialProfile;
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    Label title = root.addChild(new Label("MISSION TYPE", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    Label subtitle = root.addChild(new Label("// select the target orbit", FormStyles.STYLE));
    subtitle.setFont(UiKit.ibmPlexMono(11));
    subtitle.setColor(FormStyles.TEXT_SECONDARY);

    root.addChild(UiKit.vSpacer(ROW_GAP));

    List<MissionProfile> profiles = List.of(MissionProfile.values());
    Container row = null;
    for (int i = 0; i < profiles.size(); i++) {
      if (i % CARDS_PER_ROW == 0) {
        if (row != null) {
          root.addChild(UiKit.vSpacer(ROW_GAP));
        }
        row = root.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
        row.setBackground(null);
      } else {
        row.addChild(UiKit.hSpacer(CARD_GAP));
      }

      MissionProfile profile = profiles.get(i);
      SelectableCard card = buildCard(profile, locked);
      cards.put(profile, card);
      row.addChild(card.getNode());

      if (!locked || profile.missionType() == initialProfile.missionType()) {
        MouseEventControl.addListenersToSpatial(
            card.getNode(),
            new DefaultMouseListener() {
              @Override
              public void click(MouseButtonEvent e, Spatial t, Spatial c) {
                select(profile, missionContext);
              }
            });
      }
    }
    padRow(row, profiles.size() % CARDS_PER_ROW);
  }

  private SelectableCard buildCard(MissionProfile profile, boolean locked) {
    boolean selected = profile == selectedProfile;
    // A locked step greys out every card the edited mission cannot become; a disabled card also
    // loses SelectableCard's hover feedback, so it reads as inert rather than merely unresponsive.
    boolean inert = locked && profile.missionType() != selectedProfile.missionType();
    SelectableCard.State state =
        selected
            ? SelectableCard.State.SELECTED
            : (inert ? SelectableCard.State.DISABLED : SelectableCard.State.IDLE);
    return new SelectableCard(
        CARD_W,
        CARD_H,
        profile.title(),
        profile.subtitle(),
        profile.value(),
        badgeFor(profile),
        state,
        profile.iconPath(),
        ICON_SIZE,
        SelectableCard.Variant.MISSION);
  }

  /**
   * The MEO badge does not say "available", because the catalog does not make it so: it needs an
   * upper stage holding a 2 h 58 coast, or a payload whose kick motor takes the apogee burn over
   * (spec {@code 01} §6). Saying it on the card is what keeps the refusal at the launcher step from
   * reading as a surprise.
   */
  private static Badge badgeFor(MissionProfile profile) {
    return switch (profile.availability()) {
      case AVAILABLE -> new Badge("AVAILABLE", Badge.Variant.SUCCESS);
      case CONSTRAINED -> new Badge("LONG-COAST STAGE OR AKM", Badge.Variant.WARNING);
    };
  }

  /**
   * Fills the last row up to the full grid width, so a short row's cards keep the size and the left
   * alignment of a full one instead of being stretched across the step.
   *
   * @param row the row to pad
   * @param used how many of its {@link #CARDS_PER_ROW} slots are taken, {@code 0} meaning full
   */
  private static void padRow(Container row, int used) {
    int empty = used == 0 ? 0 : CARDS_PER_ROW - used;
    for (int i = 0; i < empty; i++) {
      row.addChild(UiKit.hSpacer(CARD_GAP));
      row.addChild(UiKit.spacer(CARD_W, CARD_H));
    }
    float trailing =
        FormStyles.CONTENT_WIDTH - CARDS_PER_ROW * CARD_W - (CARDS_PER_ROW - 1) * CARD_GAP;
    if (trailing > 0) {
      row.addChild(UiKit.hSpacer(trailing));
    }
  }

  /**
   * Moves the selection, and tells everyone who needs to know in the two languages they speak: the
   * mission <em>type</em> goes to {@code MissionContext}, which the launcher step reads to narrow
   * its payload list, and the <em>profile</em> goes to the listener, because the parameters step
   * needs to know which of the four Earth-orbit presets it is showing.
   */
  private void select(MissionProfile profile, MissionContext missionContext) {
    if (profile == selectedProfile) {
      return;
    }
    cards.get(selectedProfile).applyState(SelectableCard.State.IDLE);
    cards.get(profile).applyState(SelectableCard.State.SELECTED);
    selectedProfile = profile;
    missionContext.setSelectedMissionType(profile.missionType());
    onProfileSelected.accept(profile);
  }

  /**
   * Sets what to do when the selected profile changes.
   *
   * @param action the listener, called with the newly selected profile
   */
  public void setOnProfileSelected(Consumer<MissionProfile> action) {
    this.onProfileSelected = action != null ? action : profile -> {};
  }

  public Container getNode() {
    return root;
  }

  /**
   * @return the selected profile
   */
  public MissionProfile selectedProfile() {
    return selectedProfile;
  }

  @Override
  public Map<String, Object> getValues() {
    return Map.of(
        FormField.MISSION_TYPE.key(), selectedProfile.missionType().name(),
        FormField.MISSION_PROFILE.key(), selectedProfile.name());
  }
}
