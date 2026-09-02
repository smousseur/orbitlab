package com.smousseur.orbitlab.engine.scene.body.lod;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * The hand-off that puts a loaded 3D model into the scene graph on the JME thread.
 *
 * <p><b>An interface, and not the application itself.</b> Attaching a child to the scene graph may
 * only happen on the render thread, and the only object able to schedule work there is the running
 * {@code SimpleApplication}. Handing that application to a view would point a dependency from
 * {@code engine} — the generic half, reusable across whatever assembles it — to the one class that
 * assembles everything. The abstraction is therefore declared here, beside the view that needs it,
 * and implemented by the application: the view asks for an attach and knows nothing of who performs
 * it.
 *
 * <p>It is also what replaced the static {@code OrbitLabApplication.app} that {@link Model3dView}
 * used to reach through ({@code DT-4}) — the same global access the project's no-{@code getState()}
 * rule exists to forbid, arriving by another door.
 */
@FunctionalInterface
public interface Model3dAttacher {

  /**
   * Attaches a loaded model under its bucket, on the thread allowed to touch the scene graph.
   *
   * @param modelBucket the node the model belongs under
   * @param model3d the loaded model
   */
  void attach(Node modelBucket, Spatial model3d);
}
