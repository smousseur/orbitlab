package com.smousseur.orbitlab.simulation.mission.vehicle;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.AscentProfile;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageModel;
import com.smousseur.orbitlab.simulation.mission.vehicle.model.stage.StageRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.orekit.utils.Constants;

/**
 * What a {@link VehicleStack} knows about its own staging beyond the masses: the role of each
 * entry, and whether the two bottom entries burn in parallel (spec {@code
 * docs/etagement/03-conception-L1.md} §3.2).
 *
 * <p><b>Why it exists at all.</b> {@code StageModel.toVehicle} drops {@code capabilities}, so a
 * flying stack cannot say that what is active is the core; and the throttle lives on {@link
 * AscentProfile}, which the stack never sees. {@link
 * com.smousseur.orbitlab.simulation.mission.vehicle.model.LauncherModel#instantiate} is the single
 * point of the repository holding both, so it computes this once and hands it over.
 *
 * @param roles the role of every stack entry, launcher stages then payload; entries are {@code
 *     null} on a stack assembled by hand, which declares none
 * @param parallelBlock the two bottom entries burning together, or {@code null} when the launcher
 *     stages sequentially; callers test {@link #hasParallelBlock()} rather than the value
 */
public record StagingPlan(List<StageRole> roles, ParallelBlock parallelBlock) {

  /**
   * The settling offset the model imposes on every transition, and therefore the shortest phase it
   * can schedule. A core-only phase below it would not be orderable, which is what makes it the
   * yardstick for {@link ParallelBlock#groupedJettison()}.
   */
  private static final double SETTLING_EPSILON = 1.0e-3;

  public StagingPlan {
    Objects.requireNonNull(roles, "roles");
    roles = Collections.unmodifiableList(new ArrayList<>(roles));
  }

  /**
   * The two bottom entries of a stack that burn at the same time: {@code bottomIndex} holds the
   * boosters, {@code bottomIndex + 1} the core.
   *
   * @param bottomIndex stack index of the booster entry
   * @param groupedJettison whether one separation drops both entries, which is the case exactly
   *     when the core runs dry with the boosters — there is then no core-only phase to fly
   * @param coreThrottle the fraction of its thrust the core applies while the boosters burn
   * @param coreLeftAtBoosterBurnout the propellant still in the core when the boosters run dry
   *     (kg), zero on a grouped jettison
   */
  public record ParallelBlock(
      int bottomIndex,
      boolean groupedJettison,
      double coreThrottle,
      double coreLeftAtBoosterBurnout) {

    /** Stack index of the core entry, immediately above the boosters. */
    public int coreIndex() {
      return bottomIndex + 1;
    }
  }

  /**
   * The plan of a stack assembled by hand, which knows neither roles nor parallel burn. Separations
   * guarding on a role refuse such a stack rather than letting the guard pass silently.
   *
   * @param stackSize the number of entries, payload included
   * @return a plan declaring nothing
   */
  public static StagingPlan unknown(int stackSize) {
    List<StageRole> none = new ArrayList<>(stackSize);
    for (int i = 0; i < stackSize; i++) {
      none.add(null);
    }
    return new StagingPlan(none, null);
  }

  /**
   * Derives the plan of one instantiated launcher. The parallel block is decided on the loads
   * actually flown, not on the throttle: at {@code f = 1} a Falcon Heavy splits with its core and
   * its boosters running dry together, while an Ariane 64 keeps burning its core for minutes (spec
   * §3.3).
   *
   * @param stages the launcher stages, bottom to top
   * @param propellantLoads the propellant load of each stage (kg), same order
   * @param profile the launcher's flight profile, holding the throttle
   * @return the staging plan of the stack these stages will assemble
   * @throws OrbitlabException if the stack cannot be flown as described
   */
  public static StagingPlan forLauncher(
      List<StageModel> stages, double[] propellantLoads, AscentProfile profile) {
    List<StageRole> roles = checkStructure(stages, profile);
    int boosterIndex = roles.indexOf(StageRole.BOOSTER);
    return boosterIndex < 0
        ? new StagingPlan(roles, null)
        : new StagingPlan(roles, block(stages, propellantLoads, profile, boosterIndex));
  }

