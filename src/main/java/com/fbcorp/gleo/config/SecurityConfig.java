package com.fbcorp.gleo.config;

import com.fbcorp.gleo.service.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final RoleBasedAuthenticationSuccessHandler successHandler;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          RoleBasedAuthenticationSuccessHandler successHandler) {
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(mvc.pattern("/css/**"), mvc.pattern("/js/**"), mvc.pattern("/images/**")).permitAll()
                        .requestMatchers(mvc.pattern("/uploads/**")).permitAll()
                        .requestMatchers(mvc.pattern("/h2-console/**")).permitAll()
                        .requestMatchers(mvc.pattern("/error")).permitAll()
                        .requestMatchers(mvc.pattern("/e/{eventCode}/usher/**")).hasAnyRole("USHER", "ADMIN", "ORGANIZER")
                        .requestMatchers(mvc.pattern("/e/**")).permitAll()
                        .requestMatchers(mvc.pattern("/login"), mvc.pattern("/")).permitAll()
                        .requestMatchers(mvc.pattern("/admin/**")).hasAnyRole("ADMIN", "ORGANIZER")
                        .requestMatchers(mvc.pattern("/organizer/**")).hasRole("ORGANIZER")
                        .requestMatchers(mvc.pattern("/vendor/**")).hasAnyRole("VENDOR", "STAFF")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
