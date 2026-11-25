package edu.tiet.scms.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.tiet.scms.dashboard.dto.ApiPage;
import java.util.*;

/** The UI depends on this contract; HTTP details stay in ScmsService. */
public interface DashboardApi {
  JsonNode get(String path);

  JsonNode write(String method, String path, Object body);

  ApiPage list(String path, int page, int size, Map<String, String> filters);

  List<JsonNode> allEquipment();
}
