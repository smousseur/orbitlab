package com.smousseur.orbitlab.engine.scene.spacecraft;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.simulation.mission.vehicle.catalog.Launchers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The nozzle clusters of the launcher silhouettes that fire: where each cluster's exit plane is,
 * its centre and its radius, in the frame the silhouettes are drawn in — one unit is the full
 * stack, the base at the origin — and in metres at the catalog stack height. NOT a gate: it asserts
 * nothing. Run with {@code -Dorbitlab.probe=true --tests '*Fx3NozzleProbe*'}; the report is also
 * written to {@code build/fx3-nozzles.txt}.
 *
 * <p>Each cluster is measured two ways wherever both exist. <b>Named</b>: the subtree of the node
 * the asset names after its engines — its lowest vertex is the exit plane, its box's centre the
 * centre, its widest vertex about that centre the radius. <b>Bottom slice</b>: the vertices of the
 * block's own subtree within {@link NozzleSlices#BAND} of its lowest one, read the same way, with
 * the meshes they come from. The slice is the cluster: a Falcon Heavy core's named engine node
 * holds its centre Merlin alone, the eight others being one unnamed mesh beside it whose exit rims
 * sit 8 cm higher — 33 vertices per rim, so a slice of 297 is the nine — while the landing legs
 * start 1.5 m up, well clear of the band. Two cross-checks close the report: the Falcon Heavy upper
 * stage's unnamed {@code after_s1} engine against the named one of the full stack, shifted down by
 * where the {@code after_s1} remnant sits in it, and the Ariane 64 booster nozzle against the
 * detached booster piece.
 */
@EnabledIfSystemProperty(named = "orbitlab.probe", matches = "true")
class Fx3NozzleProbe {

  private static final String FH = "models/vehicles/heavy_falcon/heavy_falcon";
  private static final String A64 = "models/vehicles/ariane_64/ariane_64";
  private static final float FH_HEIGHT_M = 70f;
  private static final float A64_HEIGHT_M = 63f;

  private final DesktopAssetManager assets = new DesktopAssetManager(true);
  private final List<String> report = new ArrayList<>();

