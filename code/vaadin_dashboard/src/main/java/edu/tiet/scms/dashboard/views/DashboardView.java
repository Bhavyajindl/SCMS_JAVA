package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.*;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.ScmsService;
import jakarta.annotation.security.RolesAllowed;
import java.util.*;

@Route(value = "admin/dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | SCMS")
@RolesAllowed("ADMIN")
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {
  private final ScmsService api;
  private final UserSession session;

  public DashboardView(ScmsService api, UserSession session) {
    this.api = api;
    this.session = session;
    addClassName("page");
    setPadding(false);
  }

  public void beforeEnter(BeforeEnterEvent e) {
    removeAll();
    add(new H1("Dashboard"), new Paragraph("System overview at a glance"));
    add(
        AnalyticsContent.load(
            () -> AnalyticsContent.metrics(api.get("/admin/analytics/overview")), session));
    Div charts = new Div();
    charts.addClassName("chart-grid");
    charts.add(
        AnalyticsContent.load(
            () ->
                new DataChart(
                    "7-Day Usage Trends",
                    api.get("/admin/analytics/usage-trends?days=7"),
                    "date",
                    List.of("bookings", "issues", "returns"),
                    true),
            session));
    charts.add(
        AnalyticsContent.load(
            () -> AnalyticsContent.bookings(api.get("/admin/analytics/bookings")), session));
    add(charts);
    Div links = Ui.card(new H2("Quick Actions"));
    links.addClassName("quick-actions");
    for (String path : List.of("inventory", "bookings", "transactions", "defaulters"))
      links.add(new Anchor("/admin/" + path, Ui.label(path)));
    add(links);
  }
}
