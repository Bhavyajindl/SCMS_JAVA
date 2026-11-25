package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/staff", layout = MainLayout.class)
@PageTitle("Staff Accounts | SCMS")
@RolesAllowed("ADMIN")
public class StaffView extends RecordsView {
  public StaffView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "staff",
        "Staff Accounts",
        "Manage staff and admin users",
        "/admin/staff",
        "name,email,role,status,created_at",
        api,
        session,
        forms);
  }
}
