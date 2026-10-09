package com.smousseur.orbitlab.app.dataset;

import java.util.List;
import java.util.Objects;

/**
 * One file of the dataset as the application reads it under {@code ~/.orbitlab/dataset}, and the
 * pieces it is published as.
 *
 * <p>Every file has at least one piece. A file published whole has exactly one, which repeats its
 * size and fingerprint under the file's own name, so a client walks the pieces of every file the
 * same way.
 *
 * @param path the file's location relative to the dataset directory, with {@code /} as separator on
 *     every platform (for instance {@code ephemeris/MOON.bin})
 * @param size the whole file's size in bytes
 * @param sha256 the whole file's SHA-256, as 64 lowercase hexadecimal characters
 * @param pieces the published pieces, in the order they are concatenated
 */
public record DatasetFile(String path, long size, String sha256, List<DatasetPiece> pieces) {

  /** Rejects missing components and keeps an immutable copy of the pieces. */
  public DatasetFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(sha256, "sha256");
    pieces = List.copyOf(Objects.requireNonNull(pieces, "pieces"));
  }
}
