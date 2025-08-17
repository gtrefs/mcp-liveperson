package com.example.mcp.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AppJwtService {

    private final RestClient restClient;
    private final String accountId;
    private final String sentinelDomain;
    private final String clientId;
    private final String clientSecret;
    private final int renewSkewSeconds;

    private final AtomicReference<Token> cached = new AtomicReference<>();

    public AppJwtService(RestClient.Builder restClientBuilder,
                         @Value("${lp.account-id}") String accountId,
                         @Value("${lp.domains.sentinel}") String sentinelDomain,
                         @Value("${lp.auth.client-id}") String clientId,
                         @Value("${lp.auth.client-secret}") String clientSecret,
                         @Value("${lp.auth.renew-skew-seconds:300}") int renewSkewSeconds) {

        this.restClient = restClientBuilder.build();
        this.accountId = accountId;
        this.sentinelDomain = sentinelDomain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.renewSkewSeconds = renewSkewSeconds;
    }

    /** Returns the Authorization header value (optionally "Bearer ...") synchronously. */
    public String getAppJwt() {
        Token current = cached.get();
        Instant now = Instant.now();

        if (current != null && current.expiresAt().minusSeconds(renewSkewSeconds).isAfter(now)) {
            return current.value();
        }

        // Fetch a new token and cache it
        Token fresh = requestNewToken();
        cached.set(fresh);
        return fresh.value();
    }

    private Token requestNewToken() throws RestClientException {
        String url = "https://" + sentinelDomain +
                "/sentinel/api/account/" + accountId +
                "/app/token?v=1.0&grant_type=client_credentials";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse resp = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        return new Token(resp.accessToken(), Instant.now().plusSeconds(resp.expiresIn()));
    }

    record Token(String value, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    sealed interface AuthResponse permits TokenResponse {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") int expiresIn) implements AuthResponse {
    }

}
