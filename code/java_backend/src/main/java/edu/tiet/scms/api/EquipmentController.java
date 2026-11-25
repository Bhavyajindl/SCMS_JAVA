package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class EquipmentController {
  private final JdbcStore db;private final AuthService auth;private final BookingService bookings;
  public EquipmentController(JdbcStore db,AuthService auth,BookingService bookings) { this.db=db;this.auth=auth;this.bookings=bookings; }
  @GetMapping("/equipment")
  public Object equipment(@RequestParam Map<String,String> q) { return page(filter(db.rows("SELECT * FROM equipment WHERE status<>'RETIRED' ORDER BY id"),q,"category"),q); }
  @GetMapping("/equipment/{id}")
  public Object detail(@PathVariable long id) {
    var e=bookings.row("equipment",id);require(!str(e,"status").equals("RETIRED"),404,"EQUIPMENT_NOT_FOUND","Equipment not found.");return ok(e);
  }
  @GetMapping("/equipment/{id}/availability")
  public Object availability(@PathVariable long id) {
    detail(id);return ok(map("equipment_id",id,"available_slots",db.rows("SELECT *,id AS slot_id FROM slots WHERE equipment_id=? AND date>=? AND status IN ('OPEN','FULL') ORDER BY date,start_time",id,LocalDate.now(ZoneOffset.UTC).toString())));
  }
  @GetMapping("/slots")
  public Object slots(@RequestParam Map<String,String> q) {
    var rows=db.rows("SELECT s.*,e.name AS equipment_name FROM slots s JOIN equipment e ON e.id=s.equipment_id WHERE s.date>=? AND s.status<>'CLOSED' ORDER BY s.date,s.start_time",q.getOrDefault("from_date",LocalDate.now(ZoneOffset.UTC).toString()));
    return page(filter(rows,q,"equipment_id"),q);
  }
  @GetMapping("/slots/{id}")
  public Object slot(@PathVariable long id) { return ok(bookings.row("slots",id)); }
  @GetMapping("/inventory")
  public Object inventory(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).staff();return page(filter(db.rows("SELECT * FROM equipment ORDER BY id"),q,"category","status"),q);
  }
  @PostMapping("/inventory") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Object add(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    var admin=auth.authenticate(token);admin.admin();
    long id=db.insert("INSERT INTO equipment(name,category,qr_code,location,notes,added_by,added_at) VALUES(?,?,?,?,?,?,?)",text(b,"name"),text(b,"category"),text(b,"qr_code"),b.get("location"),b.get("notes"),admin.id(),now());
    return ok(bookings.row("equipment",id));
  }
  @PutMapping("/inventory/{id}") @Transactional
  public Object edit(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> b) {
    auth.authenticate(token).admin();var old=bookings.row("equipment",id);
    if(b.containsKey("status")) {
      require(!str(old,"status").equals("ISSUED"),400,"CANNOT_RETIRE_ISSUED","Cannot change status of issued equipment.");
      choice(b,"status","AVAILABLE","BOOKED","MAINTENANCE","RETIRED");
    }
    if(b.containsKey("condition"))choice(b,"condition","GOOD","FAIR","DAMAGED");
    for(String key:List.of("name","category"))if(b.containsKey(key))text(b,key);
    fields(db,"equipment",id,b,"name","category","location","notes","status","condition");return ok(bookings.row("equipment",id));
  }
  @PatchMapping("/inventory/{id}/retire") @Transactional
  public Object retire(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { return edit(token,id,map("status","RETIRED")); }
  @GetMapping("/admin/slots")
  public Object adminSlots(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    auth.authenticate(token).admin();return page(db.rows("SELECT s.*,e.name AS equipment_name FROM slots s JOIN equipment e ON e.id=s.equipment_id ORDER BY s.date DESC,s.id"),q);
  }
  @PostMapping("/admin/slots") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Object createSlot(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) {
    var admin=auth.authenticate(token);admin.admin();var equipment=bookings.row("equipment",number(b,"equipment_id"));
    require(!str(equipment,"status").equals("RETIRED"),400,"EQUIPMENT_RETIRED","Equipment is retired.");validateSlot(b);
    long slot=db.insert("INSERT INTO slots(equipment_id,date,start_time,end_time,capacity,available_count,booking_cutoff_at,status,created_by,created_at) VALUES(?,?,?,?,?,?,?,'OPEN',?,?)",number(b,"equipment_id"),text(b,"date"),text(b,"start_time"),text(b,"end_time"),number(b,"capacity"),number(b,"capacity"),instant(b.get("booking_cutoff_at")).toString(),admin.id(),now());return ok(bookings.row("slots",slot));
  }
  @PutMapping("/admin/slots/{id}") @Transactional
  public Object editSlot(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,@RequestBody Map<String,Object> b) {
    auth.authenticate(token).admin();var merged=new LinkedHashMap<>(bookings.row("slots",id));merged.putAll(b);validateSlot(merged);
    long allocated=db.count("SELECT count(*) FROM bookings WHERE slot_id=? AND status IN ('CONFIRMED','COMPLETED')",id);
    require(number(merged,"capacity")>=allocated,400,"VALIDATION_ERROR","Capacity is smaller than allocated places.");
    fields(db,"slots",id,b,"date","start_time","end_time","capacity");
    if(b.containsKey("booking_cutoff_at"))db.update("UPDATE slots SET booking_cutoff_at=? WHERE id=?",instant(b.get("booking_cutoff_at")).toString(),id);
    db.update("UPDATE slots SET available_count=? WHERE id=?",number(merged,"capacity")-allocated,id);return ok(bookings.row("slots",id));
  }
  @PatchMapping("/admin/slots/{id}/deactivate") @Transactional
  public Object closeSlot(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    auth.authenticate(token).admin();bookings.row("slots",id);
    for(var b:db.rows("SELECT * FROM bookings WHERE slot_id=? AND status='REQUESTED'",id))bookings.notifyStudent(((Number)b.get("student_id")).longValue(),"BOOKING_CANCELLED","The slot was closed.");
    int cancelled=db.update("UPDATE bookings SET status='CANCELLED' WHERE slot_id=? AND status='REQUESTED'",id);
    db.update("UPDATE slots SET status='CLOSED' WHERE id=?",id);return ok(map("id",id,"status","CLOSED","cancelled_bookings",cancelled));
  }
  @PostMapping("/admin/slots/{id}/run-allocation")
  public Object allocate(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) { auth.authenticate(token).admin();return ok(bookings.allocate(id)); }
  private void validateSlot(Map<String,Object> b) {
    require(number(b,"capacity")>0&&number(b,"capacity")<=10000,422,"VALIDATION_ERROR","Capacity must be 1 to 10000.");
    var date=LocalDate.parse(text(b,"date"));var start=LocalTime.parse(text(b,"start_time"));var end=LocalTime.parse(text(b,"end_time"));
    require(end.isAfter(start),422,"VALIDATION_ERROR","End time must be after start time.");
    require(instant(b.get("booking_cutoff_at")).isBefore(LocalDateTime.of(date,start).toInstant(ZoneOffset.UTC)),422,"VALIDATION_ERROR","Booking cutoff must be before slot start.");
  }
  static void fields(JdbcStore db,String table,long id,Map<String,Object> b,String... keys) {
    // Callers supply fixed table and column names; request values are always bound.
    for(String key:keys)if(b.containsKey(key))db.update("UPDATE "+table+" SET "+key+"=? WHERE id=?",b.get(key),id);
  }
}
