package edu.tiet.scms.dashboard.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.timepicker.TimePicker;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.service.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** One reusable Java dialog; field definitions and API types stay in FormCatalog. */
public class RecordEditor extends Dialog {
  private final Map<String, Supplier<String>> values = new LinkedHashMap<>();

  public RecordEditor(
      String kind,
      JsonNode record,
      DashboardApi api,
      FormCatalog forms,
      UserSession session,
      Runnable saved) {
    boolean edit = record != null;
    setHeaderTitle((edit ? "Edit " : "Add ") + Ui.label(kind));
    setWidth("640px");
    setMaxWidth("95vw");
    FormLayout form = new FormLayout();
    form.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
    for (var field : forms.fields(kind, edit)) {
      String existing = edit ? record.path(field.name()).asText("") : "";
      Component component;
      switch (field.type()) {
        case "equipment" -> {
          ComboBox<JsonNode> select = new ComboBox<>(field.label());
          select.setItems(
              api.allEquipment().stream()
                  .filter(r -> !r.path("status").asText().equals("RETIRED"))
                  .toList());
          select.setItemLabelGenerator(
              r -> r.path("name").asText() + " (#" + r.path("id").asText() + ")");
          component = select;
          values.put(
              field.name(),
              () -> select.getValue() == null ? "" : select.getValue().path("id").asText());
        }
        case "select" -> {
          ComboBox<String> select = new ComboBox<>(field.label());
          select.setItems(field.options());
          select.setValue(field.options().contains(existing) ? existing : field.options().get(0));
          component = select;
          values.put(field.name(), () -> select.getValue() == null ? "" : select.getValue());
        }
        case "date" -> {
          DatePicker input = new DatePicker(field.label());
          if (!existing.isEmpty()) input.setValue(LocalDate.parse(existing));
          component = input;
          values.put(field.name(), () -> Objects.toString(input.getValue(), ""));
        }
        case "time" -> {
          TimePicker input = new TimePicker(field.label());
          input.setStep(Duration.ofMinutes(1));
          if (!existing.isEmpty()) input.setValue(LocalTime.parse(existing));
          component = input;
          values.put(field.name(), () -> Objects.toString(input.getValue(), ""));
        }
        case "datetime-local" -> {
          DateTimePicker input = new DateTimePicker(field.label());
          input.setStep(Duration.ofMinutes(1));
          if (!existing.isEmpty())
            input.setValue(
                LocalDateTime.parse(existing.substring(0, Math.min(19, existing.length()))));
          component = input;
          values.put(field.name(), () -> Objects.toString(input.getValue(), ""));
        }
        case "integer" -> {
          IntegerField input = new IntegerField(field.label());
          input.setMin(1);
          input.setValue(existing.isEmpty() ? 1 : Integer.parseInt(existing));
          component = input;
          values.put(field.name(), () -> Objects.toString(input.getValue(), ""));
        }
        case "password" -> {
          PasswordField input = new PasswordField(field.label());
          input.setMinLength(6);
          input.setAutocomplete(Autocomplete.NEW_PASSWORD);
          component = input;
          values.put(field.name(), input::getValue);
        }
        case "email" -> {
          EmailField input = new EmailField(field.label());
          input.setValue(existing);
          component = input;
          values.put(field.name(), input::getValue);
        }
        default -> {
          TextField input = new TextField(field.label());
          input.setValue(existing);
          component = input;
          values.put(field.name(), input::getValue);
        }
      }
      component.setId("field-" + field.name());
      component.getElement().setAttribute("data-field", field.name());
      form.add(component);
    }
    Paragraph error = new Paragraph();
    error.addClassName("form-error");
    error.setVisible(false);
    add(form, error);
    getFooter()
        .add(
            Ui.button("Cancel", this::close),
            Ui.primary(
                edit ? "Save Changes" : "Create",
                () -> {
                  try {
                    Map<String, String> raw = new LinkedHashMap<>();
                    values.forEach((name, read) -> raw.put(name, read.get()));
                    Map<String, Object> body = forms.body(kind, edit, raw);
                    if (kind.equals("slots")) validateSlot(raw);
                    if (kind.equals("staff") && !edit && raw.get("password").length() < 6)
                      throw new ApiException(400, "Password must have at least 6 characters.");
                    if (kind.equals("staff")
                        && !raw.get("email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                      throw new ApiException(400, "Enter a valid email address.");
                    String path =
                        switch (kind) {
                          case "inventory" -> "/inventory";
                          case "slots" -> "/admin/slots";
                          case "staff" -> "/admin/staff";
                          default -> throw new ApiException(404, "Form not found.");
                        };
                    api.write(
                        edit ? "PUT" : "POST",
                        path + (edit ? "/" + record.path("id").asLong() : ""),
                        body);
                    close();
                    Ui.success("Record " + (edit ? "updated." : "created."));
                    saved.run();
                  } catch (Exception e) {
                    if (e instanceof ApiException a && a.status == 401) {
                      session.logout();
                      return;
                    }
                    error.setText(e.getMessage());
                    error.setVisible(true);
                  }
                }));
  }

  static void validateSlot(Map<String, String> values) {
    LocalTime start = LocalTime.parse(values.get("start_time")),
        end = LocalTime.parse(values.get("end_time"));
    if (!end.isAfter(start)) throw new ApiException(400, "End time must be after start time.");
    LocalDateTime begins = LocalDate.parse(values.get("date")).atTime(start);
    if (!LocalDateTime.parse(values.get("booking_cutoff_at")).isBefore(begins))
      throw new ApiException(400, "Booking cutoff must be before the slot start.");
  }
}
