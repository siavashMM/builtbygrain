package com.builtbygrain.backend.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.builtbygrain.backend.shop.TestResponse;

@RestController
public class UserOrdersController {

    @GetMapping("/api/user/orders/test")
    public TestResponse testOrder() {
        return new TestResponse("user order endpoint");
    }
}
