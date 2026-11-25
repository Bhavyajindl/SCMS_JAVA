package edu.tiet.scms.dashboard.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.dto.ApiPage;
import edu.tiet.scms.dashboard.service.*;
import java.util.*;

/** Shared Java Grid, API paging, filters, confirmation dialogs and record actions. */
public class RecordsView extends VerticalLayout implements BeforeEnterObserver {
  protected final DashboardApi api;
  protected final UserSession session;
  private final FormCatalog forms;
  private final String kind, path;
  private int page = 1, totalPages = 1;
  private final Grid<JsonNode> grid = new Grid<>();
  private List<JsonNode> current = List.of();
  private final Map<String, String> filters = new LinkedHashMap<>();
  private final Map<String, ComboBox<String>> controls = new LinkedHashMap<>();
  private final Span count = new Span(), pageLabel = new Span();
  private final TextField search = new TextField("Search this page");
  private final Paragraph problem = new Paragraph();
  private final Button
      previous =
          Ui.button(
              "Previous",
              () -> {
                page--;
                reload();
              }),
      next =
          Ui.button(
              "Next",
              () -> {
                page++;
                reload();
              });

  public RecordsView(
      String kind,
      String title,
      String description,
      String path,
      String columns,
      DashboardApi api,
      UserSession session,
      FormCatalog forms) {
    this.kind = kind;
    this.path = path;
    this.api = api;
    this.session = session;
    this.forms = forms;
    addClassName("page");
    setPadding(false);
    setWidthFull();
    Div heading = new Div(new H1(title), new Paragraph(description));
    HorizontalLayout top = new HorizontalLayout(heading);
    top.setWidthFull();
    top.expand(heading);
    top.setAlignItems(Alignment.CENTER);
    if (Set.of("inventory", "slots", "staff").contains(kind) && session.isAdmin())
      top.add(
          Ui.primary(
              kind.equals("slots")
                  ? "Create Slot"
                  : kind.equals("staff") ? "Add Staff" : "Add Equipment",
              () -> edit(null)));
    add(top);
    HorizontalLayout toolbar = new HorizontalLayout();
    toolbar.addClassName("filters");
    toolbar.setWidthFull();
    toolbar.setAlignItems(Alignment.END);
    if (kind.equals("inventory"))
      filter(
          toolbar,
          "category",
          "Category",
          List.of(
              "Cricket",
              "Football",
              "Basketball",
              "Tennis",
              "Badminton",
              "Athletics",
              "Swimming",
              "Other"));
    if (kind.equals("bookings"))
      filter(
          toolbar,
          "status",
          "Status",
          List.of("REQUESTED", "CONFIRMED", "WAITLISTED", "CANCELLED", "NO_SHOW", "COMPLETED"));
    if (kind.equals("transactions"))
      filter(
          toolbar,
          "status",
          "Status",
          List.of("ISSUED", "RETURNED", "RETURNED_DAMAGED", "OVERDUE"));
    if (kind.equals("defaulters"))
      filter(toolbar, "status", "Status", List.of("ACTIVE", "NOTIFIED", "RESOLVED"));
    search.setClearButtonVisible(true);
    search.setValueChangeMode(ValueChangeMode.LAZY);
    search.addValueChangeListener(e -> display());
    toolbar.add(search, Ui.button("Refresh", this::reload), count);
    add(toolbar);
    for (String column : columns.split(",")) {
      if (Set.of("status", "condition", "condition_on_return", "role").contains(column))
        grid.addComponentColumn(r -> Ui.badge(Ui.value(r, column)))
            .setHeader(Ui.label(column))
            .setKey(column)
            .setAutoWidth(true);
      else
        grid.addColumn(r -> Ui.value(r, column))
            .setHeader(Ui.label(column))
            .setKey(column)
            .setSortable(true)
            .setAutoWidth(true)
            .setFlexGrow(1);
    }
    grid.addComponentColumn(this::actions).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    grid.addItemClickListener(
        e -> Ui.details(Ui.label(kind) + " #" + e.getItem().path("id").asText(), e.getItem()));
    grid.setHeight("570px");
    grid.addClassName("records-grid");
    grid.setAllRowsVisible(false);
    add(grid);
    HorizontalLayout paging = new HorizontalLayout(previous, pageLabel, next);
    paging.setAlignItems(Alignment.CENTER);
    paging.addClassName("pagination");
    add(paging);
    problem.addClassName("form-error");
    problem.setVisible(false);
    add(problem);
  }

  private void filter(HorizontalLayout bar, String key, String label, List<String> options) {
    ComboBox<String> combo = new ComboBox<>(label);
    combo.setItems(options);
    combo.setPlaceholder("All " + label.toLowerCase() + "s");
    combo.setClearButtonVisible(true);
    combo.addValueChangeListener(
        e -> {
          if (e.isFromClient()) {
            filters.put(key, Objects.toString(e.getValue(), ""));
            page = 1;
            reload();
          }
        });
    controls.put(key, combo);
    bar.add(combo);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent e) {
    var query = e.getLocation().getQueryParameters();
    filters.clear();
    for (String key : List.of("status", "category", "slot_id", "equipment_id", "student_id"))
      query.getSingleParameter(key).ifPresent(v -> filters.put(key, v));
    controls.forEach((key, c) -> c.setValue(filters.get(key)));
    try {
      page = Integer.parseInt(query.getSingleParameter("page").orElse("1"));
      if (page < 1) page = 1;
    } catch (NumberFormatException x) {
      page = 1;
    }
    reload();
  }

