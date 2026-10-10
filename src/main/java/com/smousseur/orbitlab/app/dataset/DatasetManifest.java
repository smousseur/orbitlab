package com.smousseur.orbitlab.app.dataset;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * What a dataset release contains: its tag, and every file of the dataset with its size, its
 * fingerprint and the pieces it is published as.
 *
 * <p>The manifest embedded in the application is the source of truth for the data that build
 * expects: the release is found by its tag, each piece by its name, and every byte downloaded is
 * checked against the fingerprints written here. It is written by the publication tool and read,
 * through {@link DatasetManifestCodec}, by everything that downloads or checks the data.
 *
 * <p>A piece's address is not stored: it is deduced from the tag and the piece's name, under {@link
 * #GITHUB_DOWNLOAD_BASE} for the published release, under any other base for a test server.
 *
 * @param formatVersion the schema this manifest was written with; one claiming more than {@link
 *     #CURRENT_FORMAT_VERSION} is refused whole
 * @param tag the release the files are published under, which is also the version of the data
 * @param files the dataset's files, in a fixed order: ephemerides by body, then orbits by body
 */
public record DatasetManifest(int formatVersion, String tag, List<DatasetFile> files) {

  /** The version this build writes, and the highest one it accepts to read. */
  public static final int CURRENT_FORMAT_VERSION = 1;

  /** Where GitHub serves the files of this repository's releases, one folder per tag. */
  public static final URI GITHUB_DOWNLOAD_BASE =
      URI.create("https://github.com/smousseur/orbitlab/releases/download/");

  /** Rejects missing components and keeps an immutable copy of the files. */
  public DatasetManifest {
    Objects.requireNonNull(tag, "tag");
    files = List.copyOf(Objects.requireNonNull(files, "files"));
  }

  /**
   * The address a piece of this release is downloaded from.
   *
   * @param downloadBase the folder holding one sub-folder per tag, ending with {@code /}; {@link
   *     #GITHUB_DOWNLOAD_BASE} for the published release
   * @param piece a piece of this manifest
   * @return {@code <downloadBase><tag>/<piece name>}
   */
  public URI pieceUri(URI downloadBase, DatasetPiece piece) {
    return downloadBase.resolve(tag + "/" + piece.name());
  }

  /**
   * The size of the whole dataset.
   *
   * @return the sum of every file's size, in bytes
   */
  public long totalSize() {
    long total = 0;
    for (DatasetFile file : files) {
      total = Math.addExact(total, file.size());
    }
    return total;
  }
}
