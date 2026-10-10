package com.smousseur.orbitlab.tools.datasetpack;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.app.dataset.DatasetFile;
import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.app.dataset.DatasetManifestCodec;
import com.smousseur.orbitlab.app.dataset.DatasetPiece;
import com.smousseur.orbitlab.app.dataset.Sha256;
import com.smousseur.orbitlab.core.OrbitlabException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The packing tool on a dataset of three small files and a piece limit of 100 bytes: a file over
 * the limit is cut into equal pieces that concatenate back to it byte for byte, the manifest
 * written reads back equal, and every refusal happens before the previous manifest is touched.
 */
class DatasetPackerTest {

  private static final long LIMIT = 100;

  @TempDir Path root;

  private Path dataset;
  private Path ephemeris;
  private Path orbits;
  private Path output;
  private Path manifestFile;
  private List<DatasetPacker.Source> sources;

  @BeforeEach
  void createDataset() throws IOException {
    dataset = root.resolve("dataset");
    ephemeris = Files.createDirectories(dataset.resolve("ephemeris"));
    orbits = Files.createDirectories(dataset.resolve("orbits"));
    output = root.resolve("build").resolve("dataset").resolve("t1");
    manifestFile = root.resolve("resources").resolve("dataset-manifest.json");
    Random random = new Random(42);
    sources =
        List.of(
            write(random, ephemeris.resolve("A.bin"), "ephemeris/A.bin", 250),
            write(random, ephemeris.resolve("B.bin"), "ephemeris/B.bin", 100),
            write(random, orbits.resolve("A-orbit.bin"), "orbits/A-orbit.bin", 101));
  }

  private static DatasetPacker.Source write(Random random, Path file, String path, int size)
      throws IOException {
    byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    Files.write(file, bytes);
    return new DatasetPacker.Source(file, path);
  }

  private DatasetPacker packer() {
    return packer(directory -> Long.MAX_VALUE);
  }

  private DatasetPacker packer(DatasetPacker.UsableSpace space) {
    return new DatasetPacker(sources, output, manifestFile, LIMIT, space);
  }

  private static String sha256(byte[] bytes) {
    MessageDigest digest = Sha256.newDigest();
    digest.update(bytes);
    return Sha256.hex(digest);
  }

  private static List<String> namesIn(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }

  @Test
  void cutsAFileOverTheLimitIntoEqualPiecesThatJoinBackByteForByte() throws IOException {
    DatasetManifest manifest = packer().pack("t1");

    DatasetFile file = manifest.files().getFirst();
    assertEquals("ephemeris/A.bin", file.path());
    assertEquals(
        List.of("A.bin.001", "A.bin.002", "A.bin.003"),
        file.pieces().stream().map(DatasetPiece::name).toList());
    assertEquals(List.of(84L, 83L, 83L), file.pieces().stream().map(DatasetPiece::size).toList());

    ByteArrayOutputStream joined = new ByteArrayOutputStream();
    for (DatasetPiece piece : file.pieces()) {
      byte[] bytes = Files.readAllBytes(output.resolve(piece.name()));
      assertEquals(piece.size(), bytes.length);
      assertEquals(sha256(bytes), piece.sha256(), piece.name());
      joined.writeBytes(bytes);
    }
    byte[] source = Files.readAllBytes(ephemeris.resolve("A.bin"));
    assertArrayEquals(source, joined.toByteArray());
    assertEquals(sha256(source), file.sha256());
  }

  @Test
  void keepsAFileAtTheLimitWholeAndCutsOneByteMoreInTwo() throws IOException {
    DatasetManifest manifest = packer().pack("t1");

    DatasetFile atLimit = manifest.files().get(1);
    assertEquals(List.of(new DatasetPiece("B.bin", 100, atLimit.sha256())), atLimit.pieces());
    assertArrayEquals(
        Files.readAllBytes(ephemeris.resolve("B.bin")),
        Files.readAllBytes(output.resolve("B.bin")));

    DatasetFile overLimit = manifest.files().get(2);
    assertEquals(List.of(51L, 50L), overLimit.pieces().stream().map(DatasetPiece::size).toList());
    assertEquals(
        List.of(
            "A-orbit.bin.001", "A-orbit.bin.002", "A.bin.001", "A.bin.002", "A.bin.003", "B.bin"),
        namesIn(output));
  }

