package com.smousseur.orbitlab.engine.scene.calibration;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A lat/long grid drawn on a body's globe from the mesh's own UV parameterisation (L2 of {@code
 * docs/orientation-planetes/01-decoupage.md}).
 *
 * <p><b>Why from the UVs and not from the axes.</b> A grid built on the reference axes would sit
 * where the application believes the body's longitudes are, and would look perfectly correct on a
 * model turned ninety degrees away — the texture would slide under it and nothing would say so.
 * Built on the measured frame, the grid is attached to the map: it rides with the asset, so what
 * the eye compares is the grid against the physics, and any offset in the chain shows as the grid
 * landing somewhere the physics says it should not.
 *
 * <p>That also makes it honest about the two defects a purely geometric probe is blind to. A
 * mirrored map makes the grid run backwards round the pole, and a map stored the wrong way up puts
 * the labelled poles on the wrong ends.
 */
public final class GraticuleMesh {

  /** Meridians drawn, one every 30° of texture longitude. */
  public static final int MERIDIANS = 12;

  /** Parallels drawn, at every 30° of colatitude, poles excluded. */
  public static final int PARALLELS = 5;

  /** Samples along each line. Enough that a great circle reads as a curve at any zoom. */
  private static final int SAMPLES = 72;

  private static final ColorRGBA GRID = new ColorRGBA(0.35f, 0.75f, 0.85f, 0.55f);
  private static final ColorRGBA EQUATOR = new ColorRGBA(0.55f, 0.95f, 1f, 0.95f);
  private static final ColorRGBA COLUMN_ZERO = new ColorRGBA(1f, 0.75f, 0.25f, 0.95f);

  private GraticuleMesh() {}

  /**
   * Builds the grid for a measured frame.
   *
   * @param frame the frame the body's mesh was measured to carry
   * @param radius the globe's radius, in the units of the node the grid will hang off
   * @return a line mesh in the model's own axes, carrying a colour per vertex
   */
  public static Mesh build(MeshFrame frame, float radius) {
    Objects.requireNonNull(frame, "frame");
    List<Vector3f> points = new ArrayList<>();
    List<ColorRGBA> colours = new ArrayList<>();

    for (int meridian = 0; meridian < MERIDIANS; meridian++) {
      double column = (double) meridian / MERIDIANS;
      ColorRGBA colour = meridian == 0 ? COLUMN_ZERO : GRID;
      for (int step = 0; step < SAMPLES; step++) {
        addSegment(
            points,
            colours,
            colour,
            frame,
            radius,
            column,
            (double) step / SAMPLES,
            column,
            (double) (step + 1) / SAMPLES);
      }
    }

    for (int parallel = 1; parallel <= PARALLELS; parallel++) {
      double row = (double) parallel / (PARALLELS + 1);
      ColorRGBA colour = parallel * 2 == PARALLELS + 1 ? EQUATOR : GRID;
      for (int step = 0; step < SAMPLES; step++) {
        addSegment(
            points,
            colours,
            colour,
            frame,
            radius,
            (double) step / SAMPLES,
            row,
            (double) (step + 1) / SAMPLES,
            row);
      }
    }

    Mesh mesh = new Mesh();
    mesh.setMode(Mesh.Mode.Lines);
    mesh.setBuffer(
        VertexBuffer.Type.Position,
        3,
        BufferUtils.createFloatBuffer(points.toArray(Vector3f[]::new)));
    mesh.setBuffer(VertexBuffer.Type.Color, 4, colourBuffer(colours));
    mesh.updateBound();
    return mesh;
  }

  private static void addSegment(
      List<Vector3f> points,
      List<ColorRGBA> colours,
      ColorRGBA colour,
      MeshFrame frame,
      float radius,
      double fromColumn,
      double fromRow,
      double toColumn,
      double toRow) {
    points.add(TexturePainting.directionOf(frame, fromColumn, fromRow).multLocal(radius));
    points.add(TexturePainting.directionOf(frame, toColumn, toRow).multLocal(radius));
    colours.add(colour);
    colours.add(colour);
  }

  private static java.nio.FloatBuffer colourBuffer(List<ColorRGBA> colours) {
    java.nio.FloatBuffer buffer = BufferUtils.createFloatBuffer(colours.size() * 4);
    for (ColorRGBA colour : colours) {
      buffer.put(colour.r).put(colour.g).put(colour.b).put(colour.a);
    }
    buffer.flip();
    return buffer;
  }
}
