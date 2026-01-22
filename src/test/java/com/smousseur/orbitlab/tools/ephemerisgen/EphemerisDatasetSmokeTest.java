package com.smousseur.orbitlab.tools.ephemerisgen;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

import com.smousseur.orbitlab.simulation.OrekitService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

final class EphemerisDatasetSmokeTest {

  @Test
  void chunk_has_valid_crcs_and_expected_sample_counts() throws Exception {
    OrekitService.get().initialize();
    String zipProp =
        "C:\\Prog\\projects\\intelliJ\\orbitlab\\src\\main\\resources\\orekit-data.zip"; // System.getProperty("orekitDataZip");
    Assumptions.assumeTrue(zipProp != null && !zipProp.isBlank(), "Missing -DorekitDataZip=...");

    Path orekitZip = Path.of(zipProp).toAbsolutePath().normalize();
    Assumptions.assumeTrue(
        java.nio.file.Files.exists(orekitZip), "orekit-data.zip not found: " + orekitZip);

    initOrekitData(orekitZip.toFile());

    GeneratorConfigV1 cfg =
        GeneratorConfigV1.defaultV1(
            orekitZip, java.nio.file.Files.createTempDirectory("ephemgen-smoke"));

    TimeScale tai = TimeScalesFactory.getTAI();
    AbsoluteDate tStart = new AbsoluteDate(1989, 12, 27, 23, 59, 28.0, tai);

    // 1-day dataset window, end exclusive.
    double chunkDur = 86_400.0;
    AbsoluteDate tEndExclusive = tStart.shiftedBy(chunkDur);

    BodyGenerationParams p =
        new BodyGenerationParams(
            3_600.0, // dtPv = 1h => expected nPv = 24
            7_200.0, // dtRot = 2h => expected nRot = 12
            chunkDur);

    ChunkComputerV1 task =
        new ChunkComputerV1(cfg, SolarSystemBody.EARTH, p, tStart, tEndExclusive, 0);
    byte[] chunk = task.call().chunkBytes();

    // Parse chunk header
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
    assertEquals(chunkDur, chunkDuration, 0.0);

    // Validate chunk header CRC (CRC over header bytes excluding the CRC field)
    int chunkHeaderSize = 40;
    assertEquals(chunkHeaderSize, bb.position(), "Parser should be exactly at end of chunk header");

    // Validate chunk header CRC (CRC over header bytes excluding the CRC field)
    byte[] headerBytes = new byte[chunkHeaderSize];
    System.arraycopy(chunk, 0, headerBytes, 0, chunkHeaderSize);

    int expectedHeaderCrcSigned = crc32(headerBytes, 0, chunkHeaderSize - 4);

    long expectedHeaderCrc = Integer.toUnsignedLong(expectedHeaderCrcSigned);
    long actualHeaderCrc = Integer.toUnsignedLong(chunkHeaderCrc);

    assertEquals(expectedHeaderCrc, actualHeaderCrc, "chunkHeaderCrc32 mismatch (unsigned)");

    // Validate PV block
    assertTrue(pvBlockOffset >= 40);
    assertTrue(pvBlockLength > 0);

    ByteBuffer pv = slice(chunk, pvBlockOffset, pvBlockLength);

    int pvCodecId = pv.getInt();
    double pvT0 = pv.getDouble();
    double pvDt = pv.getDouble();
    long pvN = Integer.toUnsignedLong(pv.getInt());
    long pvPayloadLen = Integer.toUnsignedLong(pv.getInt());
    int pvPayloadCrc = pv.getInt();

    assertEquals(1, pvCodecId);
    assertEquals(0.0, pvT0, 0.0);
    assertEquals(3_600.0, pvDt, 0.0);
    assertEquals(24, pvN);
    assertTrue(pvPayloadLen > 0);

    byte[] pvPayload = new byte[(int) pvPayloadLen];
    pv.get(pvPayload);
    assertEquals(crc32(pvPayload, 0, pvPayload.length), pvPayloadCrc);

    // Validate ROT block
    assertTrue(rotBlockOffset > pvBlockOffset);
    assertTrue(rotBlockLength > 0);

    ByteBuffer rot = slice(chunk, rotBlockOffset, rotBlockLength);

    int rotCodecId = rot.getInt();
    double rotT0 = rot.getDouble();
    double rotDt = rot.getDouble();
    long rotN = Integer.toUnsignedLong(rot.getInt());
    long rotPayloadLen = Integer.toUnsignedLong(rot.getInt());
    int rotPayloadCrc = rot.getInt();

    assertEquals(1, rotCodecId);
    assertEquals(0.0, rotT0, 0.0);
    assertEquals(7_200.0, rotDt, 0.0);
    assertEquals(12, rotN);
    assertTrue(rotPayloadLen > 0);

    byte[] rotPayload = new byte[(int) rotPayloadLen];
    rot.get(rotPayload);
    assertEquals(crc32(rotPayload, 0, rotPayload.length), rotPayloadCrc);
  }

  private static ByteBuffer slice(byte[] src, int offset, int length) {
    ByteBuffer bb = ByteBuffer.wrap(src, offset, length).order(ByteOrder.LITTLE_ENDIAN);
    return bb;
  }

  private static int crc32(byte[] bytes, int off, int len) {
    CRC32 crc = new CRC32();
    crc.update(bytes, off, len);
    return (int) crc.getValue();
  }

  private static void initOrekitData(File zipFile) throws Exception {
    DataContext.getDefault();

    DataProvidersManager mgr = DataContext.getDefault().getDataProvidersManager();
    mgr.clearProviders();
    mgr.addProvider(new ZipJarCrawler(zipFile));
  }
}
