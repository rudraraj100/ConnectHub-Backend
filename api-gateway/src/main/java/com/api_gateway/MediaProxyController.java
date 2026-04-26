package com.api_gateway;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * MediaProxyController — handles /media/upload at the gateway level.
 *
 * The WebMVC-based Spring Cloud Gateway (spring-cloud-starter-gateway-server-webmvc)
 * cannot proxy multipart requests via its standard route mechanism because:
 *   - Tomcat parses the multipart body before the gateway route filter sees it.
 *   - The ProxyExchange re-sends a raw InputStream that is already consumed.
 *
 * This controller intercepts /media/upload BEFORE the gateway routing filter,
 * reads the parsed MultipartFile from Tomcat, and manually re-assembles + forwards
 * it to the media-service using RestTemplate + MultiValueMap (which re-encodes
 * the multipart body correctly on the outgoing HTTP call).
 *
 * All other /media/** routes (GET, DELETE) are still proxied via the standard gateway
 * routes — they don't have a multipart body and work fine.
 *
 * Security note: JWT validation + X-User-Id injection happens in JwtGatewayFilter
 * which runs BEFORE this controller (higher precedence = lower order number).
 * By the time a request reaches this controller, the user is already authenticated.
 */
@RestController
@RequestMapping("/media")
@Slf4j
public class MediaProxyController {

    private static final String MEDIA_SERVICE_BASE = "http://localhost:8084";

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5s connect timeout
        factory.setReadTimeout(30_000);     // 30s read timeout (allow large files)
        return new RestTemplate(factory);
    }

    /**
     * POST /media/upload — re-streams the multipart body to media-service.
     *
     * JwtGatewayFilter has already:
     *  - validated the Bearer token
     *  - injected X-User-Id into the request headers (accessible via HttpServletRequest)
     *
     * We forward X-User-Id so media-service knows who uploaded the file.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> proxyUpload(HttpServletRequest request) {

        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            log.warn("[MediaProxy] Received non-multipart request on /media/upload");
            return ResponseEntity.badRequest().body("{\"error\":\"Expected multipart/form-data request\"}");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // ── Forward the file parts ───────────────────────────────────
            Iterator<String> fileNames = multipartRequest.getFileNames();
            while (fileNames.hasNext()) {
                String fieldName = fileNames.next();
                MultipartFile mpFile = multipartRequest.getFile(fieldName);
                if (mpFile != null && !mpFile.isEmpty()) {
                    byte[] bytes = mpFile.getBytes();
                    ByteArrayResource resource = new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return mpFile.getOriginalFilename();
                        }
                    };

                    HttpHeaders partHeaders = new HttpHeaders();
                    if (mpFile.getContentType() != null) {
                        partHeaders.setContentType(MediaType.parseMediaType(mpFile.getContentType()));
                    }
                    body.add(fieldName, new HttpEntity<>(resource, partHeaders));
                }
            }

            // ── Forward text/form fields (roomId, uploaderId, messageId) ──
            Map<String, String[]> params = multipartRequest.getParameterMap();
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                for (String value : entry.getValue()) {
                    body.add(entry.getKey(), value);
                }
            }

            // ── Inject gateway-authenticated user ID ─────────────────────
            // X-User-Id was injected by JwtGatewayFilter into this request.
            // We also pass it as a form field so media-service can read it
            // even if the downstream RestTemplate call doesn't preserve custom headers.
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !body.containsKey("uploaderId")) {
                body.add("uploaderId", userId);
            }

            // ── Build headers for downstream call ────────────────────────
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (userId != null) {
                headers.set("X-User-Id", userId);
            }

            HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<>(body, headers);

            log.info("[MediaProxy] Forwarding upload to media-service for userId={}", userId);

            ResponseEntity<String> response = restTemplate.exchange(
                    MEDIA_SERVICE_BASE + "/media/upload",
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[MediaProxy] media-service returned error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());
        } catch (IOException e) {
            log.error("[MediaProxy] Failed to read uploaded file bytes: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"Failed to read uploaded file\"}");
        } catch (Exception e) {
            log.error("[MediaProxy] Unexpected error forwarding upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\":\"Upload proxy failed: " + e.getMessage() + "\"}");
        }
    }
}
