package edu.tiet.scms.mobile;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Native Java HTTP adapter. Routes mirror the original Dart remote sources. */
public final class ScmsApiClient {
  private final String base;
  private volatile String token;
  public ScmsApiClient(String origin) {
    URI uri=URI.create(origin.trim());
    if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||
      uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||
      !(uri.getPath().isEmpty()||uri.getPath().equals("/")||uri.getPath().equals("/api")||uri.getPath().equals("/api/")))
      throw new IllegalArgumentException("Enter the API origin, for example http://192.168.1.10:18001");
    base=origin.trim().replaceAll("/+$","")+(uri.getPath().startsWith("/api")?"":"/api");
  }
  public static JSONObject json(Object... pairs) throws JSONException {
    JSONObject result=new JSONObject();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),pairs[i+1]);return result;
  }
  public JSONObject request(String method,String path,JSONObject body) throws Exception {
    if(!path.startsWith("/")||path.startsWith("//")||path.contains(".."))throw new IllegalArgumentException("Invalid API path.");
    HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
    try {
      c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(15000);
      c.setRequestProperty("Accept","application/json");String current=token;
      if(current!=null)c.setRequestProperty("Authorization","Bearer "+current);
      if(body!=null) {
        c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try(OutputStream out=c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
      }
      int status=c.getResponseCode();InputStream stream=status<400?c.getInputStream():c.getErrorStream();
      if(stream==null)throw new ApiException(status,"The server returned an empty response.");
      StringBuilder raw=new StringBuilder();
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
        char[] buffer=new char[4096];int n;
        while((n=reader.read(buffer))!=-1) { raw.append(buffer,0,n);if(raw.length()>2_000_000)throw new IOException("Response too large."); }
      }
      JSONObject response;
      try { response=new JSONObject(raw.toString()); }
      catch(JSONException e) { throw new ApiException(status,"Unexpected server response. Check the API URL."); }
      if(status>=400||!response.optBoolean("success",false)) {
        if(status==401)logout();
        JSONObject error=response.optJSONObject("error");
        throw new ApiException(status,error==null?"Request could not be completed.":error.optString("message","Request failed."));
      }
      return response;
    } finally { c.disconnect(); }
  }
  public JSONObject login(String email,String password,boolean staff) throws Exception {
    JSONObject data=request("POST",staff?"/auth/staff/login":"/auth/student/login",json("email",email.trim(),"password",password)).getJSONObject("data");
    String access=data.getString("token");if(access.isEmpty())throw new IOException("No session token returned.");
    JSONObject account=data.getJSONObject(staff?"user":"student");token=access;return account;
  }
  public JSONObject register(JSONObject form) throws Exception {
    JSONObject data=request("POST","/auth/student/register",form).getJSONObject("data");
    String access=data.getString("token");if(access.isEmpty())throw new IOException("No session token returned.");
    JSONObject account=data.getJSONObject("student");token=access;return account;
  }
  public JSONObject get(String path) throws Exception { return request("GET",path,null); }
  public JSONObject book(long slot) throws Exception { return request("POST","/bookings",json("slot_id",slot)); }
  public JSONObject cancel(long booking) throws Exception { return request("DELETE","/bookings/"+booking,null); }
  public JSONObject issue(long booking,long equipment) throws Exception { return request("POST","/transactions/issue",json("booking_id",booking,"equipment_id",equipment)); }
  public JSONObject returnItem(long transaction,String condition,String report) throws Exception {
    return request("POST","/transactions/"+transaction+"/return",json("condition_on_return",condition,"damage_report",report));
  }
  public void logout() { token=null; }
  public static final class ApiException extends IOException {
    public final int status;
    ApiException(int status,String message) { super(message);this.status=status; }
  }
}
