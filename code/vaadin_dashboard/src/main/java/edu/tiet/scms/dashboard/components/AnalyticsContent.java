package edu.tiet.scms.dashboard.components;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.service.*;
import java.util.*;
import java.util.function.Supplier;

public final class AnalyticsContent {
  private AnalyticsContent() {}

  public static Component load(Supplier<Component> loader, UserSession session) {
    Div target = new Div();
    target.setWidthFull();
    Runnable[] retry = new Runnable[1];
    retry[0] =
        () -> {
          target.removeAll();
          try {
            target.add(loader.get());
          } catch (Exception e) {
            if (e instanceof ApiException a && a.status == 401) {
              session.logout();
              return;
            }
            target.add(Ui.card(new Paragraph(e.getMessage()), Ui.button("Retry", retry[0])));
          }
        };
    retry[0].run();
    return target;
  }

  public static Div metrics(JsonNode object) {
    Div cards = new Div();
    cards.addClassName("metrics");
    object
        .fields()
        .forEachRemaining(
            e -> {
              if (e.getValue().isValueNode()) {
                String key = e.getKey(),
                    route =
                        switch (key) {
                          case "active_transactions" -> "/admin/transactions?status=ISSUED";
                          case "overdue_transactions" -> "/admin/transactions?status=OVERDUE";
                          case "confirmed_bookings" -> "/admin/bookings?status=CONFIRMED";
                          case "active_defaulters" -> "/admin/defaulters?status=ACTIVE";
                          default -> "";
                        };
                String value =
                    key.equals("damage_rate")
                        ? String.format(Locale.ROOT, "%.1f%%", e.getValue().asDouble() * 100)
                        : e.getValue().asText();
                Div card = Ui.card(new Paragraph(Ui.label(key)), new H2(value));
                if (!route.isEmpty()) {
                  com.vaadin.flow.component.html.Anchor a =
                      new com.vaadin.flow.component.html.Anchor(route, "");
                  a.add(card);
                  cards.add(a);
                } else cards.add(card);
              }
            });
    return cards;
  }

  public static DataChart bookings(JsonNode values) {
    ArrayNode data = JsonNodeFactory.instance.arrayNode();
    for (String k :
        List.of("requested", "confirmed", "waitlisted", "completed", "no_show", "cancelled"))
      data.addObject().put("status", Ui.label(k)).put("count", values.path(k).asInt());
    return new DataChart("Booking Status Breakdown", data, "status", List.of("count"), false);
  }

  public static JsonNode top(JsonNode data, String field) {
    List<JsonNode> rows = new ArrayList<>();
    data.forEach(rows::add);
    rows.sort(Comparator.comparingDouble((JsonNode r) -> r.path(field).asDouble()).reversed());
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    rows.stream().limit(10).forEach(result::add);
    return result;
  }

  public static Component table(String title, JsonNode data) {
    Div card = Ui.card(new H2(title));
    if (data.isEmpty()) {
      card.add(new Paragraph("No records found."));
      return card;
    }
    com.vaadin.flow.component.grid.Grid<JsonNode> grid =
        new com.vaadin.flow.component.grid.Grid<>();
    List<JsonNode> rows = new ArrayList<>();
    Set<String> keys = new LinkedHashSet<>();
    data.forEach(
        r -> {
          rows.add(r);
          r.fieldNames().forEachRemaining(keys::add);
        });
    for (String key : keys)
      grid.addColumn(r -> Ui.value(r, key))
          .setHeader(Ui.label(key))
          .setAutoWidth(true)
          .setSortable(true);
    grid.setItems(rows);
    grid.setAllRowsVisible(true);
    card.add(grid);
    return card;
  }
}
