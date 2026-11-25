package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
public class NotFoundView extends Div implements HasErrorParameter<NotFoundException> {
  public int setErrorParameter(
      BeforeEnterEvent event, ErrorParameter<NotFoundException> parameter) {
    add(
        new H1("Page not found"),
        new Paragraph("The page you requested does not exist."),
        new Anchor("/", "Back to SCMS"));
    return 404;
  }
}
