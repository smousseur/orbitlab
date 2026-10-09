package com.smousseur.orbitlab.app.dataset;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.OrbitlabPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tells, at start-up, whether the dataset directory holds the data a build expects, without any
 * network access, without reading any file's content, and without creating anything.
 *
 * <p>A file counts as verified when it is present at its exact size and the <b>marker</b> holds the
 * same entry. The marker is a copy of the manifest the last completed installation verified,
 * written at the root of the directory once every file was checked. An entry covers a file only
 * when it is identical to the entry the current manifest expects: after a move to another version
 * of the data, an unchanged file is kept without being read again, and a changed one is checked.
 * The same rule makes a marker left by an installation interrupted halfway through an update
 * harmless: a file already replaced no longer matches its old entry.
 *
 * <p>A missing, unreadable or invalid marker is treated as absent: every file present at its size
 * is then read once through SHA-256 by the installation, and adopted if it matches.
 */
public final class DatasetChecker {

  /** The marker's name at the root of the dataset directory. */
  public static final String MARKER_NAME = "manifest.json";

  /** What a file's partial download is named after: the file's own name plus this suffix. */
  public static final String PART_SUFFIX = ".part";

  private static final Logger LOGGER = LogManager.getLogger(DatasetChecker.class);

  private final DatasetManifest manifest;
  private final Path root;

  /**
   * Creates a checker.
   *
   * @param manifest the manifest the directory must match
   * @param root the dataset directory, which may not exist
   */
  public DatasetChecker(DatasetManifest manifest, Path root) {
    this.manifest = Objects.requireNonNull(manifest, "manifest");
    this.root = Objects.requireNonNull(root, "root");
  }

  /**
   * Creates the checker of the application: the manifest embedded in this build, against the user's
   * dataset directory.
   *
   * @return the checker
   * @throws OrbitlabException if the embedded manifest is missing or invalid
   */
  public static DatasetChecker forThisBuild() {
    return new DatasetChecker(DatasetManifestCodec.readEmbedded(), OrbitlabPath.DATASET_PATH);
  }

  /**
   * The manifest the directory is checked against.
   *
   * @return the manifest
   */
  public DatasetManifest manifest() {
    return manifest;
  }

  /**
   * The dataset directory.
   *
   * @return the root every file's path is relative to
   */
  public Path root() {
    return root;
  }

  /**
   * Where a file of the manifest is read from.
   *
   * @param file a file of the manifest
   * @return its location in the dataset directory
   */
  public Path fileOf(DatasetFile file) {
    return root.resolve(file.path());
  }

  /**
   * Where a file of the manifest is downloaded to before it is complete and verified.
   *
   * @param file a file of the manifest
   * @return its partial download's location, next to the file
   */
  public Path partOf(DatasetFile file) {
    return root.resolve(file.path() + PART_SUFFIX);
  }

  /**
   * Where the marker of the last completed installation is.
   *
   * @return the marker's location
   */
  public Path marker() {
    return root.resolve(MARKER_NAME);
  }

  /**
   * Checks the directory.
   *
   * @return where each file of the manifest stands
   */
  public DatasetCheck check() {
    DatasetManifest verified = readMarker();
    Set<DatasetFile> covered = verified == null ? Set.of() : new HashSet<>(verified.files());
    List<DatasetCheck.FileCheck> files = new ArrayList<>(manifest.files().size());
    for (DatasetFile file : manifest.files()) {
      if (sizeOf(fileOf(file)) == file.size()) {
        DatasetCheck.FileStatus status =
            covered.contains(file)
                ? DatasetCheck.FileStatus.VERIFIED
                : DatasetCheck.FileStatus.TO_VERIFY;
        files.add(new DatasetCheck.FileCheck(file, status, 0));
      } else {
        long part = sizeOf(partOf(file));
        files.add(
            new DatasetCheck.FileCheck(
                file,
                DatasetCheck.FileStatus.TO_DOWNLOAD,
                part > 0 && part <= file.size() ? part : 0));
      }
    }
    return new DatasetCheck(manifest, manifest.equals(verified), files);
  }

  /** The marker's manifest, or null when there is none that can be trusted. */
  private DatasetManifest readMarker() {
    Path marker = marker();
    if (!Files.isRegularFile(marker)) {
      return null;
    }
    try {
      return DatasetManifestCodec.read(Files.readString(marker));
    } catch (IOException | OrbitlabException e) {
      LOGGER.warn("Ignoring the dataset marker {}: {}", marker, e.getMessage());
      return null;
    }
  }

  /** A regular file's size, or -1 when there is none. */
  private static long sizeOf(Path path) {
    if (!Files.isRegularFile(path)) {
      return -1;
    }
    try {
      return Files.size(path);
    } catch (IOException e) {
      LOGGER.warn("Cannot read the size of {}: {}", path, e.getMessage());
      return -1;
    }
  }
}