  protected void reload() {
    try {
      ApiPage result =
          api.list(path, page, kind.equals("inventory") || kind.equals("slots") ? 20 : 25, filters);
      current = result.items();
      totalPages = result.totalPages();
      display();
      count.setText(result.total() + " total");
      previous.setEnabled(page > 1);
      next.setEnabled(page < totalPages);
      pageLabel.setText("Page " + page + " of " + totalPages);
      problem.setVisible(false);
      Map<String, String> q = new LinkedHashMap<>(filters);
      q.values().removeIf(String::isBlank);
      if (page > 1) q.put("page", String.valueOf(page));
      if (UI.getCurrent() != null)
        UI.getCurrent()
            .getPage()
            .getHistory()
            .replaceState(
                null,
                "/admin/"
                    + kind
                    + QueryParameters.simple(q)
                        .getQueryString()
                        .transform(s -> s.isBlank() ? "" : "?" + s));
    } catch (Exception e) {
      problem.setText(e.getMessage());
      problem.setVisible(true);
      Ui.error(e, session);
    }
  }

  private void display() {
    String query = search.getValue().trim().toLowerCase(Locale.ROOT);
    grid.setItems(
        current.stream()
            .filter(
                row -> query.isEmpty() || row.toString().toLowerCase(Locale.ROOT).contains(query))
            .toList());
  }

  private void edit(JsonNode row) {
    try {
      new RecordEditor(kind, row, api, forms, session, this::reload).open();
    } catch (Exception e) {
      Ui.error(e, session);
    }
  }

  private Component actions(JsonNode row) {
    HorizontalLayout buttons = new HorizontalLayout();
    buttons.setSpacing(false);
    buttons.add(
        Ui.button(
            "Details", () -> Ui.details(Ui.label(kind) + " #" + row.path("id").asText(), row)));
    if (!session.isAdmin()) return buttons;
    long id = row.path("id").asLong();
    String status = row.path("status").asText();
    if (kind.equals("inventory")) {
      buttons.add(Ui.button("Edit", () -> edit(row)));
      if (status.equals("AVAILABLE") || status.equals("MAINTENANCE")) {
        String target = status.equals("AVAILABLE") ? "MAINTENANCE" : "AVAILABLE";
        buttons.add(
            Ui.button(
                target.equals("AVAILABLE") ? "Restore" : "Maintenance",
                () -> run("PUT", path + "/" + id, Map.of("status", target), "Equipment updated.")));
        buttons.add(
            Ui.button(
                "Retire",
                () ->
                    Ui.confirm(
                        "Retire equipment?",
                        "This equipment will no longer accept bookings.",
                        "Retire",
                        () -> run("PATCH", path + "/" + id + "/retire", null, "Equipment retired."),
                        session)));
      }
    }
    if (kind.equals("slots")) {
      if (status.equals("OPEN")) buttons.add(Ui.button("Edit", () -> edit(row)));
      if (Set.of("OPEN", "PENDING_ALLOCATION").contains(status))
        buttons.add(
            Ui.button(
                "Run Allocation",
                () -> {
                  try {
                    var result = api.write("POST", path + "/" + id + "/run-allocation", null);
                    Ui.details("Allocation Complete", result);
                    reload();
                  } catch (Exception e) {
                    Ui.error(e, session);
                  }
                }));
      if (!status.equals("CLOSED"))
        buttons.add(
            Ui.button(
                "Close Slot",
                () ->
                    Ui.confirm(
                        "Close slot?",
                        "All REQUESTED bookings for this slot will be cancelled.",
                        "Close Slot",
                        () -> run("PATCH", path + "/" + id + "/deactivate", null, "Slot closed."),
                        session)));
    }
    if (kind.equals("bookings") && Set.of("REQUESTED", "CONFIRMED", "WAITLISTED").contains(status))
      buttons.add(
          Ui.button(
              "Cancel Booking",
              () ->
                  Ui.confirm(
                      "Cancel booking?",
                      "Booking #"
                          + id
                          + " will be cancelled. Allocation and waitlist rules still apply.",
                      "Cancel Booking",
                      () -> run("DELETE", "/admin/bookings/" + id, null, "Booking cancelled."),
                      session)));
    if (kind.equals("defaulters") && !status.equals("RESOLVED"))
      buttons.add(
          Ui.button(
              "Resolve",
              () ->
                  Ui.confirm(
                      "Resolve defaulter?",
                      "Mark this record as resolved.",
                      "Resolve",
                      () -> run("PUT", path + "/" + id + "/resolve", null, "Defaulter resolved."),
                      session)));
    if (kind.equals("staff")) {
      buttons.add(Ui.button("Edit", () -> edit(row)));
      if (!status.equals("INACTIVE") && id != session.user().id())
        buttons.add(
            Ui.button(
                "Deactivate",
                () ->
                    Ui.confirm(
                        "Deactivate staff account?",
                        "The user will no longer be able to sign in.",
                        "Deactivate",
                        () ->
                            run(
                                "PATCH",
                                path + "/" + id + "/deactivate",
                                null,
                                "Account deactivated."),
                        session)));
    }
    return buttons;
  }

  private void run(String method, String path, Object body, String message) {
    try {
      api.write(method, path, body);
      Ui.success(message);
      reload();
    } catch (Exception e) {
      Ui.error(e, session);
    }
  }
}
