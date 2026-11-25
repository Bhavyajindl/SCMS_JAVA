package edu.tiet.scms.api;
import edu.tiet.scms.RuleViolation;
import java.time.DateTimeException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ApiProblem.class)
  ResponseEntity<?> problem(ApiProblem e) { return error(e.status,e.code,e.getMessage()); }
  @ExceptionHandler(RuleViolation.class)
  ResponseEntity<?> rule(RuleViolation e) { return error(e.status,e.code,e.getMessage()); }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) { return error(409,"CONFLICT","A unique value already exists or a referenced record is invalid."); }
  @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
  ResponseEntity<?> invalid(Exception e) { return error(422,"VALIDATION_ERROR","Check the input values, dates and JSON."); }
  private ResponseEntity<?> error(int status,String code,String message) {
    return ResponseEntity.status(status).body(Map.of("success",false,"error",Map.of("code",code,"message",message)));
  }
}
