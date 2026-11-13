package com.example.simpleproxy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouteResolverTest {

    private RouteResolver resolverWith(ProxyProperties.Route... routes) {
        ProxyProperties properties = new ProxyProperties();
        properties.setRoutes(List.of(routes));
        return new RouteResolver(properties);
    }

    private ProxyProperties.Route route(String name, String prefix) {
        ProxyProperties.Route route = new ProxyProperties.Route();
        route.setName(name);
        route.setPrefix(prefix);
        route.setUrls(List.of("http://" + name + ":8080"));
        return route;
    }

    @Test
    void matchesExactPrefix() {
        RouteResolver resolver = resolverWith(route("backend-service-1", "/backend1"));

        Optional<ProxyProperties.Route> result = resolver.resolve("/backend1");

        assertThat(result.orElseThrow().getName()).isEqualTo("backend-service-1");
    }

    @Test
    void matchesPathsBelowThePrefix() {
        RouteResolver resolver = resolverWith(route("backend-service-1", "/backend1"));

        Optional<ProxyProperties.Route> result = resolver.resolve("/backend1/hello");

        assertThat(result.orElseThrow().getName()).isEqualTo("backend-service-1");
    }

    @Test
    void doesNotMatchAPathThatOnlySharesTheBeginning() {
        RouteResolver resolver = resolverWith(route("backend-service-1", "/backend1"));

        assertThat(resolver.resolve("/backend10/hello")).isEmpty();
    }

    @Test
    void longestMatchingPrefixWins() {
        RouteResolver resolver = resolverWith(
                route("api", "/api"),
                route("admin", "/api/admin"));

        Optional<ProxyProperties.Route> result = resolver.resolve("/api/admin/users");

        assertThat(result.orElseThrow().getName()).isEqualTo("admin");
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        RouteResolver resolver = resolverWith(route("backend-service-1", "/backend1"));

        assertThat(resolver.resolve("/xyz")).isEmpty();
    }
}