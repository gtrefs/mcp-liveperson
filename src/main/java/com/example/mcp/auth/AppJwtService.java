package com.example.mcp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AppJwtService {

    private final RestClient restClient;
    private final String accountId;
    private final String sentinelDomain;
    private final String clientId;
    private final String clientSecret;
    private final int renewSkewSeconds;
    private final boolean useBearerPrefix;

    private static final record Token(String value, Instant expiresAt) {}

    private final AtomicReference<Token> cached = new AtomicReference<>();

    public AppJwtService(RestClient.Builder restClientBuilder,
                         @Value("${lp.account-id}") String accountId,
                         @Value("${lp.domains.sentinel}") String sentinelDomain,
                         @Value("${lp.auth.client-id}") String clientId,
                         @Value("${lp.auth.client-secret}") String clientSecret,
                         @Value("${lp.auth.renew-skew-seconds:300}") int renewSkewSeconds,
                         @Value("${lp.auth.use-bearer-prefix:false}") boolean useBearerPrefix) {

        this.restClient = restClientBuilder.build();
        this.accountId = accountId;
        this.sentinelDomain = sentinelDomain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.renewSkewSeconds = renewSkewSeconds;
        this.useBearerPrefix = useBearerPrefix;
    }

    /** Returns the Authorization header value (optionally "Bearer ...") synchronously. */
    public String getAppJwt() {
        Token current = cached.get();
        Instant now = Instant.now();

        if (current != null && current.expiresAt().minusSeconds(renewSkewSeconds).isAfter(now)) {
            return headerValue(current.value());
        }

        // Fetch a new token and cache it
        Token fresh = requestNewToken();
        cached.set(fresh);
        return headerValue(fresh.value());
    }

    private Token requestNewToken() throws RestClientException {
        String url = "https://" + sentinelDomain +
                "/sentinel/api/account/" + accountId +
                "/app/token?v=1.0&grant_type=client_credentials";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> resp = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        String accessToken = (String) resp.get("access_token");
        int expiresIn = Integer.parseInt(String.valueOf(resp.getOrDefault("expires_in", 3600)));

        return new Token(accessToken, Instant.now().plusSeconds(expiresIn));
    }

    private String headerValue(String token) {
        return useBearerPrefix ? ("Bearer " + token) : token;
    }
}