  @Test
  void nozzleClustersOfTheLauncherSilhouettes() throws IOException {
    line("Falcon Heavy, stack %.0f m", FH_HEIGHT_M);
    Spatial fhFull = load(FH + ".gltf");
    row("FULL", "core: centre engine (named)", FH_HEIGHT_M, named(fhFull, "Engine Nozzle.005_2"));
    row("FULL", "core: bottom slice", FH_HEIGHT_M, slice(fhFull, "First Stage Center_16"));
    row("FULL", "left booster: centre (named)", FH_HEIGHT_M, named(fhFull, "Engine Nozzle.001_21"));
    row("FULL", "left booster: bottom slice", FH_HEIGHT_M, slice(fhFull, "Booster Left_31"));
    row(
        "FULL",
        "right booster: centre (named)",
        FH_HEIGHT_M,
        named(fhFull, "Engine Nozzle.002_32"));
    row("FULL", "right booster: bottom slice", FH_HEIGHT_M, slice(fhFull, "Booster Right_42"));
    Spatial fhAfterBoosters = load(FH + "-after_boosters.gltf");
    row(
        "AFTER_BOOSTERS",
        "core: centre engine (named)",
        FH_HEIGHT_M,
        named(fhAfterBoosters, "Engine Nozzle.005_2"));
    row(
        "AFTER_BOOSTERS",
        "core: bottom slice",
        FH_HEIGHT_M,
        slice(fhAfterBoosters, "First Stage Center_16"));
    NozzleSlices.Reading mvac =
        NozzleSlices.sliceOf(NozzleSlices.vertices(load(FH + "-after_s1.gltf")));
    row("AFTER_S1", "MVac: bottom slice (no name)", FH_HEIGHT_M, mvac);

    line("%nAriane 64, stack %.0f m", A64_HEIGHT_M);
    Spatial a64Full = load(A64 + ".gltf");
    row("FULL", "core Vulcain (named)", A64_HEIGHT_M, named(a64Full, "Engine Nozzle.001_2"));
    row("FULL", "core: bottom slice", A64_HEIGHT_M, slice(a64Full, "First Stage_3"));
    List<NozzleSlices.Reading> boosters = new ArrayList<>();
    for (String booster :
        new String[] {"Booster.001_4", "Booster.002_8", "Booster.003_9", "Booster_10"}) {
      NozzleSlices.Reading reading = slice(a64Full, booster);
      boosters.add(reading);
      row("FULL", booster + " P120C: slice", A64_HEIGHT_M, reading);
    }
    Spatial a64AfterBoosters = load(A64 + "-after_boosters.gltf");
    row(
        "AFTER_BOOSTERS",
        "core Vulcain (named)",
        A64_HEIGHT_M,
        named(a64AfterBoosters, "Engine Nozzle.001_2"));
    row(
        "AFTER_BOOSTERS",
        "core: bottom slice",
        A64_HEIGHT_M,
        slice(a64AfterBoosters, "First Stage_3"));
    Spatial a64AfterS1 = load(A64 + "-after_s1.gltf");
    row("AFTER_S1", "Vinci (named)", A64_HEIGHT_M, named(a64AfterS1, "Engine Nozzle.002_5"));
    row(
        "AFTER_S1",
        "Vinci: bottom slice",
        A64_HEIGHT_M,
        NozzleSlices.sliceOf(NozzleSlices.vertices(a64AfterS1)));

    line("%nCross-checks");
    float shift = 1f - (float) LauncherStackGeometry.afterS1Fraction(Launchers.FALCON_HEAVY.id());
    NozzleSlices.Reading fullMvac = named(fhFull, "Merlin Vaccum Engine_17");
    line(
        "  FH MVac: after_s1 slice exit %.4f, centre (%.4f, %.4f), radius %.4f | full-stack named"
            + " node shifted by -%.3f: exit %.4f, centre (%.4f, %.4f), radius %.4f",
        mvac.exitY(),
        mvac.centreX(),
        mvac.centreZ(),
        mvac.radius(),
        shift,
        fullMvac.exitY() - shift,
        fullMvac.centreX(),
        fullMvac.centreZ(),
        fullMvac.radius());
    NozzleSlices.Reading detached =
        NozzleSlices.sliceOf(NozzleSlices.vertices(load(A64 + "-booster.gltf")));
    line(
        "  A64 P120C: detached booster piece slice exit %.4f, radius %.4f | in the stack: exits"
            + " %s, radii %s",
        detached.exitY(),
        detached.radius(),
        boosters.stream().map(r -> String.format(Locale.ROOT, "%.4f", r.exitY())).toList(),
        boosters.stream().map(r -> String.format(Locale.ROOT, "%.4f", r.radius())).toList());

    Path out = Path.of("build/fx3-nozzles.txt");
    Files.createDirectories(out.getParent());
    Files.write(out, report, StandardCharsets.UTF_8);
  }

  private Spatial load(String path) {
    Spatial model = assets.loadModel(path);
    model.updateGeometricState();
    return model;
  }

  private static Spatial subtree(Spatial model, String name) {
    Spatial found = model instanceof Node node ? node.getChild(name) : null;
    if (found == null) {
      throw new IllegalStateException("no node named '" + name + "' in " + model.getName());
    }
    return found;
  }

  /** The named node's subtree read whole. */
  private static NozzleSlices.Reading named(Spatial model, String name) {
    return NozzleSlices.read(NozzleSlices.vertices(subtree(model, name)));
  }

  private static NozzleSlices.Reading slice(Spatial model, String block) {
    return NozzleSlices.sliceOf(NozzleSlices.vertices(subtree(model, block)));
  }

  private void row(String silhouette, String cluster, float stackMeters, NozzleSlices.Reading r) {
    line(
        "  %-15s %-31s exit %8.4f (%6.2f m) top %8.4f | centre (%8.4f, %8.4f) = (%6.2f, %6.2f) m"
            + " | radius %7.4f (%5.2f m) | %6d vertices: %s",
        silhouette,
        cluster,
        r.exitY(),
        r.exitY() * stackMeters,
        r.topY(),
        r.centreX(),
        r.centreZ(),
        r.centreX() * stackMeters,
        r.centreZ() * stackMeters,
        r.radius(),
        r.radius() * stackMeters,
        r.vertices(),
        r.meshes());
  }

  private void line(String format, Object... args) {
    String text = String.format(Locale.ROOT, format, args);
    System.out.println(text);
    report.add(text);
  }
}
