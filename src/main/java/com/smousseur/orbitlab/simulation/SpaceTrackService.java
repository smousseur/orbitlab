package com.smousseur.orbitlab.simulation;

import com.smousseur.orbitlab.core.PropertiesService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SpaceTrackService {
  private static final String LOGIN_URL = "/ajaxauth/login";
  private static final String CHECK_SESSION_URL = "/app/data/whoami";
  private static final String LOGOUT_URL = "/ajaxauth/logout";
  private final String username;
  private final String password;
  private final String baseUrl;
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  private SpaceTrackService() {
    CookieManager cookieHandler = new CookieManager();
    cookieHandler.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    this.client = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
    PropertiesService propertiesService = PropertiesService.get();
    this.username = propertiesService.getProperty("spacetrack.username");
    this.password = propertiesService.getProperty("spacetrack.password");
    this.baseUrl = propertiesService.getProperty("spacetrack.baseUrl");
  }

  protected void login() throws IOException, InterruptedException {
    client.send(
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + LOGIN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "identity=" + username + "&password=" + password))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected void logout() throws IOException, InterruptedException {
    client.send(
        HttpRequest.newBuilder().uri(URI.create(baseUrl + LOGOUT_URL)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected boolean isConnected() throws IOException, InterruptedException {
    String check =
        client
            .send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + CHECK_SESSION_URL)).build(),
                HttpResponse.BodyHandlers.ofString())
            .body();
    JsonNode jsonNode = mapper.readTree(check);
    return jsonNode.get("logged_in").asBoolean();
  }

  private static class Holder {
    private static final SpaceTrackService INSTANCE = new SpaceTrackService();
  }

  /**
   * Returns the singleton instance of the PropertiesService service.
   *
   * @return the shared {@code PropertiesService} instance
   */
  public static SpaceTrackService get() {
    return SpaceTrackService.Holder.INSTANCE;
  }
}
