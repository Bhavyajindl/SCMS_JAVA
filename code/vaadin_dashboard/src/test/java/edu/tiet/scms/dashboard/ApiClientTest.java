package edu.tiet.scms.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edu.tiet.scms.dashboard.dto.PortalUser;
import edu.tiet.scms.dashboard.service.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiClientTest {
  @Test
  void usesHttp11JsonAndServerSideBearerToken() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/check",
        exchange -> {
          assertEquals("HTTP/1.1", exchange.getProtocol());
          assertNull(exchange.getRequestHeaders().getFirst("Upgrade"));
          assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
          assertEquals("POST", exchange.getRequestMethod());
          assertTrue(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                  .contains("Bat"));
          byte[] body = "{\"data\":{\"id\":2}}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      var api =
          new ApiClient(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/api", new ObjectMapper());
      assertEquals(
          2,
          api.send(
                  "POST",
                  "/check",
                  Map.of("name", "Bat"),
                  new PortalUser(1, "Test", "test@example.com", "ADMIN", "test-token"))
              .path("data")
              .path("id")
              .asInt());
      assertThrows(IllegalArgumentException.class, () -> api.get("//other-server", null));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void preservesBusinessRuleStatusAndMessage() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/fail",
        exchange -> {
          byte[] body =
              "{\"error\":{\"message\":\"Duplicate booking\"}}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(409, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try {
      var api =
          new ApiClient(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/api", new ObjectMapper());
      ApiException error = assertThrows(ApiException.class, () -> api.get("/fail", null));
      assertEquals(409, error.status);
      assertEquals("Duplicate booking", error.getMessage());
    } finally {
      server.stop(0);
    }
  }
}
