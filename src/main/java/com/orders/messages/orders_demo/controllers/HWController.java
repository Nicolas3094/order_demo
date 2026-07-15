package com.orders.messages.orders_demo.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HWController {

    @GetMapping("/api/hw")
    public String hwmessage() {
        return "Hellow World";
    }

}
