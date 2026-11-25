package edu.tiet.scms.api;

import static edu.tiet.scms.api.ApiProblem.require;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  public record Identity(long id,String role,Map<String,Object> account) {
    public void student() { require(role.equals("STUDENT"),403,"FORBIDDEN","Student access required."); }
    public void staff() { require(Set.of("STAFF","ADMIN").contains(role),403,"FORBIDDEN","Staff access required."); }
    public void admin() { require(role.equals("ADMIN"),403,"FORBIDDEN","Administrator access required."); }
  }
  private final JdbcStore db;
  private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  public AuthService(JdbcStore db,@Value("${scms.jwt-secret}") String secret) {
    this.db=db;
    byte[] key;
    if(secret.isBlank()) { key=new byte[32];new SecureRandom().nextBytes(key); }
    else { key=secret.getBytes(StandardCharsets.UTF_8);require(key.length>=32,500,"CONFIGURATION","SCMS_JAVA_SECRET must contain at least 32 bytes."); }
    var signingKey=new SecretKeySpec(key,"HmacSHA256");
    encoder=new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    decoder=NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("scms-java"));
  }
  public String hash(String password) {
    require(password.length()>=8&&password.getBytes(StandardCharsets.UTF_8).length<=72,422,"VALIDATION_ERROR","Password must contain at least 8 characters and at most 72 UTF-8 bytes.");
    return passwords.encode(password);
  }
  public Map<String,Object> login(Map<String,Object> input,boolean student) {
    String email=ApiData.text(input,"email"),password=Objects.toString(input.get("password"),"");
    var account=db.one("SELECT * FROM "+(student?"students":"users")+" WHERE lower(email)=lower(?)",email);
    require(!password.isEmpty()&&password.getBytes(StandardCharsets.UTF_8).length<=72,401,"INVALID_CREDENTIALS","Invalid email or password.");
    boolean valid=account!=null&&passwords.matches(password,Objects.toString(account.get("password_hash"),""));
    require(valid,401,"INVALID_CREDENTIALS","Invalid email or password.");
    require(!Set.of("SUSPENDED","INACTIVE").contains(account.get("status")),403,"ACCOUNT_INACTIVE","Account is not active.");
    String role=student?"STUDENT":account.get("role").toString();
    Instant now=Instant.now();
    var claims=JwtClaimsSet.builder().issuer("scms-java").subject(account.get("id").toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("role",role).build();
    String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    return Map.of("token",token,"token_type","bearer",student?"student":"user",publicAccount(account));
  }
  public Identity authenticate(String authorization) {
    require(authorization!=null&&authorization.startsWith("Bearer "),401,"UNAUTHORIZED","Please sign in.");
    try {
      var jwt=decoder.decode(authorization.substring(7));String role=jwt.getClaimAsString("role");
      require(role!=null&&Set.of("STUDENT","STAFF","ADMIN").contains(role),401,"UNAUTHORIZED","Invalid token role.");
      long id=Long.parseLong(jwt.getSubject());
      var account=db.one("SELECT * FROM "+(role.equals("STUDENT")?"students":"users")+" WHERE id=?",id);
      require(account!=null,401,"UNAUTHORIZED","Account not found.");
      require(!Set.of("SUSPENDED","INACTIVE").contains(account.get("status")),403,"ACCOUNT_INACTIVE","Account is not active.");
      require(role.equals("STUDENT")||role.equals(account.get("role")),401,"UNAUTHORIZED","Account role changed. Sign in again.");
      return new Identity(id,role,account);
    } catch(ApiProblem e) { throw e; } catch(Exception e) { throw new ApiProblem(401,"UNAUTHORIZED","Token is invalid or expired."); }
  }
  public static Map<String,Object> publicAccount(Map<String,Object> account) {
    var result=new LinkedHashMap<>(account);result.remove("password_hash");result.remove("qr_identifier");return result;
  }
}
