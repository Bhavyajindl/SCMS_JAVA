package edu.tiet.scms.dashboard.views;

import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.login.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Sign in | SCMS")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
  private final LoginForm login = new LoginForm();
  private final Paragraph message = new Paragraph();

  public LoginView() {
    setSizeFull();
    addClassName("login-page");
    setAlignItems(Alignment.CENTER);
    setJustifyContentMode(JustifyContentMode.CENTER);
    Div brand =
        new Div(new Span("S"), new H1("SCMS"), new Paragraph("Sports Complex Management System"));
    brand.addClassName("login-brand");
    LoginI18n i18n = LoginI18n.createDefault();
    i18n.getForm().setTitle("Staff / Admin Sign In");
    i18n.getForm().setUsername("Email address");
    i18n.getErrorMessage().setTitle("Sign-in failed");
    i18n.getErrorMessage()
        .setMessage("Check your email and password, and that your account is active.");
    login.setI18n(i18n);
    login.setAction("login");
    login.setForgotPasswordButtonVisible(false);
    message.addClassName("login-message");
    message.setVisible(false);
    Div card = new Div(message, login);
    card.addClassName("login-card");
    add(brand, card);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    var q = event.getLocation().getQueryParameters().getParameters();
    login.setError(q.containsKey("error"));
    if (q.containsKey("unavailable")) {
      message.setText("The SCMS backend is unavailable. Please try again when it is running.");
      message.setVisible(true);
    }
    if (q.containsKey("expired")) {
      message.setText("Your session expired. Please sign in again.");
      message.setVisible(true);
    }
  }
}
