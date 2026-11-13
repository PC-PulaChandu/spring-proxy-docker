package com.example.simpleproxy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "proxy")
@Getter
@Setter
public class ProxyProperties {

    private List<Route> routes = new ArrayList<>();
    private int maxAttempts = 2;

    @Getter
    @Setter
    public static class Route {
        private String name;
        private String prefix;
        private List<String> urls = new ArrayList<>();
    }
}