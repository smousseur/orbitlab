package com.smousseur.orbitlab.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the HUD dismissal registry. No Lemur, no JME: surfaces are plain suppliers and
 * runnables, the way {@code AppMenuModelTest} exercises the menu's logic.
 */
class HudSurfacesTest {

  /** A surface that is open until it is dismissed, recording the order of dismissals. */
  private static HudSurface tracked(
      String name, float layer, List<String> log, AtomicBoolean open) {
    return new HudSurface(
        name,
        layer,
        open::get,
        () -> {
          log.add(name);
          open.set(false);
        });
  }

  @Test
  void anEmptyRegistryHasNothingToDismiss() {
    HudSurfaces surfaces = new HudSurfaces();

    assertEquals(Optional.empty(), surfaces.topmostOpen());
    assertFalse(surfaces.dismissTopmost());
  }

  @Test
  void aClosedSurfaceIsNeverReturned() {
    HudSurfaces surfaces = new HudSurfaces();
    surfaces.register(new HudSurface("window", 20f, () -> false, () -> {}));

    assertEquals(Optional.empty(), surfaces.topmostOpen());
    assertFalse(surfaces.dismissTopmost());
  }

  @Test
  void theOnlyOpenSurfaceIsDismissed() {
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    surfaces.register(tracked("window", 20f, log, new AtomicBoolean(true)));

    assertTrue(surfaces.dismissTopmost());
    assertEquals(List.of("window"), log);
  }

  @Test
  void theHighestOpenLayerWinsWhateverTheRegistrationOrder() {
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    AtomicBoolean dialogOpen = new AtomicBoolean(true);
    AtomicBoolean windowOpen = new AtomicBoolean(true);
    // Registered low-to-high on purpose: the ordering must come from the layer, not the order.
    surfaces.register(tracked("window", 20f, log, windowOpen));
    surfaces.register(tracked("dialog", 201f, log, dialogOpen));

    assertTrue(surfaces.dismissTopmost());
    assertTrue(surfaces.dismissTopmost());
    assertEquals(List.of("dialog", "window"), log);
  }

  @Test
  void aHigherButClosedSurfaceDoesNotShieldALowerOpenOne() {
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    surfaces.register(tracked("window", 20f, log, new AtomicBoolean(true)));
    surfaces.register(new HudSurface("dialog", 201f, () -> false, () -> log.add("dialog")));

    assertTrue(surfaces.dismissTopmost());
    assertEquals(List.of("window"), log);
  }

  @Test
  void surfacesOnTheSameLayerAreDismissedMostRecentlyRegisteredFirst() {
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    surfaces.register(tracked("first", 201f, log, new AtomicBoolean(true)));
    surfaces.register(tracked("second", 201f, log, new AtomicBoolean(true)));

    assertTrue(surfaces.dismissTopmost());
    assertEquals(List.of("second"), log);
  }

  @Test
  void dismissingASurfaceLeavesItRegisteredForItsNextOpening() {
    // The normal life of a surface: its owner registers it once at initialization, and the surface
    // then opens and closes many times. Only the handle unregisters, never a dismissal.
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    AtomicBoolean open = new AtomicBoolean(true);
    surfaces.register(tracked("window", 20f, log, open));

    assertTrue(surfaces.dismissTopmost());
    assertFalse(surfaces.dismissTopmost());

    open.set(true);

    assertTrue(surfaces.dismissTopmost());
    assertEquals(List.of("window", "window"), log);
  }

  @Test
  void closingTheHandleUnregistersTheSurface() throws Exception {
    HudSurfaces surfaces = new HudSurfaces();
    List<String> log = new ArrayList<>();
    AutoCloseable handle = surfaces.register(tracked("window", 20f, log, new AtomicBoolean(true)));

    handle.close();

    assertEquals(Optional.empty(), surfaces.topmostOpen());
    assertFalse(surfaces.dismissTopmost());
    assertTrue(log.isEmpty());
  }

  @Test
  void aSurfaceCanBeQueriedByName() {
    HudSurfaces surfaces = new HudSurfaces();
    surfaces.register(new HudSurface("missionManagement", 20f, () -> true, () -> {}));
    surfaces.register(new HudSurface("appMenu", 60f, () -> false, () -> {}));

    assertTrue(surfaces.isOpen("missionManagement"));
    assertFalse(surfaces.isOpen("appMenu"));
    assertFalse(surfaces.isOpen("wizard"));
  }

  @Test
  void registeringNullIsRejected() {
    HudSurfaces surfaces = new HudSurfaces();

    assertThrows(NullPointerException.class, () -> surfaces.register(null));
  }

  @Test
  void aSurfaceRequiresAllItsParts() {
    assertThrows(NullPointerException.class, () -> new HudSurface(null, 20f, () -> true, () -> {}));
    assertThrows(NullPointerException.class, () -> new HudSurface("window", 20f, null, () -> {}));
    assertThrows(NullPointerException.class, () -> new HudSurface("window", 20f, () -> true, null));
  }
}
