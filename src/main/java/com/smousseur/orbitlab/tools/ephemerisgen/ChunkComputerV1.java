package com.smousseur.orbitlab.tools.ephemerisgen;

import com.github.luben.zstd.Zstd;
import com.smousseur.orbitlab.core.SolarSystemBody;
import com.smousseur.orbitlab.simulation.OrekitService;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.zip.CRC32;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.orekit.bodies.CelestialBody;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/**
 * Computes a single ephemeris data chunk for a celestial body in the V1 binary format.
 *
 * <p>Each chunk contains position/velocity (PV) samples and rotation (quaternion) samples
 * for a fixed time window. Samples are serialized as little-endian IEEE 754 doubles,
 * compressed with Zstd, and packaged with CRC-32 integrity checksums.
 *
 * <p>This class implements {@link Callable} so it can be submitted to an executor for
 * parallel chunk computation.
 */
final class ChunkComputerV1 implements Callable<BodyFileWriterV1.ChunkResult> {

  private final GeneratorConfigV1 cfg;
  private final SolarSystemBody bodyId;
  private final BodyGenerationParams params;
  private final AbsoluteDate tStart;
  private final AbsoluteDate tEndExclusive;
  private final int chunkId;

  ChunkComputerV1(
      GeneratorConfigV1 cfg,
      SolarSystemBody bodyId,
      BodyGenerationParams params,
      AbsoluteDate tStart,
      AbsoluteDate tEndExclusive,
      int chunkId) {
    this.cfg = Objects.requireNonNull(cfg, "cfg");
    this.bodyId = Objects.requireNonNull(bodyId, "bodyId");
    this.params = Objects.requireNonNull(params, "params");
    this.tStart = Objects.requireNonNull(tStart, "tStart");
    this.tEndExclusive = Objects.requireNonNull(tEndExclusive, "tEndExclusive");
    this.chunkId = chunkId;
  }

  @Override
  public BodyFileWriterV1.ChunkResult call() throws Exception {
    Frame icrf = FramesFactory.getICRF();
    CelestialBody body = OrekitService.get().body(bodyId);
    Frame bodyFrame = body.getBodyOrientedFrame();

    double chunkDur = params.chunkDurationSeconds();
    double chunkStartOffsetSeconds = chunkId * chunkDur;

    AbsoluteDate chunkStart = tStart.shiftedBy(chunkStartOffsetSeconds);
    AbsoluteDate chunkEndExclusive = minDate(chunkStart.shiftedBy(chunkDur), tEndExclusive);

    // PV samples
    SampledCompressed pv =
        buildPvCompressed(body, icrf, chunkStart, chunkEndExclusive, params.dtPvSeconds());
    // ROT samples
    SampledCompressed rot =
        buildRotCompressed(icrf, bodyFrame, chunkStart, chunkEndExclusive, params.dtRotSeconds());

    byte[] pvBlock =
        buildPvBlock(chunkStartOffsetSeconds, params.dtPvSeconds(), pv.n(), pv.compressedPayload());
    byte[] rotBlock =
        buildRotBlock(
            chunkStartOffsetSeconds, params.dtRotSeconds(), rot.n(), rot.compressedPayload());

    int chunkHeaderSize = 40; // u32 + f64 + f64 + 4*u32 + u32 CRC = 40 bytes
    int pvBlockLength = pvBlock.length;
    int rotBlockOffset = chunkHeaderSize + pvBlockLength;
    int rotBlockLength = rotBlock.length;

    // Chunk header (without CRC field first)
    ByteBuffer hdr = ByteBuffer.allocate(chunkHeaderSize).order(ByteOrder.LITTLE_ENDIAN);
    hdr.putInt(chunkId);
    hdr.putDouble(chunkStartOffsetSeconds);
    hdr.putDouble(chunkDur);
    hdr.putInt(chunkHeaderSize);
    hdr.putInt(pvBlockLength);
    hdr.putInt(rotBlockOffset);
    hdr.putInt(rotBlockLength);
    hdr.putInt(0); // placeholder CRC
    byte[] hdrBytes = hdr.array();

    int chunkHeaderCrc = crc32(hdrBytes, true);
    ByteBuffer.wrap(hdrBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(chunkHeaderSize - 4, chunkHeaderCrc);

    // Build full chunk bytes
    byte[] chunkBytes = new byte[hdrBytes.length + pvBlock.length + rotBlock.length];
    System.arraycopy(hdrBytes, 0, chunkBytes, 0, hdrBytes.length);
    System.arraycopy(pvBlock, 0, chunkBytes, hdrBytes.length, pvBlock.length);
    System.arraycopy(rotBlock, 0, chunkBytes, hdrBytes.length + pvBlock.length, rotBlock.length);

    int chunkCrc = crc32(chunkBytes, false);
    return new BodyFileWriterV1.ChunkResult(chunkId, chunkStartOffsetSeconds, chunkBytes, chunkCrc);
  }

  private static AbsoluteDate minDate(AbsoluteDate a, AbsoluteDate b) {
    return (a.compareTo(b) <= 0) ? a : b;
  }

  private SampledCompressed buildPvCompressed(
      CelestialBody body,
      Frame icrf,
      AbsoluteDate chunkStart,
      AbsoluteDate chunkEndExclusive,
      double dtPv)
      throws Exception {

    int n = sampleCount(chunkStart, chunkEndExclusive, dtPv);
    ByteBuffer raw = ByteBuffer.allocate(n * 6 * 8).order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < n; i++) {
      AbsoluteDate t = chunkStart.shiftedBy(i * dtPv);
      PVCoordinates pv = body.getPVCoordinates(t, icrf);

      raw.putDouble(pv.getPosition().getX());
      raw.putDouble(pv.getPosition().getY());
      raw.putDouble(pv.getPosition().getZ());
      raw.putDouble(pv.getVelocity().getX());
      raw.putDouble(pv.getVelocity().getY());
      raw.putDouble(pv.getVelocity().getZ());
    }

    byte[] rawBytes = raw.array();
    byte[] compressed = Zstd.compress(rawBytes, cfg.zstdLevel());
    return new SampledCompressed(n, compressed);
  }

