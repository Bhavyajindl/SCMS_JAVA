package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import edu.tiet.scms.*;
import edu.tiet.scms.api.AuthService.Identity;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses the same Java rules as the existing hybrid SCMS project. */
@Service
public class BookingService {
  private final JdbcStore db;
  private final WorkflowRules workflows=new WorkflowRules();
  private final AllocationPolicy policy=new UsageRecencyPolicy();
  public BookingService(JdbcStore db) { this.db=db; }
  public Map<String,Object> row(String table,long id) {
    require(Set.of("bookings","equipment","slots","students","transactions","users","defaulters","fairness_config").contains(table),500,"INTERNAL","Unknown table.");
    return db.required("SELECT * FROM "+table+" WHERE id=?",id);
  }
  public void notifyStudent(long student,String type,String message) {
    db.update("INSERT INTO notifications(student_id,type,title,message,is_read,created_at) VALUES(?,?,?,?,0,?)",student,type,type.replace('_',' '),message,now());
  }
  public Map<String,Object> enrich(Map<String,Object> b,boolean admin) {
    var r=new LinkedHashMap<>(b);
    var slot=row("slots",((Number)b.get("slot_id")).longValue());
    r.put("slot",slot);r.put("booking_cutoff_at",slot.get("booking_cutoff_at"));
    r.put("equipment",db.required("SELECT id,name FROM equipment WHERE id=?",b.get("equipment_id")));
    if(admin)r.put("student",db.required("SELECT id,name,student_id FROM students WHERE id=?",b.get("student_id")));
    return r;
  }
  @Transactional
  public Map<String,Object> create(Identity student,long slotId) {
    student.student();var slot=db.one("SELECT * FROM slots WHERE id=?",slotId);var cfg=row("fairness_config",1);
    Map<String,String> input=new HashMap<>();
    input.put("student_status",str(student.account(),"status"));
    input.put("defaulter",""+(db.count("SELECT count(*) FROM defaulters WHERE student_id=? AND status IN ('ACTIVE','NOTIFIED')",student.id())>0));
    input.put("slot_exists",""+(slot!=null));input.put("slot_status",slot==null?"":str(slot,"status"));
    input.put("cutoff_reached",""+(slot==null||!Instant.now().isBefore(instant(slot.get("booking_cutoff_at")))));
    input.put("capacity",slot==null?"0":str(slot,"capacity"));
    input.put("duplicate",""+(db.count("SELECT count(*) FROM bookings WHERE student_id=? AND slot_id=?",student.id(),slotId)>0));
    input.put("daily_cap",str(cfg,"daily_usage_cap"));
    input.put("sessions_today",""+db.count("SELECT count(*) FROM transactions WHERE student_id=? AND date(issued_at)=date('now')",student.id()));
    workflows.validate("booking",input);
    long booking=db.insert("INSERT INTO bookings(student_id,equipment_id,slot_id,status,created_at) VALUES(?,?,?,'REQUESTED',?)",student.id(),slot.get("equipment_id"),slotId,now());
    notifyStudent(student.id(),"BOOKING_RECEIVED","Your booking request has been received.");
    return enrich(row("bookings",booking),false);
  }
  @Transactional
  public Map<String,Object> cancel(long bookingId,Identity user) {
    var booking=row("bookings",bookingId);long student=((Number)booking.get("student_id")).longValue();
    workflows.validate("cancel",Map.of("owned",""+(user.role().equals("ADMIN")||(user.role().equals("STUDENT")&&user.id()==student)),"status",str(booking,"status")));
    require(db.count("SELECT count(*) FROM transactions WHERE booking_id=? AND status IN ('ISSUED','OVERDUE')",bookingId)==0,400,"EQUIPMENT_NOT_RETURNED","Return issued equipment before cancelling.");
    db.update("UPDATE bookings SET status='CANCELLED',queue_position=NULL WHERE id=?",bookingId);
    if(str(booking,"status").equals("CONFIRMED")) {
      long slotId=((Number)booking.get("slot_id")).longValue();
      db.update("UPDATE slots SET available_count=min(capacity,available_count+1) WHERE id=?",slotId);
      var next=db.one("SELECT * FROM bookings WHERE slot_id=? AND status='WAITLISTED' ORDER BY queue_position,created_at,id LIMIT 1",slotId);
      if(next!=null) {
        db.update("UPDATE bookings SET status='CONFIRMED',queue_position=NULL WHERE id=?",id(next));
        db.update("UPDATE slots SET available_count=max(0,available_count-1) WHERE id=?",slotId);
        notifyStudent(((Number)next.get("student_id")).longValue(),"WAITLIST_PROMOTED","Your booking is now confirmed.");
      }
      int position=1;
      for(var waiting:db.rows("SELECT id FROM bookings WHERE slot_id=? AND status='WAITLISTED' ORDER BY queue_position,created_at,id",slotId))db.update("UPDATE bookings SET queue_position=? WHERE id=?",position++,id(waiting));
      db.update("UPDATE slots SET status=CASE WHEN available_count=0 THEN 'FULL' ELSE 'OPEN' END WHERE id=? AND status<>'CLOSED'",slotId);
    }
    notifyStudent(student,"BOOKING_CANCELLED","Your booking was cancelled.");return enrich(row("bookings",bookingId),user.role().equals("ADMIN"));
  }
  @Transactional
  public Map<String,Object> allocate(long slotId) {
    var slot=row("slots",slotId);
    require(Set.of("OPEN","PENDING_ALLOCATION").contains(str(slot,"status"))&&slot.get("allocation_run_at")==null,400,"ALLOCATION_ALREADY_RUN","Allocation cannot run in this state.");
    var requests=db.rows("SELECT * FROM bookings WHERE slot_id=? AND status='REQUESTED' ORDER BY created_at,id",slotId);
    List<AllocationPolicy.Candidate> candidates=new ArrayList<>();
    for(var b:requests) {
      var stats=AuthController.stats(db,((Number)b.get("student_id")).longValue());
      double days=stats.get("last_session_at")==null?999999:Math.max(0,Duration.between(instant(stats.get("last_session_at")),Instant.now()).toSeconds()/86400.0);
      candidates.add(new AllocationPolicy.Candidate(id(b),((Number)stats.get("sessions_last_7_days")).intValue(),days,instant(b.get("created_at"))));
    }
    var cfg=row("fairness_config",1);
    var ranked=policy.rank(candidates,((Number)cfg.get("recency_threshold_days")).doubleValue(),((Number)cfg.get("recency_weight")).doubleValue());
    int places=((Number)slot.get("available_count")).intValue(),confirmed=0,waiting=0;
    for(var score:ranked) {
      boolean accepted=confirmed<places;
      if(accepted)confirmed++;else waiting++;
      String status=accepted?"CONFIRMED":"WAITLISTED";
      db.update("UPDATE bookings SET status=?,priority_score=?,queue_position=?,allocated_at=? WHERE id=?",status,score.score(),accepted?null:waiting,now(),score.id());
      notifyStudent(((Number)row("bookings",score.id()).get("student_id")).longValue(),"BOOKING_"+status,accepted?"Your booking is confirmed.":"Your booking is on the waiting list.");
    }
    String status=places-confirmed==0?"FULL":"OPEN",run=now();
    db.update("UPDATE slots SET available_count=?,status=?,allocation_run_at=? WHERE id=?",places-confirmed,status,run,slotId);
    return map("slot_id",slotId,"confirmed_count",confirmed,"waitlisted_count",waiting,"slot_status",status,"allocation_run_at",run);
  }
}
