package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
  private final JdbcStore db;private final AuthService auth;private final BookingService bookings;private final OperationsController operations;
  public AdminController(JdbcStore db,AuthService auth,BookingService bookings,OperationsController operations) { this.db=db;this.auth=auth;this.bookings=bookings;this.operations=operations; }
  @GetMapping("/bookings")
  public Object bookings(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).admin();return page(filter(db.rows("SELECT * FROM bookings ORDER BY created_at DESC,id DESC"),q,"status","student_id","slot_id").stream().map(r->bookings.enrich(r,true)).toList(),q);
  }
  @GetMapping("/bookings/{id}")
  public Object booking(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { auth.authenticate(token).admin();return ok(bookings.enrich(bookings.row("bookings",id),true)); }
  @DeleteMapping("/bookings/{id}")
  public Object cancel(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { var user=auth.authenticate(token);user.admin();return ok(bookings.cancel(id,user)); }
  @GetMapping("/transactions")
  public Object transactions(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).admin();return page(filter(db.rows("SELECT * FROM transactions ORDER BY issued_at DESC,id DESC"),q,"status","student_id").stream().map(operations::transaction).toList(),q);
  }
  @GetMapping("/transactions/{id}")
  public Object transaction(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { auth.authenticate(token).admin();return ok(operations.transaction(bookings.row("transactions",id))); }
  @GetMapping("/staff")
  public Object staff(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) { auth.authenticate(token).admin();return page(db.rows("SELECT id,name,email,role,status,created_at FROM users ORDER BY id"),q); }
  @PostMapping("/staff") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Object addStaff(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> input) {
    var admin=auth.authenticate(token);admin.admin();var b=new LinkedHashMap<>(input);b.putIfAbsent("role","STAFF");choice(b,"role","ADMIN","STAFF");
    require(text(b,"email").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),422,"VALIDATION_ERROR","Invalid email.");
    long id=db.insert("INSERT INTO users(name,email,password_hash,role,status,created_at,created_by) VALUES(?,?,?,?,'ACTIVE',?,?)",text(b,"name"),text(b,"email"),auth.hash(Objects.toString(b.get("password"),"")),text(b,"role"),now(),admin.id());
    return ok(AuthService.publicAccount(bookings.row("users",id)));
  }
  @PutMapping("/staff/{id}") @Transactional
  public Object editStaff(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> b) {
    var admin=auth.authenticate(token);admin.admin();bookings.row("users",id);
    if(b.containsKey("role")) { choice(b,"role","ADMIN","STAFF");require(id!=admin.id()||text(b,"role").equals("ADMIN"),400,"CANNOT_SELF_DEMOTE","Cannot remove your own administrator role."); }
    for(String key:List.of("name","email"))if(b.containsKey(key))text(b,key);
    EquipmentController.fields(db,"users",id,b,"name","email","role");return ok(AuthService.publicAccount(bookings.row("users",id)));
  }
  @PatchMapping("/staff/{id}/deactivate")
  public Object deactivate(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    var admin=auth.authenticate(token);admin.admin();require(id!=admin.id(),400,"CANNOT_SELF_DEACTIVATE","Cannot deactivate yourself.");bookings.row("users",id);
    db.update("UPDATE users SET status='INACTIVE' WHERE id=?",id);return ok(AuthService.publicAccount(bookings.row("users",id)));
  }
  @GetMapping("/fairness-config")
  public Object config(@RequestHeader(value="Authorization",required=false) String token) { auth.authenticate(token).admin();return ok(bookings.row("fairness_config",1)); }
  @PutMapping("/fairness-config") @Transactional
  public Object updateConfig(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    var admin=auth.authenticate(token);admin.admin();
    for(String k:List.of("daily_usage_cap","recency_threshold_days"))if(b.containsKey(k))require(number(b,k)>=0&&number(b,k)<=365,422,"VALIDATION_ERROR","Invalid "+k);
    if(b.containsKey("recency_weight")) { double v=Double.parseDouble(text(b,"recency_weight"));require(Double.isFinite(v)&&v>=0&&v<=1,422,"VALIDATION_ERROR","Weight must be 0 to 1."); }
    EquipmentController.fields(db,"fairness_config",1,b,"daily_usage_cap","recency_threshold_days","recency_weight");
    db.update("UPDATE fairness_config SET updated_at=?,updated_by=? WHERE id=1",now(),admin.id());return ok(bookings.row("fairness_config",1));
  }
  @GetMapping("/defaulters")
  public Object defaulters(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).admin();return page(filter(db.rows("SELECT d.*,s.name AS student_name FROM defaulters d JOIN students s ON s.id=d.student_id ORDER BY detected_at DESC"),q,"status"),q);
  }
  @GetMapping("/defaulters/{id}")
  public Object defaulter(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    auth.authenticate(token).admin();var r=bookings.row("defaulters",id);
    r.put("student",AuthService.publicAccount(bookings.row("students",((Number)r.get("student_id")).longValue())));return ok(r);
  }
  @PutMapping("/defaulters/{id}/resolve")
  public Object resolve(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    var admin=auth.authenticate(token);admin.admin();var r=bookings.row("defaulters",id);
    require(!str(r,"status").equals("RESOLVED"),400,"VALIDATION_ERROR","Record is already resolved.");
    db.update("UPDATE defaulters SET status='RESOLVED',resolved_at=?,resolved_by=? WHERE id=?",now(),admin.id(),id);return ok(bookings.row("defaulters",id));
  }
  @GetMapping("/analytics/{kind}")
  public Object analytics(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String kind,@RequestParam Map<String,String> q) {
    auth.authenticate(token).admin();
    if(kind.equals("overview"))return ok(map("total_equipment",db.count("SELECT count(*) FROM equipment"),"available_equipment",db.count("SELECT count(*) FROM equipment WHERE status='AVAILABLE'"),"total_bookings",db.count("SELECT count(*) FROM bookings"),"confirmed_bookings",db.count("SELECT count(*) FROM bookings WHERE status='CONFIRMED'"),"active_transactions",db.count("SELECT count(*) FROM transactions WHERE status IN ('ISSUED','OVERDUE')"),"overdue_transactions",db.count("SELECT count(*) FROM transactions WHERE status='OVERDUE'"),"active_defaulters",db.count("SELECT count(*) FROM defaulters WHERE status='ACTIVE'")));
    if(kind.equals("bookings")) {
      var r=map("total",db.count("SELECT count(*) FROM bookings"));
      for(String status:List.of("REQUESTED","CONFIRMED","WAITLISTED","CANCELLED","NO_SHOW","COMPLETED"))r.put(status.toLowerCase(Locale.ROOT),db.count("SELECT count(*) FROM bookings WHERE status=?",status));return ok(r);
    }
    if(kind.equals("equipment"))return ok(db.rows("SELECT e.id AS equipment_id,e.name,e.category,(SELECT count(*) FROM bookings b WHERE b.equipment_id=e.id) AS total_bookings,(SELECT count(*) FROM transactions t WHERE t.equipment_id=e.id) AS total_issues FROM equipment e").stream().map(r->{r.put("utilization_rate",((Number)r.get("total_bookings")).doubleValue()==0?0:((Number)r.get("total_issues")).doubleValue()/((Number)r.get("total_bookings")).doubleValue()*100);return r;}).toList());
    if(kind.equals("usage-trends")) {
      List<Map<String,Object>> rows=new ArrayList<>();
      for(int i=integer(q,"days",7,1,90)-1;i>=0;i--) { String date=LocalDate.now(ZoneOffset.UTC).minusDays(i).toString();rows.add(map("date",date,"bookings",db.count("SELECT count(*) FROM bookings WHERE date(created_at)=?",date),"issues",db.count("SELECT count(*) FROM transactions WHERE date(issued_at)=?",date),"returns",db.count("SELECT count(*) FROM transactions WHERE date(returned_at)=?",date))); }return ok(rows);
    }
    if(kind.equals("peak-hours")) {
      List<Map<String,Object>> rows=new ArrayList<>();for(int h=0;h<24;h++)rows.add(map("hour",h,"booking_count",db.count("SELECT count(*) FROM bookings WHERE CAST(strftime('%H',created_at) AS INTEGER)=?",h)));
      rows.sort(Comparator.comparingLong(r->-((Number)r.get("booking_count")).longValue()));return ok(rows);
    }
    if(kind.equals("damage-stats")) {
      long returned=db.count("SELECT count(*) FROM transactions WHERE returned_at IS NOT NULL"),damaged=db.count("SELECT count(*) FROM transactions WHERE status='RETURNED_DAMAGED'");
      return ok(map("total_returns",returned,"damaged_returns",damaged,"damage_rate",damaged*100.0/Math.max(1,returned),"recent_damage_reports",db.rows("SELECT id AS transaction_id,equipment_id,damage_report,returned_at FROM transactions WHERE status='RETURNED_DAMAGED' ORDER BY returned_at DESC LIMIT 10")));
    }
    throw new ApiProblem(404,"NOT_FOUND","Analytics endpoint not found.");
  }
}
