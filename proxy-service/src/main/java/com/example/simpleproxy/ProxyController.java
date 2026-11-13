package com.example.simpleproxy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/**")
@RequiredArgsConstructor
public class ProxyController {

    // Headers that belong to one connection only; they must not be forwarded
    private static final Set<String> SKIPPED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host",
            "content-length", "expect");

    // Only these methods are safe to repeat on another instance
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE");

    private final RestClient restClient;
    private final RequestLogService requestLogService;
    private final RouteResolver routeResolver;
    private final UpstreamSelector upstreamSelector;
    private final UpstreamHealthTracker healthTracker;
    private final ProxyProperties properties;

    @RequestMapping
    public ResponseEntity<byte[]> proxyRequest(HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        long startNanos = System.nanoTime();
        String requestUri = request.getRequestURI();

        Optional<ProxyProperties.Route> match = routeResolver.resolve(requestUri);
        if (match.isEmpty()) {
            logRecord(request, "unknown", 404, startNanos);
            return ResponseEntity.notFound().build();
        }
        ProxyProperties.Route route = match.get();
        String targetService = route.getName();
        String pathSuffix = requestUri.substring(route.getPrefix().length());
        HttpHeaders requestHeaders = copyRequestHeaders(request);

        // Prefer instances that did not fail recently; if all failed, try them all
        List<String> candidates = new ArrayList<>(
                route.getUrls().stream().filter(healthTracker::isAvailable).toList());
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(route.getUrls());
        }

        boolean canRetry = IDEMPOTENT_METHODS.contains(request.getMethod());
        int maxAttempts = canRetry
                ? Math.max(1, Math.min(properties.getMaxAttempts(), candidates.size()))
                : 1;

        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String backendUrl = null;
            try {
                backendUrl = upstreamSelector.select(candidates);
                candidates.remove(backendUrl); // never try the same instance twice

                ResponseEntity<byte[]> response = forward(request, body, requestHeaders, backendUrl, pathSuffix);

                healthTracker.markHealthy(backendUrl);
                logRecord(request, targetService, response.getStatusCode().value(), startNanos);
                return response;

            } catch (ResourceAccessException e) {
                // Instance unreachable or too slow: remember it and try another one
                healthTracker.markFailed(backendUrl);
                lastError = e;
                log.warn("Attempt {}/{} failed for {} via {}: {}",
                        attempt, maxAttempts, targetService, backendUrl, e.getMessage());

            } catch (Exception e) {
                logRecord(request, targetService, 500, startNanos);
                return ResponseEntity.status(500)
                        .body(("Error processing request: " + e.getMessage())
                                .getBytes(StandardCharsets.UTF_8));
            }
        }

        // Every attempt failed
        boolean timeout = isTimeout(lastError);
        int status = timeout ? 504 : 502;
        String message = timeout
                ? "Gateway timeout: " + targetService + " did not respond in time"
                : "Bad gateway: " + targetService + " is unreachable";
        logRecord(request, targetService, status, startNanos);
        return ResponseEntity.status(status).body(message.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body,
            HttpHeaders headers, String backendUrl,
            String pathSuffix) {
        URI targetUri = UriComponentsBuilder.fromUriString(backendUrl)
                .path(pathSuffix)
                .query(request.getQueryString())
                .build(true).toUri();

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(targetUri)
                .headers(h -> h.addAll(headers));

        if (body != null && body.length > 0) {
            spec.body(body);
        }

        // exchange() does not throw on 4xx/5xx, so the real status is passed through
        return spec.exchange((clientRequest, clientResponse) -> ResponseEntity.status(clientResponse.getStatusCode())
                .headers(filterHeaders(clientResponse.getHeaders()))
                .body(clientResponse.bodyTo(byte[].class)));
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private HttpHeaders copyRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (SKIPPED_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        headers.add("X-Forwarded-For", request.getRemoteAddr());
        return headers;
    }

    private HttpHeaders filterHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!SKIPPED_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> headers.add(name, value));
            }
        });
        return headers;
    }

    private void logRecord(HttpServletRequest request, String targetService,
            int httpStatus, long startNanos) {
        MonitoringRecord record = new MonitoringRecord();
        record.setClientIp(request.getRemoteAddr());
        record.setRequestPath(request.getRequestURI());
        record.setHttpMethod(request.getMethod());
        record.setTargetService(targetService);
        record.setHttpStatus(httpStatus);
        record.setTimestamp(LocalDateTime.now());
        record.setDurationMs((System.nanoTime() - startNanos) / 1_000_000);
        requestLogService.log(record);
    }
}