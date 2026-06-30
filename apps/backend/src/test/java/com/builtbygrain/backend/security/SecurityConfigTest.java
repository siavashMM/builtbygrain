package com.builtbygrain.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void publicProductEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/public/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void corsAllowsLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/public/products")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void userOrderEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/orders/test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userOrderEndpointAllowsUser() throws Exception {
        mockMvc.perform(get("/api/user/orders/test").with(httpBasic("user", "password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("user order endpoint"));
    }

    @Test
    void userOrderEndpointAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/user/orders/test").with(httpBasic("admin", "admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("user order endpoint"));
    }

    @Test
    void adminProductEndpointRejectsUser() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("user", "password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Oak Board",
                      "description": "Handmade oak serving board",
                      "priceCents": 4900,
                      "currency": "EUR"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminProductEndpointAllowsAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Oak Board",
                      "description": "Handmade oak serving board",
                      "priceCents": 4900,
                      "currency": "EUR"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Oak Board"));
    }
}
