package com.smousseur.orbitlab.app.dataset;

import java.util.Objects;

/**
 * One file as it is published in the dataset release, and downloaded from it.
 *
 * <p>A release is a flat list of files, each under a size limit; a dataset file that fits is
 * published whole, as its own single piece under its own name, and one that does not is cut into
 * several pieces the client concatenates back in order.
 *
 * @param name the file's name in the release, which is also the last segment of its download
 *     address
 * @param size the piece's size in bytes
 * @param sha256 the piece's SHA-256, as 64 lowercase hexadecimal characters
 */
public record DatasetPiece(String name, long size, String sha256) {

  /** Rejects missing components; the codec checks their values. */
  public DatasetPiece {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(sha256, "sha256");
  }
}
