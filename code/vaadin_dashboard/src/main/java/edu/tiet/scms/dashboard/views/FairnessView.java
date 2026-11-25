package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.Ui;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.RolesAllowed;
import java.util.*;

@Route(value = "admin/fairness", layout = MainLayout.class)
@PageTitle("Fairness | SCMS")
@RolesAllowed("ADMIN")
public class FairnessView extends VerticalLayout implements BeforeEnterObserver {
  private final ScmsService api;
  private final UserSession session;
  private final IntegerField cap = new IntegerField("Daily Usage Cap"),
      threshold = new IntegerField("Recency Threshold (days)");
  private final NumberField weight = new NumberField("Recency Weight");
  private final Paragraph updated = new Paragraph();

  public FairnessView(ScmsService api, UserSession session) {
    this.api = api;
    this.session = session;
    addClassName("page");
    setPadding(false);
    cap.setMin(1);
    cap.setMax(10);
    cap.setHelperText("Maximum sessions per student per day");
    threshold.setMin(1);
    threshold.setMax(30);
    weight.setMin(0);
    weight.setMax(1);
    weight.setStep(0.05);
    VerticalLayout fields =
        new VerticalLayout(
            new H2("Allocation Parameters"),
            updated,
            cap,
            threshold,
            weight,
            Ui.primary("Save Configuration", this::save));
    fields.setPadding(false);
    add(
        new H1("Fairness Configuration"),
        new Paragraph("Configure the batch allocation fairness parameters"),
        Ui.card(fields));
    add(
        Ui.card(
            new H2("Formula Reference"),
            new Paragraph("priority_score = S_norm + P_recency"),
            new Paragraph("Lower score = higher allocation priority."),
            new Paragraph("S_norm = sessions_7d / max(max_in_batch, 1)"),
            new Paragraph("P_recency = max(0, (threshold - days_since_last) × weight)")));
  }

  public void beforeEnter(BeforeEnterEvent e) {
    load();
  }

  private void load() {
    try {
      var data = api.get("/admin/fairness-config");
      cap.setValue(data.path("daily_usage_cap").asInt());
      threshold.setValue(data.path("recency_threshold_days").asInt());
      weight.setValue(data.path("recency_weight").asDouble());
      updated.setText(
          "Last updated "
              + data.path("updated_at").asText()
              + " by staff #"
              + data.path("updated_by").asText("—"));
    } catch (Exception e) {
      Ui.error(e, session);
    }
  }

  private void save() {
    try {
      if (cap.getValue() == null
          || cap.getValue() < 1
          || cap.getValue() > 10
          || threshold.getValue() == null
          || threshold.getValue() < 1
          || threshold.getValue() > 30
          || weight.getValue() == null
          || weight.getValue() < 0
          || weight.getValue() > 1)
        throw new ApiException(
            400, "Enter a daily cap of 1–10, a threshold of 1–30 days and a weight of 0–1.");
      api.write(
          "PUT",
          "/admin/fairness-config",
          Map.of(
              "daily_usage_cap",
              cap.getValue(),
              "recency_threshold_days",
              threshold.getValue(),
              "recency_weight",
              weight.getValue()));
      Ui.success("Fairness config updated.");
      load();
    } catch (Exception e) {
      Ui.error(e, session);
    }
  }
}
