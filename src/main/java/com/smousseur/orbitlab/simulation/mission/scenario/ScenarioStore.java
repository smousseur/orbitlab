package com.smousseur.orbitlab.simulation.mission.scenario;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The disk, and nothing but the disk: {@code ~/.orbitlab/scenarios/&lt;name&gt;.json}, created on
 * demand (spec {@code docs/scenario/01-persistance-missions.md} §8).
 *
 * <p>This is the first file the application ever writes, hence the first user directory it owns. A
 * future preferences file belongs <b>beside</b> it in {@code ~/.orbitlab/}, never inside the
 * scenarios: replaying someone else's scenario must not reconfigure the screen of whoever opens it
 * (§10).
 *
 * <p><b>Names are refused, never sanitised.</b> Silently turning {@code ../../passwd} into {@code
 * passwd} would save a scenario under a name the user did not choose, and would make a later "does
 * it already exist?" answer about a different file than the one about to be written. The allowed
 * set is {@code [A-Za-z0-9 _-]}, which is also what keeps a name a valid file name on every
 * platform this runs on.
 */
public final class ScenarioStore {

  /** The file name character set, deliberately narrow (§7). */
  private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9 _-]+");

  private static final String EXTENSION = ".json";

  private final Path directory;

  /**
   * Creates a store over an explicit directory. The directory is created when the first scenario is
   * written, not here: opening the browser on a machine that has never saved anything must not
   * leave a folder behind.
   *
   * @param directory the folder scenarios live in
   */
  public ScenarioStore(Path directory) {
    this.directory = Objects.requireNonNull(directory, "directory");
  }

  /**
   * The store the application uses: {@code ~/.orbitlab/scenarios}.
   *
   * @return the user-home store
   */
  public static ScenarioStore inUserHome() {
    return new ScenarioStore(OrbitlabPath.SCENARIOS_PATH);
  }

  /**
   * @return the folder this store reads and writes
   */
  public Path directory() {
    return directory;
  }

  /**
   * Lists the scenario names on disk, sorted, extension stripped.
   *
   * @return the names, empty when nothing has ever been saved
   * @throws OrbitlabException if the folder exists but cannot be listed
   */
  public List<String> list() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(fileName -> fileName.endsWith(EXTENSION))
          .map(fileName -> fileName.substring(0, fileName.length() - EXTENSION.length()))
          .sorted(Comparator.naturalOrder())
          .toList();
    } catch (IOException e) {
      throw new OrbitlabException(
          "Cannot list scenarios in " + directory + ": " + e.getMessage(), e);
    }
  }

  /**
   * Whether a scenario of that name is already on disk — the question the save mode asks before it
   * offers to overwrite.
   *
   * @param name the scenario name
   * @return {@code true} when the file exists
   * @throws OrbitlabException if the name is outside the allowed set
   */
  public boolean exists(String name) {
    return Files.isRegularFile(pathFor(name));
  }

  /**
   * Reads one scenario.
   *
   * @param name the scenario name
   * @return the parsed scenario
   * @throws OrbitlabException if the name is invalid, the file is missing, or its content is not a
   *     scenario this build reads
   */
  public ScenarioFile read(String name) {
    Path path = pathFor(name);
    String json;
    try {
      json = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new OrbitlabException("Cannot read scenario '" + name + "': " + e.getMessage(), e);
    }
    return ScenarioCodec.read(json);
  }

  /**
   * Writes one scenario, replacing any file of the same name.
   *
   * @param name the scenario name
   * @param file the scenario to write
   * @throws OrbitlabException if the name is invalid or the file cannot be written
   */
  public void write(String name, ScenarioFile file) {
    Objects.requireNonNull(file, "file");
    Path path = pathFor(name);
    String json = ScenarioCodec.write(file);
    try {
      Files.createDirectories(directory);
      Files.writeString(path, json, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new OrbitlabException("Cannot write scenario '" + name + "': " + e.getMessage(), e);
    }
  }

  /**
   * Whether a name may be used for a scenario file.
   *
   * <p>Exposed so the browser window can grey out its confirm button on the same predicate the
   * store enforces. Two predicates would be two truths, and the one the user sees would sooner or
   * later disagree with the one that refuses the write.
   *
   * <p>Spaces are allowed <b>inside</b> a name but a name made only of them is not: the character
   * set alone would accept {@code " "}, which names a file no one can refer to and which Windows
   * mangles by trimming.
   *
   * @param name the candidate name
   * @return {@code true} when it carries something and is made only of {@code [A-Za-z0-9 _-]}
   */
  public static boolean isValidName(String name) {
    return name != null && !name.isBlank() && VALID_NAME.matcher(name).matches();
  }

  /**
   * Resolves a name to its file, refusing anything outside the allowed set.
   *
   * @param name the scenario name
   * @return the file path
   * @throws OrbitlabException if the name is empty or carries a character outside {@code [A-Za-z0-9
   *     _-]}
   */
  public Path pathFor(String name) {
    if (!isValidName(name)) {
      throw new OrbitlabException(
          "Scenario name may only contain letters, digits, spaces, '_' and '-': " + name);
    }
    return directory.resolve(name + EXTENSION);
  }
}
