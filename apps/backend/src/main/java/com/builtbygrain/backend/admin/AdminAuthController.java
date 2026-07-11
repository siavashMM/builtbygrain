package com.builtbygrain.backend.admin;

import java.security.Principal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminAuthController {

    @PostMapping("/api/admin/auth/login")
    public AdminLoginResponse login(Principal principal) {
        return new AdminLoginResponse(principal.getName(), true);
    }

    public record AdminLoginResponse(String username, boolean admin) {
    }
}
