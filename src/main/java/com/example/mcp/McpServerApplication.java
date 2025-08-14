package com.example.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.prompt.PromptCallbackProvider;
import org.springframework.ai.prompt.method.MethodPromptCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider conversationToolsProvider(ConversationTools tools) {
        // Auto-expose @Tool methods as MCP tools
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    PromptCallbackProvider conversationPromptsProvider(ConversationPrompts prompts) {
        // Auto-expose @Prompt methods as MCP prompts
        return MethodPromptCallbackProvider.builder().promptObjects(prompts).build();
    }
}
