package com.example.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public sealed interface LivePersonResponse permits LivePersonResponse.ConsumerResponse,
        LivePersonResponse.ConversationResponse,
        LivePersonResponse.PublishMessageResponse,
        LivePersonResponse.CloseConversationResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConsumerResponse(String id) implements LivePersonResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConversationResponse(String id, String conversationId, List<Dialog> dialogs) implements LivePersonResponse {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Dialog(String id) {
        }

        public String resolvedId() {
            return id != null ? id : conversationId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PublishMessageResponse(String conversationId, String dialogId, String messageId) implements LivePersonResponse {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CloseConversationResponse(String id, String status) implements LivePersonResponse {
    }
}

