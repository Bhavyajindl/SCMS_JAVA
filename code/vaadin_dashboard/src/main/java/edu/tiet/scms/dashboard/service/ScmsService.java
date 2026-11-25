package edu.tiet.scms.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.dto.ApiPage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScmsService implements DashboardApi {
  private final ApiClient client;
  private final UserSession session;

  public ScmsService(ApiClient client, UserSession session) {
    this.client = client;
    this.session = session;
  }

  public JsonNode get(String path) {
    return client.get(path, session.user()).path("data");
  }

  public JsonNode write(String method, String path, Object body) {
    if ((path.startsWith("/admin/") || path.startsWith("/inventory")) && !session.isAdmin())
      throw new ApiException(403, "Administrator access required.");
    return client.send(method, path, body, session.user()).path("data");
  }

  public ApiPage list(String path, int page, int size, Map<String, String> filters) {
    if (page < 1 || size < 1 || size > 100) throw new ApiException(400, "Invalid page.");
    StringBuilder query = new StringBuilder(path + "?page=" + page + "&limit=" + size);
    filters.forEach(
        (k, v) -> {
          if (v != null && !v.isBlank())
            query
                .append('&')
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
    return ApiPage.from(client.get(query.toString(), session.user()));
  }

  public List<JsonNode> allEquipment() {
    List<JsonNode> records = new ArrayList<>();
    for (int p = 1; ; p++) {
      ApiPage page = list("/inventory", p, 100, Map.of());
      records.addAll(page.items());
      if (p >= page.totalPages()) return records;
    }
  }
}
