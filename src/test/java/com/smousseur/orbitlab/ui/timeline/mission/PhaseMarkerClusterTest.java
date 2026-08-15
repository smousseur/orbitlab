package com.smousseur.orbitlab.ui.timeline.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Declustering rules of {@link PhaseMarkerCluster} (spec §8). */
class PhaseMarkerClusterTest {

  private static final float MIN_X = 14f + PhaseMarkerCluster.MARKER_WIDTH_PX / 2f;
  private static final float MAX_X = 586f - PhaseMarkerCluster.MARKER_WIDTH_PX / 2f;

  private static List<PhaseMarkerCluster.Anchor> anchors(float... xs) {
    List<PhaseMarkerCluster.Anchor> list = new ArrayList<>();
    for (int i = 0; i < xs.length; i++) {
      list.add(new PhaseMarkerCluster.Anchor(i + 1, xs[i]));
    }
    return list;
  }

  private static List<PhaseMarkerCluster> cluster(List<PhaseMarkerCluster.Anchor> anchors) {
    return PhaseMarkerCluster.cluster(
        anchors, PhaseMarkerCluster.MIN_SPACING_PX, MIN_X, MAX_X);
  }

  @Test
  void wellSeparatedMarkersStayIndividual() {
    List<PhaseMarkerCluster> out = cluster(anchors(100f, 200f, 300f));
    assertEquals(3, out.size());
    for (PhaseMarkerCluster c : out) {
      assertEquals(1, c.size());
      assertFalse(c.isGroup());
    }
  }

  @Test
  void aRealisticGeoAscentCollapsesIntoOneGroup() {
    // Four transitions spread over 5.2 px at the very start of the track, as measured on the
    // reference GEO, then two far-apart later ones.
    List<PhaseMarkerCluster> out =
        cluster(anchors(14.14f, 16.0f, 17.9f, 19.34f, 240f, 470f));
    assertEquals(3, out.size());
    assertEquals(4, out.get(0).size());
    assertTrue(out.get(0).isGroup());
    assertEquals(1, out.get(1).size());
    assertEquals(1, out.get(2).size());
  }

  @Test
  void aGroupSitsOnItsFirstTransitionNotOnItsBarycentre() {
    List<PhaseMarkerCluster> out = cluster(anchors(300f, 303f, 306f));
    assertEquals(1, out.size());
    assertEquals(300f, out.get(0).x(), 1e-4f);
    assertEquals(1, out.get(0).firstRunIndex());
  }

  @Test
  void spacingIsMeasuredFromTheGroupAnchorNotFromTheLastMemberSoAChainCannotDrift() {
    // 300, 307, 314, 321: each is 7 px from its predecessor but 21 px from the anchor.
    List<PhaseMarkerCluster> out = cluster(anchors(300f, 307f, 314f, 321f));
    assertEquals(2, out.size());
    assertEquals(2, out.get(0).size());
    assertEquals(300f, out.get(0).x(), 1e-4f);
    assertEquals(314f, out.get(1).x(), 1e-4f);
  }

  @Test
  void noMarkerIsEverLost() {
    List<PhaseMarkerCluster.Anchor> in = anchors(14.1f, 15f, 16f, 17f, 90f, 91f, 400f);
    int total = 0;
    for (PhaseMarkerCluster c : cluster(in)) {
      total += c.size();
    }
    assertEquals(in.size(), total);
  }

  @Test
  void markersAreHeldOffBothEdgesByHalfTheirWidth() {
    List<PhaseMarkerCluster> out = cluster(anchors(14.14f, 585.9f));
    assertEquals(MIN_X, out.get(0).x(), 1e-4f);
    assertEquals(MAX_X, out.get(1).x(), 1e-4f);
  }

  @Test
  void runIndicesKeepChronologicalOrderInsideAGroup() {
    List<PhaseMarkerCluster> out = cluster(anchors(300f, 302f, 304f));
    assertEquals(List.of(1, 2, 3), out.get(0).runIndices());
  }

  @Test
  void noAnchorsGivesNoClusters() {
    assertTrue(cluster(List.of()).isEmpty());
  }
}
