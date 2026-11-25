package edu.tiet.scms.dashboard;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@Theme("scms")
public class ScmsDashboard implements AppShellConfigurator {
  public static void main(String[] args) {
    SpringApplication.run(ScmsDashboard.class, args);
  }
}
