package com.smousseur.orbitlab.states.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jme3.math.Vector3f;
import com.smousseur.orbitlab.engine.scene.spacecraft.PieceGeometry;
import org.junit.jupiter.api.Test;

/** The point a turned mesh pivots about: its middle, on its own axis. */
class TrackedObjectViewPivotTest {

  @Test
  void aLauncherPiecePivotsHalfwayUpItsBody() {
    Vector3f pivot = TrackedObjectView.pivotOf(new PieceGeometry(0.383f, 0.02f));
    assertEquals(new Vector3f(0f, 0.1915f, 0f), pivot);
  }

  @Test
  void aPayloadPivotsOnItsOwnAxisWhereverItsOriginIs() {
    Vector3f pivot = TrackedObjectView.pivotOf(new PieceGeometry(0.51f, 0.3f, -1.96f, 0.4f, -0.7f));
    assertEquals(0.4f, pivot.x, 1e-6f);
    assertEquals(-0.725f, pivot.y, 1e-6f);
    assertEquals(-0.7f, pivot.z, 1e-6f);
  }
}
