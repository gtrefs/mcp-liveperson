package com.example.mcp;

import com.example.mcp.auth.AppJwtService;
import com.example.mcp.auth.ConsumerJwsService;
import com.example.mcp.auth.ConsumerJwsService.ConsumerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;

@Component
public class LivePersonRestClient {

    private static final Logger logger = LoggerFactory.getLogger(LivePersonRestClient.class);

    private final RestClient restClient;
    private final String restDomain;
    private final String accountId;
    private final String clientSource;
    private final AppJwtService appJwtService;
    private final ConsumerJwsService consumerJwsService;

    public LivePersonRestClient(RestClient.Builder restClientBuilder,
                                @Value("${lp.domains.messaging}") String restDomain,
                                @Value("${lp.account-id}") String accountId,
                                @Value("${lp.client.client-source}") String clientSource,
                                AppJwtService appJwtService,
                                ConsumerJwsService consumerJwsService) {
        this.restClient = restClientBuilder.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
        this.restDomain = restDomain;
        this.accountId = accountId;
        this.clientSource = clientSource;
        this.appJwtService = appJwtService;
        this.consumerJwsService = consumerJwsService;
    }

    private HttpHeaders baseHeaders(String consumerId) {
        ConsumerIdentity consumerJws = consumerJwsService.getConsumerJws(consumerId);
        String appJwt = appJwtService.getAppJwt(); // If still reactive

        logger.info("AppJwt: {}", appJwt);
        logger.info("ConsumerJws: {}", consumerJws);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", appJwt);
        headers.add("LP-ON-BEHALF", consumerJws.token());
        headers.add("Brand-ID", accountId);
        headers.add("Client-source", clientSource);
        headers.add("Request-ID", UUID.randomUUID().toString());
        return headers;
    }

    private String baseUrl() {
        return "https://" + restDomain + "/messaging";
    }

    // --- Consumers ---
    public Map putConsumer(String consumerId, Map<String, Object> body) {
        ConsumerIdentity identity = consumerJwsService.getConsumerJws(consumerId);
        String url = baseUrl() + "/v1/consumers/" + identity.lpConsumerId();

        return restClient.put()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    // --- Conversations ---
    public Map createConversation(String consumerId, Map<String, Object> body) {
        ConsumerIdentity identity = consumerJwsService.getConsumerJws(consumerId);
        String url = baseUrl() + "/v1/consumers/" + identity.lpConsumerId() + "/conversations";

        Map result = restClient.post()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .body(body)
                .retrieve()
                .body(Map.class);

        logger.info("Create conversation: {}", result);
        return result;
    }

    public Map getConversationRaw(String consumerId, String convId) {
        String url = baseUrl() + "/v1/conversations/" + convId;
        return restClient.get()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .retrieve()
                .body(Map.class);
    }

    public Map closeConversation(String consumerId, String convId, String etag) {
        String url = baseUrl() + "/v1/conversations/" + convId;
        Map<String, Object> stageUpdate = Map.of("stage", "CLOSE");

        return restClient.patch()
                .uri(url)
                .headers(h -> {
                    h.addAll(baseHeaders(consumerId));
                    h.add("If-Match", etag);
                })
                .body(stageUpdate)
                .retrieve()
                .body(Map.class);
    }

    // --- Dialogs & Messages ---
    public Map<String, Object> publishMessage(String consumerId, String convId, Map<String, Object> body) {
        String url = baseUrl() + "/v1/conversations/" + convId + "/dialogs/" + convId + "/messages";

        return restClient.post()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public ResponseEntity<Map> getConversationEntity(String consumerId, String convId) {
        String url = baseUrl() + "/v1/conversations/" + convId;
        return restClient.get()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .retrieve()
                .toEntity(Map.class);
    }

    // --- Messages ---
    public Map<String, Object> getDialogMessages(String consumerId, String convId, String dialogId) {
        String url = baseUrl() + "/v1/conversations/" + convId + "/dialogs/" + dialogId + "/messages";
        return restClient.get()
                .uri(url)
                .headers(h -> h.addAll(baseHeaders(consumerId)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
