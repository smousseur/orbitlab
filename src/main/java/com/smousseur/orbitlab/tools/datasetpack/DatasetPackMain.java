package com.smousseur.orbitlab.tools.datasetpack;

import com.smousseur.orbitlab.app.SimulationConfig;
import com.smousseur.orbitlab.app.dataset.DatasetManifest;
import com.smousseur.orbitlab.core.OrbitlabPath;
import com.smousseur.orbitlab.core.SolarSystemBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Prepares the local dataset for publication as a GitHub release.
 *
 * <p>Run through the Gradle task, after {@code ephemerisGen} and {@code orbitGen}:
 *
 * <pre>
 *   ./gradlew datasetPack -PdatasetTag=dataset-v1
 * </pre>
 *
 * <p>The task passes three arguments: the tag, the folder the files to publish are written to
 * ({@code build/dataset/<tag>}), and the manifest to write ({@code
 * src/main/resources/dataset-manifest.json}). The dataset is read from {@code ~/.orbitlab/dataset},
 * where the two generators write it.
 */
public final class DatasetPackMain {

  private static final Logger LOGGER = LogManager.getLogger(DatasetPackMain.class);

  /**
   * The largest file a GitHub release accepts: its documentation states that each file "must be
   * under 2 GiB".
   */
  static final long RELEASE_FILE_LIMIT = (1L << 31) - 1;

  private DatasetPackMain() {}

  /**
   * Entry point.
   *
   * @param args {@code <tag> <outputDir> <manifestFile>}
   * @throws IOException if reading the dataset or writing the release files fails
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException(
          "Usage: DatasetPackMain <tag> <outputDir> <manifestFile> (run ./gradlew datasetPack"
              + " -PdatasetTag=<tag>)");
    }
    String tag = args[0];
    Path outputDir = Path.of(args[1]);
    Path manifestFile = Path.of(args[2]);
    LOGGER.info(
        "Packing {} from {} with Java {}", tag, OrbitlabPath.DATASET_PATH, Runtime.version());

    DatasetPacker packer =
        new DatasetPacker(
            expectedFiles(
                OrbitlabPath.DATASET_PATH, OrbitlabPath.EPHEMERIS_PATH, OrbitlabPath.ORBITS_PATH),
            outputDir,
            manifestFile,
            RELEASE_FILE_LIMIT,
            directory -> Files.getFileStore(directory).getUsableSpace());
    DatasetManifest manifest = packer.pack(tag);

    LOGGER.info(
        "Next: commit the manifest and push, then publish from Git Bash (which expands the *):");
    LOGGER.info(
        "  gh release create {} {}/* --latest=false --target <sha of the manifest commit>"
            + " --title \"OrbitLab {}\" --notes-file <notes>",
        manifest.tag(),
        outputDir.toAbsolutePath().normalize().toString().replace('\\', '/'),
        manifest.tag());
    LOGGER.info("Then check the published release: ./gradlew datasetVerify");
  }

  /**
   * The files the dataset is made of, in manifest order: one ephemeris file per body, the set the
   * ephemeris reader opens, then one orbit file per body whose orbit the application draws.
   *
   * @param datasetDir the dataset directory manifest paths are relative to
   * @param ephemerisDir where the ephemeris generator writes
   * @param orbitsDir where the orbit generator writes
   * @return the expected files
   */
  static List<DatasetPacker.Source> expectedFiles(
      Path datasetDir, Path ephemerisDir, Path orbitsDir) {
    List<DatasetPacker.Source> sources = new ArrayList<>();
    for (SolarSystemBody body : SolarSystemBody.values()) {
      sources.add(source(datasetDir, ephemerisDir.resolve(body.name() + ".bin")));
    }
    Set<SolarSystemBody> orbitBodies = SimulationConfig.defaultSolarSystem().orbitBodies();
    for (SolarSystemBody body : SolarSystemBody.values()) {
      if (orbitBodies.contains(body)) {
        sources.add(source(datasetDir, orbitsDir.resolve(body.name() + "-orbit.bin")));
      }
    }
    return sources;
  }

  private static DatasetPacker.Source source(Path datasetDir, Path file) {
    List<String> segments = new ArrayList<>();
    for (Path segment : datasetDir.relativize(file)) {
      segments.add(segment.toString());
    }
    return new DatasetPacker.Source(file, String.join("/", segments));
  }
}
