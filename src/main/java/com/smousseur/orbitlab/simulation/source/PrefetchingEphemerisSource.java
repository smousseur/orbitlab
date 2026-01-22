package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.SolarSystemBody;
import org.orekit.time.AbsoluteDate;

/**
 * Optional extension: sources that can prefetch data for a time interval to avoid stalls.
 *
 * <p>Threading: expected to be called from the ephemeris worker thread.
 */
public interface PrefetchingEphemerisSource extends EphemerisSource {

  /**
   * Hint the source that a window rebuild is about to request many samples in [start, end].
   *
   * @param body target body
   * @param start inclusive start
   * @param end inclusive end (same semantics as SlidingWindow windows)
   * @param speed simulation speed (sign indicates forward/backward)
   */
  void prefetch(SolarSystemBody body, AbsoluteDate start, AbsoluteDate end, double speed);
}
