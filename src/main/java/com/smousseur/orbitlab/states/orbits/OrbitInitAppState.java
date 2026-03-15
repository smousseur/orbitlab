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

/**
 * Application state that loads precomputed orbital path data from disk and builds the
 * initial orbit line geometry for each configured celestial body.
 *
 * <p>During initialization, reads binary orbit dataset files containing heliocentric
 * position samples and creates colored line-strip geometries attached to the scene graph's
 * orbit layer. This provides the static visual representation of planetary orbits.
 */
public class OrbitInitAppState extends BaseAppState {

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
    try {
      for (SolarSystemBody body : bodies) {
        List<Vector3D> pts = readOrbitData(body);
        Geometry orbitGeometry =
            OrbitLineFactory.buildBodyRelativeLineStrip(
                body, pts, PlanetColors.colorFor(body), 1.f);
        orbitLayer.orbitNode(body).attachChild(orbitGeometry);
      }
    } catch (IOException e) {
      throw new OrbitlabException("Cannot read orbit dataset: " + e.getMessage());
    }
  }

  private List<Vector3D> readOrbitData(SolarSystemBody body) throws IOException {
    Path orbitFile = this.datasetDir.resolve(body.name() + "-orbit.bin");
    List<Vector3D> results = new ArrayList<>();
    if (!Files.isRegularFile(orbitFile)) {
      throw new OrbitlabException("Missing orbit dataset file: " + orbitFile);
    }
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
