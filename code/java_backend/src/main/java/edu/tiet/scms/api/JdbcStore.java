package edu.tiet.scms.api;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** All values use prepared-statement parameters. SQL identifiers are developer constants. */
@Repository
public class JdbcStore {
  private final JdbcTemplate jdbc;
  public JdbcStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public List<Map<String,Object>> rows(String sql,Object... args) { return jdbc.queryForList(sql,args); }
  public Map<String,Object> one(String sql,Object... args) {
    var rows=rows(sql,args);return rows.isEmpty()?null:new LinkedHashMap<>(rows.get(0));
  }
  public Map<String,Object> required(String sql,Object... args) {
    var row=one(sql,args);if(row==null)throw new ApiProblem(404,"NOT_FOUND","Record not found.");return row;
  }
  public long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
  public int update(String sql,Object... args) { return jdbc.update(sql,args); }
  public long insert(String sql,Object... args) {
    var keys=new GeneratedKeyHolder();
    jdbc.update(connection->{var statement=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }
}
