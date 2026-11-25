package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Explicit opt-in classroom data; never enabled in a normal server by default. */
@RestController @RequestMapping("/api/admin/classroom")
@ConditionalOnProperty(name="scms.classroom.enabled",havingValue="true")
public class ClassroomController {
  private final JdbcStore db;private final AuthService auth;private final BookingService bookings;
  public ClassroomController(JdbcStore db,AuthService auth,BookingService bookings) { this.db=db;this.auth=auth;this.bookings=bookings; }
  @PostMapping("/scenario") @Transactional
  public Object create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> body) {
    var admin=auth.authenticate(token);admin.admin();
    int a=(int)number(body,"sessions_a"),b=(int)number(body,"sessions_b");
    require(a>=0&&a<=10&&b>=0&&b<=10,422,"VALIDATION_ERROR","Choose 0 to 10 sessions.");
    String key=UUID.randomUUID().toString(),stamp=now();
    long equipment=db.insert("INSERT INTO equipment(name,category,qr_code,location,notes,added_at,added_by) VALUES('Classroom Badminton Racket','Badminton',?,'Court A','CLASSROOM_DEMO',?,?)","CLASS-"+key,stamp,admin.id());
    LocalDate date=LocalDate.now(ZoneOffset.UTC).plusDays(1);
    long slot=db.insert("INSERT INTO slots(equipment_id,date,start_time,end_time,capacity,available_count,booking_cutoff_at,status,created_by,created_at) VALUES(?,?,'14:00:00','15:00:00',1,1,?,'OPEN',?,?)",equipment,date.toString(),Instant.now().plusSeconds(600).toString(),admin.id(),stamp);
    // Four days ago removes the recency penalty under the default two-day threshold.
    db.update("UPDATE fairness_config SET daily_usage_cap=2,recency_threshold_days=2,recency_weight=0.5 WHERE id=1");
    for(int i=0;i<2;i++) {
      int sessions=i==0?a:b;String name=i==0?"Student A":"Student B",email="class-"+key+"-"+i+"@demo.invalid";
      long student=db.insert("INSERT INTO students(student_id,name,email,department,password_hash,qr_identifier,status,created_at) VALUES(?,?,?,'Classroom demo',?,?,'ACTIVE',?)","CLASS-"+key+"-"+i,name,email,auth.hash(UUID.randomUUID().toString()),UUID.randomUUID().toString(),stamp);
      String history=Instant.now().minus(Duration.ofDays(4)).toString();
      for(int n=0;n<sessions;n++)db.update("INSERT INTO transactions(student_id,equipment_id,issued_at,due_at,returned_at,status,condition_on_return,issued_by,returned_to) VALUES(?,?,?,?,?,'RETURNED','GOOD',?,?)",student,equipment,history,history,history,admin.id(),admin.id());
      bookings.create(new AuthService.Identity(student,"STUDENT",bookings.row("students",student)),slot);
    }
    return ok(state(slot));
  }
  @GetMapping("/{slot}")
  public Object state(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long slot) {
    auth.authenticate(token).admin();return ok(state(slot));
  }
  private Map<String,Object> state(long slot) {
    var data=bookings.row("slots",slot);var equipment=bookings.row("equipment",((Number)data.get("equipment_id")).longValue());
    require(str(equipment,"notes").equals("CLASSROOM_DEMO"),404,"NOT_FOUND","Classroom scenario not found.");
    var records=db.rows("SELECT * FROM bookings WHERE slot_id=? ORDER BY created_at,id",slot).stream().map(r->{
      var result=bookings.enrich(r,true);
      result.put("sessions_last_7_days",AuthController.stats(db,((Number)r.get("student_id")).longValue()).get("sessions_last_7_days"));return result;
    }).toList();
    return map("slot_id",slot,"equipment_id",equipment.get("id"),"equipment",equipment.get("name"),"slot",data,"bookings",records);
  }
}
