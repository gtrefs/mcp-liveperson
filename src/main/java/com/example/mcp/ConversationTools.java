package com.example.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ConversationTools {
    private final LivePersonRestClient lp;
    private final String brandId;
    private final ConversationResourceService resources;
    private static final Logger log = LoggerFactory.getLogger(ConversationTools.class);

    public ConversationTools(LivePersonRestClient lp,
                             ConversationResourceService resources,
                             @Value("${lp.account-id}") String brandId) {
        this.lp = lp;
        this.resources = resources;
        this.brandId = brandId;
    }

    @Tool(
            name = "create_conversation",
            description = "Create a conversation via Messaging REST API. Returns conversationId and mainDialogId."
    )
    public CreateConversationResult createConversation(CreateConversationArgs args) {
        Map<String, Object> consumerPayload = Map.of(
                "firstName", args.firstName(),
                "lastName", args.lastName(),
                "brandId", brandId,
                "acr", "0"
        );

        log.info("putConsumer start");
        Map<String, Object> consumerRes = lp.putConsumer(args.consumerId(), consumerPayload);
        log.info("putConsumer completed: {}", consumerRes);

        Map<String, Object> conv = lp.createConversation(
                args.consumerId(),
                Map.of("channelType", "MESSAGING")
        );
        log.info("createConversation completed: {}", conv);

        String conversationId = (String) conv.getOrDefault("id", conv.get("conversationId"));
        String mainDialogId = conversationId; // fallback

        Object dialogs = conv.get("dialogs");
        if (dialogs instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                Object id = m.get("id");
                if (id instanceof String s) {
                    mainDialogId = s;
                }
            }
        }
        resources.registerConversation(args.consumerId(), conversationId);
        return new CreateConversationResult(conversationId, mainDialogId, "CREATED");
    }

    @Tool(
            name = "send_message",
            description = "Publish a PLAIN_TEXT message via REST API. If dialogId omitted, we'll resolve it from the conversation."
    )
    public Map<String, Object> sendMessage(SendMessageArgs args) {
        Map<String, Object> body = Map.of(
                "type", "PLAIN_TEXT",
                "content", Map.of("text", args.text())
        );
        return lp.publishMessage(args.consumerId(), args.conversationId(), body);
    }

    @Tool(
            name = "close_conversation",
            description = "Close a conversation via REST API using StageUpdate."
    )
    public CloseConversationResult closeConversation(CloseConversationArgs args) {
        // Needs headers for ETag — use a method that returns ResponseEntity<Map>
        ResponseEntity<Map> entity = lp.getConversationEntity(args.consumerId(), args.conversationId());
        String etag = entity.getHeaders().getFirst("ETag");

        lp.closeConversation(args.consumerId(), args.conversationId(), etag);
        resources.unregisterConversation(args.consumerId(), args.conversationId());
        return new CloseConversationResult(args.conversationId(), "CLOSED");
    }

    // === Records ===
    public record CreateConversationArgs(String consumerId, String firstName, String lastName) {}
    public record CreateConversationResult(String conversationId, String dialogId, String status) {}
    public record SendMessageArgs(String consumerId, String conversationId, String text) {}
    public record CloseConversationArgs(String consumerId, String conversationId) {}
    public record CloseConversationResult(String conversationId, String status) {}
}
