package edu.tiet.scms.desktop;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
/** Authenticated HTTP client. Token is private and never put in the UI. */
public final class DesktopApi {
 private final String base;private final ObjectMapper json=new ObjectMapper();
 private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
 private String token;private String role="",name="";
 public DesktopApi(String base){URI uri=URI.create(base);if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!java.util.Set.of("","/","/api","/api/").contains(uri.getPath()))throw new IllegalArgumentException("Enter a valid API origin.");this.base=base.replaceAll("/+$","")+(uri.getPath().startsWith("/api")?"":"/api");}
 public void login(String email,String password){var data=call("POST","/auth/staff/login",Map.of("email",email.trim(),"password",password));String nextRole=data.path("user").path("role").asText();String nextToken=data.path("token").asText();if(!java.util.Set.of("ADMIN","STAFF").contains(nextRole)||nextToken.isBlank())throw new IllegalArgumentException("Staff or administrator access required.");role=nextRole;name=data.path("user").path("name").asText();token=nextToken;}
 public JsonNode inventory(int page,String category){return request("GET","/inventory?page="+page+"&limit=20"+(category.isBlank()?"":"&category="+URLEncoder.encode(category,java.nio.charset.StandardCharsets.UTF_8)),null);}
 public void addEquipment(String name,String category,String qr,String location){if(!isAdmin())throw new IllegalStateException("Administrator access required.");if(name.isBlank()||category.isBlank()||qr.isBlank())throw new IllegalArgumentException("Name, category and QR identifier are required.");call("POST","/inventory",Map.of("name",name.trim(),"category",category.trim(),"qr_code",qr.trim(),"location",location.trim()));}
 private JsonNode call(String method,String path,Object body){return request(method,path,body).path("data");}
 private JsonNode request(String method,String path,Object body){
  try {
   var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");
   if(token!=null)request.header("Authorization","Bearer "+token);
   if(body!=null)request.header("Content-Type","application/json");
   request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
   var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());var result=json.readTree(response.body());
   if(response.statusCode()>=400){if(response.statusCode()==401)logout();throw new ApiFailure(response.statusCode(),result.path("error").path("message").asText("The request could not be completed."));}
   return result;
  }catch(ApiFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Request interrupted.");}catch(Exception e){throw new IllegalStateException("Cannot reach the SCMS API. Check the server address and launcher.");}
 }
 public boolean isAdmin(){return role.equals("ADMIN");}public String displayName(){return name+" ("+role+")";}
 public void logout(){token=null;role="";name="";}
 static final class ApiFailure extends RuntimeException{final int status;ApiFailure(int status,String message){super(message);this.status=status;}}
}
