package com.smousseur.orbitlab.app.dataset;

import java.util.List;
import java.util.Objects;

/**
 * What the dataset directory holds against the manifest a build expects, as {@link DatasetChecker}
 * found it without reading any file's content.
 *
 * @param manifest the manifest the directory was checked against
 * @param markerCurrent whether the marker left by the last completed installation equals that
 *     manifest
 * @param files the state of every file of the manifest, in its order
 */
public record DatasetCheck(DatasetManifest manifest, boolean markerCurrent, List<FileCheck> files) {

  /** Where a file of the manifest stands. */
  public enum FileStatus {
    /**
     * Present at its exact size, and the marker holds the same entry: it was verified by an
     * installation and is used as is.
     */
    VERIFIED,
    /**
     * Present at its exact size, but no identical entry in the marker: it must be read through
     * SHA-256 before it is adopted.
     */
    TO_VERIFY,
    /** Missing or of another size: it must be downloaded. */
    TO_DOWNLOAD
  }

  /**
   * One file and where it stands.
   *
   * @param file the manifest's entry
   * @param status where it stands
   * @param partBytes the size of its partial download, 0 when there is none or when it is larger
   *     than the file and will be discarded
   */
  public record FileCheck(DatasetFile file, FileStatus status, long partBytes) {

    /** Rejects missing components. */
    public FileCheck {
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(status, "status");
    }
  }

  /** Rejects missing components and keeps an immutable copy of the files. */
  public DatasetCheck {
    Objects.requireNonNull(manifest, "manifest");
    files = List.copyOf(Objects.requireNonNull(files, "files"));
  }

  /**
   * Tells whether the dataset can be used without installing anything.
   *
   * @return true when the marker equals the manifest and every file is verified
   */
  public boolean ready() {
    return markerCurrent && files.stream().allMatch(f -> f.status() == FileStatus.VERIFIED);
  }

  /**
   * The files in a given state.
   *
   * @param status the state
   * @return those files, in the manifest's order
   */
  public List<DatasetFile> filesWith(FileStatus status) {
    return files.stream().filter(f -> f.status() == status).map(FileCheck::file).toList();
  }

  /**
   * The bytes left to download, files to verify not counted.
   *
   * @return for every file to download, its size minus its partial download
   */
  public long bytesToDownload() {
    long total = 0;
    for (FileCheck check : files) {
      if (check.status() == FileStatus.TO_DOWNLOAD) {
        total = Math.addExact(total, check.file().size() - check.partBytes());
      }
    }
    return total;
  }
}
