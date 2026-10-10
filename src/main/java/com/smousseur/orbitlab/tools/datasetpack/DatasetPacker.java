package com.smousseur.orbitlab.tools.datasetpack;

import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.app.dataset.DatasetManifestCodec;
import com.smousseur.orbitlab.app.dataset.DatasetPiece;
import com.smousseur.orbitlab.app.dataset.Sha256;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Prepares a dataset release: copies every file of the dataset into one folder, cut into pieces
 * where a file is over the release's size limit, and writes the manifest that describes them.
 *
 * <p>The order of the work is the order of what can go wrong. Everything that can refuse without
 * copying anything does so first: a missing or empty file, a manifest that already describes this
 * tag with other data, an output folder that is not this tool's to clear, a volume too small. Then
 * each source is read once, every byte written to its piece and fed to two SHA-256 digests, the
 * piece's and the whole file's. The manifest is written last, so a run that fails on the way leaves
 * the previous manifest as it was.
 *
 * <p><b>A tag keeps its data.</b> Once a tag is published, the manifest embedded in the application
 * pins its fingerprints, and the orbit files cannot be regenerated identically — their generator
 * starts from the current date. Packing the same tag again is therefore accepted only when it
 * produces the same manifest; anything else is refused, and the message says to choose a new tag.
 */
public final class DatasetPacker {

  private static final Logger LOGGER = LogManager.getLogger(DatasetPacker.class);

  private static final int COPY_BUFFER_BYTES = 8 << 20;

  /** Piece names carry a three-digit index. */
  private static final int MAX_PIECES = 999;

  /**
   * A file the dataset must contain.
   *
   * @param file where the file is read from
   * @param path its path in the manifest, relative to the dataset directory, with {@code /}
   */
  public record Source(Path file, String path) {

    /** Rejects missing components. */
    public Source {
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(path, "path");
    }
  }

  /** The space left on the volume holding a directory, in bytes. */
  @FunctionalInterface
  public interface UsableSpace {

    /**
     * Measures the usable space of a directory's volume.
     *
     * @param directory an existing directory
     * @return the bytes that can still be written there
     * @throws IOException if the volume cannot be queried
     */
    long of(Path directory) throws IOException;
  }

  private final List<Source> sources;
  private final Path outputDir;
  private final Path manifestFile;
  private final long pieceLimit;
  private final UsableSpace usableSpace;

  /**
   * Creates a packer.
   *
   * @param sources every file of the dataset, in manifest order; their file names must differ,
   *     since a release is a flat list of files
   * @param outputDir the folder the files to publish are written to; cleared at each run, so it
   *     must hold nothing but this tool's output
   * @param manifestFile where the manifest is written
   * @param pieceLimit the largest size a published file may have, in bytes
   * @param usableSpace how the space left on the output volume is measured
   */
  public DatasetPacker(
      List<Source> sources,
      Path outputDir,
      Path manifestFile,
      long pieceLimit,
      UsableSpace usableSpace) {
    this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    this.outputDir = Objects.requireNonNull(outputDir, "outputDir").toAbsolutePath().normalize();
    this.manifestFile = Objects.requireNonNull(manifestFile, "manifestFile");
    this.usableSpace = Objects.requireNonNull(usableSpace, "usableSpace");
    if (pieceLimit < 1) {
      throw new IllegalArgumentException("pieceLimit must be >= 1");
    }
    this.pieceLimit = pieceLimit;
    Set<String> names = new HashSet<>();
    for (Source source : this.sources) {
      if (!names.add(source.file().getFileName().toString())) {
        throw new IllegalArgumentException(
            "Two sources share the file name " + source.file().getFileName());
      }
    }
  }

