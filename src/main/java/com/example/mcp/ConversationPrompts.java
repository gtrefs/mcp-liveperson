package com.example.mcp;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

@Configuration
public class ConversationPrompts {

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> prompts() {
        var prompt = new McpSchema.Prompt(
                "conversation_flow",
                "Guides the assistant to create a conversation, send messages, and finally close the conversation using the available tools.",
                List.of());

        var promptSpec = new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            var userMessage = new PromptMessage(Role.USER, new TextContent("""
                    Follow this workflow when interacting with LivePerson:
                    1. Generate a random consumerId (for example, a UUID string) and keep it for this workflow.
                    2. Call `create_conversation` with that consumerId, firstName and lastName to start a new conversation. Save the returned conversationId.
                    3. For each user message, call `send_message` supplying the same consumerId, conversationId and the text to send.
                    4. When all messages have been exchanged and the task is complete, call `close_conversation` with that consumerId and conversationId to end the conversation.
                    For a new workflow, repeat from step 1 to obtain a new consumerId.
                    Only call these tools; do not fabricate responses.
                    """));
            return new GetPromptResult("Instructions for the LivePerson conversation workflow", List.of(userMessage));
        });

        return List.of(promptSpec);
    }
}

