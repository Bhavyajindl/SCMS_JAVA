package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import jakarta.annotation.security.PermitAll;

@Route("")
@PermitAll
public class HomeView extends Div implements BeforeEnterObserver {
  private final UserSession session;

  public HomeView(UserSession session) {
    this.session = session;
  }

  public void beforeEnter(BeforeEnterEvent e) {
    e.forwardTo(session.isAdmin() ? "admin/dashboard" : "admin/inventory");
  }
}
