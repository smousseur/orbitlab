package com.smousseur.orbitlab.ui.mission.wizard.step;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.*;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.event.DefaultMouseListener;
import com.simsilica.lemur.event.MouseEventControl;
import com.smousseur.orbitlab.simulation.mission.context.MissionContext;
import com.smousseur.orbitlab.ui.UiKit;
import com.smousseur.orbitlab.ui.form.FormStyles;
import com.smousseur.orbitlab.ui.mission.wizard.FormField;
import com.smousseur.orbitlab.ui.mission.wizard.MissionDomain;
import com.smousseur.orbitlab.ui.mission.wizard.MissionProfile;
import com.smousseur.orbitlab.ui.mission.wizard.StepValues;
import com.smousseur.orbitlab.ui.mission.wizard.component.Badge;
import com.smousseur.orbitlab.ui.mission.wizard.component.SelectableCard;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The wizard's first step: one tab per {@link MissionDomain}, one card per {@link MissionProfile}
 * inside it.
 *
 * <p>Four of the six cards are backed by the same {@code MissionType} and the same spec record —
 * they are <b>presets</b>, not types (spec {@code docs/earth-orbit/02-wizard-orbites-terrestres.md}
 * §1). What a card actually decides is the parameter panel the next step shows and the inclination
 * it starts from.
 *
 * <p><b>Why tabs (MIS-5 / L6).</b> Six cards three per row filled 421 px of the 424 the content
 * pane offers; a seventh row would have landed on the footer, nothing in this wizard clipping.
 * Grouping by destination buys the room back and keeps every card above the fold — a mission type
 * below it is a mission type the user does not know exists, and this is the first screen of the
 * wizard.
 *
 * <p><b>The cards are built once.</b> Switching tabs mounts and unmounts row containers held in
 * {@link #grids}; it never rebuilds a card. Rebuilding would drop the selection state and register
 * a second listener on every card of the tab being reopened.
 */
public class StepMissionType implements StepValues {

  private static final float CARD_W = 256f;

  /**
   * Card height, and it cannot be reduced.
   *
   * <p>A card stacks 163 px of content — a 10 px spacer, a 48 px icon, the title, the subtitle, the
   * value line and a 35 px badge with its gaps — inside a box whose usable height is {@code CARD_H
   * − 24}: its {@code card-mission} skin is a 9-slice with a 12 px corner inset, which Lemur turns
   * into a margin on every side. The content therefore already overruns that inner area by 11 px at
   * 176 and stops one pixel short of the card's outer edge. {@code SelectableCard.centerH}
   * compensates the horizontal half of that margin by hand (its {@code − 24}); nothing compensates
   * the vertical half. Anything under 176 pushes the badge out of the card, which is what 164 did.
   */
  private static final float CARD_H = 176f;

  private static final float ICON_SIZE = 48f;
  private static final float CARD_GAP = 16f;
  private static final float ROW_GAP = 12f;

  /** Space between the step title and the tab strip. */
  private static final float TITLE_GAP = 10f;

  /** Cards per row; the Earth tab therefore lays out as 3 + 2. */
  private static final int CARDS_PER_ROW = 3;

  /** Width of a full row of cards, and the panel's inner width — the two are equal by design. */
  private static final float GRID_WIDTH = CARDS_PER_ROW * CARD_W + (CARDS_PER_ROW - 1) * CARD_GAP;

  private static final float PANEL_PAD_X = (FormStyles.CONTENT_WIDTH - GRID_WIDTH) / 2f;

  /**
   * Thin, and deliberately: the cards cannot shrink (see {@link #CARD_H}), so the frame is what
   * pays for itself. Six pixels of gutter plus the 12 px each card already insets its own content
   * leave 18 px between the frame and anything drawn in a card.
   */
  private static final float PANEL_PAD_Y = 6f;

  /**
   * The panel keeps the height of the tallest tab, so switching tabs does not resize the folder.
   */
  private static final float PANEL_HEIGHT = 2 * CARD_H + ROW_GAP + 2 * PANEL_PAD_Y;

  /** 9-slice corner inset of {@code tab-panel}, a 28x28 texture like {@code card-mission}. */
  private static final int PANEL_BORDER = 12;

  private final Container root;
  private final Container panel;
  private final MissionDomainTabs tabs;
  private final Map<MissionProfile, SelectableCard> cards = new EnumMap<>(MissionProfile.class);
  private final Map<MissionDomain, Container> grids = new EnumMap<>(MissionDomain.class);
  private MissionProfile selectedProfile;
  private Consumer<MissionProfile> onProfileSelected = profile -> {};

  /**
   * Builds the mission-type step.
   *
   * <p>When {@code locked}, the wizard is editing an existing mission and the cards of the
   * <em>other</em> mission type are inert: a mission's stages, propellant budget and payload
   * eligibility derive from its type, and {@code MissionEntry.applySpec} refuses a spec that would
   * change it. The cards sharing the edited mission's type stay live, because switching between
   * them only changes the target — which is precisely what editing a mission means. A tab holding
   * no live card at all is inert too, and says so rather than making the user click to find out.
   *
   * @param missionContext the context whose selected type drives the downstream steps
   * @param initialProfile the profile to show as selected, whose tab the step opens on
   * @param locked {@code true} to forbid changing the mission type (wizard edit mode)
   */
  public StepMissionType(
      MissionContext missionContext, MissionProfile initialProfile, boolean locked) {
    this.selectedProfile = initialProfile;
    missionContext.setSelectedMissionType(initialProfile.missionType());
    root = new Container(new BoxLayout(Axis.Y, FillMode.None));
    root.setBackground(null);
    root.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, FormStyles.CONTENT_HEIGHT, 0));

    Label title = root.addChild(new Label("MISSION TYPE", FormStyles.STYLE));
    title.setFont(UiKit.orbitron(13));
    title.setColor(FormStyles.TEXT_PRIMARY);

    root.addChild(UiKit.vSpacer(TITLE_GAP));

    // The "// select the target orbit" line the five wizard pages otherwise share is gone from this
    // one: the tabs say what it said, and its 27 px are what the strip is paid with (L6 §4).
    tabs =
        new MissionDomainTabs(
            initialProfile.domain(),
            reachableDomains(initialProfile, locked),
            FormStyles.CONTENT_WIDTH);
    root.addChild(tabs.getNode());

    panel = root.addChild(new Container(new BoxLayout(Axis.Y, FillMode.None), FormStyles.STYLE));
    // The skin's margin is the panel's padding: Lemur draws the frame at the box's full size and
    // lays the grid inside the margin (see MissionDomainTabs.applyStates for the same reasoning).
    TbtQuadBackgroundComponent panelSkin = UiKit.wizardBg9("tab-panel", PANEL_BORDER);
    panelSkin.setMargin(PANEL_PAD_X, PANEL_PAD_Y);
    panel.setBackground(panelSkin);
    panel.setPreferredSize(new Vector3f(FormStyles.CONTENT_WIDTH, PANEL_HEIGHT, 0));

    for (MissionDomain domain : MissionDomain.values()) {
      grids.put(domain, buildGrid(domain, initialProfile, locked, missionContext));
    }
    tabs.setOnSelected(this::showDomain);
    mountGrid(initialProfile.domain());
  }

  /** The tabs the user can open: all of them, unless editing leaves one with no live card. */
  private static Set<MissionDomain> reachableDomains(
      MissionProfile initialProfile, boolean locked) {
    Set<MissionDomain> reachable = EnumSet.allOf(MissionDomain.class);
    if (locked) {
      reachable.removeIf(domain -> !domain.enabledUnderLock(initialProfile.missionType()));
    }
    return reachable;
  }

  private Container buildGrid(
      MissionDomain domain,
      MissionProfile initialProfile,
      boolean locked,
      MissionContext missionContext) {
    Container grid = new Container(new BoxLayout(Axis.Y, FillMode.None));
    grid.setBackground(null);

    List<MissionProfile> profiles = domain.profiles();
    Container row = null;
    for (int i = 0; i < profiles.size(); i++) {
      if (i % CARDS_PER_ROW == 0) {
        if (row != null) {
          grid.addChild(UiKit.vSpacer(ROW_GAP));
        }
        row = grid.addChild(new Container(new BoxLayout(Axis.X, FillMode.None)));
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
    return grid;
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
   * Two cards do not say "available", and for two different reasons — which is why there is one
   * wording per constant rather than one per variant.
   *
   * <p>The MEO's is the catalog: it needs an upper stage holding a 2 h 58 coast, or a payload whose
   * kick motor takes the apogee burn over (spec {@code 01} §6), and saying so on the card is what
   * keeps the refusal at the launcher step from reading as a surprise. The lunar one is the
   * calendar: nothing refuses the mission, but its date is not free (MIS-4 / L5 §2.3).
   */
  private static Badge badgeFor(MissionProfile profile) {
    return switch (profile.availability()) {
      case AVAILABLE -> new Badge("AVAILABLE", Badge.Variant.SUCCESS);
      case CONSTRAINED -> new Badge("LONG-COAST STAGE OR AKM", Badge.Variant.WARNING);
      case WINDOWED -> new Badge("LAUNCH WINDOW REQUIRED", Badge.Variant.WARNING);
    };
  }

  /**
   * Fills the last row up to the full grid width, so a short row's cards keep the size and the left
   * alignment of a full one.
   *
   * <p>No trailing spacer any more: the panel's inner width is {@link #GRID_WIDTH} by construction,
   * the horizontal padding being derived from it rather than chosen.
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
  }

  private void showDomain(MissionDomain domain) {
    mountGrid(domain);
    tabs.setActive(domain);
  }

  private void mountGrid(MissionDomain domain) {
    panel.clearChildren();
    panel.addChild(grids.get(domain));
  }

  /**
   * Moves the selection, and tells everyone who needs to know in the two languages they speak: the
   * mission <em>type</em> goes to {@code MissionContext}, which the launcher step reads to narrow
   * its payload list, and the <em>profile</em> goes to the listener, because the parameters step
   * needs to know which of the four Earth-orbit presets it is showing.
   *
   * <p>The outgoing profile is captured before the field moves. Assigning first made both lookups
   * land on the same card, so the card being left kept its selected skin and two cards stayed lit —
   * a defect introduced with the six-card grid (MIS-7 P2) and repaired here. The card to extinguish
   * may sit in the tab that is not mounted; {@link #cards} holds all six regardless.
   */
  private void select(MissionProfile profile, MissionContext missionContext) {
    if (profile == selectedProfile) {
      return;
    }
    MissionProfile previous = selectedProfile;
    selectedProfile = profile;
    cards.get(previous).applyState(SelectableCard.State.IDLE);
    cards.get(profile).applyState(SelectableCard.State.SELECTED);
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
