package com.smousseur.orbitlab.ui.mission.wizard;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import java.util.Arrays;
import java.util.List;

/**
 * A tab of the wizard's first step: where the missions of a group of cards fly (MIS-5 / L6 §3).
 *
 * <p><b>A domain is not a {@link MissionType}, and not a mission profile either.</b> It is the
 * coarsest classification the first screen needs: six cards no longer fit one grid, and grouping
 * them by destination is the only split that stays true as the catalog grows — a seventh card is a
 * lunar orbit before it is anything else.
 *
 * <p><b>Deliberately free of Lemur</b>, like {@link MissionProfile}: it carries a label and answers
 * questions about profiles, never about widgets, so the tab a card belongs to is unit-testable
 * where the step that draws it is not.
 *
 * <p>The taxonomy is duplicated from {@code Payloads.domainOf}, on purpose and under a test.
 * Reading the catalog from here would drag its static initialisation into {@code
 * MissionProfileTest} and would route the question through {@code MissionType}, which four of the
 * six cards share; {@code MissionDomainTest} pins the two tables together instead, so they cannot
 * drift in silence.
 */
public enum MissionDomain {

  /**
   * Anything that stays around the Earth: the four low-orbit presets and the geostationary belt.
   */
  EARTH("EARTH"),

  /**
   * Anything aimed at the Moon.
   *
   * <p>Labelled {@code MOON} and not {@code LUNAR} because {@code MissionProfile.LUNAR.title()} is
   * already {@code "LUNAR"}: a card and its tab bearing the same word at two levels reads as a
   * repetition rather than as a hierarchy, and {@code L7} adds a {@code LUNAR ORBIT} card beside
   * it. The tab names the destination, the cards name the orbits.
   */
  LUNAR("MOON");

  private final String label;

  MissionDomain(String label) {
    this.label = label;
  }

  /**
   * @return the text the tab shows
   */
  public String label() {
    return label;
  }

  /**
   * The cards this tab holds, in the order {@link MissionProfile} declares them.
   *
   * <p><b>Not to be confused with {@link MissionProfile#earthOrbitProfiles()}</b>, which answers a
   * different question and a different count: that one filters on {@code missionType() == LEO} to
   * pick a parameter panel, so it returns four profiles and leaves GEO out. This one returns the
   * five cards of the Earth tab, GEO included.
   *
   * <p>Computed on each call rather than cached in a static field: the constants of {@code
   * MissionProfile} name a {@code MissionDomain}, so a static initialiser here reading {@code
   * MissionProfile.values()} would run inside a circular class initialisation and observe an
   * unfinished array. The call sites build a wizard step, and there are two of them.
   *
   * @return the profiles of this domain, in card order
   */
  public List<MissionProfile> profiles() {
    return Arrays.stream(MissionProfile.values())
        .filter(profile -> profile.domain() == this)
        .toList();
  }

  /**
   * Whether this tab is reachable while the wizard edits an existing mission.
   *
   * <p>Stated as <b>"holds at least one selectable card"</b> rather than "is the edited mission's
   * domain". The two agree today, but only the first survives a domain holding two mission types of
   * which one is being edited — which is what the lunar tab becomes at {@code L7}. A tab leading
   * only to greyed cards is a promise of navigation that does not hold, and the label can say so
   * one click earlier than the cards can.
   *
   * <p>Disabling is safe because of an invariant that holds for every mission type, and that {@code
   * MissionDomainTest} pins: in edit mode exactly one tab is entirely inert, and it is never the
   * one the step opens on. Disabling can therefore not strand the user on a page they cannot leave.
   *
   * @param lockedType the type of the mission being edited
   * @return {@code true} when at least one card of this tab can still be chosen
   */
  public boolean enabledUnderLock(MissionType lockedType) {
    return profiles().stream().anyMatch(profile -> profile.missionType() == lockedType);
  }
}
