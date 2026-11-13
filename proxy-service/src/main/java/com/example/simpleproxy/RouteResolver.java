package com.example.simpleproxy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RouteResolver {

    private final ProxyProperties properties;

    public Optional<ProxyProperties.Route> resolve(String requestUri) {
        return properties.getRoutes().stream()
                .filter(route -> matches(requestUri, route.getPrefix()))
                .max(Comparator.comparingInt(route -> route.getPrefix().length()));
    }

    // "/backend1" and "/backend1/x" match; "/backend10" does not
    private boolean matches(String uri, String prefix) {
        return uri.equals(prefix) || uri.startsWith(prefix + "/");
    }
}