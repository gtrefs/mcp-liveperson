package com.example.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the conversation message resource with the MCP server.
 */
@Configuration
public class ConversationResources {

    private final ConversationResourceService service;

    public ConversationResources(ConversationResourceService service) {
        this.service = service;
    }

    @Bean
    public java.util.List<McpServerFeatures.SyncResourceSpecification> conversationResources() {
        McpSchema.Resource template = new McpSchema.Resource(
                "conv://{consumerId}/{conversationId}/messages",
                "LivePerson conversation messages",
                "", "text/plain", null);
        McpServerFeatures.SyncResourceSpecification spec = new McpServerFeatures.SyncResourceSpecification(
                template,
                (exchange, request) -> service.getResource(request.uri()));
        return java.util.List.of(spec);
    }
}

