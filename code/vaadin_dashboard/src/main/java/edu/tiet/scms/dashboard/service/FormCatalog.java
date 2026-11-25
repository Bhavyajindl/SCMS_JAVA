package edu.tiet.scms.dashboard.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FormCatalog {
  public record Field(
      String name, String label, String type, boolean required, List<String> options) {}

  private Field f(String n, String label, String type, boolean required, String options) {
    return new Field(
        n, label, type, required, options.isEmpty() ? List.of() : List.of(options.split(",")));
  }

  public List<Field> fields(String screen, boolean edit) {
    List<Field> fields = new ArrayList<>();
    switch (screen) {
      case "inventory" -> {
        fields.add(f("name", "Equipment name", "text", true, ""));
        fields.add(f("category", "Category", "text", true, ""));
        if (!edit) fields.add(f("qr_code", "Equipment QR identifier", "text", true, ""));
        fields.add(f("location", "Location", "text", false, ""));
        fields.add(f("notes", "Notes", "text", false, ""));
        if (edit) {
          fields.add(
              f("status", "Status", "select", true, "AVAILABLE,BOOKED,ISSUED,MAINTENANCE,RETIRED"));
          fields.add(f("condition", "Condition", "select", true, "GOOD,FAIR,DAMAGED"));
        }
      }
      case "slots" -> {
        if (!edit) fields.add(f("equipment_id", "Equipment", "equipment", true, ""));
        fields.add(f("date", "Slot date (UTC)", "date", true, ""));
        fields.add(f("start_time", "Start time (UTC)", "time", true, ""));
        fields.add(f("end_time", "End time (UTC)", "time", true, ""));
        fields.add(f("capacity", "Capacity", "integer", true, ""));
        fields.add(f("booking_cutoff_at", "Booking cutoff (UTC)", "datetime-local", true, ""));
      }
      case "staff" -> {
        fields.add(f("name", "Full name", "text", true, ""));
        fields.add(f("email", "Email", "email", true, ""));
        if (!edit) fields.add(f("password", "Password", "password", true, ""));
        fields.add(f("role", "Role", "select", true, "STAFF,ADMIN"));
      }
      case "fairness" -> {
        fields.add(f("daily_usage_cap", "Daily usage cap", "integer", true, ""));
        fields.add(f("recency_threshold_days", "Recency threshold in days", "integer", true, ""));
        fields.add(f("recency_weight", "Recency weight", "number", true, ""));
      }
      default -> throw new ApiException(404, "Form not found.");
    }
    if (screen.equals("inventory") && edit) fields.removeIf(field -> field.name().equals("status"));
    return fields;
  }

  public Map<String, Object> body(String screen, boolean edit, Map<String, String> input) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Field f : fields(screen, edit)) {
      String raw = input.getOrDefault(f.name(), "");
      String value = f.type().equals("password") ? raw : raw.trim();
      if (f.required() && value.isEmpty())
        throw new ApiException(400, "Please enter " + f.label().toLowerCase() + ".");
      try {
        Object parsed =
            switch (f.type()) {
              case "integer", "equipment" -> Long.parseLong(value);
              case "number" -> new BigDecimal(value);
              case "datetime-local" ->
                  LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).toString();
              default -> value;
            };
        out.put(f.name(), parsed);
      } catch (RuntimeException e) {
        throw new ApiException(400, "Please check " + f.label().toLowerCase() + ".");
      }
    }
    return out;
  }
}
