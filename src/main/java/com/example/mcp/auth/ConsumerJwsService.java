package com.example.mcp.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConsumerJwsService {
    private final RestClient restClient;
    private final String accountId;
    private final String idpDomain;
    private final AppJwtService appJwtService;
    private final ConcurrentHashMap<String, ConsumerIdentity> cache = new ConcurrentHashMap<>();

    public ConsumerJwsService(RestClient.Builder builder,
                              @Value("${lp.account-id}") String accountId,
                              @Value("${lp.domains.idp}") String idpDomain,
                              AppJwtService appJwtService) {
        this.restClient = builder.build();
        this.accountId = accountId;
        this.idpDomain = idpDomain;
        this.appJwtService = appJwtService;
    }

    /** Returns a cached Consumer JWS for the given external consumer id, creating it if needed. */
    public ConsumerIdentity getConsumerJws(String extConsumerId) {
        return cache.computeIfAbsent(extConsumerId, id -> {
            String url = "https://" + idpDomain + "/api/account/" + accountId + "/consumer?v=1.0";
            String appJwt = appJwtService.getAppJwt();
            TokenResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", appJwt)
                    .body(Map.of("ext_consumer_id", extConsumerId))
                    .retrieve()
                    .body(TokenResponse.class);
            String token = response.token();
            return new ConsumerIdentity(token, extractLpConsumerId(token));
        });
    }

    private static String extractLpConsumerId(String jwt) {
        final ObjectReader reader = new ObjectMapper().reader();
        // jwt = header.payload.signature
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) return null;
        byte[] json = Base64.getUrlDecoder().decode(parts[1]);
        try {
            JsonNode claims = reader.readTree(new String(json, StandardCharsets.UTF_8));
            JsonNode lpConsumerId = claims.get("lp_consumer_id");
            return lpConsumerId != null && !lpConsumerId.isNull() ? lpConsumerId.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record ConsumerIdentity(String token, String lpConsumerId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private sealed interface JwsResponse permits TokenResponse {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(String token) implements JwsResponse {}
}
