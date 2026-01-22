package com.smousseur.orbitlab.tools.ephemerisgen;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class LittleEndianWriter {

  private final ByteArrayOutputStream out;

  LittleEndianWriter(int initialCapacity) {
    this.out = new ByteArrayOutputStream(Math.max(32, initialCapacity));
  }

  void writeBytes(byte[] b) {
    out.writeBytes(b);
  }

  void writeU32(int v) {
    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(v);
    out.writeBytes(bb.array());
  }

  void writeU64(long v) {
    ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    bb.putLong(v);
    out.writeBytes(bb.array());
  }

  void writeF64(double v) {
    ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    bb.putDouble(v);
    out.writeBytes(bb.array());
  }

  void writeStringUtf8(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    writeU32(bytes.length);
    writeBytes(bytes);
  }

  byte[] toByteArray() {
    return out.toByteArray();
  }
}
