package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in seed data goes only into the separately configured Java demo database. */
@Component
public class DemoSeeder implements CommandLineRunner {
  private final JdbcStore db;private final AuthService auth;
  public DemoSeeder(JdbcStore db,AuthService auth) { this.db=db;this.auth=auth; }
  @Transactional
  public void run(String... args) {
    String password=System.getenv("SCMS_JAVA_DEMO_PASSWORD");if(password==null||password.isBlank())return;
    String hash=auth.hash(password);
    for(String role:List.of("ADMIN","STAFF"))db.update("INSERT OR IGNORE INTO users(name,email,password_hash,role,status,created_at) VALUES(?,?,?,?,'ACTIVE',?)",role+" Demo",role.toLowerCase(Locale.ROOT)+"@scms.local",hash,role,now());
    long admin=((Number)db.required("SELECT id FROM users WHERE email='admin@scms.local'").get("id")).longValue();
    db.update("INSERT OR IGNORE INTO students(student_id,name,email,department,password_hash,qr_identifier,status,created_at) VALUES('JAVA-DEMO','Student Demo','student@scms.local','CSE',?,?,'ACTIVE',?)",hash,UUID.randomUUID().toString(),now());
    for(String name:List.of("Badminton Racket","Basketball","Football"))db.update("INSERT OR IGNORE INTO equipment(name,category,qr_code,location,added_at,added_by) VALUES(?,'Sports',?,'Sports Complex',?,?)",name,"JAVA-"+name.replace(' ','-'),now(),admin);
    if(db.count("SELECT count(*) FROM slots")==0) {
      var date=LocalDate.now(ZoneOffset.UTC).plusDays(1);
      for(var eq:db.rows("SELECT id FROM equipment"))db.update("INSERT INTO slots(equipment_id,date,start_time,end_time,capacity,available_count,booking_cutoff_at,created_by,created_at) VALUES(?,?,'10:00:00','11:00:00',1,1,?,?,?)",eq.get("id"),date.toString(),date.atTime(9,0).toInstant(ZoneOffset.UTC).toString(),admin,now());
    }
  }
}
