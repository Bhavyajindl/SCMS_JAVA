package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/defaulters", layout = MainLayout.class)
@PageTitle("Defaulters | SCMS")
@RolesAllowed("ADMIN")
public class DefaultersView extends RecordsView {
  public DefaultersView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "defaulters",
        "Defaulters",
        "Students with overdue equipment",
        "/admin/defaulters",
        "student_name,student_id,transaction_id,overdue_days,status,detected_at,resolved_at",
        api,
        session,
        forms);
  }
}
