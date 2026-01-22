package com.smousseur.orbitlab.tools.ephemerisgen;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

public record GeneratorConfigV1(
    Path orekitDataZipPath,
    String orekitDataIdSha256Hex,
    Path outputDir,
    List<SolarSystemBody> bodiesInOrder,
    double datasetStartTaiOffsetSeconds,
    double datasetEndTaiOffsetSecondsExclusive,
    int computeThreads,
    int maxBodiesInParallel,
    int maxChunksInFlightPerBody,
    int maxChunksInFlightGlobal,
    int zstdLevel,
    EnumMap<SolarSystemBody, BodyGenerationParams> paramsByBody) {

  public GeneratorConfigV1 {
    Objects.requireNonNull(orekitDataZipPath, "orekitDataZipPath");
    Objects.requireNonNull(orekitDataIdSha256Hex, "orekitDataIdSha256Hex");
    Objects.requireNonNull(outputDir, "outputDir");
    Objects.requireNonNull(bodiesInOrder, "bodiesInOrder");
    Objects.requireNonNull(paramsByBody, "paramsByBody");
  }

  public static GeneratorConfigV1 defaultV1(Path orekitZip, Path outputDir) throws Exception {
    String sha256 = HashUtils.sha256HexOfFile(orekitZip);

    List<SolarSystemBody> bodies =
        List.of(
            SolarSystemBody.SUN,
            SolarSystemBody.MERCURY,
            SolarSystemBody.VENUS,
            SolarSystemBody.EARTH,
            SolarSystemBody.MARS,
            SolarSystemBody.JUPITER,
            SolarSystemBody.SATURN,
            SolarSystemBody.URANUS,
            SolarSystemBody.NEPTUNE,
            SolarSystemBody.PLUTO);

    // Dataset v1 time model:
    // T_START = 1949-12-31T00:00:00 TAI => offset 0.
    // datasetEnd exclusive. Here we keep the same origin and only store offsets.
    double datasetStartOffset = 0.0;

    // End: 2049-12-31T00:00:00 TAI (exclusive). We'll compute its offset using Orekit at runtime,
    // but the writer uses offsets relative to T_START. We'll set it later after Orekit init,
    // so here we keep a placeholder; generator will recompute.
    double datasetEndOffsetPlaceholder = Double.NaN;

    EnumMap<SolarSystemBody, BodyGenerationParams> p = new EnumMap<>(SolarSystemBody.class);

    double chunk30d = 2_592_000.0;

    p.put(SolarSystemBody.SUN, new BodyGenerationParams(21_600.0, 21_600.0, chunk30d));
    p.put(SolarSystemBody.MERCURY, new BodyGenerationParams(10_800.0, 7_200.0, chunk30d));
    p.put(SolarSystemBody.VENUS, new BodyGenerationParams(21_600.0, 21_600.0, chunk30d));
    p.put(SolarSystemBody.EARTH, new BodyGenerationParams(21_600.0, 10_800.0, chunk30d));
    p.put(SolarSystemBody.MARS, new BodyGenerationParams(43_200.0, 21_600.0, chunk30d));
    p.put(SolarSystemBody.JUPITER, new BodyGenerationParams(86_400.0, 43_200.0, chunk30d));
    p.put(SolarSystemBody.SATURN, new BodyGenerationParams(172_800.0, 86_400.0, chunk30d));
    p.put(SolarSystemBody.URANUS, new BodyGenerationParams(345_600.0, 172_800.0, chunk30d));
    p.put(SolarSystemBody.NEPTUNE, new BodyGenerationParams(604_800.0, 345_600.0, chunk30d));
    p.put(SolarSystemBody.PLUTO, new BodyGenerationParams(1_209_600.0, 604_800.0, chunk30d));

    return new GeneratorConfigV1(
        orekitZip,
        sha256,
        outputDir,
        bodies,
        datasetStartOffset,
        datasetEndOffsetPlaceholder,
        6, // computeThreads (6 cores)
        2, // maxBodiesInParallel
        2, // maxChunksInFlightPerBody
        8, // maxChunksInFlightGlobal
        3, // zstdLevel fixed ("fast-ish" but deterministic)
        p);
  }
}
