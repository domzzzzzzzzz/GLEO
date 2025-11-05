package com.fbcorp.gleo.config;

import com.fbcorp.gleo.repo.UserAccountRepo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserAccountRepo userAccountRepo;

    public RoleBasedAuthenticationSuccessHandler(UserAccountRepo userAccountRepo) {
        this.userAccountRepo = userAccountRepo;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        var accountOpt = userAccountRepo.findByUsername(authentication.getName());
        if (accountOpt.isPresent()) {
            var account = accountOpt.get();
            if (account.hasRole("USHER")
                    && account.getVendor() != null
                    && account.getVendor().getEvent() != null) {
                String eventCode = account.getVendor().getEvent().getCode();
                response.sendRedirect("/e/" + eventCode + "/usher");
                return;
            }
        }
        response.sendRedirect("/dashboard");
    }
}