  /**
   * Packs the dataset under a tag.
   *
   * @param tag the release the files will be published under
   * @return the manifest written
   * @throws OrbitlabException if the dataset is incomplete, if the tag is already described with
   *     other data, if the output folder holds anything but files, or if the volume is too small
   * @throws IOException if reading or writing fails
   */
  public DatasetManifest pack(String tag) throws IOException {
    DatasetManifestCodec.requireSafeName("tag", tag);
    long[] sizes = measureSources();
    DatasetManifest previous = previousManifestOf(tag);
    if (previous != null) {
      requireSameFiles(previous, sizes);
    }
    long total = 0;
    for (long size : sizes) {
      total = Math.addExact(total, size);
    }
    clearOutputDir();
    long usable = usableSpace.of(outputDir);
    if (usable < total) {
      throw new OrbitlabException(
          String.format(
              Locale.ROOT,
              "%s has %,d bytes free, the dataset needs %,d; nothing was copied",
              outputDir,
              usable,
              total));
    }

    long started = System.nanoTime();
    List<DatasetFile> files = new ArrayList<>(sources.size());
    for (int i = 0; i < sources.size(); i++) {
      files.add(copy(sources.get(i), sizes[i]));
    }
    DatasetManifest manifest =
        new DatasetManifest(DatasetManifest.CURRENT_FORMAT_VERSION, tag, files);
    if (previous != null && !previous.equals(manifest)) {
      throw alreadyPacked(tag, firstDifference(previous, manifest));
    }
    writeManifest(manifest);
    LOGGER.info(
        String.format(
            Locale.ROOT,
            "Packed %s: %d files, %d pieces, %,d bytes in %.1f s, into %s",
            tag,
            files.size(),
            files.stream().mapToInt(f -> f.pieces().size()).sum(),
            total,
            (System.nanoTime() - started) / 1e9,
            outputDir));
    LOGGER.info("Manifest written to {}", manifestFile);
    return manifest;
  }

  private long[] measureSources() throws IOException {
    List<String> problems = new ArrayList<>();
    long[] sizes = new long[sources.size()];
    for (int i = 0; i < sources.size(); i++) {
      Path file = sources.get(i).file();
      if (!Files.isRegularFile(file)) {
        problems.add(file + " is missing");
      } else {
        sizes[i] = Files.size(file);
        if (sizes[i] == 0) {
          problems.add(file + " is empty");
        }
      }
    }
    if (!problems.isEmpty()) {
      throw new OrbitlabException(
          "The dataset is incomplete, nothing was written:\n  " + String.join("\n  ", problems));
    }
    warnAboutExtraFiles();
    return sizes;
  }

