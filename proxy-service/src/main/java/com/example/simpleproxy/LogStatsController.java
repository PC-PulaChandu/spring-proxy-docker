package com.example.simpleproxy;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LogStatsController {

    private final RequestLogService requestLogService;

    @GetMapping("/internal/log-stats")
    public Map<String, Object> stats() {
        return requestLogService.stats();
    }
}