package com.example.backendservice2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackendController {
    @GetMapping("/")
    public String hello() {
        return "Hello from Backend Service 2!";
    }
}
