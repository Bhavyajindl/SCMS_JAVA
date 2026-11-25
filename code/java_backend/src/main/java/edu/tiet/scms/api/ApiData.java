package edu.tiet.scms.api;

import static edu.tiet.scms.api.ApiProblem.require;
import java.time.*;
import java.util.*;

/** Shared response shapes match the existing SCMS clients. */
public final class ApiData {
  private ApiData() {}
  public static Map<String,Object> map(Object... pairs) {
    Map<String,Object> r=new LinkedHashMap<>();
    for(int i=0;i<pairs.length;i+=2)r.put(pairs[i].toString(),pairs[i+1]);
    return r;
  }
  public static Map<String,Object> ok(Object value) { return map("success",true,"data",value,"pagination",null); }
  public static String text(Map<String,?> b,String key) {
    String s=Objects.toString(b.get(key),"").trim();
    require(!s.isEmpty()&&s.length()<=4096,422,"VALIDATION_ERROR",key+" is required.");
    return s;
  }
  public static long number(Map<String,?> b,String key) { return Long.parseLong(text(b,key)); }
  public static String str(Map<String,?> b,String key) { return Objects.toString(b.get(key),""); }
  public static long id(Map<String,?> b) { return ((Number)b.get("id")).longValue(); }
  public static String now() { return Instant.now().toString(); }
  public static Instant instant(Object value) {
    String s=Objects.toString(value,"").replace(' ','T');
    try { return Instant.parse(s); } catch(Exception ignored) {
      try { return OffsetDateTime.parse(s).toInstant(); }
      catch(Exception e) { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC); }
    }
  }
  public static int integer(Map<String,String> q,String key,int fallback,int min,int max) {
    int n=Integer.parseInt(q.getOrDefault(key,""+fallback));
    require(n>=min&&n<=max,422,"VALIDATION_ERROR","Invalid "+key);return n;
  }
  public static Map<String,Object> page(List<Map<String,Object>> rows,Map<String,String> q) {
    int page=integer(q,"page",1,1,Integer.MAX_VALUE),limit=integer(q,"limit",20,1,100);
    int start=(int)Math.min(rows.size(),(long)(page-1)*limit),end=Math.min(rows.size(),start+limit);
    return map("success",true,"data",rows.subList(start,end),"pagination",map("page",page,"limit",limit,"total",rows.size(),"totalPages",(rows.size()+limit-1)/limit));
  }
  public static List<Map<String,Object>> filter(List<Map<String,Object>> rows,Map<String,String> q,String... keys) {
    return rows.stream().filter(r->Arrays.stream(keys).allMatch(k->!q.containsKey(k)||q.get(k).isBlank()||q.get(k).equals(str(r,k)))).toList();
  }
  public static void choice(Map<String,?> b,String field,String... values) {
    require(Arrays.asList(values).contains(text(b,field)),422,"VALIDATION_ERROR","Invalid "+field);
  }
}
