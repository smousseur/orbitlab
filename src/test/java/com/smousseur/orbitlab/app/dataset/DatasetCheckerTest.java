package com.smousseur.orbitlab.app.dataset;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The start-up check: what it says of a directory from the files' sizes and the marker alone, and
 * that it creates nothing.
 */
class DatasetCheckerTest {

  @TempDir Path root;

  private byte[] a;
  private byte[] b;
  private DatasetManifest manifest;
  private DatasetChecker checker;

  @BeforeEach
  void setUp() {
    Random random = new Random(11);
    a = new byte[300];
    random.nextBytes(a);
    b = new byte[100];
    random.nextBytes(b);
    manifest = manifest("dataset-v1", a, b);
    checker = new DatasetChecker(manifest, root);
  }

  private static DatasetManifest manifest(String tag, byte[] a, byte[] b) {
    return new DatasetManifest(
        DatasetManifest.CURRENT_FORMAT_VERSION,
        tag,
        List.of(file("ephemeris/A.bin", a), file("orbits/B.bin", b)));
  }

  private static DatasetFile file(String path, byte[] bytes) {
    String sha = sha256(bytes);
    String name = path.substring(path.lastIndexOf('/') + 1);
    return new DatasetFile(
        path, bytes.length, sha, List.of(new DatasetPiece(name, bytes.length, sha)));
  }

  private static String sha256(byte[] bytes) {
    MessageDigest digest = Sha256.newDigest();
    digest.update(bytes);
    return Sha256.hex(digest);
  }

  private void put(String path, byte[] bytes) throws IOException {
    Path target = root.resolve(path);
    Files.createDirectories(target.getParent());
    Files.write(target, bytes);
  }

  private void writeMarker(DatasetManifest marker) throws IOException {
    Files.writeString(checker.marker(), DatasetManifestCodec.write(marker));
  }

  private static DatasetCheck.FileStatus status(DatasetCheck check, String path) {
    return check.files().stream()
        .filter(f -> f.file().path().equals(path))
        .findFirst()
        .orElseThrow()
        .status();
  }

  @Test
  void aDirectoryMatchingItsMarkerIsReady() throws IOException {
    put("ephemeris/A.bin", a);
    put("orbits/B.bin", b);
    writeMarker(manifest);

    DatasetCheck check = checker.check();

    assertTrue(check.ready());
    assertTrue(check.markerCurrent());
    assertEquals(0, check.bytesToDownload());
    assertEquals(2, check.filesWith(DatasetCheck.FileStatus.VERIFIED).size());
  }

  @Test
  void withoutAMarkerEveryFileAtItsSizeIsToVerify() throws IOException {
    put("ephemeris/A.bin", a);
    put("orbits/B.bin", b);

    DatasetCheck check = checker.check();

    assertFalse(check.ready());
    assertFalse(check.markerCurrent());
    assertEquals(
        List.of(manifest.files().get(0), manifest.files().get(1)),
        check.filesWith(DatasetCheck.FileStatus.TO_VERIFY));
  }

  @Test
  void aMarkerOfAnotherVersionCoversTheUnchangedEntriesOnly() throws IOException {
    byte[] oldB = b.clone();
    oldB[0] ^= 1;
    put("ephemeris/A.bin", a);
    put("orbits/B.bin", b);
    writeMarker(manifest("dataset-v0", a, oldB));

    DatasetCheck check = checker.check();

    assertFalse(check.ready());
    assertFalse(check.markerCurrent());
    assertEquals(DatasetCheck.FileStatus.VERIFIED, status(check, "ephemeris/A.bin"));
    assertEquals(DatasetCheck.FileStatus.TO_VERIFY, status(check, "orbits/B.bin"));
  }

  @Test
  void aMarkerEqualToTheManifestDoesNotCoverAFileOfTheWrongSize() throws IOException {
    put("ephemeris/A.bin", a);
    put("orbits/B.bin", new byte[99]);
    writeMarker(manifest);

    DatasetCheck check = checker.check();

    assertFalse(check.ready());
    assertTrue(check.markerCurrent());
    assertEquals(DatasetCheck.FileStatus.TO_DOWNLOAD, status(check, "orbits/B.bin"));
    assertEquals(100, check.bytesToDownload());
  }

  @Test
  void thePartialDownloadOfAMissingFileIsCountedAsAlreadyThere() throws IOException {
    put("ephemeris/A.bin.part", new byte[120]);
    put("orbits/B.bin.part", new byte[101]);

    DatasetCheck check = checker.check();

    assertEquals(120, check.files().get(0).partBytes());
    assertEquals(0, check.files().get(1).partBytes(), "larger than its file: discarded");
    assertEquals(300 - 120 + 100, check.bytesToDownload());
  }

  @Test
  void anUnreadableMarkerIsTreatedAsAbsent() throws IOException {
    put("ephemeris/A.bin", a);
    put("orbits/B.bin", b);
    Files.writeString(checker.marker(), "{ not json");

    DatasetCheck check = checker.check();

    assertFalse(check.markerCurrent());
    assertEquals(2, check.filesWith(DatasetCheck.FileStatus.TO_VERIFY).size());
  }

  @Test
  void checkingAMissingDirectoryCreatesNothing() {
    Path missing = root.resolve("not-there");

    DatasetCheck check = new DatasetChecker(manifest, missing).check();

    assertFalse(check.ready());
    assertEquals(400, check.bytesToDownload());
    assertFalse(Files.exists(missing));
  }
}
