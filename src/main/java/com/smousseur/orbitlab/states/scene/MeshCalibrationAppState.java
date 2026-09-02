package com.smousseur.orbitlab.states.scene;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Label;
import com.smousseur.orbitlab.app.ApplicationContext;
import com.smousseur.orbitlab.app.view.RenderContext;
import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.engine.scene.PlanetMeshCorrection;
import com.smousseur.orbitlab.engine.scene.PlanetRadius;
import com.smousseur.orbitlab.engine.scene.body.LodView;
import com.smousseur.orbitlab.engine.scene.calibration.CalibrationReading;
import com.smousseur.orbitlab.engine.scene.calibration.GraticuleView;
import com.smousseur.orbitlab.engine.scene.calibration.TexturePainting;
import com.smousseur.orbitlab.engine.scene.mesh.MeshFrame;
import com.smousseur.orbitlab.engine.scene.mesh.PlanetMeshCalibration;
import com.smousseur.orbitlab.engine.scene.planet.PlanetPresenter;
import com.smousseur.orbitlab.simulation.ephemeris.service.EphemerisServiceRegistry;
import com.smousseur.orbitlab.ui.UiLayers;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;

/**
 * L2 of {@code docs/orientation-planetes/01-decoupage.md}: the instrument that turns "does this
 * planet look right?" into a number of degrees.
 *
 * <p>Press <b>G</b> while a body is focused. Its globe gets a graticule drawn from its own texture
 * map, each meridian labelled with the body-fixed longitude the application believes it carries,
 * and a cross on the sub-solar point computed from the Sun's direction. Reading a recognisable
 * feature against the grid gives what the application thinks that feature's longitude is;
 * subtracting the catalogued value gives what λ0 has to become.
 *
 * <p>The line at the top of the screen carries the numbers, {@code chain offset} first. That one is
 * computed without any eye and must read zero: it compares λ0 against the longitude the whole chain
 * actually paints column 0 at. Anything else means the composition is broken and nothing else on
 * screen can be trusted.
 *
 * <p><b>Nothing here changes what is rendered.</b> The instrument reads; the values it leads to are
 * committed by hand in {@code PlanetMeshCorrection}, which is what keeps an asset swap a detected
 * event rather than a silently absorbed one (§4.1).
 */
public final class MeshCalibrationAppState extends BaseAppState implements ActionListener {

  private static final Logger logger = LogManager.getLogger(MeshCalibrationAppState.class);

  private static final String ACTION_TOGGLE = "meshCalibration.toggle";

  /** Seconds between log lines while the instrument is up; the display itself is per frame. */
  private static final float LOG_PERIOD = 2f;

  private final ApplicationContext context;

  private InputManager inputManager;
  private Label readout;
  private GraticuleView graticule;
  private SolarSystemBody shownBody;
  private boolean active;
  private float sinceLog;

