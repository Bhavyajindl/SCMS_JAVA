package edu.tiet.scms.dashboard.auth;

import edu.tiet.scms.dashboard.dto.PortalUser;
import edu.tiet.scms.dashboard.service.*;
import java.util.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class BackendAuthenticationProvider implements AuthenticationProvider {
  private final ApiClient api;

  public BackendAuthenticationProvider(ApiClient api) {
    this.api = api;
  }

  @Override
  public Authentication authenticate(Authentication input) {
    try {
      var data =
          api.send(
                  "POST",
                  "/auth/staff/login",
                  Map.of(
                      "email",
                      input.getName().trim(),
                      "password",
                      input.getCredentials().toString()),
                  null)
              .path("data");
      var profile = data.path("user");
      String role = profile.path("role").asText();
      String token = data.path("token").asText();
      if (!Set.of("ADMIN", "STAFF").contains(role) || token.isBlank())
        throw new BadCredentialsException("Staff access is required.");
      var user =
          new PortalUser(
              profile.path("id").asLong(),
              profile.path("name").asText(),
              profile.path("email").asText(),
              role,
              token);
      return new UsernamePasswordAuthenticationToken(
          user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    } catch (ApiException error) {
      if (error.status == 503)
        throw new AuthenticationServiceException("The SCMS API is unavailable.");
      throw new BadCredentialsException(error.getMessage());
    }
  }

  @Override
  public boolean supports(Class<?> kind) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(kind);
  }
}
