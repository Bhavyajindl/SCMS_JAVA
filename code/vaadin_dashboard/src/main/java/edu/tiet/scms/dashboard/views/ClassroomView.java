package edu.tiet.scms.dashboard.views;
import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.*;
import edu.tiet.scms.dashboard.auth.UserSession;
import edu.tiet.scms.dashboard.components.Ui;
import edu.tiet.scms.dashboard.layout.MainLayout;
import edu.tiet.scms.dashboard.service.ScmsService;
import jakarta.annotation.security.RolesAllowed;
import java.util.*;

@Route(value="admin/classroom",layout=MainLayout.class)
@PageTitle("Classroom demo | SCMS")
@RolesAllowed("ADMIN")
public class ClassroomView extends VerticalLayout {
  private final ScmsService api;
  private final Div result=new Div();
  private final Paragraph message=new Paragraph();
  private long slot;
  private com.vaadin.flow.component.button.Button allocate;
  public ClassroomView(ScmsService api,UserSession session) {
    this.api=api;setWidthFull();addClassName("records-page");setPadding(true);
    H1 title=new H1("Classroom demo");
    Paragraph intro=new Paragraph("One racket. Two students. A fair allocation.");
    Paragraph note=new Paragraph("Uses synthetic classroom records in the separate Java database. The scores and saved results come from the Java backend.");
    IntegerField a=new IntegerField("Student A: sessions in the last 7 days");
    IntegerField b=new IntegerField("Student B: sessions in the last 7 days");
    for(var field:List.of(a,b)) { field.setMin(0);field.setMax(10);field.setStepButtonsVisible(true);field.setWidth("min(100%, 23rem)"); }
    a.setValue(0);b.setValue(4);
    var create=Ui.primary("Create two booking requests",()->{
      if(a.isInvalid()||b.isInvalid()||a.getValue()==null||b.getValue()==null) { message.setText("Choose 0 to 10 sessions for each student.");return; }
      try {
        JsonNode state=api.write("POST","/admin/classroom/scenario",Map.of("sessions_a",a.getValue(),"sessions_b",b.getValue()));
        slot=state.path("slot_id").asLong();render(state);allocate.setEnabled(true);
        message.setText("Both requests are saved. Run allocation to calculate scores and select the student.");
      } catch(Exception e) { message.setText(e.getMessage()); }
    });
    allocate=Ui.primary("Run Java allocation",()->{
      if(slot==0)return;
      try {
        api.write("POST","/admin/slots/"+slot+"/run-allocation",null);
        render(api.get("/admin/classroom/"+slot));allocate.setEnabled(false);
        message.setText("Allocation saved. Lower score means higher priority. Equal scores use request time.");
      } catch(Exception e) { message.setText(e.getMessage()); }
    });
    allocate.setEnabled(false);
    HorizontalLayout controls=new HorizontalLayout(a,b);controls.setWidthFull();controls.setWrap(true);
    HorizontalLayout actions=new HorizontalLayout(create,allocate);actions.setWrap(true);
    result.setWidthFull();
    var links=new HorizontalLayout(new Anchor("/admin/inventory","Open inventory"),new Anchor("/admin/slots","Open slots"),new Anchor("/admin/bookings","Open bookings"));
    links.setWrap(true);
    add(title,intro,note,Ui.card(new H2("Set up the comparison"),controls,actions,message),result,
      Ui.card(new H2("How Java decides"),new Paragraph("Score = normalized seven-day usage + recency penalty. The example history is four days old, so the default two-day recency penalty is zero."),
        new Paragraph("WorkflowRules checks each request. UsageRecencyPolicy ranks the students. BookingService saves CONFIRMED and WAITLISTED. JDBC writes the result to SQLite.")),links);
  }
  private void render(JsonNode state) {
    result.removeAll();
    Grid<JsonNode> grid=new Grid<>();
    grid.addColumn(r->r.path("student").path("name").asText()).setHeader("Student").setAutoWidth(true);
    grid.addColumn(r->r.path("sessions_last_7_days").asInt()).setHeader("Recent sessions").setAutoWidth(true);
    grid.addColumn(r->r.path("priority_score").isNull()?"Not calculated":String.format(Locale.ROOT,"%.2f",r.path("priority_score").asDouble())).setHeader("Priority score").setAutoWidth(true);
    grid.addComponentColumn(r->Ui.badge(r.path("status").asText())).setHeader("Booking status").setAutoWidth(true);
    grid.addColumn(r->r.path("queue_position").isNull()?"—":r.path("queue_position").asText()).setHeader("Waiting position");
    List<JsonNode> rows=new ArrayList<>();state.path("bookings").forEach(rows::add);grid.setItems(rows);
    grid.setAllRowsVisible(true);
    result.add(Ui.card(new H2(state.path("equipment").asText()),new Paragraph("Capacity: 1 | Scenario slot: "+state.path("slot_id").asText()),grid));
  }
}
