package com.smousseur.orbitlab.engine.scene.mesh;

/**
 * One measurable geometry found under a loaded model, with the frame it carries.
 *
 * @param name the geometry's name, as the asset declares it — what the report prints so a reading
 *     can be traced back to a specific mesh inside a multi-part model
 * @param frame the frame measured on it, expressed in the model root's own axes
 */
public record ProbedGeometry(String name, MeshFrame frame) {}