  private SampledCompressed buildRotCompressed(
      Frame icrf,
      Frame bodyFrame,
      AbsoluteDate chunkStart,
      AbsoluteDate chunkEndExclusive,
      double dtRot)
      throws Exception {

    int n = sampleCount(chunkStart, chunkEndExclusive, dtRot);
    ByteBuffer raw = ByteBuffer.allocate(n * 4 * 8).order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < n; i++) {
      AbsoluteDate t = chunkStart.shiftedBy(i * dtRot);

      Transform tr = icrf.getTransformTo(bodyFrame, t);
      Rotation r = tr.getRotation(); // rotation ICRF -> body frame

      raw.putDouble(r.getQ0());
      raw.putDouble(r.getQ1());
      raw.putDouble(r.getQ2());
      raw.putDouble(r.getQ3());
    }

    byte[] rawBytes = raw.array();
    byte[] compressed = Zstd.compress(rawBytes, cfg.zstdLevel());
    return new SampledCompressed(n, compressed);
  }

  private static int sampleCount(AbsoluteDate start, AbsoluteDate endExclusive, double dtSeconds) {
    double span = endExclusive.durationFrom(start);
    if (span <= 0) return 0;
    return (int) Math.ceil(span / dtSeconds);
  }

  private static byte[] buildPvBlock(
      double t0OffsetSeconds, double dtSeconds, int n, byte[] compressedPayload) {
    LittleEndianWriter w = new LittleEndianWriter(128 + compressedPayload.length);
    w.writeU32(1); // pvCodecId = PV_F64_RAW_ZSTD
    w.writeF64(t0OffsetSeconds);
    w.writeF64(dtSeconds);
    w.writeU32(n);
    w.writeU32(compressedPayload.length);
    w.writeU32(crc32(compressedPayload, false)); // CRC of COMPRESSED bytes
    w.writeBytes(compressedPayload);
    return w.toByteArray();
  }

  private static byte[] buildRotBlock(
      double t0OffsetSeconds, double dtSeconds, int n, byte[] compressedPayload) {
    LittleEndianWriter w = new LittleEndianWriter(128 + compressedPayload.length);
    w.writeU32(1); // rotCodecId = ROT_F64_RAW_ZSTD
    w.writeF64(t0OffsetSeconds);
    w.writeF64(dtSeconds);
    w.writeU32(n);
    w.writeU32(compressedPayload.length);
    w.writeU32(crc32(compressedPayload, false)); // CRC of COMPRESSED bytes
    w.writeBytes(compressedPayload);
    return w.toByteArray();
  }

  private static int crc32(byte[] bytes, boolean excludeLastU32) {
    CRC32 crc = new CRC32();
    int len = excludeLastU32 ? (bytes.length - 4) : bytes.length;
    crc.update(bytes, 0, len);
    return (int) crc.getValue();
  }

  /**
   * Holds sampled data after Zstd compression.
   *
   * @param n the number of samples before compression
   * @param compressedPayload the Zstd-compressed byte payload
   */
  private record SampledCompressed(int n, byte[] compressedPayload) {}
}
