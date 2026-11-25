package edu.tiet.scms.dashboard.dto;

import java.io.Serializable;

public record PortalUser(long id, String name, String email, String role, String token)
    implements Serializable {
  @Override
  public String toString() {
    return "PortalUser[role=" + role + "]";
  }
}
