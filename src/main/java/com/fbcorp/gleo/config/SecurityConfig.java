package com.fbcorp.gleo.config;

import com.fbcorp.gleo.service.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
        // Use BCrypt for password encoding. NoOp is insecure and deprecated.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
            .requestMatchers("/uploads/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers(new AntPathRequestMatcher("/e/*/usher/**")).hasAnyRole("USHER", "ADMIN", "ORGANIZER")
            .requestMatchers("/e/**").permitAll() // Allow all event/guest pages for everyone
            .requestMatchers("/login", "/").permitAll()
            .requestMatchers("/admin/**").hasAnyRole("ADMIN", "ORGANIZER")
            .requestMatchers("/organizer/**").hasRole("ORGANIZER")
            .requestMatchers("/vendor/**").hasAnyRole("VENDOR", "STAFF")
            .anyRequest().authenticated()
        )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .permitAll()
                )
        .logout(logout -> logout
            .logoutUrl("/logout")
            // redirect users to the login page after sign-out instead of the public guest page
            .logoutSuccessUrl("/login")
            .permitAll()
        )
                .authenticationProvider(authenticationProvider())
                // Remove httpBasic to prevent browser sign-in popup for guests
                .csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
