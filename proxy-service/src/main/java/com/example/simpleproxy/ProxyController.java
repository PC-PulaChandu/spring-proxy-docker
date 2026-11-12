package com.example.simpleproxy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.Set;

@RestController
@RequestMapping("/**")
@RequiredArgsConstructor
public class ProxyController {

    // Headers that belong to one connection only; they must not be forwarded
    private static final Set<String> SKIPPED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host",
            "content-length", "expect");

    private final RestClient restClient;
    private final MonitoringRecordRepository recordRepository;

    @RequestMapping
    public ResponseEntity<byte[]> proxyRequest(HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        String requestUri = request.getRequestURI();
        String targetService;
        String backendUrl;

        // Simple routing based on URI path
        if (requestUri.startsWith("/backend1")) {
            targetService = "backend-service-1";
            backendUrl = "http://backend-service-1:8080";
        } else if (requestUri.startsWith("/backend2")) {
            targetService = "backend-service-2";
            backendUrl = "http://backend-service-2:8080";
        } else {
            logRecord(request, "unknown", 404);
            return ResponseEntity.notFound().build();
        }

        try {
            // Everything after "/backendN" becomes the path on the backend
            String pathSuffix = "";
            int slashIndex = requestUri.indexOf("/", 1);
            if (slashIndex != -1) {
                pathSuffix = requestUri.substring(slashIndex);
            }

            URI targetUri = UriComponentsBuilder.fromUriString(backendUrl)
                    .path(pathSuffix)
                    .query(request.getQueryString())
                    .build(true).toUri();

            HttpHeaders requestHeaders = copyRequestHeaders(request);

            RestClient.RequestBodySpec spec = restClient
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(targetUri)
                    .headers(h -> h.addAll(requestHeaders));

            if (body != null && body.length > 0) {
                spec.body(body);
            }

            // exchange() does NOT throw on 4xx/5xx, so the real status is passed through
            ResponseEntity<byte[]> response = spec
                    .exchange((clientRequest, clientResponse) -> ResponseEntity.status(clientResponse.getStatusCode())
                            .headers(filterHeaders(clientResponse.getHeaders()))
                            .body(clientResponse.bodyTo(byte[].class)));

            logRecord(request, targetService, response.getStatusCode().value());
            return response;

        } catch (ResourceAccessException e) {
            // Backend is down or unreachable
            logRecord(request, targetService, 502);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Bad gateway: " + targetService + " is unreachable")
                            .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logRecord(request, targetService, 500);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error processing request: " + e.getMessage())
                            .getBytes(StandardCharsets.UTF_8));
        }
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

    private void logRecord(HttpServletRequest request, String targetService, int httpStatus) {
        MonitoringRecord record = new MonitoringRecord();
        record.setClientIp(request.getRemoteAddr());
        record.setRequestPath(request.getRequestURI());
        record.setHttpMethod(request.getMethod());
        record.setTargetService(targetService);
        record.setHttpStatus(httpStatus);
        record.setTimestamp(LocalDateTime.now());
        recordRepository.save(record);
    }
}