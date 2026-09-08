package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.simulation.mission.vehicle.StagingPlan.ParallelBlock;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AerodynamicProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.orekit.utils.Constants;

/**
 * A composite vehicle made up of multiple stacked stages. The first vehicle in the list represents
 * the currently active (bottom) vehicle. Mass and propulsion queries delegate to the constituent
 * vehicles.
 *
 * <p><b>{@link #aerodynamics()} is deliberately not overridden</b>, so a stack inherits the {@code
 * null} of {@link Vehicle}: a stack has no single frontal area — the flow sees the bottom stage —
 * and no production path ever asks one for it, since drag resolves through {@link
 * #resolveActiveStage(double)}. An override here would be one more piece of unreachable defensive
 * code stating something false about the model, which is exactly the mistake recorded below (spec
 * {@code docs/atmosphere/04-conception-L1.md} §3.1).
 *
 * @param vehicles the ordered list of vehicle stages (index 0 = bottom vehicle)
 * @param stagingPlan the roles of the entries and the parallel block, if any — computed once by
 *     {@code LauncherModel.instantiate} (spec {@code docs/etagement/03-conception-L1.md} §3.2)
 */
public record VehicleStack(List<Vehicle> vehicles, StagingPlan stagingPlan) implements Vehicle {

  public VehicleStack {
    Objects.requireNonNull(vehicles, "vehicles");
    Objects.requireNonNull(stagingPlan, "stagingPlan");
  }

  /** A stack assembled by hand, declaring neither roles nor parallel burn. */
  public VehicleStack(List<Vehicle> vehicles) {
    this(vehicles, StagingPlan.unknown(vehicles.size()));
  }

  @Override
  public double dryMass() {
    return vehicles.stream().mapToDouble(Vehicle::dryMass).sum();
  }

  @Override
  public double propellantCapacity() {
    return vehicles.stream().mapToDouble(Vehicle::propellantCapacity).sum();
  }

  @Override
  public double propellantLoad() {
    return vehicles.stream().mapToDouble(Vehicle::propellantLoad).sum();
  }

  @Override
  public double getMass() {
    return vehicles.stream().mapToDouble(Vehicle::getMass).sum();
  }

  @Override
  public PropulsionSystem propulsion() {
    return vehicles.getFirst().propulsion();
  }

  /**
   * Resolves the active vehicle based on the current spacecraft mass. Outside a parallel block, the
   * active vehicle is the lowest one whose cumulative mass-above is strictly less than the current
   * mass.
   *
   * <p><b>Invariant: the active stage changes only by an explicit jettison.</b> Burning cannot do
   * it, however long the burn runs; only dropping the mass outright (a {@link
   * com.smousseur.orbitlab.simulation.mission.stage.StageSeparationStage}) crosses a threshold.
   *
   * <p><b>The invariant holds for two different reasons, and the second one is new.</b> For a
   * sequential stage it is that the depletion floor, {@code dryMass_i + massAbove[i]}, sits
   * strictly above the {@code massAbove[i]} threshold. That argument is <em>false</em> for a
   * parallel block, which burns straight through the reference mass of the stack above it — on the
   * split Falcon Heavy, 74 % of the way into the shared burn (spec {@code
   * docs/etagement/03-conception-L1.md} §2.2). What holds instead is that the block's threshold
   * <em>is</em> the mass it leaves behind when its boosters are dropped, so the strict {@code >}
   * hands over exactly at the jettison and never during a burn.
   *
   * <p>Worth stating because the opposite was once assumed: several two-burn analytic stages
   * re-resolved the active stage from the mass predicted after their first burn, as if staging
   * could happen mid-phase. That second resolution always returned the first one, so it was
   * unreachable defensive code writing a false claim about the model — that an intra-phase staging
   * is supported somewhere. It is not, anywhere (spec {@code
   * docs/mission-stages/01-separations-implicites.md} S4).
   *
   * @param currentMass the current spacecraft mass from SpacecraftState
   * @return the active vehicle information
   */
  @Override
  public ActiveStageInfo resolveActiveStage(double currentMass) {
    int n = vehicles.size();
    double[] massAbove = new double[n];
    double[] dryMassAbove = new double[n];
    double cumulativeMass = 0;
    double cumulativeDryMass = 0;

    for (int i = n - 1; i >= 0; i--) {
      massAbove[i] = cumulativeMass;
      dryMassAbove[i] = cumulativeDryMass;
      cumulativeMass += vehicles.get(i).getMass();
      cumulativeDryMass += vehicles.get(i).dryMass();
    }

    int scanFrom = 0;
    ParallelBlock block = stagingPlan.parallelBlock();
    if (block != null) {
      ActiveStageInfo blockInfo = blockInfo(block, massAbove, dryMassAbove);
      if (currentMass > blockInfo.massAbove()) {
        return blockInfo;
      }
      scanFrom = block.groupedJettison() ? block.coreIndex() + 1 : block.coreIndex();
    }

    for (int i = scanFrom; i < n; i++) {
      if (currentMass > massAbove[i]) {
        return new ActiveStageInfo(
            i, vehicles.get(i), massAbove[i], dryMassAbove[i], stagingPlan.roleAt(i));
      }
    }
    // Fallback: topmost vehicle
    int last = n - 1;
    return new ActiveStageInfo(last, vehicles.get(last), 0, 0, stagingPlan.roleAt(last));
  }

