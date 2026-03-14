package com.smousseur.orbitlab.tools.ephemerisgen;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Utility class for computing cryptographic hash digests of files.
 */
public final class HashUtils {

  /**
   * Computes the SHA-256 hash of a file and returns it as a lowercase hexadecimal string.
   *
   * @param path the path to the file to hash
   * @return the SHA-256 digest as a lowercase hex string
   * @throws Exception if the file cannot be read or the SHA-256 algorithm is unavailable
   */
  public static String sha256HexOfFile(Path path) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(path)) {
      byte[] buf = new byte[1024 * 1024];
      int r;
      while ((r = in.read(buf)) >= 0) {
        if (r > 0) md.update(buf, 0, r);
      }
    }
    return toHex(md.digest());
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }

  private HashUtils() {}
}
