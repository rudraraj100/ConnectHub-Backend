package com.admin_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Admin Server.
 *
 * Requires spring-boot-starter-web (MVC) for MockMvc to be auto-configured.
 * Eureka is disabled so tests run without a live service registry.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false",
    "spring.security.user.name=testadmin",
    "spring.security.user.password=testpassword"
})
class AdminServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Spring application context loads successfully")
    void contextLoads() {
    }

    @Test
    @DisplayName("Unauthenticated request to /admin redirects to login")
    void adminDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin").accept(MediaType.TEXT_HTML))
               .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Actuator health endpoint is publicly accessible")
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator info endpoint requires authentication")
    void actuatorInfoRequiresAuthentication() throws Exception {
        // /actuator/info is protected by Spring Boot Admin's security — returns 401 without credentials
        mockMvc.perform(get("/actuator/info").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isUnauthorized());
    }
}
