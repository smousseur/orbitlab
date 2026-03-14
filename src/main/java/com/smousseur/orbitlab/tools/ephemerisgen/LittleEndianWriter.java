package com.smousseur.orbitlab.tools.ephemerisgen;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A sequential byte writer that encodes primitive values in little-endian byte order.
 *
 * <p>Provides convenience methods for writing unsigned 32-bit and 64-bit integers,
 * 64-bit floating-point values, length-prefixed UTF-8 strings, and raw byte arrays.
 * The accumulated bytes can be retrieved as a single array via {@link #toByteArray()}.
 */
final class LittleEndianWriter {

  private final ByteArrayOutputStream out;

  /**
   * Creates a new writer with the specified initial buffer capacity.
   *
   * @param initialCapacity the initial capacity in bytes (minimum 32)
   */
  LittleEndianWriter(int initialCapacity) {
    this.out = new ByteArrayOutputStream(Math.max(32, initialCapacity));
  }

  /**
   * Writes raw bytes to the output buffer.
   *
   * @param b the byte array to write
   */
  void writeBytes(byte[] b) {
    out.writeBytes(b);
  }

  /**
   * Writes an unsigned 32-bit integer in little-endian byte order.
   *
   * @param v the integer value to write
   */
  void writeU32(int v) {
    ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(v);
    out.writeBytes(bb.array());
  }

  /**
   * Writes an unsigned 64-bit integer in little-endian byte order.
   *
   * @param v the long value to write
   */
  void writeU64(long v) {
    ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    bb.putLong(v);
    out.writeBytes(bb.array());
  }

  /**
   * Writes a 64-bit IEEE 754 floating-point value in little-endian byte order.
   *
   * @param v the double value to write
   */
  void writeF64(double v) {
    ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    bb.putDouble(v);
    out.writeBytes(bb.array());
  }

  /**
   * Writes a UTF-8 encoded string prefixed by its byte length as a U32.
   *
   * @param s the string to write
   */
  void writeStringUtf8(String s) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    writeU32(bytes.length);
    writeBytes(bytes);
  }

  /**
   * Returns all accumulated bytes as a new byte array.
   *
   * @return the byte array containing all written data
   */
  byte[] toByteArray() {
    return out.toByteArray();
  }
}
