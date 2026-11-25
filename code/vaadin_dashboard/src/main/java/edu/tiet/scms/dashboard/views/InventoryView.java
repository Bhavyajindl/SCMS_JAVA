package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/inventory", layout = MainLayout.class)
@PageTitle("Inventory | SCMS")
@PermitAll
public class InventoryView extends RecordsView {
  public InventoryView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "inventory",
        "Inventory",
        "Manage all equipment",
        "/inventory",
        "name,category,status,condition,location,qr_code,added_at",
        api,
        session,
        forms);
  }
}
