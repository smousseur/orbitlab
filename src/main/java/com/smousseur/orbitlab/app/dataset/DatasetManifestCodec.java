package com.smousseur.orbitlab.app.dataset;

import com.smousseur.orbitlab.core.OrbitlabException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON envelope around {@link DatasetManifest}, and the one place that decides whether a
 * manifest can be trusted.
 *
 * <p>Like the scenario codec, it reads the version off the tree <b>before</b> binding it, so a
 * manifest from a later build is refused with its number rather than half read. Unlike it, it then
 * checks the manifest as a whole: a manifest drives downloads and names files on the user's disk,
 * and a wrong size, a malformed fingerprint or a path leaving the dataset directory would only show
 * up later, as a download that can never verify or a file written in the wrong place. Every write
 * goes through the same checks, so the publication tool cannot produce a manifest the application
 * would refuse.
 */
public final class DatasetManifestCodec {

  /** Where the manifest of the data this build expects sits on the classpath. */
  public static final String EMBEDDED_RESOURCE = "/dataset-manifest.json";

  /**
   * A tag, a piece name or a path segment: it ends up in a download address and in a file name, so
   * only characters both accept unescaped on every platform.
   */
  private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

  private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  private DatasetManifestCodec() {}

  /**
   * Serialises a manifest to its JSON text, after checking it.
   *
   * @param manifest the manifest to write
   * @return the indented JSON
   * @throws OrbitlabException if the manifest is not valid, or cannot be serialised
   */
  public static String write(DatasetManifest manifest) {
    validate(manifest);
    try {
      return MAPPER.writeValueAsString(manifest);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot write the dataset manifest: " + e.getMessage(), e);
    }
  }

  /**
   * Parses and checks a manifest.
   *
   * @param json the JSON text
   * @return the manifest
   * @throws OrbitlabException if the text is not readable, was written by a later version, or
   *     describes an inconsistent dataset
   */
  public static DatasetManifest read(String json) {
    JsonNode tree;
    try {
      tree = MAPPER.readTree(json);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot read the dataset manifest: " + e.getMessage(), e);
    }
    int version = tree.path("formatVersion").asInt(0);
    if (version > DatasetManifest.CURRENT_FORMAT_VERSION) {
      throw new OrbitlabException(
          "Dataset manifest format version "
              + version
              + " is newer than this build reads (version "
              + DatasetManifest.CURRENT_FORMAT_VERSION
              + ")");
    }
    DatasetManifest manifest;
    try {
      manifest = MAPPER.treeToValue(tree, DatasetManifest.class);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot read the dataset manifest: " + e.getMessage(), e);
    }
    validate(manifest);
    return manifest;
  }

  /**
   * Reads the manifest embedded in this build.
   *
   * @return the manifest of the data this build expects
   * @throws OrbitlabException if no manifest is embedded, or if it is not valid
   */
  public static DatasetManifest readEmbedded() {
    try (InputStream in = DatasetManifestCodec.class.getResourceAsStream(EMBEDDED_RESOURCE)) {
      if (in == null) {
        throw new OrbitlabException(
            "No dataset manifest is embedded in this build (" + EMBEDDED_RESOURCE + ")");
      }
      return read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new OrbitlabException(
          "Cannot read the embedded dataset manifest: " + e.getMessage(), e);
    }
  }

  /**
   * Checks that a tag or a piece name can be used as is in a download address and a file name.
   *
   * @param what what the value is, for the message
   * @param value the value to check
   * @throws OrbitlabException if it holds anything but letters, digits, {@code .}, {@code _} and
   *     {@code -}, or is {@code .} or {@code ..}
   */
  public static void requireSafeName(String what, String value) {
    if (!isSafeName(value)) {
      throw invalid(what + " '" + value + "' is not a plain file name");
    }
  }

  private static boolean isSafeName(String value) {
    return SAFE_NAME.matcher(value).matches() && !".".equals(value) && !"..".equals(value);
  }

  private static void validate(DatasetManifest manifest) {
    if (manifest.formatVersion() < 1
        || manifest.formatVersion() > DatasetManifest.CURRENT_FORMAT_VERSION) {
      throw invalid("format version " + manifest.formatVersion() + " is not supported");
    }
    requireSafeName("tag", manifest.tag());
    if (manifest.files().isEmpty()) {
      throw invalid("it lists no file");
    }
    Set<String> paths = new HashSet<>();
    Set<String> pieceNames = new HashSet<>();
    for (DatasetFile file : manifest.files()) {
      validateFile(file);
      if (!paths.add(file.path())) {
        throw invalid("file " + file.path() + " is listed twice");
      }
      for (DatasetPiece piece : file.pieces()) {
        if (!pieceNames.add(piece.name())) {
          throw invalid("piece " + piece.name() + " is listed twice");
        }
      }
    }
  }

  private static void validateFile(DatasetFile file) {
    for (String segment : file.path().split("/", -1)) {
      if (!isSafeName(segment)) {
        throw invalid("path '" + file.path() + "' does not stay inside the dataset directory");
      }
    }
    requirePositive(file.path(), file.size());
    requireSha256(file.path(), file.sha256());
    if (file.pieces().isEmpty()) {
      throw invalid("file " + file.path() + " has no piece");
    }
    long total = 0;
    for (DatasetPiece piece : file.pieces()) {
      requireSafeName("piece name", piece.name());
      requirePositive(piece.name(), piece.size());
      requireSha256(piece.name(), piece.sha256());
      total = Math.addExact(total, piece.size());
    }
    if (total != file.size()) {
      throw invalid(
          "the pieces of " + file.path() + " add up to " + total + " bytes, not " + file.size());
    }
    if (file.pieces().size() == 1 && !file.pieces().getFirst().sha256().equals(file.sha256())) {
      throw invalid("the single piece of " + file.path() + " has another fingerprint than it");
    }
  }

  private static void requirePositive(String what, long size) {
    if (size <= 0) {
      throw invalid(what + " has size " + size);
    }
  }

  private static void requireSha256(String what, String sha256) {
    if (!SHA256_HEX.matcher(sha256).matches()) {
      throw invalid(what + " has fingerprint '" + sha256 + "', not 64 lowercase hex digits");
    }
  }

  private static OrbitlabException invalid(String reason) {
    return new OrbitlabException("Invalid dataset manifest: " + reason);
  }
}
