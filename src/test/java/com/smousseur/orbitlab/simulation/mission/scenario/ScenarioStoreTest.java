package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSite;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioVehicle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The disk layer on a temporary folder: what it writes it reads back, and what it refuses. */
class ScenarioStoreTest {

  @TempDir Path directory;

  private static ScenarioFile fileNamed(String missionName) {
    return new ScenarioFile(
        ScenarioFile.CURRENT_FORMAT_VERSION,
        "2026-08-21T14:32:10Z",
        "2030-03-01T05:30:00Z",
        List.of(
            new ScenarioMission.Geo(
                MissionType.GEO,
                missionName,
                "2030-03-01T12:00:00Z",
                new ScenarioSite("Kourou - French Guiana", 5.236, -52.769, 14.0),
                new ScenarioVehicle("FALCON_HEAVY", "GEO_SAT", 2_000.0),
                null,
                "NONE",
                "FAST",
                "#FFAA00",
                true,
                null,
                300.0)));
  }

  @Test
  void writesThenReadsBack() {
    ScenarioStore store = new ScenarioStore(directory);

    store.write("My scenario", fileNamed("GEO sat"));

    assertTrue(store.exists("My scenario"));
    assertEquals("GEO sat", store.read("My scenario").missions().getFirst().name());
  }

  @Test
  void writeCreatesTheFolderOnDemand() {
    Path nested = directory.resolve("orbitlab").resolve("scenarios");
    new ScenarioStore(nested).write("first", fileNamed("GEO sat"));

    assertTrue(Files.isRegularFile(nested.resolve("first.json")));
  }

  /**
   * Opening the browser on a machine that never saved anything must not fail, nor create a folder.
   */
  @Test
  void listsNothingWhenTheFolderDoesNotExist() {
    Path missing = directory.resolve("never-created");
    ScenarioStore store = new ScenarioStore(missing);

    assertEquals(List.of(), store.list());
    assertFalse(store.exists("anything"));
    assertFalse(Files.exists(missing), "listing must not create the folder");
  }

  @Test
  void listsNamesSortedAndWithoutTheirExtension() {
    ScenarioStore store = new ScenarioStore(directory);
    store.write("charlie", fileNamed("c"));
    store.write("alpha", fileNamed("a"));
    store.write("bravo", fileNamed("b"));

    assertEquals(List.of("alpha", "bravo", "charlie"), store.list());
  }

  @Test
  void ignoresFilesThatAreNotScenarios() throws Exception {
    ScenarioStore store = new ScenarioStore(directory);
    store.write("alpha", fileNamed("a"));
    Files.writeString(directory.resolve("notes.txt"), "not a scenario");

    assertEquals(List.of("alpha"), store.list());
  }

  /**
   * Refused, never sanitised: quietly turning a traversal into a bare name would save the scenario
   * somewhere the user did not ask for, and would make {@code exists} answer about another file.
   */
  @Test
  void refusesANameOutsideTheAllowedSet() {
    ScenarioStore store = new ScenarioStore(directory);

    for (String name :
        List.of("../escape", "with/slash", "with\\backslash", "dots.in.name", "", "   ")) {
      assertThrows(
          OrbitlabException.class, () -> store.write(name, fileNamed("x")), "name: " + name);
    }
    assertThrows(OrbitlabException.class, () -> store.exists(".."));
    assertThrows(OrbitlabException.class, () -> store.read("../escape"));
  }

  @Test
  void acceptsLettersDigitsSpacesUnderscoresAndDashes() {
    ScenarioStore store = new ScenarioStore(directory);

    store.write("LEO 400 - run_2", fileNamed("GEO sat"));

    assertEquals(List.of("LEO 400 - run_2"), store.list());
  }

  @Test
  void readingAMissingScenarioFails() {
    assertThrows(OrbitlabException.class, () -> new ScenarioStore(directory).read("absent"));
  }

  @Test
  void readingSomethingThatIsNotAScenarioFails() throws Exception {
    Files.writeString(directory.resolve("broken.json"), "{ not json");

    assertThrows(OrbitlabException.class, () -> new ScenarioStore(directory).read("broken"));
  }
}
