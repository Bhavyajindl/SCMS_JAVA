package edu.tiet.scms.dashboard.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public record ApiPage(List<JsonNode> items, int page, int totalPages, int total) {
  public static ApiPage from(JsonNode response) {
    List<JsonNode> items = new ArrayList<>();
    response.path("data").forEach(items::add);
    var p = response.path("pagination");
    return new ApiPage(
        List.copyOf(items),
        p.path("page").asInt(1),
        Math.max(1, p.path("totalPages").asInt(1)),
        p.path("total").asInt(items.size()));
  }
}
