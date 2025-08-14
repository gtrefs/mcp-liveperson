package com.example.mcp;

import org.springframework.ai.prompt.annotation.Prompt;
import org.springframework.stereotype.Component;

@Component
public class ConversationPrompts {

    @Prompt(
            name = "conversation_flow",
            description = "Guides the assistant to create a conversation, send messages, and finally close the conversation using the available tools."
    )
    public String conversationFlow() {
        return """
                Follow this workflow when interacting with LivePerson:
                1. Generate a random consumerId (for example, a UUID string) and keep it for this workflow.
                2. Call `create_conversation` with that consumerId, firstName and lastName to start a new conversation. Save the returned conversationId.
                3. For each user message, call `send_message` supplying the same consumerId, conversationId and the text to send.
                4. When all messages have been exchanged and the task is complete, call `close_conversation` with that consumerId and conversationId to end the conversation.
                For a new workflow, repeat from step 1 to obtain a new consumerId.
                Only call these tools; do not fabricate responses.
                """;
    }
}
