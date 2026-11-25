package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.RecordsView;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.*;
import jakarta.annotation.security.*;

@Route(value = "admin/slots", layout = MainLayout.class)
@PageTitle("Slots | SCMS")
@RolesAllowed("ADMIN")
public class SlotsView extends RecordsView {
  public SlotsView(ScmsService api, UserSession session, FormCatalog forms) {
    super(
        "slots",
        "Slots",
        "Manage booking slots and batch allocation",
        "/admin/slots",
        "equipment_id,date,start_time,end_time,capacity,available_count,status,booking_cutoff_at,allocation_run_at",
        api,
        session,
        forms);
  }
}
