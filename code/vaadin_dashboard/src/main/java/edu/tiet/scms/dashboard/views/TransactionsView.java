package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/transactions", layout = MainLayout.class)
@PageTitle("Transactions | SCMS")
@RolesAllowed("ADMIN")
public class TransactionsView extends RecordsView {
  public TransactionsView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "transactions",
        "Transactions",
        "Equipment issue and return history",
        "/admin/transactions",
        "id,student_id,equipment_name,status,issued_at,due_at,returned_at,condition_on_return",
        api,
        session,
        forms);
  }
}
