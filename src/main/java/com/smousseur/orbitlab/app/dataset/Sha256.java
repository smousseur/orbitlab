package com.smousseur.orbitlab.app.dataset;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The digest the dataset manifest uses, and the way it writes fingerprints. */
public final class Sha256 {

  private Sha256() {}

  /**
   * Creates an empty SHA-256 digest.
   *
   * @return a fresh digest
   */
  public static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required of every Java platform", e);
    }
  }

  /**
   * Completes a digest and writes it as the manifest does.
   *
   * @param digest the digest to complete; it is reset
   * @return 64 lowercase hexadecimal characters
   */
  public static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }
}
