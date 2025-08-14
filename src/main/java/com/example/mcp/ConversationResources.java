package com.example.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
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
    public McpServerFeatures.SyncResourceAdapter conversationResourceAdapter() {
        return new McpServerFeatures.SyncResourceAdapter(
                exchange -> service.listResources(),
                (exchange, request) -> service.getResource(request.getUri()),
                (exchange, request, consumer) -> service.subscribe(request.getUri(), consumer)
        );
    }
}