  /**
   * Checks everything about a launcher's staging that does not depend on the propellant loads, and
   * returns the role of each stack entry. Called by {@code LauncherModel} at construction, so a
   * catalog entry that cannot be flown is refused before any mission is built; {@link #forLauncher}
   * calls it again on the loads it is given.
   *
   * @param stages the launcher stages, bottom to top
   * @param profile the launcher's flight profile
   * @return the role of each stack entry, payload included
   * @throws OrbitlabException if the staging cannot be resolved
   */
  public static List<StageRole> checkStructure(List<StageModel> stages, AscentProfile profile) {
    List<StageRole> roles = new ArrayList<>(stages.size() + 1);
    for (StageModel stage : stages) {
      StageRole role = stage.capabilities().role();
      if (roles.contains(role)) {
        throw new OrbitlabException(
            "two launcher stages declare the role "
                + role
                + ": staging cannot be resolved by role");
      }
      roles.add(role);
    }
    roles.add(StageRole.KICK);

    int boosterIndex = roles.indexOf(StageRole.BOOSTER);
    if (boosterIndex < 0 && profile.throttlesCore()) {
      throw new OrbitlabException(
          "the ascent profile throttles the core to "
              + profile.coreThrottle()
              + " but the launcher declares no booster stage: the throttle would be read by"
              + " nothing");
    }
    if (boosterIndex >= 0
        && (boosterIndex + 1 >= stages.size() || roles.get(boosterIndex + 1) != StageRole.CORE)) {
      throw new OrbitlabException(
          "the booster stage at index " + boosterIndex + " has no core stage directly above it");
    }
    return roles;
  }

  private static ParallelBlock block(
      List<StageModel> stages, double[] propellantLoads, AscentProfile profile, int boosterIndex) {
    StageModel boosters = stages.get(boosterIndex);
    StageModel core = stages.get(boosterIndex + 1);
    double boosterFlow = massFlow(boosters, 1.0);
    double coreSharedFlow = massFlow(core, profile.coreThrottle());
    double coreLeft =
        propellantLoads[boosterIndex + 1]
            - propellantLoads[boosterIndex] * coreSharedFlow / boosterFlow;

    double epsilon = massFlow(core, 1.0) * SETTLING_EPSILON;
    if (coreLeft < -epsilon) {
      throw new OrbitlabException(
          String.format(
              "the core runs dry %.0f kg before the boosters do: the shared phase would lose the"
                  + " core's thrust halfway through, which no constant-thrust phase expresses",
              -coreLeft));
    }
    boolean grouped = coreLeft <= epsilon;
    return new ParallelBlock(
        boosterIndex, grouped, profile.coreThrottle(), grouped ? 0.0 : coreLeft);
  }

  private static double massFlow(StageModel stage, double throttle) {
    return throttle
        * stage.propulsion().thrust()
        / (stage.propulsion().isp() * Constants.G0_STANDARD_GRAVITY);
  }

  /** Whether the two bottom entries of the stack burn together. */
  public boolean hasParallelBlock() {
    return parallelBlock != null;
  }

  /** Whether this plan knows the role of its entries at all. */
  public boolean declaresRoles() {
    return roles.stream().anyMatch(Objects::nonNull);
  }

  /**
   * The role of one stack entry, or {@code null} on a stack that declares none.
   *
   * @param stackIndex the entry index, payload included
   * @return the role, or {@code null}
   */
  public StageRole roleAt(int stackIndex) {
    return stackIndex >= 0 && stackIndex < roles.size() ? roles.get(stackIndex) : null;
  }

  /**
   * The stack index of the entry playing a role. Roles are unique by construction, so this is what
   * replaces the stack indices the missions used to write by hand (spec §3.7).
   *
   * @param role the role to find
   * @return the stack index of that entry
   * @throws OrbitlabException if no entry plays it
   */
  public int indexOf(StageRole role) {
    int index = roles.indexOf(role);
    if (index < 0) {
      throw new OrbitlabException("the stack declares no " + role + " stage");
    }
    return index;
  }

  /** Whether some entry plays this role. */
  public boolean hasRole(StageRole role) {
    return roles.contains(role);
  }

  /** Number of entries the plan describes, payload included. */
  public int size() {
    return roles.size();
  }
}
