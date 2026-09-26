package com.smousseur.orbitlab.simulation.ephemeris.service;

import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import com.smousseur.orbitlab.simulation.ephemeris.SlidingWindowEphemerisBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.orekit.time.AbsoluteDate;

/**
 * The {@link EphemerisService} backed by one {@link SlidingWindowEphemerisBuffer} per body: {@link
 * #trySampleIcrf} reads each buffer's window, {@link #trySampleIcrfOnGrid} its grid beyond the
 * window.
 *
 * <p>The map is read at each call, not copied, so it may be filled after construction.
 */
public final class BufferedEphemerisService implements EphemerisService {

  private final Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffers;

  /**
   * Creates a service over the given buffers.
   *
   * @param buffers the buffer of each body, read at each call
   */
  public BufferedEphemerisService(Map<SolarSystemBody, SlidingWindowEphemerisBuffer> buffers) {
    this.buffers = Objects.requireNonNull(buffers, "buffers");
  }

  @Override
  public Optional<BodySample> trySampleIcrf(SolarSystemBody body, AbsoluteDate t) {
    SlidingWindowEphemerisBuffer buffer = buffers.get(Objects.requireNonNull(body, "body"));
    return buffer == null ? Optional.empty() : buffer.trySampleInterpolated(t);
  }

  @Override
  public Optional<BodySample> trySampleIcrfOnGrid(SolarSystemBody body, AbsoluteDate t) {
    SlidingWindowEphemerisBuffer buffer = buffers.get(Objects.requireNonNull(body, "body"));
    return buffer == null ? Optional.empty() : buffer.trySampleOnGrid(t);
  }
}