  @Test
  void writesAManifestThatReadsBackEqual() throws IOException {
    DatasetManifest manifest = packer().pack("t1");

    assertEquals(
        manifest,
        DatasetManifestCodec.read(Files.readString(manifestFile, StandardCharsets.UTF_8)));
    assertEquals("t1", manifest.tag());
    assertEquals(451, manifest.totalSize());
  }

  @Test
  void packsTheSameDataIntoTheSameManifestTwice() throws IOException {
    packer().pack("t1");
    String first = Files.readString(manifestFile, StandardCharsets.UTF_8);

    packer().pack("t1");
    assertEquals(first, Files.readString(manifestFile, StandardCharsets.UTF_8));
  }

  @Test
  void refusesAMissingFileBeforeWritingAnything() throws IOException {
    Files.delete(orbits.resolve("A-orbit.bin"));

    OrbitlabException e = assertThrows(OrbitlabException.class, () -> packer().pack("t1"));
    assertTrue(e.getMessage().contains("A-orbit.bin is missing"), e.getMessage());
    assertFalse(Files.exists(output));
    assertFalse(Files.exists(manifestFile));
  }

  @Test
  void refusesToDescribeAPackedTagWithOtherDataAndKeepsItsManifest() throws IOException {
    packer().pack("t1");
    String packed = Files.readString(manifestFile, StandardCharsets.UTF_8);
    byte[] bytes = Files.readAllBytes(orbits.resolve("A-orbit.bin"));
    bytes[0] ^= 1;
    Files.write(orbits.resolve("A-orbit.bin"), bytes);

    OrbitlabException e = assertThrows(OrbitlabException.class, () -> packer().pack("t1"));
    assertTrue(e.getMessage().contains("orbits/A-orbit.bin has fingerprint"), e.getMessage());
    assertTrue(e.getMessage().contains("new tag"), e.getMessage());
    assertEquals(packed, Files.readString(manifestFile, StandardCharsets.UTF_8));
  }

  @Test
  void refusesAPackedTagWhoseSizesChangedBeforeCopyingAnything() throws IOException {
    packer().pack("t1");
    List<String> packedOutput = namesIn(output);
    Files.write(ephemeris.resolve("B.bin"), new byte[99]);

    OrbitlabException e = assertThrows(OrbitlabException.class, () -> packer().pack("t1"));
    assertTrue(e.getMessage().contains("ephemeris/B.bin (99 bytes) to pack"), e.getMessage());
    assertEquals(packedOutput, namesIn(output));
  }

  @Test
  void replacesTheManifestOfAnotherTag() throws IOException {
    packer().pack("t1");
    Files.write(ephemeris.resolve("B.bin"), new byte[99]);

    DatasetManifest manifest = packer().pack("t2");
    assertEquals(
        manifest,
        DatasetManifestCodec.read(Files.readString(manifestFile, StandardCharsets.UTF_8)));
  }

  @Test
  void refusesWhenTheOutputVolumeIsTooSmall() {
    OrbitlabException e =
        assertThrows(OrbitlabException.class, () -> packer(directory -> 450).pack("t1"));
    assertTrue(e.getMessage().contains("needs 451"), e.getMessage());
    assertFalse(Files.exists(manifestFile));
  }

  @Test
  void refusesToClearAnOutputFolderHoldingADirectory() throws IOException {
    Files.createDirectories(output.resolve("nested"));

    assertThrows(OrbitlabException.class, () -> packer().pack("t1"));
    assertTrue(Files.isDirectory(output.resolve("nested")));
  }

  @Test
  void refusesAnOutputFolderOverlappingTheDataset() {
    DatasetPacker packer =
        new DatasetPacker(sources, dataset, manifestFile, LIMIT, directory -> Long.MAX_VALUE);
    assertThrows(OrbitlabException.class, () -> packer.pack("t1"));
  }

  @Test
  void theProductionListNamesElevenEphemeridesThenTenOrbits() {
    Path home = root.resolve("dataset");
    List<DatasetPacker.Source> expected =
        DatasetPackMain.expectedFiles(home, home.resolve("ephemeris"), home.resolve("orbits"));

    List<String> paths = new ArrayList<>();
    for (DatasetPacker.Source source : expected) {
      paths.add(source.path());
    }
    assertEquals(21, paths.size());
    assertEquals("ephemeris/SUN.bin", paths.getFirst());
    assertEquals("ephemeris/MOON.bin", paths.get(10));
    assertEquals("orbits/MERCURY-orbit.bin", paths.get(11));
    assertEquals("orbits/MOON-orbit.bin", paths.getLast());
    assertFalse(paths.contains("orbits/SUN-orbit.bin"));
  }
}
