package com.example.simpleproxy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Enumeration;

@RestController
@RequestMapping("/**")
@RequiredArgsConstructor
public class ProxyController {

    private final RestTemplate restTemplate;
    private final MonitoringRecordRepository recordRepository;

    @RequestMapping
    public ResponseEntity<String> proxyRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String targetService = null;
        String backendUrl = null;

        // Simple routing based on URI path
        if (requestUri.startsWith("/backend1")) {
            targetService = "backend-service-1";
            backendUrl = "http://backend-service-1:8080";
        } else if (requestUri.startsWith("/backend2")) {
            targetService = "backend-service-2";
            backendUrl = "http://backend-service-2:8080";
        } else {
            // Log and return 404 for un-routable requests
            logRecord(request, "unknown", 404);
            return ResponseEntity.notFound().build();
        }

        try {
            // Safely calculate path suffix to avoid Range error
            String pathSuffix = "";
            int slashIndex = requestUri.indexOf("/", 1);
            if (slashIndex != -1) {
                pathSuffix = requestUri.substring(slashIndex);
            }

            // Build the target URI
            URI targetUri = UriComponentsBuilder.fromUriString(backendUrl)
                    .path(pathSuffix)
                    .query(request.getQueryString())
                    .build(true).toUri();

            // Copy all headers from the original request
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.set(headerName, request.getHeader(headerName));
            }

            // Create the request entity to be sent to the backend
            RequestEntity<String> requestEntity = new RequestEntity<>(
                    (String) request.getAttribute("body"),
                    headers,
                    HttpMethod.valueOf(request.getMethod()),
                    targetUri);

            // Forward the request and get the response
            ResponseEntity<String> responseEntity = restTemplate.exchange(targetUri,
                    HttpMethod.valueOf(request.getMethod()), requestEntity, String.class);

            // Log the request and response status
            logRecord(request, targetService, responseEntity.getStatusCode().value());

            // Return the response from the backend
            return responseEntity;

        } catch (Exception e) {
            // Log errors and return a 500 status code
            logRecord(request, targetService, 500);
            return ResponseEntity.status(500).body("Error processing request: " + e.getMessage());
        }
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

