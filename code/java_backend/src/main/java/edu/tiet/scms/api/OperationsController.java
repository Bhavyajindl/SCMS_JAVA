package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import edu.tiet.scms.WorkflowRules;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class OperationsController {
  private final JdbcStore db;private final AuthService auth;private final BookingService bookings;
  private final WorkflowRules rules=new WorkflowRules();
  public OperationsController(JdbcStore db,AuthService auth,BookingService bookings) { this.db=db;this.auth=auth;this.bookings=bookings; }
  public Map<String,Object> transaction(Map<String,Object> txn) {
    var r=new LinkedHashMap<>(txn);r.put("equipment_name",bookings.row("equipment",((Number)txn.get("equipment_id")).longValue()).get("name"));
    r.put("student_name",bookings.row("students",((Number)txn.get("student_id")).longValue()).get("name"));return r;
  }
  @PostMapping("/transactions/issue") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Object issue(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    var user=auth.authenticate(token);user.staff();long bookingId=number(b,"booking_id"),equipmentId=number(b,"equipment_id");
    var booking=db.one("SELECT * FROM bookings WHERE id=?",bookingId);var eq=db.one("SELECT * FROM equipment WHERE id=?",equipmentId);
    rules.validate("issue",Map.of("booking_exists",""+(booking!=null),"booking_status",booking==null?"":str(booking,"status"),"equipment_exists",""+(eq!=null),"equipment_status",eq==null?"":str(eq,"status"),"matches",""+(booking!=null&&((Number)booking.get("equipment_id")).longValue()==equipmentId)));
    require(db.count("SELECT count(*) FROM transactions WHERE booking_id=?",bookingId)==0,409,"ALREADY_ISSUED","This booking has already been issued.");
    var slot=bookings.row("slots",((Number)booking.get("slot_id")).longValue());
    String due=LocalDateTime.of(LocalDate.parse(str(slot,"date")),LocalTime.parse(str(slot,"end_time"))).toInstant(ZoneOffset.UTC).toString();
    long txn=db.insert("INSERT INTO transactions(booking_id,student_id,equipment_id,issued_at,due_at,status,issued_by) VALUES(?,?,?,?,?,'ISSUED',?)",bookingId,booking.get("student_id"),equipmentId,now(),due,user.id());
    db.update("UPDATE equipment SET status='ISSUED' WHERE id=?",equipmentId);return ok(transaction(bookings.row("transactions",txn)));
  }
  @PostMapping("/transactions/{id}/return") @Transactional
  public Object returnItem(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> b) {
    var user=auth.authenticate(token);user.staff();var txn=db.one("SELECT * FROM transactions WHERE id=?",id);
    choice(b,"condition_on_return","GOOD","FAIR","DAMAGED");String condition=text(b,"condition_on_return"),report=Objects.toString(b.get("damage_report"),"").trim();
    rules.validate("return",Map.of("transaction_exists",""+(txn!=null),"status",txn==null?"":str(txn,"status"),"condition",condition,"has_report",""+!report.isBlank()));
    boolean damaged=condition.equals("DAMAGED");
    db.update("UPDATE transactions SET status=?,returned_at=?,condition_on_return=?,damage_report=?,returned_to=? WHERE id=?",damaged?"RETURNED_DAMAGED":"RETURNED",now(),condition,report.isBlank()?null:report,user.id(),id);
    db.update("UPDATE equipment SET status=?,condition=? WHERE id=?",damaged?"MAINTENANCE":"AVAILABLE",condition,txn.get("equipment_id"));
    if(txn.get("booking_id")!=null)db.update("UPDATE bookings SET status='COMPLETED' WHERE id=?",txn.get("booking_id"));
    db.update("UPDATE defaulters SET status='RESOLVED',resolved_at=?,resolved_by=? WHERE transaction_id=? AND status<>'RESOLVED'",now(),user.id(),id);
    long student=((Number)txn.get("student_id")).longValue();
    bookings.notifyStudent(student,"RETURN_CONFIRMED","Equipment return recorded.");
    if(damaged)bookings.notifyStudent(student,"DAMAGE_LOGGED","Damage report: "+report);
    var stats=AuthController.stats(db,student);
    db.update("INSERT INTO usage_statistics(student_id,sessions_last_7_days,total_sessions,last_session_at,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(student_id) DO UPDATE SET sessions_last_7_days=excluded.sessions_last_7_days,total_sessions=excluded.total_sessions,last_session_at=excluded.last_session_at,updated_at=excluded.updated_at",student,stats.get("sessions_last_7_days"),stats.get("total_sessions"),stats.get("last_session_at"),now());
    return ok(transaction(bookings.row("transactions",id)));
  }
  @GetMapping("/transactions")
  public Object history(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    var user=auth.authenticate(token);user.student();return page(filter(db.rows("SELECT * FROM transactions WHERE student_id=? ORDER BY issued_at DESC,id DESC",user.id()),q,"status").stream().map(this::transaction).toList(),q);
  }
  @GetMapping({"/transactions/{id}","/transactions/{id}/damage"})
  public Object txn(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,jakarta.servlet.http.HttpServletRequest request) {
    var user=auth.authenticate(token);user.student();var txn=bookings.row("transactions",id);
    require(((Number)txn.get("student_id")).longValue()==user.id(),404,"NOT_FOUND","Transaction not found.");
    if(request.getRequestURI().endsWith("/damage"))require(str(txn,"condition_on_return").equals("DAMAGED"),404,"NOT_FOUND","No damage report.");
    return ok(transaction(txn));
  }
  @PostMapping("/qr/validate/equipment")
  public Object equipmentQr(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    auth.authenticate(token).staff();var eq=db.required("SELECT * FROM equipment WHERE qr_code=?",text(b,"qr_code"));
    require(!str(eq,"status").equals("RETIRED"),400,"EQUIPMENT_RETIRED","Equipment is retired.");return ok(eq);
  }
  @PostMapping("/qr/validate/student")
  public Object studentQr(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    auth.authenticate(token).staff();var s=db.required("SELECT * FROM students WHERE qr_identifier=?",text(b,"token"));
    var booking=db.one("SELECT * FROM bookings WHERE student_id=? AND status='CONFIRMED' AND NOT EXISTS(SELECT 1 FROM transactions t WHERE t.booking_id=bookings.id) ORDER BY created_at DESC,id DESC LIMIT 1",id(s));
    var txn=db.one("SELECT * FROM transactions WHERE student_id=? AND status IN ('ISSUED','OVERDUE') ORDER BY issued_at DESC,id DESC LIMIT 1",id(s));
    return ok(map("student",AuthService.publicAccount(s),"current_booking",booking==null?null:bookings.enrich(booking,false),"open_transaction",txn==null?null:transaction(txn)));
  }
  @GetMapping("/staff/queue")
  public Object queue(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).staff();
    var confirmed=db.rows("SELECT b.* FROM bookings b JOIN slots s ON s.id=b.slot_id WHERE b.status='CONFIRMED' AND s.date=? AND NOT EXISTS(SELECT 1 FROM transactions t WHERE t.booking_id=b.id) ORDER BY b.created_at,b.id",q.getOrDefault("for_date",LocalDate.now(ZoneOffset.UTC).toString())).stream().map(r->{var b=bookings.enrich(r,true);b.put("booking_id",r.get("id"));return b;}).toList();
    var open=db.rows("SELECT * FROM transactions WHERE status IN ('ISSUED','OVERDUE') ORDER BY due_at").stream().map(r->{var t=transaction(r);t.put("transaction_id",r.get("id"));t.put("student",db.required("SELECT id,name FROM students WHERE id=?",r.get("student_id")));t.put("equipment",db.required("SELECT id,name FROM equipment WHERE id=?",r.get("equipment_id")));return t;}).toList();
    return ok(map("confirmed_bookings_today",confirmed,"open_transactions",open));
  }
  @GetMapping("/notifications")
  public Object notifications(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    var user=auth.authenticate(token);user.student();var rows=db.rows("SELECT * FROM notifications WHERE student_id=? ORDER BY created_at DESC,id DESC",user.id());
    rows.forEach(r->r.put("is_read",((Number)r.get("is_read")).intValue()!=0));return page(rows,q);
  }
  @GetMapping("/notifications/unread-count")
  public Object unread(@RequestHeader(value="Authorization",required=false) String token) {
    var user=auth.authenticate(token);user.student();return ok(map("unread_count",db.count("SELECT count(*) FROM notifications WHERE student_id=? AND is_read=0",user.id())));
  }
  @PostMapping("/notifications/mark-all-read")
  public Object readAll(@RequestHeader(value="Authorization",required=false) String token) {
    var user=auth.authenticate(token);user.student();return ok(map("updated",db.update("UPDATE notifications SET is_read=1 WHERE student_id=? AND is_read=0",user.id())));
  }
  @PutMapping("/notifications/{id}/read")
  public Object read(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    var user=auth.authenticate(token);user.student();
    require(db.update("UPDATE notifications SET is_read=1 WHERE id=? AND student_id=?",id,user.id())==1,404,"NOT_FOUND","Notification not found.");
    var r=db.required("SELECT * FROM notifications WHERE id=?",id);r.put("is_read",true);return ok(r);
  }
}
