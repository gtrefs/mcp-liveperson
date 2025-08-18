package com.gtrefs.liveperson.mcp.poc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationTools {
    private final LivePersonRestClient lp;
    private final String brandId;
    private static final Logger log = LoggerFactory.getLogger(ConversationTools.class);

    public ConversationTools(LivePersonRestClient lp, @Value("${lp.account-id}") String brandId) {
        this.lp = lp;
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
        LivePersonResponse.ConsumerResponse consumerRes = lp.putConsumer(args.consumerId(), consumerPayload);
        log.info("putConsumer completed: {}", consumerRes);

        LivePersonResponse.ConversationResponse conv = lp.createConversation(
                args.consumerId(),
                Map.of("channelType", "MESSAGING")
        );
        log.info("createConversation completed: {}", conv);

        String conversationId = conv.resolvedId();
        String mainDialogId = conversationId; // fallback

        List<LivePersonResponse.ConversationResponse.Dialog> dialogs = conv.dialogs();
        if (dialogs != null && !dialogs.isEmpty()) {
            String id = dialogs.getFirst().id();
            if (id != null) {
                mainDialogId = id;
            }
        }
        return new CreateConversationResult(conversationId, mainDialogId, "CREATED");
    }

    @Tool(
            name = "send_message",
            description = "Publish a PLAIN_TEXT message via REST API. If dialogId omitted, we'll resolve it from the conversation."
    )
    public SendMessageResult sendMessage(SendMessageArgs args) {
        Map<String, Object> body = Map.of(
                "type", "PLAIN_TEXT",
                "content", Map.of("text", args.text())
        );
        LivePersonResponse.PublishMessageResponse res =
                lp.publishMessage(args.consumerId(), args.conversationId(), body);
        return new SendMessageResult(res.conversationId(), res.dialogId(), res.messageId());
    }

    @Tool(
            name = "close_conversation",
            description = "Close a conversation via REST API using StageUpdate."
    )
    public CloseConversationResult closeConversation(CloseConversationArgs args) {
        // Needs headers for ETag — use a method that returns typed ResponseEntity
        ResponseEntity<LivePersonResponse.ConversationResponse> entity =
                lp.getConversationEntity(args.consumerId(), args.conversationId());
        String etag = entity.getHeaders().getFirst("ETag");

        lp.closeConversation(args.consumerId(), args.conversationId(), etag);
        return new CloseConversationResult(args.conversationId(), "CLOSED");
    }

    @Tool(
            name = "generate_random_uuid",
            description = "Generates and returns a random UUID."
    )
    public String generateRandomUUID() {
        return UUID.randomUUID().toString();
    }

    // === Records ===
    public record CreateConversationArgs(String consumerId, String firstName, String lastName) {}
    public record CreateConversationResult(String conversationId, String dialogId, String status) {}
    public record SendMessageArgs(String consumerId, String conversationId, String text) {}
    public record SendMessageResult(String conversationId, String dialogId, String messageId) {}
    public record CloseConversationArgs(String consumerId, String conversationId) {}
    public record CloseConversationResult(String conversationId, String status) {}
}
