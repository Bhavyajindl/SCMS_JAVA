package edu.tiet.scms.dashboard.config;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import edu.tiet.scms.dashboard.auth.BackendAuthenticationProvider;
import edu.tiet.scms.dashboard.views.LoginView;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain security(HttpSecurity http, BackendAuthenticationProvider provider)
      throws Exception {
    http.authenticationProvider(provider);
    http.authorizeHttpRequests(r -> r.requestMatchers("/health").permitAll());
    http.with(VaadinSecurityConfigurer.vaadin(), v -> v.loginView(LoginView.class));
    http.formLogin(
        f ->
            f.successHandler(
                    (request, response, auth) -> {
                      var user = (edu.tiet.scms.dashboard.dto.PortalUser) auth.getPrincipal();
                      var success =
                          new org.springframework.security.web.authentication
                              .SavedRequestAwareAuthenticationSuccessHandler();
                      success.setDefaultTargetUrl(
                          user.role().equals("ADMIN") ? "/admin/dashboard" : "/admin/inventory");
                      success.onAuthenticationSuccess(request, response, auth);
                    })
                .failureHandler(
                    (request, response, error) ->
                        response.sendRedirect(
                            error
                                    instanceof
                                    org.springframework.security.authentication
                                        .AuthenticationServiceException
                                ? "/login?unavailable"
                                : "/login?error")));
    return http.build();
  }
}
