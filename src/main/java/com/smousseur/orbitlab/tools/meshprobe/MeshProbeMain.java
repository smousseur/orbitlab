package com.smousseur.orbitlab.tools.meshprobe;

import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.mesh.MeshConformance;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrameProbe;
import com.smousseur.orbitlab.engine.scene.mesh.ProbedGeometry;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI entry-point reporting, for every planetary model, the frame its geometry carries and whether
 * it conforms to the export convention (see {@code docs/orientation-planetes/01-decoupage.md}, L0).
 *
 * <p>The report is meant to be read, and its rows copied into the javadoc of whatever ends up
 * holding a correction — deliberately not generated code, which would relit itself badly and add a
 * build step for no gain.
 *
 * <p>Usage:
 *
 * <pre>
 *   ./gradlew meshProbe
 * </pre>
 *
 * <p>or, against a packaged build:
 *
 * <pre>
 *   java -cp build/libs/orbitlab.jar com.smousseur.orbitlab.tools.meshprobe.MeshProbeMain
 * </pre>
 */
public final class MeshProbeMain {

  private MeshProbeMain() {}

  /**
   * Entry point. Loads every planetary model through the same JME loader the application uses — so
   * the frame reported is the one the renderer will actually see, not the one the raw file happens
   * to hold — and prints one row per measurable geometry.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    // The GLTF loader logs an unsupported-extension warning per material, which would bury a
    // report whose whole point is to be read.
    Logger.getLogger("com.jme3").setLevel(Level.SEVERE);
    AssetManager assetManager = new DesktopAssetManager(true);
    System.out.printf(
        Locale.ROOT,
        "%-9s %-26s %-22s %-22s %8s %10s  %s%n",
        "body",
        "geometry",
        "pole",
        "u=0",
        "residual",
        "deg/u",
        "verdict");

    for (SolarSystemBody body : SolarSystemBody.values()) {
      String name = body.displayName().toLowerCase(Locale.ROOT);
      Spatial model;
      try {
        model = assetManager.loadModel("models/planets/" + name + "/" + name + ".gltf");
      } catch (RuntimeException e) {
        System.out.printf(Locale.ROOT, "%-9s NOT LOADED: %s%n", name, e.getMessage());
        continue;
      }
      List<ProbedGeometry> probed = MeshFrameProbe.probe(model);
      if (probed.isEmpty()) {
        System.out.printf(Locale.ROOT, "%-9s no measurable geometry%n", name);
        continue;
      }
      for (ProbedGeometry geometry : probed) {
        if (!geometry.hasFrame()) {
          System.out.printf(
              Locale.ROOT,
              "%-9s %-26s %-22s %-22s %8s %10s  %s%n",
              name,
              abbreviate(geometry.name()),
              "-",
              "-",
              "-",
              "-",
              "NO UV MAP - nothing to measure");
          continue;
        }
        MeshFrame frame = geometry.frame();
        System.out.printf(
            Locale.ROOT,
            "%-9s %-26s %-22s %-22s %8.2f %10.1f  %s%n",
            name,
            abbreviate(geometry.name()),
            format(frame.pole()),
            format(frame.primeMeridian()),
            frame.equirectangularResidualDeg(),
            frame.azimuthDegreesPerU(),
            describe(MeshConformance.of(frame)));
      }
    }
  }

  private static String describe(MeshConformance verdict) {
    return switch (verdict) {
      case MeshConformance.Conforming ignored -> "conforming";
      case MeshConformance.NeedsRotation rotation ->
          String.format(
              Locale.ROOT,
              "rotate %.1f deg about %s",
              rotation.angleDeg(),
              format(rotation.axis()));
      case MeshConformance.Mirrored mirrored ->
          String.format(
              Locale.ROOT,
              "MIRRORED (%.1f deg/u) - needs a UV flip, no rotation can fix it",
              mirrored.azimuthDegreesPerU());
      case MeshConformance.NotALatLongMap notMap ->
          String.format(
              Locale.ROOT,
              "NOT A LAT/LONG MAP (residual %.1f deg) - nothing measurable",
              notMap.residualDeg());
    };
  }

  private static String format(Vector3f v) {
    return String.format(Locale.ROOT, "(%+.3f,%+.3f,%+.3f)", v.x, v.y, v.z);
  }

  private static String abbreviate(String name) {
    return name == null ? "?" : name.length() <= 26 ? name : name.substring(0, 25) + "~";
  }
}
