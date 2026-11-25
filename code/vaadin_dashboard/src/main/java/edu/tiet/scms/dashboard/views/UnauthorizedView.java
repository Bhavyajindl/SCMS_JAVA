package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.layout.MainLayout;
import jakarta.annotation.security.PermitAll;

@Route(value = "unauthorized", layout = MainLayout.class)
@PageTitle("Access denied | SCMS")
@PermitAll
public class UnauthorizedView extends Div {
  public UnauthorizedView() {
    add(
        new H1("Access denied"),
        new Paragraph("Your account does not have permission to open this page."),
        new Anchor("/", "Back to SCMS"));
  }
}
