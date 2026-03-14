package com.smousseur.orbitlab.simulation.source;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.ephemeris.BodySample;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Objects;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

/**
 * EphemerisSource backed by the on-disk dataset (*.bin), using FileChannel random access.
 *
 * <p>Threading: designed for single-worker usage (like current SlidingWindowEphemerisBuffer
 * rebuilds). If you later need multi-threaded rebuilds, wrap caches with synchronization or use
 * per-thread instances.
 */
public final class DatasetEphemerisSource
    implements EphemerisSource, PrefetchingEphemerisSource, AutoCloseable {

  // Dataset coverage (as requested)
  private static final TimeScale TAI = TimeScalesFactory.getTAI();
  private static final AbsoluteDate DATASET_T_START = new AbsoluteDate(1990, 1, 1, 0, 0, 0.0, TAI);
  private static final AbsoluteDate DATASET_T_END_EXCL =
      new AbsoluteDate(2101, 1, 1, 0, 0, 0.0, TAI);

  private final Path datasetDir;
  private final int chunksInCachePerBody;

  private final EnumMap<SolarSystemBody, BodyFile> bodyFiles = new EnumMap<>(SolarSystemBody.class);

  public DatasetEphemerisSource(Path datasetDir, int chunksInCachePerBody) {
    this.datasetDir = Objects.requireNonNull(datasetDir, "datasetDir").toAbsolutePath().normalize();
    if (chunksInCachePerBody < 1) {
      throw new IllegalArgumentException("chunksInCachePerBody must be >= 1");
    }
    this.chunksInCachePerBody = chunksInCachePerBody;

    if (!Files.isDirectory(this.datasetDir)) {
      throw new OrbitlabException("Ephemeris dataset directory not found: " + this.datasetDir);
    }

    // Open & index all bodies upfront (fail-fast).
    for (SolarSystemBody b : SolarSystemBody.values()) {
      Path p = this.datasetDir.resolve(b.name() + ".bin");
      if (!Files.isRegularFile(p)) {
        throw new OrbitlabException("Missing ephemeris dataset file: " + p);
      }
      try {
        bodyFiles.put(b, BodyFile.open(b, p, chunksInCachePerBody));
      } catch (IOException e) {
        throw new OrbitlabException("Failed to open ephemeris dataset file: " + p + " (" + e + ")");
      } catch (RuntimeException e) {
        throw new OrbitlabException(
            "Invalid ephemeris dataset file: " + p + " (" + e.getMessage() + ")");
      }
    }
  }

  @Override
  public BodySample sampleIcrf(SolarSystemBody body, AbsoluteDate date) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(date, "date");

    if (date.compareTo(DATASET_T_START) < 0 || date.compareTo(DATASET_T_END_EXCL) >= 0) {
      throw new OrbitlabException(
          "Date outside ephemeris dataset range: "
              + fmtDate(date)
              + " (expected ["
              + fmtDate(DATASET_T_START)
              + ", "
              + fmtDate(DATASET_T_END_EXCL)
              + "))");
    }

    BodyFile f = bodyFiles.get(body);
    if (f == null) {
      throw new OrbitlabException("No dataset file loaded for body=" + body);
    }

    double offsetSeconds = date.durationFrom(DATASET_T_START);
    int chunkId = f.chunkIdForOffset(offsetSeconds);

    DecodedChunk chunk = f.getDecodedChunk(chunkId);

    // Prefetch neighbor chunk in the "forward" direction by default; actual speed-aware prefetch
    // can be added later (e.g. by exposing speed into the source or by a small prefetch API called
    // by the worker).
    f.prefetchDecodedChunk(chunkId + 1);

    PVCoordinates pv = chunk.samplePv(offsetSeconds);
    Rotation rot = chunk.sampleRot(offsetSeconds);

    return new BodySample(date, pv, rot);
  }

  @Override
  public void prefetch(SolarSystemBody body, AbsoluteDate start, AbsoluteDate end, double speed) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");

    BodyFile f = bodyFiles.get(body);
    if (f == null) return;

    AbsoluteDate a = (start.compareTo(end) <= 0) ? start : end;
    AbsoluteDate b = (start.compareTo(end) <= 0) ? end : start;

    if (b.compareTo(DATASET_T_START) < 0 || a.compareTo(DATASET_T_END_EXCL) >= 0) {
      return; // outside dataset => ignore hint
    }

    double aOff = Math.max(0.0, a.durationFrom(DATASET_T_START));
    double bOff = Math.max(0.0, b.durationFrom(DATASET_T_START));

    int c0 = f.chunkIdForOffset(aOff);
    int c1 = f.chunkIdForOffset(bOff);

    // Margin: tune this. 2-4 is usually enough to hide stalls.
    int margin = 3;

    int from = Math.min(c0, c1);
    int to = Math.max(c0, c1);

    if (speed >= 0.0) {
      to = Math.min(f.chunkCount - 1, to + margin);
    } else {
      from = Math.max(0, from - margin);
    }

    for (int c = from; c <= to; c++) {
      f.prefetchDecodedChunk(c);
    }
  }

  @Override
  public void close() {
    for (BodyFile bf : bodyFiles.values()) {
      try {
        bf.close();
      } catch (Exception ignored) {
        // ignore
      }
    }
    bodyFiles.clear();
  }

  private static String fmtDate(AbsoluteDate d) {
    // Human readable without depending on Orekit formatting utilities.
    double taiOffset = d.durationFrom(AbsoluteDate.J2000_EPOCH);
    return "TAI+(" + taiOffset + "s from J2000)";
  }
}
