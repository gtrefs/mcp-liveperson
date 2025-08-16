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
                    Start the interactive LivePerson workflow:
                       1. Generate a random consumerId (for example, a UUID string) and keep it for this workflow.
                       2. Ask the user for their first name and last name.
                       3. Once the user provides the names, call create_conversation using the new consumerId and the provided names.
                       4. Ask the user for the message they want to send.
                       5. Once the user provides the message, call send_message.
                       6. Ask the user if they are finished. If they are, call close_conversation. If not, repeat from step 4.
                    For a new workflow, repeat from step 1 to obtain a new consumerId.
                    Only call these tools; do not fabricate responses.
                    """));
            return new GetPromptResult("Instructions for the LivePerson conversation workflow", List.of(userMessage));
        });

        return List.of(promptSpec);
    }
}

