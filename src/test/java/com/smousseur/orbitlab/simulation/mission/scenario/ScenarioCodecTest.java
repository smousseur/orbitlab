package com.smousseur.orbitlab.simulation.mission.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.MissionType;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioMission;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSite;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioSolution;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioVehicle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The JSON layer alone: no mission, no propagation, no disk. What it has to prove is that a
 * meaningful absence survives as an <b>absence</b> — omitted from the text, {@code null} on the way
 * back — and that a file from a later version is refused with its number rather than half read.
 */
class ScenarioCodecTest {

  private static ScenarioSite site() {
    return new ScenarioSite("Kourou - French Guiana", 5.236, -52.769, 14.0);
  }

  private static ScenarioVehicle vehicle() {
    return new ScenarioVehicle("FALCON_HEAVY", "EARTH_OBS_SAT", 8_000.0);
  }

  private static ScenarioMission.EarthOrbit leo(
      Double horizonDays, Double inclinationDeg, Double raanDeg, ScenarioSolution solution) {
    return new ScenarioMission.EarthOrbit(
        MissionType.LEO,
        "LEO 400",
        "2030-03-01T12:00:00Z",
        site(),
        vehicle(),
        horizonDays,
        "NONE",
        "BALANCED",
        "#4FC3F7",
        true,
        solution,
        400.0,
        550.0,
        inclinationDeg,
        raanDeg);
  }

  private static ScenarioFile fileOf(ScenarioMission... missions) {
    return new ScenarioFile(
        ScenarioFile.CURRENT_FORMAT_VERSION,
        "2026-08-21T14:32:10Z",
        "2030-03-01T05:30:00Z",
        List.of(missions));
  }

  @Test
  void earthOrbitMission_survivesTheJson() {
    ScenarioMission.EarthOrbit original = leo(4.5, 51.6, 120.0, null);

    ScenarioMission.EarthOrbit read =
        (ScenarioMission.EarthOrbit)
            ScenarioCodec.read(ScenarioCodec.write(fileOf(original))).missions().getFirst();

    assertEquals(original, read);
  }

  @Test
  void geoMission_survivesTheJson() {
    ScenarioMission.Geo original =
        new ScenarioMission.Geo(
            MissionType.GEO,
            "GEO sat",
            "2030-03-01T12:00:00Z",
            site(),
            new ScenarioVehicle("FALCON_HEAVY", "GEO_SAT", 2_000.0),
            null,
            "NONE",
            "FAST",
            "#FFAA00",
            false,
            null,
            300.0);

    ScenarioFile read = ScenarioCodec.read(ScenarioCodec.write(fileOf(original)));

    assertEquals(original, read.missions().getFirst());
  }

  /** The two branches of the sealed hierarchy are told apart by the {@code type} they carry. */
  @Test
  void bothBranches_areReadBackAsThemselves() {
    ScenarioFile read =
        ScenarioCodec.read(
            ScenarioCodec.write(
                fileOf(
                    leo(null, null, null, null),
                    new ScenarioMission.Geo(
                        MissionType.GEO,
                        "GEO sat",
                        null,
                        site(),
                        vehicle(),
                        null,
                        "NONE",
                        "FAST",
                        "#FFAA00",
                        false,
                        null,
                        300.0))));

    assertInstanceOf(ScenarioMission.EarthOrbit.class, read.missions().get(0));
    assertInstanceOf(ScenarioMission.Geo.class, read.missions().get(1));
  }

  /** An absence is written as an absence: no key at all, never a derived value and never a null. */
  @Test
  void absentValues_areOmittedFromTheText() {
    String json = ScenarioCodec.write(fileOf(leo(null, null, null, null)));

    assertFalse(json.contains("horizonDays"), json);
    assertFalse(json.contains("inclinationDeg"), json);
    assertFalse(json.contains("raanDeg"), json);
    assertFalse(json.contains("solution"), json);
    assertTrue(json.contains("\"type\" : \"LEO\""), json);
  }

  @Test
  void absentValues_comeBackAbsent() {
    ScenarioMission.EarthOrbit read =
        (ScenarioMission.EarthOrbit)
            ScenarioCodec.read(ScenarioCodec.write(fileOf(leo(null, null, null, null))))
                .missions()
                .getFirst();

    assertNull(read.horizonDays());
    assertNull(read.inclinationDeg());
    assertNull(read.raanDeg());
    assertNull(read.solution());
  }

  @Test
  void solution_survivesTheJson() {
    ScenarioSolution solution =
        new ScenarioSolution(
            Map.of("Gravity turn (S1)", new double[] {0.31, 12.4, 148.0}),
            new double[] {320_000.0, 90_000.0});

    ScenarioSolution read =
        ScenarioCodec.read(ScenarioCodec.write(fileOf(leo(null, null, null, solution))))
            .missions()
            .getFirst()
            .solution();

    assertArrayEquals(
        solution.vectors().get("Gravity turn (S1)"), read.vectors().get("Gravity turn (S1)"), 0.0);
    assertArrayEquals(solution.launcherLoads(), read.launcherLoads(), 0.0);
  }

  /** Outside PRECISE the loads are derived, so the file carries none — and says so by omission. */
  @Test
  void solutionWithoutFlownLoads_omitsThem() {
    ScenarioSolution solution =
        new ScenarioSolution(Map.of("Gravity turn (S1)", new double[] {0.31}), null);
    String json = ScenarioCodec.write(fileOf(leo(null, null, null, solution)));

    assertFalse(json.contains("launcherLoads"), json);
    assertNull(
        ScenarioCodec.read(json).missions().getFirst().solution().launcherLoads(),
        "no flown loads read back either");
  }

  /** We do not know what we are reading, so we refuse the file whole, with its number (§7). */
  @Test
  void futureFormatVersion_isRefusedWithItsNumber() {
    String json =
        ScenarioCodec.write(fileOf(leo(null, null, null, null)))
            .replace(
                "\"formatVersion\" : " + ScenarioFile.CURRENT_FORMAT_VERSION,
                "\"formatVersion\" : 99");

    OrbitlabException failure =
        assertThrows(OrbitlabException.class, () -> ScenarioCodec.read(json));
    assertTrue(failure.getMessage().contains("99"), failure.getMessage());
  }

  @Test
  void malformedText_isRefused() {
    assertThrows(OrbitlabException.class, () -> ScenarioCodec.read("{ not json"));
  }
}
