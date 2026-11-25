package edu.tiet.scms.dashboard.auth;

import com.vaadin.flow.spring.security.AuthenticationContext;
import edu.tiet.scms.dashboard.dto.PortalUser;
import org.springframework.stereotype.Component;

@Component
public class UserSession {
  private final AuthenticationContext authentication;

  public UserSession(AuthenticationContext authentication) {
    this.authentication = authentication;
  }

  public PortalUser user() {
    return authentication
        .getAuthenticatedUser(PortalUser.class)
        .orElseThrow(
            () ->
                new org.springframework.security.access.AccessDeniedException("Sign in required"));
  }

  public boolean isAdmin() {
    return user().role().equals("ADMIN");
  }

  public void logout() {
    authentication.logout();
  }
}