  private void warnAboutExtraFiles() throws IOException {
    Set<Path> expected = new HashSet<>();
    Set<Path> directories = new LinkedHashSet<>();
    for (Source source : sources) {
      Path file = source.file().toAbsolutePath().normalize();
      expected.add(file);
      directories.add(file.getParent());
    }
    for (Path directory : directories) {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
        for (Path entry : entries) {
          if (Files.isRegularFile(entry)
              && !expected.contains(entry.toAbsolutePath().normalize())) {
            LOGGER.warn("{} is not part of the dataset and is not packed", entry);
          }
        }
      }
    }
  }

  /** The manifest already written for this tag, or {@code null} if there is none. */
  private DatasetManifest previousManifestOf(String tag) throws IOException {
    if (!Files.isRegularFile(manifestFile)) {
      return null;
    }
    DatasetManifest previous;
    try {
      previous = DatasetManifestCodec.read(Files.readString(manifestFile, StandardCharsets.UTF_8));
    } catch (OrbitlabException e) {
      throw new OrbitlabException(
          "The existing manifest "
              + manifestFile
              + " cannot be read; repair or delete it before packing: "
              + e.getMessage(),
          e);
    }
    if (!previous.tag().equals(tag)) {
      LOGGER.info("{} describes {}; it will be replaced by {}", manifestFile, previous.tag(), tag);
      return null;
    }
    return previous;
  }

  /** Refuses before any copy when the files' paths or sizes already differ from the manifest. */
  private void requireSameFiles(DatasetManifest previous, long[] sizes) {
    List<DatasetFile> packed = previous.files();
    if (packed.size() != sources.size()) {
      throw alreadyPacked(
          previous.tag(), packed.size() + " files described, " + sources.size() + " to pack");
    }
    for (int i = 0; i < sources.size(); i++) {
      DatasetFile file = packed.get(i);
      if (!file.path().equals(sources.get(i).path()) || file.size() != sizes[i]) {
        throw alreadyPacked(
            previous.tag(),
            file.path()
                + " ("
                + file.size()
                + " bytes) described, "
                + sources.get(i).path()
                + " ("
                + sizes[i]
                + " bytes) to pack");
      }
    }
  }

  private static String firstDifference(DatasetManifest previous, DatasetManifest packed) {
    for (int i = 0; i < packed.files().size(); i++) {
      DatasetFile before = previous.files().get(i);
      DatasetFile now = packed.files().get(i);
      if (!before.equals(now)) {
        return now.path() + " has fingerprint " + now.sha256() + ", " + before.sha256() + " before";
      }
    }
    return "the manifests differ";
  }

  private OrbitlabException alreadyPacked(String tag, String difference) {
    return new OrbitlabException(
        manifestFile
            + " already describes "
            + tag
            + " with other data ("
            + difference
            + "). A published tag must keep its files: pack under a new tag, or delete the"
            + " manifest if "
            + tag
            + " was never published. The manifest was left as it was.");
  }

  /**
   * Empties the output folder, after checking it is this tool's: it must not overlap the sources,
   * and it must hold nothing but files, so a wrong argument cannot turn the clearing into a
   * recursive deletion.
   */
  private void clearOutputDir() throws IOException {
    for (Source source : sources) {
      Path directory = source.file().toAbsolutePath().normalize().getParent();
      if (outputDir.startsWith(directory) || directory.startsWith(outputDir)) {
        throw new OrbitlabException(
            "The output folder " + outputDir + " overlaps the dataset folder " + directory);
      }
    }
    Files.createDirectories(outputDir);
    List<Path> previousOutput = new ArrayList<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(outputDir)) {
      for (Path entry : entries) {
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
          throw new OrbitlabException(
              "The output folder " + outputDir + " holds something else than files: " + entry);
        }
        previousOutput.add(entry);
      }
    }
    for (Path entry : previousOutput) {
      Files.delete(entry);
    }
  }

  private DatasetFile copy(Source source, long size) throws IOException {
    long pieceCount = Math.ceilDiv(size, pieceLimit);
    if (pieceCount > MAX_PIECES) {
      throw new OrbitlabException(
          source.file() + " would need " + pieceCount + " pieces, more than " + MAX_PIECES);
    }
    int count = (int) pieceCount;
    long pieceSize = size / count;
    long longerPieces = size % count;
    String fileName = source.file().getFileName().toString();
    MessageDigest whole = Sha256.newDigest();
    List<DatasetPiece> pieces = new ArrayList<>(count);
    ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
    long started = System.nanoTime();
    try (FileChannel in = FileChannel.open(source.file(), StandardOpenOption.READ)) {
      for (int i = 0; i < count; i++) {
        String name =
            count == 1 ? fileName : String.format(Locale.ROOT, "%s.%03d", fileName, i + 1);
        long length = pieceSize + (i < longerPieces ? 1 : 0);
        MessageDigest digest = Sha256.newDigest();
        try (FileChannel out =
            FileChannel.open(
                outputDir.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
          long remaining = length;
          while (remaining > 0) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), remaining));
            int read = in.read(buffer);
            if (read < 0) {
              throw new IOException(
                  source.file() + " ended before its " + size + " bytes: it changed while packed");
            }
            buffer.flip();
            whole.update(buffer.duplicate());
            digest.update(buffer.duplicate());
            while (buffer.hasRemaining()) {
              out.write(buffer);
            }
            remaining -= read;
          }
        }
        pieces.add(new DatasetPiece(name, length, Sha256.hex(digest)));
      }
      if (in.size() != size) {
        throw new IOException(
            source.file() + " is now " + in.size() + " bytes, not " + size + ": it changed");
      }
    }
    DatasetFile file = new DatasetFile(source.path(), size, Sha256.hex(whole), pieces);
    LOGGER.info(
        String.format(
            Locale.ROOT,
            "%-24s %,15d bytes  %d piece(s)  sha256 %s  %.1f s",
            source.path(),
            size,
            count,
            file.sha256(),
            (System.nanoTime() - started) / 1e9));
    return file;
  }

  private void writeManifest(DatasetManifest manifest) throws IOException {
    String json = DatasetManifestCodec.write(manifest) + System.lineSeparator();
    Path directory = manifestFile.toAbsolutePath().getParent();
    Files.createDirectories(directory);
    Path temporary = directory.resolve(manifestFile.getFileName() + ".tmp");
    Files.writeString(temporary, json, StandardCharsets.UTF_8);
    Files.move(
        temporary,
        manifestFile,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }
}
