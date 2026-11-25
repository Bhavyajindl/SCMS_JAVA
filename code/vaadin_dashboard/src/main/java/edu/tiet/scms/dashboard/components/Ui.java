package edu.tiet.scms.dashboard.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.*;
import com.vaadin.flow.component.orderedlayout.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.service.ApiException;
import java.util.*;

public final class Ui {
  private Ui() {}

  public static Button button(String label, Runnable action) {
    Button b =
        new Button(
            label,
            e -> {
              e.getSource().setEnabled(false);
              try {
                action.run();
              } finally {
                e.getSource().setEnabled(true);
              }
            });
    return b;
  }

  public static Button primary(String label, Runnable action) {
    Button b = button(label, action);
    b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    return b;
  }

  public static Div card(Component... children) {
    Div div = new Div(children);
    div.addClassName("card");
    return div;
  }

  public static String label(String s) {
    String v = s.replace('_', ' ');
    return v.isEmpty() ? v : Character.toUpperCase(v.charAt(0)) + v.substring(1);
  }

  public static String value(JsonNode row, String field) {
    JsonNode node = row;
    for (String p : field.split("\\.")) node = node.path(p);
    return node.isNull() || node.isMissingNode() ? "—" : node.asText(node.toString());
  }

  public static Span badge(String text) {
    Span b = new Span(text);
    b.addClassNames("badge", "badge-" + text.toLowerCase(Locale.ROOT));
    return b;
  }

  public static void success(String message) {
    Notification n = Notification.show(message, 4500, Notification.Position.TOP_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  public static void error(Exception e, UserSession session) {
    if (e instanceof ApiException a && a.status == 401) {
      session.logout();
      return;
    }
    Notification n =
        Notification.show(
            e.getMessage() == null ? "Request failed. Please try again." : e.getMessage(),
            8000,
            Notification.Position.TOP_END);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  public static void confirm(
      String title, String description, String label, Runnable action, UserSession session) {
    Dialog d = new Dialog();
    d.setHeaderTitle(title);
    d.add(new Paragraph(description));
    d.getFooter()
        .add(
            button("Cancel", d::close),
            primary(
                label,
                () -> {
                  try {
                    action.run();
                    d.close();
                  } catch (Exception e) {
                    error(e, session);
                  }
                }));
    d.open();
  }

  public static void details(String title, JsonNode row) {
    Dialog d = new Dialog();
    d.setHeaderTitle(title);
    Div fields = new Div();
    fields.addClassName("detail-grid");
    row.fields()
        .forEachRemaining(
            e -> {
              Div item =
                  new Div(new Span(label(e.getKey())), new Paragraph(value(row, e.getKey())));
              fields.add(item);
            });
    d.add(fields);
    d.getFooter().add(button("Close", d::close));
    d.setWidth("650px");
    d.setMaxWidth("95vw");
    d.open();
  }
}
