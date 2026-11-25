package edu.tiet.scms.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.textfield.TextField;
import edu.tiet.scms.dashboard.auth.*;
import edu.tiet.scms.dashboard.components.*;
import edu.tiet.scms.dashboard.dto.*;
import edu.tiet.scms.dashboard.service.*;
import edu.tiet.scms.dashboard.views.*;
import jakarta.annotation.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.*;

class DashboardTests {
  private final ObjectMapper mapper = new ObjectMapper();

  @AfterEach
  void cleanup() {
    UI.setCurrent(null);
  }

  @Test
  void apiPaginationRetainsAllRowsAndCounts() throws Exception {
    var page =
        ApiPage.from(
            mapper.readTree(
                "{\"data\":[{\"id\":1},{\"id\":2}],\"pagination\":{\"page\":2,\"totalPages\":4,\"total\":72}}"));
    assertEquals(2, page.items().size());
    assertEquals(2, page.page());
    assertEquals(4, page.totalPages());
    assertEquals(72, page.total());
  }

  @Test
  void loginDelegatesToApiAndDoesNotRetainPassword() throws Exception {
    ApiClient api = mock(ApiClient.class);
    when(api.send(eq("POST"), eq("/auth/staff/login"), any(), isNull()))
        .thenReturn(
            mapper.readTree(
                "{\"data\":{\"user\":{\"id\":1,\"name\":\"Test"
                    + " Admin\",\"email\":\"admin@example.com\",\"role\":\"ADMIN\"},\"token\":\"secret-test-token\"}}"));
    var auth =
        new BackendAuthenticationProvider(api)
            .authenticate(
                new UsernamePasswordAuthenticationToken(
                    " admin@example.com ", "sample-test-password"));
    assertNull(auth.getCredentials());
    assertTrue(auth.isAuthenticated());
    assertEquals("ADMIN", ((PortalUser) auth.getPrincipal()).role());
    assertFalse(auth.getPrincipal().toString().contains("secret-test-token"));
    verify(api)
        .send(
            eq("POST"),
            eq("/auth/staff/login"),
            eq(Map.of("email", "admin@example.com", "password", "sample-test-password")),
            isNull());
  }

  @Test
  void invalidOrUnavailableLoginDoesNotAuthenticate() {
    ApiClient api = mock(ApiClient.class);
    when(api.send(anyString(), anyString(), any(), isNull()))
        .thenThrow(new ApiException(401, "Invalid credentials"));
    var provider = new BackendAuthenticationProvider(api);
    assertThrows(
        BadCredentialsException.class,
        () -> provider.authenticate(new UsernamePasswordAuthenticationToken("x", "x")));
    doThrow(new ApiException(503, "Unavailable"))
        .when(api)
        .send(anyString(), anyString(), any(), isNull());
    assertThrows(
        AuthenticationServiceException.class,
        () -> provider.authenticate(new UsernamePasswordAuthenticationToken("x", "x")));
  }

  @Test
  void staffCannotCallAdminWritesThroughService() {
    ApiClient client = mock(ApiClient.class);
    UserSession session = mock(UserSession.class);
    when(session.user())
        .thenReturn(new PortalUser(1, "Staff", "staff@example.com", "STAFF", "test"));
    var service = new ScmsService(client, session);
    assertThrows(ApiException.class, () -> service.write("DELETE", "/admin/bookings/1", null));
    assertThrows(
        ApiException.class,
        () -> service.write("PUT", "/inventory/1", Map.of("status", "RETIRED")));
    verifyNoInteractions(client);
  }

  @Test
  void allAdminViewsHaveRoleAnnotationsAndInventoryAllowsStaff() {
    for (Class<?> view :
        List.of(
            DashboardView.class,
            SlotsView.class,
            BookingsView.class,
            TransactionsView.class,
            DefaultersView.class,
            StaffView.class,
            AnalyticsView.class,
            FairnessView.class)) {
      assertArrayEquals(
          new String[] {"ADMIN"}, view.getAnnotation(RolesAllowed.class).value(), view.getName());
    }
    assertNotNull(InventoryView.class.getAnnotation(PermitAll.class));
    assertNotNull(OperationsView.class.getAnnotation(PermitAll.class));
  }

  @Test
  void fieldsAreAllowlistedAndSlotNumbersAndDatesAreTyped() {
    FormCatalog forms = new FormCatalog();
    var data =
        forms.body(
            "slots",
            false,
            Map.of(
                "equipment_id",
                "3",
                "date",
                "2026-12-01",
                "start_time",
                "09:00",
                "end_time",
                "10:00",
                "capacity",
                "2",
                "booking_cutoff_at",
                "2026-12-01T08:00",
                "created_by",
                "999"));
    assertEquals(3L, data.get("equipment_id"));
    assertEquals(2L, data.get("capacity"));
    assertEquals("2026-12-01T08:00Z", data.get("booking_cutoff_at"));
    assertFalse(data.containsKey("created_by"));
    assertThrows(ApiException.class, () -> forms.body("inventory", false, Map.of("name", "Bat")));
  }

  @Test
  void svgEscapesUntrustedLabelsAndHasAccessibleDataTable() throws Exception {
    UI.setCurrent(new UI());
    var chart =
        new DataChart(
            "Usage",
            mapper.readTree("[{\"date\":\"<script>alert(1)</script>\",\"issues\":3}]"),
            "date",
            List.of("issues"),
            true);
    String html =
        chart
            .getChildren()
            .filter(c -> c instanceof Html)
            .map(c -> ((Html) c).getInnerHtml())
            .findFirst()
            .orElseThrow();
    assertFalse(html.contains("<script>"));
    assertTrue(html.contains("&lt;script&gt;"));
    assertTrue(html.contains("polyline"));
    assertTrue(
        chart.getChildren().anyMatch(c -> c instanceof com.vaadin.flow.component.details.Details));
  }

  private static List<Component> descendants(Component c) {
    List<Component> all = new ArrayList<>();
    all.add(c);
    c.getChildren().forEach(child -> all.addAll(descendants(child)));
    return all;
  }

  @Test
  void javaDialogButtonSubmitsTypedDataAndValidationPreventsEmptyWrites() throws Exception {
    UI.setCurrent(new UI());
    DashboardApi api = mock(DashboardApi.class);
    UserSession user = mock(UserSession.class);
    RecordEditor editor =
        new RecordEditor("inventory", null, api, new FormCatalog(), user, () -> {});
    Button create =
        editor
            .getFooter()
            .getElement()
            .getChildren()
            .map(el -> el.getComponent())
            .flatMap(Optional::stream)
            .filter(c -> c instanceof Button && ((Button) c).getText().equals("Create"))
            .map(c -> (Button) c)
            .findFirst()
            .orElseThrow();
    create.click();
    verifyNoInteractions(api);
    for (Component c : descendants(editor))
      if (c instanceof TextField field) {
        String key = c.getElement().getAttribute("data-field");
        if (key != null)
          field.setValue(
              switch (key) {
                case "name" -> "Test Bat";
                case "category" -> "Cricket";
                case "qr_code" -> "TEST-EQ";
                default -> "";
              });
      }
    create.click();
    verify(api)
        .write(
            eq("POST"),
            eq("/inventory"),
            eq(
                Map.of(
                    "name",
                    "Test Bat",
                    "category",
                    "Cricket",
                    "qr_code",
                    "TEST-EQ",
                    "location",
                    "",
                    "notes",
                    "")));
  }
}
