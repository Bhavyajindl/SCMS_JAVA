package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/bookings", layout = MainLayout.class)
@PageTitle("Bookings | SCMS")
@RolesAllowed("ADMIN")
public class BookingsView extends RecordsView {
  public BookingsView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "bookings",
        "Bookings",
        "All booking records across all students",
        "/admin/bookings",
        "id,student_id,equipment_id,slot_id,status,queue_position,priority_score,created_at,allocated_at",
        api,
        session,
        forms);
  }
}