  /**
   * Creates the calibration instrument.
   *
   * @param context the application context providing the clock, the focus and the planet presenters
   */
  public MeshCalibrationAppState(ApplicationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override
  protected void initialize(Application app) {
    inputManager = app.getInputManager();
    inputManager.addMapping(ACTION_TOGGLE, new KeyTrigger(KeyInput.KEY_G));
    inputManager.addListener(this, ACTION_TOGGLE);

    readout = new Label("");
    readout.setColor(new ColorRGBA(0.6f, 0.95f, 1f, 1f));
    readout.setCullHint(Spatial.CullHint.Always);
    context.guiGraph().getPlanetBillboardsNode().attachChild(readout);
  }

  @Override
  protected void cleanup(Application app) {
    hide();
    if (readout != null) {
      readout.removeFromParent();
      readout = null;
    }
    if (inputManager != null) {
      inputManager.removeListener(this);
      inputManager.deleteMapping(ACTION_TOGGLE);
      inputManager = null;
    }
  }

  @Override
  protected void onEnable() {
    // nothing
  }

  @Override
  protected void onDisable() {
    hide();
  }

  @Override
  public void onAction(String name, boolean isPressed, float tpf) {
    if (!ACTION_TOGGLE.equals(name) || !isPressed) {
      return;
    }
    active = !active;
    if (!active) {
      hide();
    }
    sinceLog = LOG_PERIOD;
  }

  @Override
  public void update(float tpf) {
    if (!active) {
      return;
    }
    SolarSystemBody body = context.focusView().getBody();
    if (body == null || body == SolarSystemBody.SUN) {
      hide();
      return;
    }
    Optional<Node> bucket = modelBucketOf(body);
    Optional<PlanetMeshCalibration> calibration = PlanetMeshCorrection.calibrationFor(body);
    if (bucket.isEmpty() || calibration.isEmpty() || context.nearCamera() == null) {
      hide();
      return;
    }

    AbsoluteDate now = context.clock().now();
    Optional<Map.Entry<Vector3D, Rotation>> sample =
        EphemerisServiceRegistry.get()
            .orElseThrow(() -> new OrbitlabException("Cannot get EphemerisService"))
            .trySampleHelioIcrf(body, now);
    if (sample.isEmpty()) {
      return;
    }

    show(body, bucket.get(), calibration.get().measured());
    render(body, bucket.get(), calibration.get(), now, sample.get(), tpf);
  }

  private void render(
      SolarSystemBody body,
      Node bucket,
      PlanetMeshCalibration calibration,
      AbsoluteDate now,
      Map.Entry<Vector3D, Rotation> sample,
      float tpf) {
    // The Sun sits at the origin of the heliocentric frame, so the body's own position, negated, is
    // the direction to it — a position, owing nothing to any rotation. That independence is the
    // whole point of the marker.
    Vector3D sunDirectionIcrf = sample.getKey().negate().normalize();
    Rotation rotationIcrf = sample.getValue();
    Quaternion renderRotation = bucket.getLocalRotation();
    MeshFrame frame = calibration.measured();

    double days = now.durationFrom(AbsoluteDate.J2000_EPOCH) / 86400.0;
    CalibrationReading reading =
        CalibrationReading.take(
            body,
            frame,
            calibration.lambda0DegAt(days),
            renderRotation,
            rotationIcrf,
            sunDirectionIcrf);

    Vector3f sunInModelAxes =
        renderRotation
            .inverse()
            .mult(
                new Vector3f(
                    (float) sunDirectionIcrf.getX(),
                    (float) sunDirectionIcrf.getZ(),
                    (float) -sunDirectionIcrf.getY()));
    boolean vZeroIsNorth =
        TexturePainting.bodyFixedOf(frame.pole().normalize(), renderRotation, rotationIcrf).getZ()
            > 0;

    graticule.update(context.nearCamera(), reading.painting(), vZeroIsNorth, sunInModelAxes);

    readout.setCullHint(Spatial.CullHint.Inherit);
    readout.setText(reading.format());
    Vector3f size = readout.getPreferredSize();
    Camera screen = getApplication().getCamera();
    readout.setLocalTranslation(
        (screen.getWidth() - size.x) * 0.5f, screen.getHeight() - 8f, UiLayers.HUD);

    sinceLog += tpf;
    if (sinceLog >= LOG_PERIOD) {
      sinceLog = 0f;
      logger.info("Mesh calibration: {}", reading.format());
    }
  }

  private void show(SolarSystemBody body, Node bucket, MeshFrame frame) {
    if (graticule != null && body == shownBody) {
      return;
    }
    hide();
    float radiusUnits =
        (float) (PlanetRadius.radiusFor(body) / RenderContext.PLANET_METERS_PER_UNIT);
    graticule =
        new GraticuleView(bucket, context.guiGraph().getPlanetBillboardsNode(), frame, radiusUnits);
    shownBody = body;
  }

  private void hide() {
    if (graticule != null) {
      graticule.detach();
      graticule = null;
    }
    shownBody = null;
    if (readout != null) {
      readout.setCullHint(Spatial.CullHint.Always);
    }
  }

  private Optional<Node> modelBucketOf(SolarSystemBody body) {
    PlanetPresenter presenter = context.getPlanets().get(body);
    if (presenter == null || !(presenter.view() instanceof LodView lodView)) {
      return Optional.empty();
    }
    // The bucket carries the render rotation whether or not the model has finished loading, so the
    // grid is correctly oriented from the first frame and the texture simply arrives under it.
    return Optional.of(lodView.getModel3dView().getModelBucket());
  }
}