  /**
   * The block as one synthetic stage. Its dry mass is the boosters' alone and its {@code massAbove}
   * the mass left once they are dropped, so {@link ActiveStageInfo#depletionFloor()} lands on the
   * booster burnout and {@link ActiveStageInfo#massAfterJettison()} drops exactly the boosters. A
   * grouped jettison shifts the same two numbers by the core: both dry masses on one side, the
   * stack above on the other — same floor, different jettison, which is the whole difference
   * between the two cases (spec §3.1).
   */
  private ActiveStageInfo blockInfo(
      ParallelBlock block, double[] massAbove, double[] dryMassAbove) {
    int b = block.bottomIndex();
    int c = block.coreIndex();
    Vehicle boosters = vehicles.get(b);
    Vehicle core = vehicles.get(c);
    double blockDryMass =
        block.groupedJettison() ? boosters.dryMass() + core.dryMass() : boosters.dryMass();
    double blockMassAbove =
        block.groupedJettison()
            ? massAbove[c]
            : core.dryMass() + block.coreLeftAtBoosterBurnout() + massAbove[c];
    double blockDryMassAbove =
        block.groupedJettison() ? dryMassAbove[c] : core.dryMass() + dryMassAbove[c];

    Vehicle aggregate =
        new LaunchVehicle(
            blockDryMass,
            boosters.propellantCapacity() + core.propellantCapacity(),
            boosters.propellantLoad() + core.propellantLoad(),
            aggregatePropulsion(boosters, core, block.coreThrottle()),
            aggregateAerodynamics(boosters, core));
    return new ActiveStageInfo(
        b, aggregate, blockMassAbove, blockDryMassAbove, stagingPlan.roleAt(b));
  }

  /**
   * Thrust summed, effective specific impulse {@code ΣF / Σ(F/Isp)}. At constant thrusts and
   * specific impulses the total thrust and the total flow are both constant, so one equivalent
   * engine reproduces the mass history and the acceleration exactly — the aggregation loses
   * nothing, it only stops being written by hand (spec {@code docs/etagement/01-decoupage.md}
   * §3.1).
   */
  private static PropulsionSystem aggregatePropulsion(
      Vehicle boosters, Vehicle core, double throttle) {
    double thrust = boosters.propulsion().thrust() + throttle * core.propulsion().thrust();
    double flow = massFlow(boosters, 1.0) + massFlow(core, throttle);
    return new PropulsionSystem(thrust / (flow * Constants.G0_STANDARD_GRAVITY), thrust);
  }

  /**
   * Sections summed over the entries that declare one, drag coefficient area-weighted — the only
   * combination preserving the drag force. {@code null} when neither entry declares any, which is
   * the entry-by-entry reading of the PHY-1 contract: declaring nothing is not dragging.
   */
  private static AerodynamicProperties aggregateAerodynamics(Vehicle boosters, Vehicle core) {
    AerodynamicProperties first = boosters.aerodynamics();
    AerodynamicProperties second = core.aerodynamics();
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    double section = first.crossSection() + second.crossSection();
    double weighted =
        first.dragCoefficient() * first.crossSection()
            + second.dragCoefficient() * second.crossSection();
    return new AerodynamicProperties(section, weighted / section);
  }

  private static double massFlow(Vehicle vehicle, double throttle) {
    PropulsionSystem propulsion = vehicle.propulsion();
    return throttle * propulsion.thrust() / (propulsion.isp() * Constants.G0_STANDARD_GRAVITY);
  }

  /**
   * Splits the propellant still aboard across the physical stages, instead of reporting a single
   * stack-wide total (bilan 10 §6). Given the current mass, the active stage holds {@link
   * ActiveStageInfo#remainingFuel(double)}, every stage below it has reached its depletion floor
   * (residual 0 — the mass model switches stages exactly there), and every stage above it is still
   * untouched at its full load.
   *
   * <p><b>A parallel block is the one active stage holding two tanks</b>, and they drain together.
   * Both flows being constant over the whole shared phase, the split depends on nothing but the
   * total consumed, so this stays a pure function of the current mass (spec {@code
   * docs/etagement/03-conception-L1.md} §3.8).
   *
   * <p>A stage jettisoned early with propellant aboard is the one case this cannot see after the
   * fact: once the mass has dropped, the discarded propellant is indistinguishable from burnt
   * propellant. {@code MissionOptimizer} captures that residual as the separation happens.
   */
  @Override
  public List<StagePropellant> resolveStagePropellant(double currentMass) {
    ActiveStageInfo active = resolveActiveStage(currentMass);
    int activeIndex = active.stageIndex();
    ParallelBlock block = stagingPlan.parallelBlock();
    boolean blockActive = block != null && activeIndex == block.bottomIndex();
    double boosterShare = blockActive ? boosterShare(block) : 0.0;
    double consumed = blockActive ? getMass() - currentMass : 0.0;

    List<StagePropellant> perStage = new ArrayList<>(vehicles.size());
    for (int i = 0; i < vehicles.size(); i++) {
      double loaded = vehicles.get(i).propellantLoad();
      double residual;
      if (blockActive && i == block.bottomIndex()) {
        residual = loaded - consumed * boosterShare;
      } else if (blockActive && i == block.coreIndex()) {
        residual = loaded - consumed * (1.0 - boosterShare);
      } else if (i < activeIndex) {
        residual = 0.0;
      } else if (i == activeIndex) {
        residual = active.remainingFuel(currentMass);
      } else {
        residual = loaded;
      }
      perStage.add(new StagePropellant(i, loaded, Math.max(0.0, Math.min(loaded, residual))));
    }
    return List.copyOf(perStage);
  }

  private double boosterShare(ParallelBlock block) {
    double boosterFlow = massFlow(vehicles.get(block.bottomIndex()), 1.0);
    double coreFlow = massFlow(vehicles.get(block.coreIndex()), block.coreThrottle());
    return boosterFlow / (boosterFlow + coreFlow);
  }
}
