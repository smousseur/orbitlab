package com.smousseur.orbitlab.app.dataset;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The manifest's JSON layer and its checks, without disk or network: a manifest written is read
 * back equal, and every inconsistency that would turn into an unverifiable download or a file
 * written outside the dataset directory is refused at reading.
 */
class DatasetManifestCodecTest {

  private static final String SHA_A = "a".repeat(64);
  private static final String SHA_B = "b".repeat(64);
  private static final String SHA_C = "c".repeat(64);

  private static DatasetManifest manifest(List<DatasetFile> files) {
    return new DatasetManifest(DatasetManifest.CURRENT_FORMAT_VERSION, "dataset-v1", files);
  }

  private static DatasetFile whole(String path, String name, long size, String sha) {
    return new DatasetFile(path, size, sha, List.of(new DatasetPiece(name, size, sha)));
  }

  private static DatasetManifest valid() {
    return manifest(
        List.of(
            whole("ephemeris/SUN.bin", "SUN.bin", 120, SHA_A),
            new DatasetFile(
                "ephemeris/MOON.bin",
                250,
                SHA_B,
                List.of(
                    new DatasetPiece("MOON.bin.001", 125, SHA_C),
                    new DatasetPiece("MOON.bin.002", 125, SHA_A))),
            whole("orbits/EARTH-orbit.bin", "EARTH-orbit.bin", 98_308, SHA_C)));
  }

  private static String rejection(DatasetManifest manifest) {
    return assertThrows(OrbitlabException.class, () -> DatasetManifestCodec.write(manifest))
        .getMessage();
  }

  @Test
  void writesAManifestThatReadsBackEqual() {
    DatasetManifest manifest = valid();
    String json = DatasetManifestCodec.write(manifest);
    assertEquals(manifest, DatasetManifestCodec.read(json));
    assertTrue(json.contains("\"MOON.bin.002\""), json);
  }

  @Test
  void refusesAManifestFromALaterVersionWithItsNumber() {
    String json =
        DatasetManifestCodec.write(valid())
            .replaceFirst("\"formatVersion\"\\s*:\\s*1", "\"formatVersion\" : 7");
    OrbitlabException e =
        assertThrows(OrbitlabException.class, () -> DatasetManifestCodec.read(json));
    assertTrue(e.getMessage().contains("version 7"), e.getMessage());
  }

  @Test
  void refusesAManifestWithoutVersion() {
    String json =
        DatasetManifestCodec.write(valid()).replaceFirst("\"formatVersion\"\\s*:\\s*1\\s*,", "");
    assertThrows(OrbitlabException.class, () -> DatasetManifestCodec.read(json));
  }

  @Test
  void refusesUnreadableText() {
    assertThrows(OrbitlabException.class, () -> DatasetManifestCodec.read("{ not json"));
  }

  @Test
  void refusesAnEmptyFileList() {
    assertTrue(rejection(manifest(List.of())).contains("no file"));
  }

  @Test
  void refusesPiecesThatDoNotAddUpToTheirFile() {
    DatasetFile file =
        new DatasetFile(
            "ephemeris/MOON.bin",
            250,
            SHA_B,
            List.of(
                new DatasetPiece("MOON.bin.001", 125, SHA_C),
                new DatasetPiece("MOON.bin.002", 124, SHA_A)));
    assertTrue(rejection(manifest(List.of(file))).contains("add up to 249"));
  }

  @Test
  void refusesAMalformedFingerprint() {
    String upper = "A".repeat(64);
    assertThrows(
        OrbitlabException.class,
        () -> DatasetManifestCodec.write(manifest(List.of(whole("a/x.bin", "x.bin", 1, upper)))));
    String shortSha = "a".repeat(63);
    assertThrows(
        OrbitlabException.class,
        () ->
            DatasetManifestCodec.write(manifest(List.of(whole("a/x.bin", "x.bin", 1, shortSha)))));
  }

  @Test
  void refusesAPathLeavingTheDatasetDirectory() {
    for (String path : List.of("../x.bin", "/x.bin", "a\\x.bin", "C:/x.bin", "a//x.bin", "a/./x")) {
      assertThrows(
          OrbitlabException.class,
          () -> DatasetManifestCodec.write(manifest(List.of(whole(path, "x.bin", 1, SHA_A)))),
          path);
    }
  }

  @Test
  void refusesAPieceNameThatIsNotAPlainFileName() {
    for (String name : List.of("../x.bin", "a/x.bin", "x bin", "..")) {
      assertThrows(
          OrbitlabException.class,
          () -> DatasetManifestCodec.write(manifest(List.of(whole("a/x.bin", name, 1, SHA_A)))),
          name);
    }
  }

  @Test
  void refusesAFileOrAPieceListedTwice() {
    assertTrue(
        rejection(
                manifest(
                    List.of(
                        whole("a/x.bin", "x.bin", 1, SHA_A), whole("a/x.bin", "y.bin", 1, SHA_B))))
            .contains("listed twice"));
    assertTrue(
        rejection(
                manifest(
                    List.of(
                        whole("a/x.bin", "x.bin", 1, SHA_A), whole("b/x.bin", "x.bin", 1, SHA_B))))
            .contains("listed twice"));
  }

  @Test
  void refusesASinglePieceWithAnotherFingerprintThanItsFile() {
    DatasetFile file =
        new DatasetFile("a/x.bin", 1, SHA_A, List.of(new DatasetPiece("x.bin", 1, SHA_B)));
    assertTrue(rejection(manifest(List.of(file))).contains("another fingerprint"));
  }

  @Test
  void refusesAnEmptyFile() {
    assertThrows(
        OrbitlabException.class,
        () -> DatasetManifestCodec.write(manifest(List.of(whole("a/x.bin", "x.bin", 0, SHA_A)))));
  }

  @Test
  void deducesAPieceAddressFromTheTagAndItsName() {
    DatasetManifest manifest = valid();
    DatasetPiece piece = manifest.files().get(1).pieces().get(1);
    assertEquals(
        URI.create(
            "https://github.com/smousseur/orbitlab/releases/download/dataset-v1/MOON.bin.002"),
        manifest.pieceUri(DatasetManifest.GITHUB_DOWNLOAD_BASE, piece));
    assertEquals(98_308 + 250 + 120, manifest.totalSize());
  }

  @Test
  void theEmbeddedManifestIsReadable() {
    DatasetManifest embedded = DatasetManifestCodec.readEmbedded();
    assertFalse(embedded.files().isEmpty());
  }
}
