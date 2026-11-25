package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import static edu.tiet.scms.api.ApiProblem.require;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService auth;
  private final JdbcStore db;
  public AuthController(AuthService auth,JdbcStore db) { this.auth=auth;this.db=db; }
  @GetMapping("/health")
  public Object health() { return ok(map("status","ok","backend","spring-boot-java","database","sqlite-jdbc")); }
  @PostMapping("/auth/student/login")
  public Object studentLogin(@RequestBody Map<String,Object> b) { return ok(auth.login(b,true)); }
  @PostMapping("/auth/staff/login")
  public Object staffLogin(@RequestBody Map<String,Object> b) { return ok(auth.login(b,false)); }
  @PostMapping("/auth/student/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Object register(@RequestBody Map<String,Object> b) {
    String email=text(b,"email"),password=Objects.toString(b.get("password"),"");
    require(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),422,"VALIDATION_ERROR","Enter a valid email.");
    long student=db.insert("INSERT INTO students(student_id,name,email,department,password_hash,qr_identifier,status,created_at) VALUES(?,?,?,?,?,?,'ACTIVE',?)",
      text(b,"student_id"),text(b,"name"),email,text(b,"department"),auth.hash(password),UUID.randomUUID()+"-"+UUID.randomUUID(),now());
    db.update("INSERT INTO usage_statistics(student_id,updated_at) VALUES(?,?)",student,now());
    return ok(auth.login(map("email",email,"password",password),true));
  }
  @GetMapping("/auth/me")
  public Object me(@RequestHeader(value="Authorization",required=false) String token) {
    var u=auth.authenticate(token);u.student();return ok(AuthService.publicAccount(u.account()));
  }
  @GetMapping("/auth/me/staff")
  public Object staff(@RequestHeader(value="Authorization",required=false) String token) {
    var u=auth.authenticate(token);u.staff();return ok(AuthService.publicAccount(u.account()));
  }
  @GetMapping("/students/qr")
  public Object qr(@RequestHeader(value="Authorization",required=false) String token) {
    var u=auth.authenticate(token);u.student();return ok(map("qr_identifier",u.account().get("qr_identifier")));
  }
  @GetMapping("/students/usage-stats")
  public Object usage(@RequestHeader(value="Authorization",required=false) String token) {
    var u=auth.authenticate(token);u.student();return ok(stats(db,u.id()));
  }
  public static Map<String,Object> stats(JdbcStore db,long student) {
    return map("sessions_last_7_days",db.count("SELECT count(*) FROM transactions WHERE student_id=? AND returned_at IS NOT NULL AND julianday(returned_at)>=julianday('now','-7 days')",student),
      "total_sessions",db.count("SELECT count(*) FROM transactions WHERE student_id=? AND returned_at IS NOT NULL",student),
      "last_session_at",db.required("SELECT max(returned_at) AS value FROM transactions WHERE student_id=?",student).get("value"),"updated_at",now());
  }
}
