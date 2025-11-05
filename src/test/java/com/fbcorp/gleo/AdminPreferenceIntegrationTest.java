package com.fbcorp.gleo;

import com.fbcorp.gleo.domain.Role;
import com.fbcorp.gleo.domain.UserAccount;
import com.fbcorp.gleo.repo.AdminPreferenceRepo;
import com.fbcorp.gleo.repo.RoleRepo;
import com.fbcorp.gleo.repo.UserAccountRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AdminPreferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepo userAccountRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private AdminPreferenceRepo adminPreferenceRepo;

    @Test
    public void savePreferenceStoresThemeForUser() throws Exception {
        // create role and user in DB
        Role r = roleRepo.findByName("ADMIN").orElseGet(() -> {
            Role nr = new Role();
            nr.setName("ADMIN");
            return roleRepo.save(nr);
        });

        UserAccount u = new UserAccount();
        u.setUsername("int-test-admin");
        u.setPassword("x");
        u.getRoles().add(r);
        userAccountRepo.save(u);

        mockMvc.perform(post("/admin/api/preferences")
                .with(user("int-test-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isOk());

        var opt = adminPreferenceRepo.findByUser_Username("int-test-admin");
        assertThat(opt).isPresent();
        assertThat(opt.get().getTheme()).isEqualTo("dark");
    }
}
