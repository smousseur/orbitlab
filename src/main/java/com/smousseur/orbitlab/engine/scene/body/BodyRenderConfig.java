package com.smousseur.orbitlab.engine.scene.body;

import com.jme3.math.ColorRGBA;
import com.smousseur.orbitlab.app.view.RenderContext;

import java.util.Objects;

/**
 * Immutable configuration record holding all visual parameters needed to render a body (planet or
 * spacecraft) in the scene. Used by the shared view layer ({@link LodView}, {@link
 * com.smousseur.orbitlab.engine.scene.body.lod.Model3dView}, {@link
 * com.smousseur.orbitlab.engine.scene.body.lod.BillboardIconView}).
 *
 * @param id unique identifier (e.g. "EARTH", "spacecraft-leo-1")
 * @param displayName human-readable label shown in the UI
 * @param color color for icon dot and orbit line
 * @param radiusMeters physical radius in meters, used for 3D model scaling and LOD distance
 * @param lodMultiplier ratio: camera distance &lt; radius * lodMultiplier =&gt; show 3D model
 * @param modelPath GLTF asset path (e.g. "models/planets/earth/earth.gltf")
 * @param renderContext the render context defining scale and frame (Solar or Planet)
 */
public record BodyRenderConfig(
    String id,
    String displayName,
    ColorRGBA color,
    double radiusMeters,
    double lodMultiplier,
    String modelPath,
    RenderContext renderContext) {

  public BodyRenderConfig {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(modelPath, "modelPath");
    Objects.requireNonNull(renderContext, "renderContext");
  }
}
