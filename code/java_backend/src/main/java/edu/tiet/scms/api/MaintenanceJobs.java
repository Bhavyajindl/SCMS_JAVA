package edu.tiet.scms.api;
import static edu.tiet.scms.api.ApiData.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Local demo jobs: overdue detection and allocation when the booking cutoff is reached. */
@Component
public class MaintenanceJobs {
  private final JdbcStore db;private final BookingService bookings;
  public MaintenanceJobs(JdbcStore db,BookingService bookings) { this.db=db;this.bookings=bookings; }
  @Scheduled(initialDelay=60000,fixedDelay=30000)
  @Transactional
  public void run() {
    for(var txn:db.rows("SELECT * FROM transactions WHERE status='ISSUED' AND julianday(due_at)<julianday('now')")) {
      db.update("UPDATE transactions SET status='OVERDUE' WHERE id=?",id(txn));
      int days=(int)Math.max(1,ChronoUnit.DAYS.between(instant(txn.get("due_at")),Instant.now()));
      db.update("INSERT OR IGNORE INTO defaulters(student_id,transaction_id,detected_at,overdue_days,status) VALUES(?,?,?,?,'ACTIVE')",txn.get("student_id"),id(txn),now(),days);
      bookings.notifyStudent(((Number)txn.get("student_id")).longValue(),"OVERDUE_ALERT","Equipment is overdue. Please return it.");
    }
    db.update("UPDATE defaulters SET overdue_days=max(1,CAST(julianday('now')-(SELECT julianday(due_at) FROM transactions t WHERE t.id=transaction_id) AS INTEGER)) WHERE status IN ('ACTIVE','NOTIFIED')");
    for(var slot:db.rows("SELECT id FROM slots WHERE status IN ('OPEN','PENDING_ALLOCATION') AND allocation_run_at IS NULL AND julianday(booking_cutoff_at)<=julianday('now')"))bookings.allocate(id(slot));
  }
}
