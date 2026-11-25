package edu.tiet.scms.dashboard.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.Ui;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.ScmsService;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.util.*;

@Route(value = "staff/queue", layout = MainLayout.class)
@PageTitle("Issue & Return | SCMS")
@PermitAll
public class OperationsView extends VerticalLayout implements BeforeEnterObserver {
  private final ScmsService api;
  private final UserSession session;
  private final DatePicker date = new DatePicker("Date");
  private final Div content = new Div();

  public OperationsView(ScmsService api, UserSession session) {
    this.api = api;
    this.session = session;
    setPadding(false);
    addClassName("page");
    date.setValue(LocalDate.now());
    date.addValueChangeListener(
        e -> {
          if (e.isFromClient()) load();
        });
    add(
        new H1("Issue & Return"),
        new Paragraph("Confirmed bookings and open transactions"),
        date,
        content);
    content.setWidthFull();
  }

  public void beforeEnter(BeforeEnterEvent e) {
    e.getLocation()
        .getQueryParameters()
        .getSingleParameter("date")
        .ifPresent(
            d -> {
              try {
                date.setValue(LocalDate.parse(d));
              } catch (Exception ignored) {
              }
            });
    load();
  }

  private void load() {
    content.removeAll();
    try {
      var data =
          api.get("/staff/queue" + (date.getValue() == null ? "" : "?for_date=" + date.getValue()));
      Div confirmed = Ui.card(new H2("Confirmed Bookings"));
      if (data.path("confirmed_bookings_today").isEmpty())
        confirmed.add(new Paragraph("No confirmed bookings for this date."));
      for (JsonNode row : data.path("confirmed_bookings_today")) {
        Div record =
            new Div(
                new H3(Ui.value(row, "student.name")),
                new Paragraph(
                    Ui.value(row, "equipment.name")
                        + " | "
                        + Ui.value(row, "slot.start_time")
                        + " – "
                        + Ui.value(row, "slot.end_time")),
                Ui.badge(row.path("status").asText()));
        record.add(
            Ui.primary(
                "Issue Equipment",
                () ->
                    Ui.confirm(
                        "Issue equipment?",
                        "Confirm this student and equipment match the booking.",
                        "Issue",
                        () -> {
                          api.write(
                              "POST",
                              "/transactions/issue",
                              Map.of(
                                  "booking_id",
                                  row.path("booking_id").asLong(),
                                  "equipment_id",
                                  row.path("equipment").path("id").asLong()));
                          Ui.success("Equipment issued.");
                          load();
                        },
                        session)));
        record.addClassName("operation-row");
        confirmed.add(record);
      }
      Div open = Ui.card(new H2("Open Transactions"));
      if (data.path("open_transactions").isEmpty())
        open.add(new Paragraph("No equipment awaiting return."));
      for (JsonNode row : data.path("open_transactions")) {
        Div record =
            new Div(
                new H3(Ui.value(row, "student.name")),
                new Paragraph(
                    Ui.value(row, "equipment.name") + " | Due: " + Ui.value(row, "due_at")),
                Ui.badge(row.path("status").asText()),
                Ui.primary(
                    "Record Return", () -> returnDialog(row.path("transaction_id").asLong())));
        record.addClassName("operation-row");
        open.add(record);
      }
      content.add(confirmed, open);
    } catch (Exception e) {
      content.add(Ui.card(new Paragraph(e.getMessage()), Ui.button("Retry", this::load)));
      Ui.error(e, session);
    }
  }

  private void returnDialog(long id) {
    Dialog d = new Dialog();
    d.setHeaderTitle("Record Return #" + id);
    ComboBox<String> condition = new ComboBox<>("Condition on Return");
    condition.setItems("GOOD", "FAIR", "DAMAGED");
    condition.setValue("GOOD");
    TextArea report = new TextArea("Damage Report");
    report.setWidthFull();
    Paragraph error = new Paragraph();
    error.addClassName("form-error");
    d.add(new VerticalLayout(condition, report, error));
    d.getFooter()
        .add(
            Ui.button("Cancel", d::close),
            Ui.primary(
                "Confirm Return",
                () -> {
                  try {
                    if (condition.getValue() == null)
                      throw new edu.tiet.scms.dashboard.service.ApiException(
                          400, "Choose a return condition.");
                    if (condition.getValue().equals("DAMAGED") && report.getValue().isBlank())
                      throw new edu.tiet.scms.dashboard.service.ApiException(
                          400, "A damage report is required for DAMAGED condition.");
                    api.write(
                        "POST",
                        "/transactions/" + id + "/return",
                        Map.of(
                            "condition_on_return",
                            condition.getValue(),
                            "damage_report",
                            report.getValue().trim()));
                    d.close();
                    Ui.success("Return recorded.");
                    load();
                  } catch (Exception e) {
                    error.setText(e.getMessage());
                    if (e instanceof edu.tiet.scms.dashboard.service.ApiException a
                        && a.status == 401) session.logout();
                  }
                }));
    d.setWidth("480px");
    d.open();
  }
}
