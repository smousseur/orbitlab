package com.smousseur.orbitlab.engine;

/**
 * The look of one engine's exhaust plume. A property of the engine drawn, not of the catalog stage:
 * the catalog's propellant types tell a solid from a liquid but not a kerolox Merlin, whose flame
 * is orange and opaque, from a hydrolox Vulcain, whose flame is nearly clear and bluish.
 */
public enum PlumeLook {
  /** Falcon Heavy first-stage Merlins, kerolox, sea-level nozzles. */
  MERLIN,
  /** Falcon Heavy upper-stage Merlin Vacuum, kerolox, vacuum nozzle. */
  MVAC,
  /** Ariane 64 core Vulcain, hydrolox, sea-level nozzle. */
  VULCAIN,
  /** Ariane 64 upper-stage Vinci, hydrolox, vacuum nozzle. */
  VINCI,
  /** Ariane 64 P120C solid boosters. */
  P120C
}
