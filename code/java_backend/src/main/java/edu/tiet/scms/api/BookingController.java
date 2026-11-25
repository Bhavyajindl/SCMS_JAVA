package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/bookings")
public class BookingController {
  private final AuthService auth;private final JdbcStore db;private final BookingService bookings;
  public BookingController(AuthService auth,JdbcStore db,BookingService bookings) { this.auth=auth;this.db=db;this.bookings=bookings; }
  @GetMapping
  public Object list(@RequestHeader(value="Authorization",required=false) String token,@RequestParam Map<String,String> q) {
    var u=auth.authenticate(token);u.student();
    return page(filter(db.rows("SELECT * FROM bookings WHERE student_id=? ORDER BY created_at DESC,id DESC",u.id()),q,"status").stream().map(r->bookings.enrich(r,false)).toList(),q);
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Object create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> b) { return ok(bookings.create(auth.authenticate(token),number(b,"slot_id"))); }
  @DeleteMapping("/{id}")
  public Object cancel(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id) {
    var u=auth.authenticate(token);u.student();return ok(bookings.cancel(id,u));
  }
  @GetMapping({"/{id}","/{id}/priority","/{id}/queue-position"})
  public Object detail(@RequestHeader(value="Authorization",required=false) String token,@PathVariable long id,jakarta.servlet.http.HttpServletRequest request) {
    var u=auth.authenticate(token);u.student();var b=bookings.row("bookings",id);
    require(((Number)b.get("student_id")).longValue()==u.id(),404,"BOOKING_NOT_FOUND","Booking not found.");
    if(request.getRequestURI().endsWith("/priority"))return ok(map("booking_id",id,"status",b.get("status"),"priority_score",b.get("priority_score"),"allocated_at",b.get("allocated_at")));
    if(request.getRequestURI().endsWith("/queue-position"))return ok(map("booking_id",id,"status",b.get("status"),"queue_position",b.get("queue_position"),"total_waitlisted",db.count("SELECT count(*) FROM bookings WHERE slot_id=? AND status='WAITLISTED'",b.get("slot_id"))));
    return ok(bookings.enrich(b,false));
  }
}
