package edu.tiet.scms.dashboard.layout;



import com.vaadin.flow.component.applayout.*;

import com.vaadin.flow.component.html.*;

import com.vaadin.flow.component.icon.VaadinIcon;

import com.vaadin.flow.component.orderedlayout.*;

import com.vaadin.flow.component.sidenav.*;

import edu.tiet.scms.dashboard.auth.UserSession;

import edu.tiet.scms.dashboard.components.Ui;

import jakarta.annotation.security.PermitAll;



@PermitAll

public class MainLayout extends AppLayout {

  public MainLayout(UserSession session) {

    setPrimarySection(Section.DRAWER);

    Div brand = new Div(new Span("S"), new H2("SCMS"));

    brand.addClassName("brand");

    addToDrawer(brand);

    SideNav nav = new SideNav();

    nav.getElement().setAttribute("aria-label", "Main navigation");

    if (session.isAdmin()) {

      item(nav, "Classroom demo", "admin/classroom", VaadinIcon.PLAY);
      item(nav, "Dashboard", "admin/dashboard", VaadinIcon.DASHBOARD);

    }

    item(nav, "Inventory", "admin/inventory", VaadinIcon.PACKAGE);

    if (session.isAdmin()) {

      item(nav, "Slots", "admin/slots", VaadinIcon.CALENDAR);

      item(nav, "Bookings", "admin/bookings", VaadinIcon.BOOK);

      item(nav, "Transactions", "admin/transactions", VaadinIcon.EXCHANGE);

      item(nav, "Defaulters", "admin/defaulters", VaadinIcon.WARNING);

      item(nav, "Analytics", "admin/analytics", VaadinIcon.CHART);

      item(nav, "Fairness", "admin/fairness", VaadinIcon.SLIDERS);

      item(nav, "Staff", "admin/staff", VaadinIcon.USERS);

    }

    item(nav, "Issue & Return", "staff/queue", VaadinIcon.CLIPBOARD_CHECK);

    item(nav, "QR Validation", "staff/qr", VaadinIcon.QRCODE);

    nav.addClassName("main-nav");

    addToDrawer(nav);

    HorizontalLayout bar =

        new HorizontalLayout(

            new DrawerToggle(),

            new Span(""),

            new Span(session.user().name()),

            Ui.badge(session.user().role()),

            Ui.button("Sign out", session::logout));

    bar.addClassName("topbar");

    bar.setWidthFull();

    bar.setAlignItems(FlexComponent.Alignment.CENTER);

    bar.expand(bar.getComponentAt(1));

    addToNavbar(bar);

  }



  private void item(SideNav nav, String label, String path, VaadinIcon icon) {

    nav.addItem(new SideNavItem(label, path, icon.create()));

  }

}

