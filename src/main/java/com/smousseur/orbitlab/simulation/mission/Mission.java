package com.smousseur.orbitlab.simulation.mission;

import com.smousseur.orbitlab.simulation.flight.AtmosphereModel;
import com.smousseur.orbitlab.simulation.gravity.GravitationalContext;
import com.smousseur.orbitlab.simulation.mission.objective.MissionObjective;
import com.smousseur.orbitlab.simulation.mission.vehicle.Vehicle;
import java.util.List;
import java.util.Objects;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;

/**
 * Abstract base class representing a space mission composed of sequential stages.
 *
 * <p>A mission holds the configuration (name, vehicle, stages, objective) and provides the initial
 * spacecraft state. The actual trajectory computation is handled by {@link
 * com.smousseur.orbitlab.simulation.mission.runtime.MissionOptimizer} and {@link
 * com.smousseur.orbitlab.simulation.mission.ephemeris.MissionEphemerisGenerator}.
 */
public abstract class Mission {
  private final String name;
  private final Vehicle vehicle;
  private final MissionObjective objective;
  private final List<MissionStage> stages;
  private SpacecraftState currentState;
  private AbsoluteDate initialDate;
  private MissionStatus status = MissionStatus.DRAFT;

  /** How far past insertion this mission is sampled */
  private MissionHorizon horizon = new MissionHorizon.TrailingCoast(86_164.0);

  /**
   * Which atmosphere this mission is flown against. {@link AtmosphereModel#NONE} until PHY-2 lets a
   * user choose otherwise — and the default is written here <em>as well as</em> in the spec's
   * compact constructor, deliberately: a mission assembled without going through {@code
   * MissionComposer} (the optimizer test base class, the fixtures) would otherwise carry {@code
   * null} (spec {@code docs/atmosphere/04-conception-L1.md} §3.2).
   */
  private AtmosphereModel atmosphere = AtmosphereModel.NONE;

  /**
   * Creates a new mission with the specified name, vehicle, stages, and objective.
   *
   * @param name the human-readable name of the mission
   * @param vehicle the vehicle used for the mission
   * @param stages the ordered list of mission stages to execute
   * @param objective the mission objective used for evaluation
   */
  public Mission(
      String name, Vehicle vehicle, List<MissionStage> stages, MissionObjective objective) {
    this.name = name;
    this.vehicle = vehicle;
    this.objective = objective;
    this.stages = stages;
  }

  /**
   * Computes the initial spacecraft state for the given launch date. Subclasses define the launch
   * site, orbital parameters, and initial mass based on the vehicle configuration.
   *
   * @param initialDate the launch date and time
   * @return the initial spacecraft state at the launch site
   */
  public abstract SpacecraftState getInitialState(AbsoluteDate initialDate);

  /**
   * The gravitational context this mission's stages fly in unless they say otherwise.
   *
   * <p>Shaped exactly like {@link MissionStage#maxStepSeconds}: a default carried by the mission,
   * overridable per stage. In L1 nothing overrides it — that is the definition of the lot (spec
   * {@code docs/multi-corps/03-conception-L1.md} §3.1). L4 is where a stage first declares another
   * body.
   *
   * @return the central body context
   */
  public GravitationalContext gravitationalContext() {
    return GravitationalContext.earth();
  }

  /**
   * Computes the geodetic altitude of the spacecraft above the reference shape of {@code context}'s
   * central body
   *
   * @param state the spacecraft state to evaluate
   * @param context the gravitational context whose reference shape the altitude is measured against
   * @return the altitude in meters above that shape
   */
  public double computeAltitudeMeters(SpacecraftState state, GravitationalContext context) {
    OneAxisEllipsoid shape = context.shape();
    GeodeticPoint gp = shape.transform(state.getPosition(), state.getFrame(), state.getDate());
    return gp.getAltitude();
  }

  /**
   * The altitude of the current state above this mission's own central body — what the objective
   * judges, which is a mission-level question and not a per-stage one.
   *
   * @return the altitude in meters
   */
  public double computeAltitudeMeters() {
    return computeAltitudeMeters(currentState, gravitationalContext());
  }

  /**
   * Called by stage event handlers during ephemeris generation to update the current state. This
   * simplified version only sets the current state — no stage tracking or listener notification.
   *
   * @param stateAtEvent the spacecraft state at the moment of transition
   */
  public void transitionToNextStage(SpacecraftState stateAtEvent) {
    this.currentState = stateAtEvent;
  }

  public String getName() {
    return name;
  }

  public Vehicle getVehicle() {
    return vehicle;
  }

  public MissionObjective getObjective() {
    return objective;
  }

  public SpacecraftState getCurrentState() {
    return currentState;
  }

  public void setCurrentState(SpacecraftState state) {
    this.currentState = state;
  }

  public List<MissionStage> getStages() {
    return stages;
  }

  public AbsoluteDate getInitialDate() {
    return initialDate;
  }

  public void setInitialDate(AbsoluteDate initialDate) {
    this.initialDate = initialDate;
  }

  /**
   * Returns the restitution horizon: how far past insertion this mission is sampled and displayed.
   * Never {@code null}.
   *
   * @return the mission horizon
   */
  public MissionHorizon getHorizon() {
    return horizon;
  }

  /**
   * Sets the restitution horizon. Called by {@code MissionComposer} right after composition, from
   * the spec's horizon; nothing else writes it.
   *
   * @param horizon the horizon to apply
   */
  public void setHorizon(MissionHorizon horizon) {
    this.horizon = Objects.requireNonNull(horizon, "horizon");
  }

  /**
   * Returns the atmosphere this mission's stages are flown against. Never {@code null}; {@link
   * AtmosphereModel#NONE} means no {@code DragForce} is mounted at all.
   *
   * @return the atmosphere model
   */
  public AtmosphereModel getAtmosphere() {
    return atmosphere;
  }

  /**
   * Sets the atmosphere model. Called by {@code MissionComposer} right after composition, from the
   * spec's choice; nothing else writes it — the same single-writer rule as {@link
   * #setHorizon(MissionHorizon)}, and for the same reason: the choice is user intent and must
   * survive the recompositions a mode toggle or a wizard edit performs.
   *
   * @param atmosphere the atmosphere model to apply
   */
  public void setAtmosphere(AtmosphereModel atmosphere) {
    this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
  }

  public MissionStatus getStatus() {
    return status;
  }

  public void setStatus(MissionStatus status) {
    this.status = status;
  }
}
