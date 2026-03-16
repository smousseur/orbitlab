package com.smousseur.orbitlab.states.camera;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.smousseur.orbitlab.engine.OrbitCameraConfig;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Blender-like orbit camera using a turntable model for 3D scene navigation.
 *
 * <p>Supports the following interaction modes:
 *
 * <ul>
 *   <li>Middle mouse button (MMB) drag: orbit around the pivot point
 *   <li>Shift + MMB drag: pan (translates the pivot in the screen plane)
 *   <li>Mouse wheel: dolly zoom (exponential distance scaling)
 * </ul>
 *
 * <p>The camera orbits around a target pivot, which can be set as a {@link Spatial} or a {@code
 * Supplier<Vector3f>} providing world-space coordinates. If the target is null or contains
 * non-finite coordinates, the camera falls back to the provided fallback pivot supplier. The field
 * of view adapts dynamically based on the zoom level, narrowing when close and widening when far.
 */
public final class OrbitCameraAppState extends BaseAppState
    implements ActionListener, AnalogListener {

  private static final String ACTION_MMB = "orbitcam.mmb";
  private static final String ACTION_SHIFT = "orbitcam.shift";

  private static final String ANALOG_MOUSE_X_POS = "orbitcam.mouseX+";
  private static final String ANALOG_MOUSE_X_NEG = "orbitcam.mouseX-";
  private static final String ANALOG_MOUSE_Y_POS = "orbitcam.mouseY+";
  private static final String ANALOG_MOUSE_Y_NEG = "orbitcam.mouseY-";

  private static final String ANALOG_WHEEL_UP = "orbitcam.wheelUp";
  private static final String ANALOG_WHEEL_DOWN = "orbitcam.wheelDown";

  private final OrbitCameraConfig config;
  private final Supplier<Vector3f> fallbackPivotWorldSupplier;
  private final BooleanSupplier uiWantsMouse;

  private InputManager inputManager;
  private Camera cam;

  private Spatial targetSpatial;
  private Supplier<Vector3f> targetWorldSupplier;

  private boolean mmbDown;
  private boolean shiftDown;

  private float yawRad;
  private float pitchRad;

  private float distance;

  private final Vector3f lastPivotWorld = new Vector3f();

  /** Dynamic minimum far plane, applied after adaptive frustum calculation. */
  private volatile float farFloor = 0f;

  // FoV adaptatif: bornes en radians et exponent de courbe
  private final float fovMinRad = (float) Math.toRadians(15.0); // étroit proche
  private final float fovMaxRad = (float) Math.toRadians(60.0); // large lointain
  private final float fovCurveK = 0.9f; // 0.6..1.5 : <1 => plus de resserrement près du proche

  /**
   * Creates a new orbit camera state.
   *
   * @param config camera configuration controlling distances, speeds, and frustum parameters
   * @param fallbackPivotWorldSupplier supplier for the fallback pivot position in world space, used
   *     when no target is set or the target position is invalid
   * @param uiWantsMouse supplier that returns {@code true} when the UI is capturing mouse input,
   *     causing the camera to ignore mouse events
   */
  public OrbitCameraAppState(
      OrbitCameraConfig config,
      Supplier<Vector3f> fallbackPivotWorldSupplier,
      BooleanSupplier uiWantsMouse) {
    this.config = Objects.requireNonNull(config, "config");
    this.fallbackPivotWorldSupplier =
        Objects.requireNonNull(fallbackPivotWorldSupplier, "fallbackPivotWorldSupplier");
    this.uiWantsMouse = Objects.requireNonNull(uiWantsMouse, "uiWantsMouse");

    reset();
  }

  /**
   * Sets the camera's orbit target to a scene spatial. The camera will orbit around the spatial's
   * world translation. Clears any previously set supplier-based target.
   *
   * @param spatial the spatial to orbit around
   */
  public void setTarget(Spatial spatial) {
    this.targetSpatial = spatial;
    this.targetWorldSupplier = null;
  }

  /**
   * Sets the camera's orbit target to a dynamic world-space position supplier. The camera will
   * orbit around the position returned by the supplier each frame. Clears any previously set
   * spatial-based target.
   *
   * @param pivotWorldSupplier supplier providing the pivot position in world space
   */
  public void setTarget(Supplier<Vector3f> pivotWorldSupplier) {
    this.targetWorldSupplier = pivotWorldSupplier;
    this.targetSpatial = null;
  }

  /**
   * Clears the current orbit target. The camera will fall back to the fallback pivot supplier until
   * a new target is set.
   */
  public void clearTarget() {
    this.targetSpatial = null;
    this.targetWorldSupplier = null;
  }

  /**
   * Returns the current distance from the camera to the orbit pivot point.
   *
   * @return the distance in world units
   */
  public float distanceToTarget() {
    return distance;
  }

  /**
   * Returns the current zoom level as a normalized value between 0 and 1, where 0 represents the
   * minimum distance (fully zoomed in) and 1 represents the maximum distance (fully zoomed out).
   * The mapping uses a logarithmic scale for perceptually uniform zoom behavior.
   *
   * @return the normalized zoom level in the range [0, 1]
   */
  public float normalizedZoom01() {
    float d = FastMath.clamp(distance, config.minDistance(), config.maxDistance());
    float min = config.minDistance();
    float max = config.maxDistance();
    float logMin = (float) Math.log(min);
    float logMax = (float) Math.log(max);
    float logD = (float) Math.log(d);
    float t = (logD - logMin) / (logMax - logMin);
    return FastMath.clamp(t, 0f, 1f);
  }

  /**
   * Returns a copy of the last computed pivot position in world space.
   *
   * @return a clone of the pivot world position vector
   */
  public Vector3f pivotWorldPosition() {
    return lastPivotWorld.clone();
  }

  /**
   * Resets the camera orientation and distance to their default values as defined by the
   * configuration. The yaw is set to zero and the pitch is set to -20 degrees.
   */
  public void reset() {
    yawRad = 0f;
    pitchRad = (float) (-20.0 * Math.PI / 180.0);
    pitchRad = FastMath.clamp(pitchRad, config.pitchMinRad(), config.pitchMaxRad());
    distance = FastMath.clamp(config.defaultDistance(), config.minDistance(), config.maxDistance());
  }

  @Override
  protected void initialize(Application app) {
    this.inputManager = app.getInputManager();
    this.cam = app.getCamera();
    registerInputs();

    // Apply initial pose immediately
    applyCameraPose();
    updateFrustum();
  }

  @Override
  protected void cleanup(Application app) {
    unregisterInputs();
    inputManager = null;
    cam = null;
  }

  @Override
  protected void onEnable() {
    // nothing special
  }

  @Override
  protected void onDisable() {
    // Release any active drag state when disabled
    mmbDown = false;
    shiftDown = false;
  }

  @Override
  public void update(float tpf) {
    if (cam == null) {
      return;
    }
    // Always keep pose consistent with moving targets (planets, origin shifting, etc.)
    applyCameraPose();
    updateFrustum();
    updateAdaptiveFov();
  }

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (uiWantsMouse.getAsBoolean()) {
      // If UI captures mouse, drop drag states to avoid "stuck" camera.
      mmbDown = false;
      shiftDown = false;
      return;
    }

    switch (name) {
      case ACTION_MMB -> mmbDown = isPressed;
      case ACTION_SHIFT -> shiftDown = isPressed;
      default -> {
        // ignore
      }
    }
  }

  @Override
  public void onAnalog(String name, float value, float tpf) {
    if (uiWantsMouse.getAsBoolean()) {
      return;
    }

    // Wheel zoom must work without holding MMB.
    if (ANALOG_WHEEL_UP.equals(name) || ANALOG_WHEEL_DOWN.equals(name)) {
      applyWheelZoom(name, value);
      applyCameraPose();
      updateFrustum();
      return;
    }

    // Orbit/pan require MMB down.
    if (!mmbDown) {
      return;
    }

    boolean panning = shiftDown;
    boolean orbiting = !shiftDown;

    float dx = 0f;
    float dy = 0f;

    switch (name) {
      case ANALOG_MOUSE_X_POS -> dx = +value;
      case ANALOG_MOUSE_X_NEG -> dx = -value;
      case ANALOG_MOUSE_Y_POS -> dy = +value;
      case ANALOG_MOUSE_Y_NEG -> dy = -value;
      default -> {
        return;
      }
    }

    if (orbiting) {
      orbitByMouseDelta(dx, dy);
      applyCameraPose();
      updateFrustum();
    } else if (panning) {
      panByMouseDelta(dx, dy);
      applyCameraPose();
      updateFrustum();
    }
  }

  /**
   * Sets a minimum floor for the far clip plane. The adaptive frustum will never produce a far
   * value below this floor. Set to 0 to disable.
   *
   * @param farFloor minimum far clip plane in world units
   */
  public void setFarFloor(float farFloor) {
    this.farFloor = Math.max(0f, farFloor);
  }

  private void applyWheelZoom(String name, float value) {
    if (uiWantsMouse.getAsBoolean()) {
      return;
    }

    float wheelDelta = 0f;
    if (ANALOG_WHEEL_UP.equals(name)) {
      wheelDelta = +value;
    } else if (ANALOG_WHEEL_DOWN.equals(name)) {
      wheelDelta = -value;
    }

    // Convention: wheel up = zoom in (distance decreases)
    if (config.invertZoom()) {
      wheelDelta = -wheelDelta;
    }

    // Many platforms send very small wheel deltas (smooth scrolling / trackpads),
    // which makes zoom feel "dead". Snap tiny deltas to a single "tick".
    float abs = Math.abs(wheelDelta);
    if (abs > 0f && abs < 0.10f) {
      wheelDelta = Math.signum(wheelDelta);
    }

    // Exponential dolly
    double scale = Math.exp(-wheelDelta * config.zoomSpeed());
    float next = (float) (distance * scale);
    distance = FastMath.clamp(next, config.minDistance(), config.maxDistance());
  }

  private void orbitByMouseDelta(float dx, float dy) {
    // dx/dy are in "analog units" from input system; treat as pixels-ish.
    yawRad -= dx * config.rotateSpeedRadPerPixel();
    pitchRad += dy * config.rotateSpeedRadPerPixel();
    pitchRad = FastMath.clamp(pitchRad, config.pitchMinRad(), config.pitchMaxRad());
  }

  private void panByMouseDelta(float dx, float dy) {
    // Pan in screen plane (camera right/up), speed scales with distance.
    Vector3f pivot = computePivotWorld();

    Vector3f right = cam.getLeft().negate(); // JME stores left; right = -left
    Vector3f up = cam.getUp();

    float panSpeed = config.panFactor() * distance;

    // Mouse Y is usually inverted vs screen; keep it feeling "editor-like".
    Vector3f delta = right.mult(dx * panSpeed).addLocal(up.mult(-dy * panSpeed));

    pivot.addLocal(delta);
    setPivotWorldDirect(pivot);
  }

  private void setPivotWorldDirect(Vector3f newPivotWorld) {
    // When panning, we need a mutable pivot. Switching to a supplier target keeps it simple:
    Vector3f stored = newPivotWorld.clone();
    setTarget(() -> stored);
  }

  private void applyCameraPose() {
    Vector3f pivotWorld = computePivotWorld();

    Quaternion qYaw = new Quaternion().fromAngleAxis(yawRad, Vector3f.UNIT_Y);
    Quaternion qPitch = new Quaternion().fromAngleAxis(pitchRad, Vector3f.UNIT_X);

    // Turntable: apply pitch then yaw (so pitch axis is "yawed" X)
    Quaternion orbitRot = qYaw.mult(qPitch);

    Vector3f offset = new Vector3f(0f, 0f, distance);
    orbitRot.multLocal(offset);

    cam.setLocation(pivotWorld.add(offset));
    cam.lookAt(pivotWorld, Vector3f.UNIT_Y);
  }

  private void updateFrustum() {
    float d = distance;

    float near = d * config.nearFactor();
    near = clampFinite(near, config.nearMin(), config.nearMax());

    // Far should scale with distance; avoid forcing far to a huge minimum when close,
    // otherwise depth precision collapses (z-fighting/"deformation").
    float far = d * config.farFactor();
    far = clampFinite(far, 0.001f, config.farMax());

    // Ensure minimum separation/ratio
    far = Math.max(far, near * 10f);

    // Optionally keep a very small absolute minimum far (but not 1000 when close)
    far = Math.max(far, 10f);

    // Apply dynamic floor (e.g. for planet view where distant orbits must remain visible)
    far = Math.max(far, farFloor);

    // Keep pivot visible: near must be < distance (minus a small margin)
    float margin = Math.max(near * 2f, 0.01f);
    float maxNear = Math.max(0.0001f, d - margin);
    if (near > maxNear) {
      near = maxNear;
      far = Math.max(far, near * 10f);
    }

    cam.setFrustumNear(near);
    cam.setFrustumFar(far);
  }

  // FoV adaptatif en fonction du zoom normalisé 0..1 (0 = proche, 1 = lointain)
  private void updateAdaptiveFov() {
    float t = normalizedZoom01(); // déjà clampé 0..1
    float tt = (float) Math.pow(t, fovCurveK);
    float fov = FastMath.interpolateLinear(tt, fovMinRad, fovMaxRad);
    // JME attend des demi-angles verticaux en radian via setFrustumPerspective(fovYDegrees, aspect,
    // near, far)
    // mais on peut définir directement via cam.setFrustumPerspective()
    float aspect = (float) cam.getWidth() / Math.max(1f, cam.getHeight());
    cam.setFrustumPerspective(
        (float) Math.toDegrees(fov), aspect, cam.getFrustumNear(), cam.getFrustumFar());
  }

  private static float clampFinite(float v, float min, float max) {
    if (!Float.isFinite(v)) {
      return min;
    }
    return FastMath.clamp(v, min, max);
  }

  private Vector3f computePivotWorld() {
    Vector3f pivot = null;

    if (targetWorldSupplier != null) {
      pivot = targetWorldSupplier.get();
    } else if (targetSpatial != null) {
      pivot = targetSpatial.getWorldTranslation();
    }

    if (pivot == null
        || !Float.isFinite(pivot.x)
        || !Float.isFinite(pivot.y)
        || !Float.isFinite(pivot.z)) {
      pivot = fallbackPivotWorldSupplier.get();
    }
    if (pivot == null
        || !Float.isFinite(pivot.x)
        || !Float.isFinite(pivot.y)
        || !Float.isFinite(pivot.z)) {
      pivot = Vector3f.ZERO;
    }

    lastPivotWorld.set(pivot);
    return lastPivotWorld.clone();
  }

  private void registerInputs() {
    // Buttons / modifiers
    inputManager.addMapping(ACTION_MMB, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
    inputManager.addMapping(
        ACTION_SHIFT, new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));

    // Mouse move (split into + and - so we can reconstruct dx/dy cleanly)
    inputManager.addMapping(ANALOG_MOUSE_X_POS, new MouseAxisTrigger(MouseInput.AXIS_X, false));
    inputManager.addMapping(ANALOG_MOUSE_X_NEG, new MouseAxisTrigger(MouseInput.AXIS_X, true));
    inputManager.addMapping(ANALOG_MOUSE_Y_POS, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
    inputManager.addMapping(ANALOG_MOUSE_Y_NEG, new MouseAxisTrigger(MouseInput.AXIS_Y, true));

    // Wheel
    inputManager.addMapping(ANALOG_WHEEL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
    inputManager.addMapping(ANALOG_WHEEL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

    inputManager.addListener(this, ACTION_MMB, ACTION_SHIFT);

    inputManager.addListener(
        this,
        ANALOG_MOUSE_X_POS,
        ANALOG_MOUSE_X_NEG,
        ANALOG_MOUSE_Y_POS,
        ANALOG_MOUSE_Y_NEG,
        ANALOG_WHEEL_UP,
        ANALOG_WHEEL_DOWN);
  }

  private void unregisterInputs() {
    if (inputManager == null) {
      return;
    }

    inputManager.removeListener(this);

    // Safe even if already removed
    inputManager.deleteMapping(ACTION_MMB);
    inputManager.deleteMapping(ACTION_SHIFT);

    inputManager.deleteMapping(ANALOG_MOUSE_X_POS);
    inputManager.deleteMapping(ANALOG_MOUSE_X_NEG);
    inputManager.deleteMapping(ANALOG_MOUSE_Y_POS);
    inputManager.deleteMapping(ANALOG_MOUSE_Y_NEG);

    inputManager.deleteMapping(ANALOG_WHEEL_UP);
    inputManager.deleteMapping(ANALOG_WHEEL_DOWN);
  }
}
