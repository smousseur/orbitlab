package com.smousseur.orbitlab.simulation.mission.scenario;

import com.smousseur.orbitlab.core.OrbitlabException;
import com.smousseur.orbitlab.simulation.mission.scenario.model.ScenarioFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The thin Jackson envelope around {@link ScenarioFile}: text in, DTO out, and back.
 *
 * <p>It knows nothing about missions and nothing about the disk — {@code ScenarioStore} owns the
 * files, {@code ScenarioSession} owns the meaning. What it does own is the <b>version gate</b>: a
 * file claiming a {@code formatVersion} above {@link ScenarioFile#CURRENT_FORMAT_VERSION} is
 * refused whole, with its number in the message, because nothing here knows what it is reading
 * (spec {@code docs/scenario/01-persistance-missions.md} §7). That is the only whole-file refusal;
 * every other rejection is per mission and happens further up, in {@code ScenarioSession}.
 *
 * <p>Nulls are omitted on write — through {@code @JsonInclude(NON_NULL)} on the records — which is
 * what makes a meaningful absence legible in the file rather than written out as {@code null}.
 * Unknown properties are <b>not</b> tolerated on read: the project rule is to refuse rather than to
 * silently degrade, and a field this build cannot place is a field it would be dropping.
 */
public final class ScenarioCodec {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  private ScenarioCodec() {}

  /**
   * Serialises a scenario to its JSON text.
   *
   * @param file the scenario to write
   * @return the indented JSON
   * @throws OrbitlabException if the scenario cannot be serialised
   */
  public static String write(ScenarioFile file) {
    try {
      return MAPPER.writeValueAsString(file);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot write the scenario: " + e.getMessage(), e);
    }
  }

  /**
   * Parses a scenario from its JSON text.
   *
   * <p>The version is read off the tree <b>before</b> the tree is bound to records: a future file
   * may well carry fields this build would refuse, and the reader must be able to say which version
   * it could not read rather than fail on one of them.
   *
   * @param json the JSON text
   * @return the parsed scenario
   * @throws OrbitlabException if the text is not readable, or was written by a later version
   */
  public static ScenarioFile read(String json) {
    JsonNode tree;
    try {
      tree = MAPPER.readTree(json);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot read the scenario: " + e.getMessage(), e);
    }
    int version = tree.path("formatVersion").asInt(0);
    if (version > ScenarioFile.CURRENT_FORMAT_VERSION) {
      throw new OrbitlabException(
          "Scenario format version "
              + version
              + " is newer than this build reads (version "
              + ScenarioFile.CURRENT_FORMAT_VERSION
              + ")");
    }
    try {
      return MAPPER.treeToValue(tree, ScenarioFile.class);
    } catch (JacksonException e) {
      throw new OrbitlabException("Cannot read the scenario: " + e.getMessage(), e);
    }
  }
}
