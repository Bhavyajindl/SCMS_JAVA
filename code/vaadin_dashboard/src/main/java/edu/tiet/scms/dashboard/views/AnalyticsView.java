package edu.tiet.scms.dashboard.views;

import com.fasterxml.jackson.databind.node.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.*;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.ScmsService;
import jakarta.annotation.security.RolesAllowed;
import java.util.*;

@Route(value = "admin/analytics", layout = MainLayout.class)
@PageTitle("Analytics | SCMS")
@RolesAllowed("ADMIN")
public class AnalyticsView extends VerticalLayout implements BeforeEnterObserver {
  private final ScmsService api;
  private final UserSession session;
  private int days = 7;
  private final Div content = new Div();

  public AnalyticsView(ScmsService api, UserSession session) {
    this.api = api;
    this.session = session;
    addClassName("page");
    setPadding(false);
    HorizontalLayout periods = new HorizontalLayout();
    for (int d : List.of(7, 14, 30))
      periods.add(
          Ui.button(
              d + " days",
              () -> {
                days = d;
                render();
              }));
    add(
        new H1("Analytics"),
        new Paragraph("Usage statistics, trends, and performance"),
        periods,
        content);
    content.setWidthFull();
  }

  public void beforeEnter(BeforeEnterEvent e) {
    render();
  }

  private void render() {
    content.removeAll();
    content.add(
        AnalyticsContent.load(
            () ->
                new DataChart(
                    days + "-Day Usage Trends",
                    api.get("/admin/analytics/usage-trends?days=" + days),
                    "date",
                    List.of("bookings", "issues", "returns"),
                    true),
            session));
    Div pair = new Div();
    pair.addClassName("chart-grid");
    pair.add(
        AnalyticsContent.load(
            () -> AnalyticsContent.bookings(api.get("/admin/analytics/bookings")), session));
    pair.add(
        AnalyticsContent.load(
            () ->
                new DataChart(
                    "Peak Hours (Top 10)",
                    AnalyticsContent.top(api.get("/admin/analytics/peak-hours"), "booking_count"),
                    "hour",
                    List.of("booking_count"),
                    false),
            session));
    content.add(pair);
    content.add(
        AnalyticsContent.load(
            () -> {
              var data = api.get("/admin/analytics/equipment");
              ArrayNode transformed = JsonNodeFactory.instance.arrayNode();
              AnalyticsContent.top(data, "utilization_rate")
                  .forEach(
                      row ->
                          transformed
                              .addObject()
                              .put("name", row.path("name").asText())
                              .put(
                                  "utilization_percent",
                                  row.path("utilization_rate").asDouble() * 100));
              return new Div(
                  new DataChart(
                      "Equipment Utilisation (Top 10)",
                      transformed,
                      "name",
                      List.of("utilization_percent"),
                      false),
                  AnalyticsContent.table("Equipment Performance", data));
            },
            session));
    content.add(
        AnalyticsContent.load(
            () -> {
              var data = api.get("/admin/analytics/damage-stats");
              return new Div(
                  AnalyticsContent.metrics(data),
                  AnalyticsContent.table(
                      "Recent Damage Reports", data.path("recent_damage_reports")));
            },
            session));
  }
}
