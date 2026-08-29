package com.smousseur.orbitlab.ui.mission.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Payloads;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.PayloadDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MIS-5 / L6 §8 — the tabs of the wizard's first step, decided without a widget.
 *
 * <p>Nothing here builds Lemur or reads Orekit: {@link MissionDomain} and {@link MissionProfile}
 * carry plain values, which is the whole reason the tab a card belongs to is decided in the model
 * rather than in the step that draws it.
 */
class MissionDomainTest {

  @Test
  @DisplayName("Every card belongs to exactly one tab")
  void theTabsPartitionTheCards() {
    List<MissionProfile> gathered = new ArrayList<>();
    for (MissionDomain domain : MissionDomain.values()) {
      gathered.addAll(domain.profiles());
    }
    assertEquals(
        MissionProfile.values().length, gathered.size(), "a card is shown twice or not at all");
    assertEquals(
        EnumSet.allOf(MissionProfile.class),
        EnumSet.copyOf(gathered),
        "the tabs do not cover the catalog of cards");
  }

  @Test
  @DisplayName("A tab lists its cards in declaration order")
  void aTabKeepsTheCardOrder() {
    for (MissionDomain domain : MissionDomain.values()) {
      List<MissionProfile> expected =
          Arrays.stream(MissionProfile.values()).filter(p -> p.domain() == domain).toList();
      assertEquals(expected, domain.profiles(), domain.name());
    }
  }

  /**
   * The accord the duplication is allowed by. {@code MissionDomain} states a taxonomy the catalog
   * already knows, so that {@code MissionProfile} stays out of {@code Payloads}; this is what makes
   * the two tables unable to drift apart in silence (L6 §3).
   */
  @Test
  @DisplayName("A card declares the domain the payload catalog gives its type")
  void theTabsAgreeWithTheCatalog() {
    for (MissionProfile profile : MissionProfile.values()) {
      PayloadDomain expected = Payloads.domainOf(profile.missionType());
      assertEquals(
          expected.name(),
          profile.domain().name(),
          () -> profile + " sits in the " + profile.domain() + " tab but flies " + expected);
    }
  }

  @Test
  @DisplayName("The lunar tab is labelled MOON, and no card is")
  void theTabLabelsDoNotEchoACardTitle() {
    assertEquals("MOON", MissionDomain.LUNAR.label());
    assertEquals("EARTH", MissionDomain.EARTH.label());
    for (MissionProfile profile : MissionProfile.values()) {
      for (MissionDomain domain : MissionDomain.values()) {
        assertNotEquals(
            domain.label(),
            profile.title(),
            () -> "the " + domain + " tab and the " + profile + " card would read alike");
      }
    }
  }

  /**
   * §3 — the distinction the javadoc warns about, pinned so that the two methods are not unified.
   * One answers which cards a tab shows, the other which cards take an Earth-orbit parameter panel,
   * and GEO is in the first and not in the second.
   */
  @Test
  @DisplayName("The Earth tab holds five cards where earthOrbitProfiles has four")
  void theEarthTabIsNotTheEarthOrbitPresets() {
    assertEquals(5, MissionDomain.EARTH.profiles().size());
    assertEquals(4, MissionProfile.earthOrbitProfiles().size());
    assertTrue(MissionDomain.EARTH.profiles().contains(MissionProfile.GEO));
    assertFalse(MissionProfile.earthOrbitProfiles().contains(MissionProfile.GEO));
  }

  @Test
  @DisplayName("A tab is reachable in edit mode when it still holds a selectable card")
  void lockingEnablesExactlyTheTabsHoldingTheEditedType() {
    // Four types since MIS-5 / L7, and no exception among them: the LUNAR_ORBIT skip this loop
    // carried was the last trace of the card that did not exist.
    for (MissionType edited : MissionType.values()) {
      for (MissionDomain domain : MissionDomain.values()) {
        boolean holdsIt = domain.profiles().stream().anyMatch(p -> p.missionType() == edited);
        assertEquals(holdsIt, domain.enabledUnderLock(edited), domain + " editing " + edited);
      }
    }
  }

  /**
   * The invariant that makes disabling a tab safe: whatever is being edited, the tab the step opens
   * on stays reachable, and exactly one other is not. Were it to fail, a user would land on a page
   * whose own tab is greyed and have nowhere to go.
   */
  @Test
  @DisplayName("Editing always leaves the opened tab reachable and exactly one tab inert")
  void exactlyOneTabIsInertAndItIsNeverTheOpenOne() {
    for (MissionProfile edited : MissionProfile.values()) {
      Set<MissionDomain> reachable =
          EnumSet.copyOf(
              Arrays.stream(MissionDomain.values())
                  .filter(domain -> domain.enabledUnderLock(edited.missionType()))
                  .toList());
      assertTrue(reachable.contains(edited.domain()), "editing " + edited + " strands its own tab");
      assertEquals(
          MissionDomain.values().length - 1,
          reachable.size(),
          "editing " + edited + " should leave exactly one tab inert");
    }
  }

  @Test
  @DisplayName("Two tabs, and the enum is what a card must name")
  void thereAreTwoTabs() {
    assertEquals(2, MissionDomain.values().length);
    assertSame(MissionDomain.LUNAR, MissionProfile.LUNAR.domain());
    assertSame(MissionDomain.EARTH, MissionProfile.GEO.domain());
  }
}
