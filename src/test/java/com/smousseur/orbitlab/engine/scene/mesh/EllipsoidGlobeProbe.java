package com.smousseur.orbitlab.engine.scene.mesh;

import com.jme3.scene.Mesh;
import java.util.Arrays;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.orekit.utils.Constants;

/**
 * OBL-167 closure measurement on the real generated grid. NOT a gate: asserts nothing. Run with
 * {@code -Dorbitlab.probe=true --tests '*EllipsoidGlobeProbe*'}.
 *
 * <p>The depth is the height a WGS84 ground point floats above the drawn facet before the ground
 * correction, which brings it to zero. The three points are given in the model's own (latitude,
 * azimuth); one column per band suffices, every column being the same cell turned about the pole.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class EllipsoidGlobeProbe {

  private static final Logger logger = LogManager.getLogger(EllipsoidGlobeProbe.class);
  private static final double A = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

  @Test
  void facetDepthByLatitudeBand() {
    EllipsoidGlobe globe = EllipsoidGlobe.earth();
    for (int band = -90; band < 90; band += 5) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (double lat = band + 0.005; lat < band + 5; lat += 0.01) {
        for (int k = 0; k < 40; k++) {
          double depth = globe.facetDepth(lat, -180.0 + 0.025 * k) * A;
          min = Math.min(min, depth);
          max = Math.max(max, depth);
        }
      }
      logger.info(
          String.format(
              Locale.ROOT,
              "%+4d..%+4d deg: facet depth %7.1f .. %7.1f m",
              band,
              band + 5,
              min,
              max));
    }
    double[][] points = {{3.8, 117.1}, {5.23, -52.77}, {28.5, -80.6}};
    for (double[] p : points) {
      logger.info(
          String.format(
              Locale.ROOT,
              "(%.2f, %.2f): facet depth %.1f m",
              p[0],
              p[1],
              globe.facetDepth(p[0], p[1]) * A));
    }
  }

  @Test
  void meshGenerationCost() {
    long start = System.nanoTime();
    Mesh cold = EllipsoidGlobe.earth().toMesh();
    double coldMs = (System.nanoTime() - start) / 1e6;
    double[] warm = new double[20];
    for (int i = 0; i < warm.length; i++) {
      long t = System.nanoTime();
      EllipsoidGlobe.earth().toMesh();
      warm[i] = (System.nanoTime() - t) / 1e6;
    }
    Arrays.sort(warm);
    logger.info(
        String.format(
            Locale.ROOT,
            "toMesh: %d vertices, %d triangles, cold %.1f ms, warm median %.1f ms",
            cold.getVertexCount(),
            cold.getTriangleCount(),
            coldMs,
            warm[warm.length / 2]));
  }
}
