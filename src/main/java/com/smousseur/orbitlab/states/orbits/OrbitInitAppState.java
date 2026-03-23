package com.smousseur.orbitlab.states.orbits;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Geometry;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.OrbitLineFactory;
import com.smousseur.orbitlab.engine.scene.PlanetColors;
import com.smousseur.orbitlab.engine.scene.graph.SceneGraph;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Application state that loads precomputed orbital path data from disk and builds the
 * initial orbit line geometry for each configured celestial body.
 *
 * <p>During initialization, reads binary orbit dataset files containing heliocentric
 * position samples and creates colored line-strip geometries attached to the scene graph's
 * orbit layer. This provides the static visual representation of planetary orbits.
 */
public class OrbitInitAppState extends BaseAppState {

  private static final Logger logger = LogManager.getLogger(OrbitInitAppState.class);
  private final SceneGraph.OrbitLayer orbitLayer;
  private final EnumSet<SolarSystemBody> bodies;
  private final Path datasetDir = Path.of("dataset", "orbits");

  /**
   * Creates a new orbit initialization state.
   *
   * @param context the application context providing scene graph and orbit body configuration
   */
  public OrbitInitAppState(ApplicationContext context) {
    orbitLayer = context.sceneGraph().orbits();
    bodies = context.config().orbitBodies();
  }

  @Override
  protected void initialize(Application app) {
    for (SolarSystemBody body : bodies) {
      try {
        List<Vector3D> pts = readOrbitData(body);
        if (pts == null) {
          continue; // no dataset file for this body — runtime will compute it
        }
        Geometry orbitGeometry =
            OrbitLineFactory.buildBodyRelativeLineStrip(
                body, pts, PlanetColors.colorFor(body), 1.f);
        orbitLayer.orbitNode(body).attachChild(orbitGeometry);
      } catch (IOException e) {
        logger.warn("Failed to read orbit dataset for {} — skipping: {}", body, e.getMessage());
      }
    }
  }

  private List<Vector3D> readOrbitData(SolarSystemBody body) throws IOException {
    Path orbitFile = this.datasetDir.resolve(body.name() + "-orbit.bin");
    if (!Files.isRegularFile(orbitFile)) {
      logger.info("No orbit dataset file for {} — will be computed at runtime: {}", body, orbitFile);
      return null;
    }
    List<Vector3D> results = new ArrayList<>();
    try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(orbitFile)))) {
      int count = in.readInt();
      for (int i = 0; i < count; i++) {
        results.add(new Vector3D(in.readDouble(), in.readDouble(), in.readDouble()));
      }
    }
    return results;
  }

  @Override
  protected void cleanup(Application app) {}

  @Override
  protected void onEnable() {}

  @Override
  protected void onDisable() {}
}
