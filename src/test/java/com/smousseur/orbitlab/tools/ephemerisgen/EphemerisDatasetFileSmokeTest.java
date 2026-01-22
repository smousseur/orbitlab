package com.smousseur.orbitlab.tools.ephemerisgen;

import static org.junit.jupiter.api.Assertions.*;

import com.github.luben.zstd.Zstd;
import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.PVCoordinates;

final class EphemerisDatasetFileSmokeTest {

  @Test
  void writes_one_body_one_chunk_file_with_valid_header_index_and_chunk_crc() throws Exception {
    // String zipProp = System.getProperty("orekitDataZip");
    OrekitService.get().initialize();
    String zipProp =
        "C:\\Prog\\projects\\intelliJ\\orbitlab\\src\\main\\resources\\orekit-data.zip"; // System.getProperty("orekitDataZip");
    Assumptions.assumeTrue(zipProp != null && !zipProp.isBlank(), "Missing -DorekitDataZip=...");

    Path orekitZip = Path.of(zipProp).toAbsolutePath().normalize();
    Assumptions.assumeTrue(
        java.nio.file.Files.exists(orekitZip), "orekit-data.zip not found: " + orekitZip);

    initOrekitData(orekitZip.toFile());

    Path outDir = java.nio.file.Files.createTempDirectory("ephemgen-file-smoke");

    GeneratorConfigV1 cfg = minimalCfg(orekitZip, outDir);

    TimeScale tai = TimeScalesFactory.getTAI();
    AbsoluteDate tStart = AbsoluteDate.J2000_EPOCH;

    double chunkDur = 86_400.0;
    AbsoluteDate tEndExclusive = tStart.shiftedBy(chunkDur);
    double datasetEndOffsetExclusive = chunkDur;

    BodyGenerationParams params =
        new BodyGenerationParams(
            3_600.0, // dtPv = 1h => nPv = 24
            7_200.0, // dtRot = 2h => nRot = 12
            chunkDur);

    var computePool = Executors.newFixedThreadPool(2);
    var globalInFlight = new Semaphore(8, true);
    try {
      BodyFileWriterV1 writer =
          new BodyFileWriterV1(
              cfg,
              SolarSystemBody.EARTH,
              params,
              tStart,
              tEndExclusive,
              datasetEndOffsetExclusive,
              computePool,
              globalInFlight);

      writer.generateAndWrite();

    } finally {
      computePool.shutdownNow();
    }

    Path file = outDir.resolve("ephem").resolve("EARTH.bin");
    assertTrue(java.nio.file.Files.exists(file), "Expected output file: " + file);

    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
      ParsedHeader h = readHeader(ch);

      assertArrayEquals(new byte[] {'O', 'R', 'B', 'L', '_', 'E', 'P', 'H'}, h.magic);
      assertEquals(1, h.versionMajor);
      assertEquals(0, h.versionMinor);
      assertEquals(1, h.timeScale);
      assertEquals(1, h.chunkCount);
      assertEquals(h.headerSizeBytes, h.chunkIndexOffset);
      assertEquals(h.chunkIndexOffset + h.chunkIndexSizeBytes, h.chunksOffset);
      assertEquals(h.headerCrc32Unsigned, h.computedHeaderCrc32Unsigned, "headerCrc32 mismatch");

      ch.position(h.chunkIndexOffset);
      ByteBuffer idx =
          ByteBuffer.allocate((int) h.chunkIndexSizeBytes).order(ByteOrder.LITTLE_ENDIAN);
      readFully(ch, idx);
      idx.flip();

      double chunkStartOffsetSeconds = idx.getDouble();
      long chunkFileOffset = idx.getLong();
      long chunkByteLength = Integer.toUnsignedLong(idx.getInt());
      long chunkCrc32Unsigned = Integer.toUnsignedLong(idx.getInt());

      assertEquals(0.0, chunkStartOffsetSeconds, 0.0);
      assertEquals(h.chunksOffset, chunkFileOffset);
      assertTrue(chunkByteLength > 0);

      ch.position(chunkFileOffset);
      ByteBuffer chunkBuf = ByteBuffer.allocate((int) chunkByteLength);
      readFully(ch, chunkBuf);
      byte[] chunk = chunkBuf.array();

      long computedChunkCrcUnsigned = Integer.toUnsignedLong(crc32(chunk, 0, chunk.length));
      assertEquals(chunkCrc32Unsigned, computedChunkCrcUnsigned, "chunkCrc32 mismatch");

      validateChunkPayloadsHaveExpectedDecompressedLengths(
          chunk, /*expectedPvN*/ 24, /*expectedRotN*/ 12);
      validateDecompressedPvAndRotAreFiniteAndQuaternionsUnit(
          chunk, /*expectedPvN*/ 24, /*expectedRotN*/ 12);
      validateFirstSampleMatchesOrekitDirect(
          chunk, SolarSystemBody.EARTH, tStart, /*expectedPvN*/ 24, /*expectedRotN*/ 12);
      validateSampleMatchesOrekitDirectAtIndex(
          chunk,
          SolarSystemBody.EARTH,
          tStart,
          /*pvIndex*/ 5,
          /*rotIndex*/ 5,
          /*expectedPvN*/ 24,
          /*expectedRotN*/ 12);
      validatePvAndRotMetadataAreExpected(
          chunk, /*expectedPvDt*/ 3_600.0, /*expectedRotDt*/ 7_200.0);
    }
  }

  private static void validateDecompressedPvAndRotAreFiniteAndQuaternionsUnit(
      byte[] chunk, int expectedPvN, int expectedRotN) {

    // Parse chunk header to locate blocks
    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
    bb.position(0);
    bb.getInt(); // chunkId
    bb.getDouble(); // chunkStartOffset
    bb.getDouble(); // chunkDuration
    int pvBlockOffset = bb.getInt();
    int pvBlockLength = bb.getInt();
    int rotBlockOffset = bb.getInt();
    int rotBlockLength = bb.getInt();
    bb.getInt(); // chunkHeaderCrc

    // PV block
    ByteBuffer pv =
        ByteBuffer.wrap(chunk, pvBlockOffset, pvBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    pv.getInt(); // pvCodecId
    pv.getDouble(); // t0
    pv.getDouble(); // dt
    int pvN = pv.getInt();
    int pvPayloadLen = pv.getInt();
    pv.getInt(); // pvPayloadCrc
    assertEquals(expectedPvN, pvN);

    byte[] pvPayload = new byte[pvPayloadLen];
    pv.get(pvPayload);

    long pvDecompressedSize = Zstd.decompressedSize(pvPayload);
    assertEquals((long) expectedPvN * 6L * 8L, pvDecompressedSize);

    byte[] pvRaw = Zstd.decompress(pvPayload, (int) pvDecompressedSize);
    ByteBuffer pvRawBb = ByteBuffer.wrap(pvRaw).order(ByteOrder.LITTLE_ENDIAN);

    boolean anyNonZero = false;
    for (int i = 0; i < expectedPvN; i++) {
      for (int k = 0; k < 6; k++) {
        double v = pvRawBb.getDouble();
        assertTrue(
            Double.isFinite(v),
            "PV contains non-finite double at sample " + i + ", component " + k);
        if (v != 0.0) anyNonZero = true;
      }
    }
    assertTrue(anyNonZero, "PV looks suspiciously all zeros");

    // ROT block
    ByteBuffer rot =
        ByteBuffer.wrap(chunk, rotBlockOffset, rotBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    rot.getInt(); // rotCodecId
    rot.getDouble(); // t0
    rot.getDouble(); // dt
    int rotN = rot.getInt();
    int rotPayloadLen = rot.getInt();
    rot.getInt(); // rotPayloadCrc
    assertEquals(expectedRotN, rotN);

    byte[] rotPayload = new byte[rotPayloadLen];
    rot.get(rotPayload);

    long rotDecompressedSize = Zstd.decompressedSize(rotPayload);
    assertEquals((long) expectedRotN * 4L * 8L, rotDecompressedSize);

    byte[] rotRaw = Zstd.decompress(rotPayload, (int) rotDecompressedSize);
    ByteBuffer rotRawBb = ByteBuffer.wrap(rotRaw).order(ByteOrder.LITTLE_ENDIAN);

    // Quaternion unit check: allow small drift.
    // Note: we store Hipparchus quaternions, should already be unit-length, but we allow tiny
    // numeric error.
    double tol = 1e-10;

    for (int i = 0; i < expectedRotN; i++) {
      double q0 = rotRawBb.getDouble();
      double q1 = rotRawBb.getDouble();
      double q2 = rotRawBb.getDouble();
      double q3 = rotRawBb.getDouble();

      assertTrue(
          Double.isFinite(q0) && Double.isFinite(q1) && Double.isFinite(q2) && Double.isFinite(q3),
          "ROT contains non-finite quaternion at sample " + i);

      double norm2 = q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3;
      assertEquals(1.0, norm2, tol, "Quaternion not unit-length at sample " + i);
    }
  }

  private static void validateChunkPayloadsHaveExpectedDecompressedLengths(
      byte[] chunk, int expectedPvN, int expectedRotN) {

    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);

    int chunkId = bb.getInt();
    double chunkStartOffset = bb.getDouble();
    double chunkDuration = bb.getDouble();
    int pvBlockOffset = bb.getInt();
    int pvBlockLength = bb.getInt();
    int rotBlockOffset = bb.getInt();
    int rotBlockLength = bb.getInt();
    int chunkHeaderCrc = bb.getInt();

    assertEquals(0, chunkId);
    assertEquals(0.0, chunkStartOffset, 0.0);
    assertTrue(chunkDuration > 0);
    assertTrue(pvBlockOffset >= 40);
    assertTrue(pvBlockLength > 0);
    assertTrue(rotBlockOffset > pvBlockOffset);
    assertTrue(rotBlockLength > 0);

    ByteBuffer pv =
        ByteBuffer.wrap(chunk, pvBlockOffset, pvBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    int pvCodecId = pv.getInt();
    double pvT0 = pv.getDouble();
    double pvDt = pv.getDouble();
    long pvN = Integer.toUnsignedLong(pv.getInt());
    long pvPayloadLen = Integer.toUnsignedLong(pv.getInt());
    int pvPayloadCrc = pv.getInt();

    assertEquals(1, pvCodecId);
    assertEquals(0.0, pvT0, 0.0);
    assertTrue(pvDt > 0);
    assertEquals(expectedPvN, pvN);

    byte[] pvPayload = new byte[(int) pvPayloadLen];
    pv.get(pvPayload);

    assertEquals(crc32(pvPayload, 0, pvPayload.length), pvPayloadCrc);

    long pvDecompressedSize = Zstd.decompressedSize(pvPayload);
    assertEquals(
        (long) expectedPvN * 6L * 8L, pvDecompressedSize, "PV decompressed byte length mismatch");

    ByteBuffer rot =
        ByteBuffer.wrap(chunk, rotBlockOffset, rotBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    int rotCodecId = rot.getInt();
    double rotT0 = rot.getDouble();
    double rotDt = rot.getDouble();
    long rotN = Integer.toUnsignedLong(rot.getInt());
    long rotPayloadLen = Integer.toUnsignedLong(rot.getInt());
    int rotPayloadCrc = rot.getInt();

    assertEquals(1, rotCodecId);
    assertEquals(0.0, rotT0, 0.0);
    assertTrue(rotDt > 0);
    assertEquals(expectedRotN, rotN);

    byte[] rotPayload = new byte[(int) rotPayloadLen];
    rot.get(rotPayload);

    assertEquals(crc32(rotPayload, 0, rotPayload.length), rotPayloadCrc);

    long rotDecompressedSize = Zstd.decompressedSize(rotPayload);
    assertEquals(
        (long) expectedRotN * 4L * 8L,
        rotDecompressedSize,
        "ROT decompressed byte length mismatch");

    assertNotEquals(0, chunkHeaderCrc);
  }

  private static void validateFirstSampleMatchesOrekitDirect(
      byte[] chunk, SolarSystemBody bodyId, AbsoluteDate t0, int expectedPvN, int expectedRotN) {

    Frame icrf = FramesFactory.getICRF();
    CelestialBody body =
        switch (bodyId) {
          case SUN -> CelestialBodyFactory.getSun();
          case MERCURY -> CelestialBodyFactory.getMercury();
          case VENUS -> CelestialBodyFactory.getVenus();
          case EARTH -> CelestialBodyFactory.getEarth();
          case MARS -> CelestialBodyFactory.getMars();
          case JUPITER -> CelestialBodyFactory.getJupiter();
          case SATURN -> CelestialBodyFactory.getSaturn();
          case URANUS -> CelestialBodyFactory.getUranus();
          case NEPTUNE -> CelestialBodyFactory.getNeptune();
          case PLUTO -> CelestialBodyFactory.getPluto();
        };
    Frame bodyFrame = body.getBodyOrientedFrame();

    // Parse chunk header to locate PV/ROT blocks
    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
    bb.getInt(); // chunkId
    bb.getDouble(); // chunkStartOffsetSeconds
    bb.getDouble(); // chunkDurationSeconds
    int pvBlockOffset = bb.getInt();
    int pvBlockLength = bb.getInt();
    int rotBlockOffset = bb.getInt();
    int rotBlockLength = bb.getInt();
    bb.getInt(); // chunkHeaderCrc32

    // --- Read & decompress PV payload
    ByteBuffer pv =
        ByteBuffer.wrap(chunk, pvBlockOffset, pvBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    pv.getInt(); // pvCodecId
    pv.getDouble(); // t0OffsetSeconds
    pv.getDouble(); // dtSeconds
    int pvN = pv.getInt();
    int pvPayloadLen = pv.getInt();
    pv.getInt(); // pvPayloadCrc32
    assertEquals(expectedPvN, pvN);

    byte[] pvPayload = new byte[pvPayloadLen];
    pv.get(pvPayload);

    int pvRawLen = Math.toIntExact(Zstd.decompressedSize(pvPayload));
    assertEquals(expectedPvN * 6 * 8, pvRawLen);

    byte[] pvRaw = Zstd.decompress(pvPayload, pvRawLen);
    ByteBuffer pvRawBb = ByteBuffer.wrap(pvRaw).order(ByteOrder.LITTLE_ENDIAN);

    double px = pvRawBb.getDouble();
    double py = pvRawBb.getDouble();
    double pz = pvRawBb.getDouble();
    double vx = pvRawBb.getDouble();
    double vy = pvRawBb.getDouble();
    double vz = pvRawBb.getDouble();

    // --- Read & decompress ROT payload
    ByteBuffer rot =
        ByteBuffer.wrap(chunk, rotBlockOffset, rotBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    rot.getInt(); // rotCodecId
    rot.getDouble(); // t0OffsetSeconds
    rot.getDouble(); // dtSeconds
    int rotN = rot.getInt();
    int rotPayloadLen = rot.getInt();
    rot.getInt(); // rotPayloadCrc32
    assertEquals(expectedRotN, rotN);

    byte[] rotPayload = new byte[rotPayloadLen];
    rot.get(rotPayload);

    int rotRawLen = Math.toIntExact(Zstd.decompressedSize(rotPayload));
    assertEquals(expectedRotN * 4 * 8, rotRawLen);

    byte[] rotRaw = Zstd.decompress(rotPayload, rotRawLen);
    ByteBuffer rotRawBb = ByteBuffer.wrap(rotRaw).order(ByteOrder.LITTLE_ENDIAN);

    double q0 = rotRawBb.getDouble();
    double q1 = rotRawBb.getDouble();
    double q2 = rotRawBb.getDouble();
    double q3 = rotRawBb.getDouble();

    // --- Orekit direct at t0
    PVCoordinates pvTrue = body.getPVCoordinates(t0, icrf);

    Transform tr = icrf.getTransformTo(bodyFrame, t0);
    Rotation rTrue = tr.getRotation(); // should be ICRF -> body frame (same convention as dataset)

    // Tolerances: these should be extremely small because it’s the exact same computation at same
    // date.
    double posTolMeters = 1e-6;
    double velTolMps = 1e-9;
    double quatTol = 1e-12;

    assertEquals(pvTrue.getPosition().getX(), px, posTolMeters);
    assertEquals(pvTrue.getPosition().getY(), py, posTolMeters);
    assertEquals(pvTrue.getPosition().getZ(), pz, posTolMeters);

    assertEquals(pvTrue.getVelocity().getX(), vx, velTolMps);
    assertEquals(pvTrue.getVelocity().getY(), vy, velTolMps);
    assertEquals(pvTrue.getVelocity().getZ(), vz, velTolMps);

    // Quaternions have a sign ambiguity: q and -q represent the same rotation.
    // We compare both possibilities and accept the best.
    double dqSame =
        Math.abs(rTrue.getQ0() - q0)
            + Math.abs(rTrue.getQ1() - q1)
            + Math.abs(rTrue.getQ2() - q2)
            + Math.abs(rTrue.getQ3() - q3);

    double dqNeg =
        Math.abs(rTrue.getQ0() + q0)
            + Math.abs(rTrue.getQ1() + q1)
            + Math.abs(rTrue.getQ2() + q2)
            + Math.abs(rTrue.getQ3() + q3);

    assertTrue(
        Math.min(dqSame, dqNeg) < quatTol, "Quaternion mismatch vs Orekit direct (up to sign)");
  }

  private static void validateSampleMatchesOrekitDirectAtIndex(
      byte[] chunk,
      SolarSystemBody bodyId,
      AbsoluteDate chunkT0,
      int pvIndex,
      int rotIndex,
      int expectedPvN,
      int expectedRotN) {

    Frame icrf = FramesFactory.getICRF();
    CelestialBody body =
        switch (bodyId) {
          case SUN -> CelestialBodyFactory.getSun();
          case MERCURY -> CelestialBodyFactory.getMercury();
          case VENUS -> CelestialBodyFactory.getVenus();
          case EARTH -> CelestialBodyFactory.getEarth();
          case MARS -> CelestialBodyFactory.getMars();
          case JUPITER -> CelestialBodyFactory.getJupiter();
          case SATURN -> CelestialBodyFactory.getSaturn();
          case URANUS -> CelestialBodyFactory.getUranus();
          case NEPTUNE -> CelestialBodyFactory.getNeptune();
          case PLUTO -> CelestialBodyFactory.getPluto();
        };
    Frame bodyFrame = body.getBodyOrientedFrame();

    // Parse chunk header to locate PV/ROT blocks
    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
    bb.getInt(); // chunkId
    bb.getDouble(); // chunkStartOffsetSeconds
    bb.getDouble(); // chunkDurationSeconds
    int pvBlockOffset = bb.getInt();
    int pvBlockLength = bb.getInt();
    int rotBlockOffset = bb.getInt();
    int rotBlockLength = bb.getInt();
    bb.getInt(); // chunkHeaderCrc32

    // --- PV: decompress all, then read sample pvIndex
    ByteBuffer pv =
        ByteBuffer.wrap(chunk, pvBlockOffset, pvBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    pv.getInt(); // pvCodecId
    pv.getDouble(); // t0OffsetSeconds
    double pvDt = pv.getDouble();
    int pvN = pv.getInt();
    int pvPayloadLen = pv.getInt();
    pv.getInt(); // pvPayloadCrc32

    assertEquals(expectedPvN, pvN);
    assertTrue(pvIndex >= 0 && pvIndex < pvN, "pvIndex out of range");

    byte[] pvPayload = new byte[pvPayloadLen];
    pv.get(pvPayload);

    int pvRawLen = Math.toIntExact(Zstd.decompressedSize(pvPayload));
    byte[] pvRaw = Zstd.decompress(pvPayload, pvRawLen);
    ByteBuffer pvRawBb = ByteBuffer.wrap(pvRaw).order(ByteOrder.LITTLE_ENDIAN);

    int pvStrideBytes = 6 * 8;
    pvRawBb.position(pvIndex * pvStrideBytes);

    double px = pvRawBb.getDouble();
    double py = pvRawBb.getDouble();
    double pz = pvRawBb.getDouble();
    double vx = pvRawBb.getDouble();
    double vy = pvRawBb.getDouble();
    double vz = pvRawBb.getDouble();

    // --- ROT: decompress all, then read sample rotIndex
    ByteBuffer rot =
        ByteBuffer.wrap(chunk, rotBlockOffset, rotBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    rot.getInt(); // rotCodecId
    rot.getDouble(); // t0OffsetSeconds
    double rotDt = rot.getDouble();
    int rotN = rot.getInt();
    int rotPayloadLen = rot.getInt();
    rot.getInt(); // rotPayloadCrc32

    assertEquals(expectedRotN, rotN);
    assertTrue(rotIndex >= 0 && rotIndex < rotN, "rotIndex out of range");

    byte[] rotPayload = new byte[rotPayloadLen];
    rot.get(rotPayload);

    int rotRawLen = Math.toIntExact(Zstd.decompressedSize(rotPayload));
    byte[] rotRaw = Zstd.decompress(rotPayload, rotRawLen);
    ByteBuffer rotRawBb = ByteBuffer.wrap(rotRaw).order(ByteOrder.LITTLE_ENDIAN);

    int rotStrideBytes = 4 * 8;
    rotRawBb.position(rotIndex * rotStrideBytes);

    double q0 = rotRawBb.getDouble();
    double q1 = rotRawBb.getDouble();
    double q2 = rotRawBb.getDouble();
    double q3 = rotRawBb.getDouble();

    // --- Orekit direct at matching instants
    AbsoluteDate pvT = chunkT0.shiftedBy(pvIndex * pvDt);
    PVCoordinates pvTrue = body.getPVCoordinates(pvT, icrf);

    AbsoluteDate rotT = chunkT0.shiftedBy(rotIndex * rotDt);
    Transform tr = icrf.getTransformTo(bodyFrame, rotT);
    Rotation rTrue = tr.getRotation();

    // Tolerances (still should be extremely small: exact same computation at exact same dates)
    double posTolMeters = 1e-6;
    double velTolMps = 1e-9;
    double quatTol = 1e-12;

    assertEquals(pvTrue.getPosition().getX(), px, posTolMeters);
    assertEquals(pvTrue.getPosition().getY(), py, posTolMeters);
    assertEquals(pvTrue.getPosition().getZ(), pz, posTolMeters);

    assertEquals(pvTrue.getVelocity().getX(), vx, velTolMps);
    assertEquals(pvTrue.getVelocity().getY(), vy, velTolMps);
    assertEquals(pvTrue.getVelocity().getZ(), vz, velTolMps);

    // Quaternion sign ambiguity: q and -q are equivalent
    double dqSame =
        Math.abs(rTrue.getQ0() - q0)
            + Math.abs(rTrue.getQ1() - q1)
            + Math.abs(rTrue.getQ2() - q2)
            + Math.abs(rTrue.getQ3() - q3);

    double dqNeg =
        Math.abs(rTrue.getQ0() + q0)
            + Math.abs(rTrue.getQ1() + q1)
            + Math.abs(rTrue.getQ2() + q2)
            + Math.abs(rTrue.getQ3() + q3);

    assertTrue(
        Math.min(dqSame, dqNeg) < quatTol, "Quaternion mismatch vs Orekit direct (up to sign)");
  }

  private static void validatePvAndRotMetadataAreExpected(
      byte[] chunk, double expectedPvDt, double expectedRotDt) {

    ByteBuffer bb = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
    bb.getInt(); // chunkId
    bb.getDouble(); // chunkStartOffsetSeconds
    bb.getDouble(); // chunkDurationSeconds
    int pvBlockOffset = bb.getInt();
    int pvBlockLength = bb.getInt();
    int rotBlockOffset = bb.getInt();
    int rotBlockLength = bb.getInt();
    bb.getInt(); // chunkHeaderCrc32

    // PV meta
    ByteBuffer pv =
        ByteBuffer.wrap(chunk, pvBlockOffset, pvBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    int pvCodecId = pv.getInt();
    double pvT0 = pv.getDouble();
    double pvDt = pv.getDouble();
    long pvN = Integer.toUnsignedLong(pv.getInt());
    long pvPayloadLen = Integer.toUnsignedLong(pv.getInt());
    pv.getInt(); // pvPayloadCrc32

    assertEquals(1, pvCodecId);
    assertEquals(0.0, pvT0, 0.0);
    assertEquals(expectedPvDt, pvDt, 0.0);
    assertTrue(pvN > 0);
    assertTrue(pvPayloadLen > 0);

    // ROT meta
    ByteBuffer rot =
        ByteBuffer.wrap(chunk, rotBlockOffset, rotBlockLength).order(ByteOrder.LITTLE_ENDIAN);
    int rotCodecId = rot.getInt();
    double rotT0 = rot.getDouble();
    double rotDt = rot.getDouble();
    long rotN = Integer.toUnsignedLong(rot.getInt());
    long rotPayloadLen = Integer.toUnsignedLong(rot.getInt());
    rot.getInt(); // rotPayloadCrc32

    assertEquals(1, rotCodecId);
    assertEquals(0.0, rotT0, 0.0);
    assertEquals(expectedRotDt, rotDt, 0.0);
    assertTrue(rotN > 0);
    assertTrue(rotPayloadLen > 0);
  }

  private static ParsedHeader readHeader(FileChannel ch) throws Exception {
    // The header is variable-length due to strings. We parse sequentially, tracking bytes consumed.
    ch.position(0);

    CountingReader r = new CountingReader(ch);

    byte[] magic = r.readBytes(8);
    int versionMajor = r.readU32();
    int versionMinor = r.readU32();
    long bodyId = Integer.toUnsignedLong(r.readU32());
    long timeScale = Integer.toUnsignedLong(r.readU32());
    double tStartTaiOffsetSeconds = r.readF64();

    String datasetStart = r.readStringUtf8();
    String datasetEnd = r.readStringUtf8();
    String icrfFrameId = r.readStringUtf8();
    String bodyFixedFrameId = r.readStringUtf8();
    String orekitDataId = r.readStringUtf8();

    double chunkDurationSeconds = r.readF64();
    long chunkCount = Integer.toUnsignedLong(r.readU32());
    long chunkIndexOffset = r.readU64();
    long chunksOffset = r.readU64();
    long headerCrc32Unsigned = Integer.toUnsignedLong(r.readU32());

    int headerSizeBytes = r.bytesRead;
    long chunkIndexSizeBytes = chunkCount * (8 + 8 + 4 + 4);

    // Compute CRC of header bytes excluding last u32:
    byte[] headerBytes = new byte[headerSizeBytes];
    ch.position(0);
    ByteBuffer hb = ByteBuffer.wrap(headerBytes);
    readFully(ch, hb);

    long computedHeaderCrc32Unsigned =
        Integer.toUnsignedLong(crc32(headerBytes, 0, headerSizeBytes - 4));

    return new ParsedHeader(
        magic,
        versionMajor,
        versionMinor,
        bodyId,
        (int) timeScale,
        tStartTaiOffsetSeconds,
        datasetStart,
        datasetEnd,
        icrfFrameId,
        bodyFixedFrameId,
        orekitDataId,
        chunkDurationSeconds,
        (int) chunkCount,
        chunkIndexOffset,
        chunksOffset,
        headerCrc32Unsigned,
        computedHeaderCrc32Unsigned,
        headerSizeBytes,
        chunkIndexSizeBytes);
  }

  private static GeneratorConfigV1 minimalCfg(Path orekitZip, Path outDir) throws Exception {
    String sha = HashUtils.sha256HexOfFile(orekitZip);

    List<SolarSystemBody> bodies = List.of(SolarSystemBody.EARTH);

    EnumMap<SolarSystemBody, BodyGenerationParams> p = new EnumMap<>(SolarSystemBody.class);
    p.put(SolarSystemBody.EARTH, new BodyGenerationParams(3_600.0, 7_200.0, 86_400.0));

    return new GeneratorConfigV1(orekitZip, sha, outDir, bodies, 0.0, Double.NaN, 6, 1, 2, 8, 3, p);
  }

  private static void initOrekitData(File zipFile) throws Exception {
    DataContext.getDefault();
    DataProvidersManager mgr = DataContext.getDefault().getDataProvidersManager();
    mgr.clearProviders();
    mgr.addProvider(new ZipJarCrawler(zipFile));
  }

  private static void readFully(FileChannel ch, ByteBuffer dst) throws Exception {
    while (dst.hasRemaining()) {
      int r = ch.read(dst);
      if (r < 0) {
        throw new IllegalStateException("Unexpected EOF");
      }
    }
  }

  private static int crc32(byte[] bytes, int off, int len) {
    CRC32 crc = new CRC32();
    crc.update(bytes, off, len);
    return (int) crc.getValue();
  }

  private record ParsedHeader(
      byte[] magic,
      int versionMajor,
      int versionMinor,
      long bodyId,
      int timeScale,
      double tStartTaiOffsetSeconds,
      String datasetStart,
      String datasetEnd,
      String icrfFrameId,
      String bodyFixedFrameId,
      String orekitDataId,
      double chunkDurationSeconds,
      int chunkCount,
      long chunkIndexOffset,
      long chunksOffset,
      long headerCrc32Unsigned,
      long computedHeaderCrc32Unsigned,
      int headerSizeBytes,
      long chunkIndexSizeBytes) {}

  private static final class CountingReader {
    private final FileChannel ch;
    private int bytesRead = 0;

    CountingReader(FileChannel ch) {
      this.ch = ch;
    }

    byte[] readBytes(int n) throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(n);
      readFullyUnchecked(ch, bb);
      bytesRead += n;
      return bb.array();
    }

    int readU32() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
      readFullyUnchecked(ch, bb);
      bytesRead += 4;
      bb.flip();
      return bb.getInt();
    }

    long readU64() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFullyUnchecked(ch, bb);
      bytesRead += 8;
      bb.flip();
      return bb.getLong();
    }

    double readF64() throws IOException {
      ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
      readFullyUnchecked(ch, bb);
      bytesRead += 8;
      bb.flip();
      return bb.getDouble();
    }

    String readStringUtf8() throws IOException {
      long len = Integer.toUnsignedLong(readU32());
      if (len > 10_000_000L) {
        throw new IllegalStateException("Suspicious string length: " + len);
      }
      byte[] b = readBytes((int) len);
      return new String(b, StandardCharsets.UTF_8);
    }

    private static void readFullyUnchecked(FileChannel ch, ByteBuffer dst) throws IOException {
      while (dst.hasRemaining()) {
        int r = ch.read(dst);
        if (r < 0) {
          throw new IllegalStateException("Unexpected EOF");
        }
      }
    }
  }
}
