package com.example.backendservice2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackendController {

    @GetMapping({ "/", "/hello" })
    public String hello() {
        return "Hello from Backend Service 2!";
    }

    @PostMapping("/echo")
    public String echo(@RequestBody String body) {
        return "Backend Service 2 received: " + body;
    }

    @GetMapping("/fail")
    public ResponseEntity<String> fail() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Simulated failure from Backend Service 2");
    }
}
