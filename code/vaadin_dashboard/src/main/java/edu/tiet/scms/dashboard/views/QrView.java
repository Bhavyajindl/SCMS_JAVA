package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.Ui;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.PermitAll;
import java.util.Map;

@Route(value = "staff/qr", layout = MainLayout.class)
@PageTitle("QR Validation | SCMS")
@PermitAll
public class QrView extends VerticalLayout {
  public QrView(ScmsService api, UserSession session) {
    setPadding(false);
    addClassName("page");
    add(
        new H1("QR Validation"),
        new Paragraph(
            "Enter a scanned student or equipment QR value. Camera scanning is available in the"
                + " mobile app."));
    ComboBox<String> kind = new ComboBox<>("QR Type");
    kind.setItems("Student", "Equipment");
    kind.setValue("Student");
    TextField value = new TextField("QR Value");
    value.setWidth("min(500px,100%)");
    Div result = new Div();
    result.setWidthFull();
    add(
        kind,
        value,
        Ui.primary(
            "Validate QR",
            () -> {
              try {
                if (value.isEmpty()) throw new ApiException(400, "Enter a QR value.");
                String type = kind.getValue().equals("Student") ? "student" : "equipment";
                var data =
                    api.write(
                        "POST",
                        "/qr/validate/" + type,
                        Map.of(
                            type.equals("student") ? "token" : "qr_code", value.getValue().trim()));
                value.clear();
                result.removeAll();
                if (type.equals("student")) {
                  var student = data.path("student");
                  result.add(
                      Ui.card(
                          new H2("Verified Student"),
                          new Paragraph(
                              student.path("name").asText()
                                  + " | "
                                  + student.path("student_id").asText()),
                          Ui.badge(student.path("status").asText())));
                  if (data.path("current_booking").isObject()) {
                    var booking = data.path("current_booking");
                    result.add(
                        Ui.primary(
                            "Issue Confirmed Equipment",
                            () ->
                                Ui.confirm(
                                    "Issue equipment?",
                                    "Confirm the student and equipment before issue.",
                                    "Issue",
                                    () -> {
                                      api.write(
                                          "POST",
                                          "/transactions/issue",
                                          Map.of(
                                              "booking_id",
                                              booking.path("id").asLong(),
                                              "equipment_id",
                                              booking.path("equipment").path("id").asLong()));
                                      Ui.success("Equipment issued.");
                                      getUI().ifPresent(ui -> ui.navigate("staff/queue"));
                                    },
                                    session)));
                  } else result.add(new Paragraph("No confirmed booking found."));
                  if (data.path("open_transaction").isObject())
                    result.add(
                        new Paragraph(
                            "Open transaction #"
                                + data.path("open_transaction").path("id").asText()
                                + " — use Issue & Return to record a return."));
                } else
                  result.add(
                      Ui.card(
                          new H2("Verified Equipment"),
                          new Paragraph(data.path("name").asText()),
                          Ui.badge(data.path("status").asText()),
                          Ui.badge(data.path("condition").asText())));
              } catch (Exception e) {
                Ui.error(e, session);
              }
            }),
        result);
  }
}
