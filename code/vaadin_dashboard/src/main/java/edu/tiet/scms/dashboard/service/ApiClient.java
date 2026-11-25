package edu.tiet.scms.dashboard.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import edu.tiet.scms.dashboard.dto.PortalUser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApiClient {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ApiClient.class);
  private final String base;
  private final ObjectMapper mapper;
  private final HttpClient client =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public ApiClient(@Value("${scms.api.base-url}") String base, ObjectMapper mapper) {
    URI uri = URI.create(base);
    if (!java.util.Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null) throw new IllegalArgumentException("Invalid API URL");
    this.base = base.replaceAll("/+$", "");
    this.mapper = mapper;
  }

  public JsonNode get(String path, PortalUser user) {
    return send("GET", path, null, user);
  }

  public JsonNode send(String method, String path, Object body, PortalUser user) {
    if (!path.startsWith("/") || path.startsWith("//") || path.contains(".."))
      throw new IllegalArgumentException("Invalid internal path");
    try {
      var b =
          HttpRequest.newBuilder(URI.create(base + path))
              .timeout(Duration.ofSeconds(15))
              .header("Accept", "application/json");
      if (user != null) b.header("Authorization", "Bearer " + user.token());
      if (body != null) b.header("Content-Type", "application/json");
      b.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      var response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode json = mapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        String message = json.path("error").path("message").asText("");
        if (message.isBlank())
          message =
              response.statusCode() == 422
                  ? "Please check the form values."
                  : "The request could not be completed.";
        throw new ApiException(response.statusCode(), message);
      }
      return json;
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Request interrupted. Please try again.");
    } catch (Exception e) {
      LOG.warn("Backend request failed: {}", e.getClass().getSimpleName());
      throw new ApiException(503, "Cannot reach the SCMS backend. Check that it is running.");
    }
  }

  public JsonNode find(String path, long id, PortalUser user) {
    for (int page = 1; ; page++) {
      JsonNode response = get(path + "?page=" + page + "&limit=100", user);
      for (JsonNode row : response.path("data")) if (row.path("id").asLong() == id) return row;
      if (page >= response.path("pagination").path("totalPages").asInt(1))
        throw new ApiException(404, "Record not found.");
    }
  }
}
