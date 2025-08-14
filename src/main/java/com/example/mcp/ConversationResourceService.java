package com.example.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceUpdated;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Maintains a resource view of LivePerson agent replies and polls the
 * Messaging REST API for new messages every two seconds. The messages are
 * exposed as an MCP resource using the URI pattern
 * {@code conv://{consumerId}/{conversationId}/messages}.
 */
@Service
public class ConversationResourceService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationResourceService.class);

    private final LivePersonRestClient lp;

    /** Tracks active conversations keyed by their resource URI. */
    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    /** Subscribers waiting for updates on a given conversation resource. */
    private final Map<String, Queue<Consumer<ResourceUpdated>>> subscribers = new ConcurrentHashMap<>();

    public ConversationResourceService(LivePersonRestClient lp) {
        this.lp = lp;
    }

    /** Register a conversation to start polling for messages. */
    public void registerConversation(String consumerId, String conversationId) {
        String uri = uriFor(consumerId, conversationId);
        conversations.computeIfAbsent(uri, u -> new ConversationState(consumerId, conversationId));
    }

    /** Stop polling for a conversation. */
    public void unregisterConversation(String consumerId, String conversationId) {
        String uri = uriFor(consumerId, conversationId);
        conversations.remove(uri);
        subscribers.remove(uri);
    }

    /**
     * Poll the LivePerson API for each active conversation and push updates to
     * subscribers when new agent messages are found.
     */
    @Scheduled(fixedDelay = 2000L)
    void poll() {
        conversations.values().forEach(state -> {
            state.pruneOldMessages();
            try {
                Map<String, Object> resp = lp.getDialogMessages(state.consumerId, state.conversationId, state.conversationId);
                Object arr = resp.get("messages");
                if (arr instanceof List<?> list) {
                    list.forEach(state::ingestMessage);
                }
            } catch (Exception e) {
                logger.warn("Polling messages failed for {}: {}", state.conversationId, e.getMessage());
            }
        });
    }

    /** List all available resources for the MCP server. */
    public List<Resource> listResources() {
        List<Resource> res = new ArrayList<>();
        conversations.values().forEach(state ->
                res.add(new Resource(state.uri,
                        "Conversation messages for consumer " + state.consumerId + " conversation " + state.conversationId,
                        null)));
        return res;
    }

    /** Return the resource data for the given URI. */
    public GetResourceResult getResource(String uri) {
        ConversationState state = conversations.get(uri);
        String text = "";
        if (state != null) {
            state.pruneOldMessages();
            text = state.messages.stream().map(m -> m.text).collect(Collectors.joining("\n"));
        }
        return new GetResourceResult(uri, List.of(new TextResourceContents(text)));
    }

    /** Subscribe for updates to the given resource URI. */
    public void subscribe(String uri, Consumer<ResourceUpdated> sink) {
        subscribers.computeIfAbsent(uri, u -> new ConcurrentLinkedQueue<>()).add(sink);
    }

    private boolean notifySubscribers(String uri, String text) {
        Queue<Consumer<ResourceUpdated>> list = subscribers.get(uri);
        if (list != null && !list.isEmpty()) {
            ResourceUpdated update = new ResourceUpdated(uri, List.of(new TextResourceContents(text)));
            list.forEach(s -> s.accept(update));
            return true;
        }
        return false;
    }

    private static String uriFor(String consumerId, String conversationId) {
        return "conv://" + consumerId + "/" + conversationId + "/messages";
    }

    /**
     * Internal state holder for a conversation's messages.
     */
    private class ConversationState {
        final String consumerId;
        final String conversationId;
        final String uri;
        long lastSeq = 0L;
        final Queue<AgentMessage> messages = new ConcurrentLinkedQueue<>();

        ConversationState(String consumerId, String conversationId) {
            this.consumerId = consumerId;
            this.conversationId = conversationId;
            this.uri = uriFor(consumerId, conversationId);
        }

        void pruneOldMessages() {
            long now = System.currentTimeMillis();
            messages.removeIf(m -> now - m.timestamp > MESSAGE_TTL_MS);
        }

        @SuppressWarnings("unchecked")
        void ingestMessage(Object messageObj) {
            if (!(messageObj instanceof Map<?, ?> m)) return;

            Number seqNum = (Number) m.getOrDefault("sequence", 0);
            long seq = seqNum.longValue();
            if (seq <= lastSeq) {
                return; // already processed
            }
            lastSeq = seq;

            Map<String, Object> body = (Map<String, Object>) m.getOrDefault("body", Map.of());
            String text = String.valueOf(body.getOrDefault("text", ""));
            String role = String.valueOf(m.getOrDefault("participantRole", ""));

            if ("AGENT".equalsIgnoreCase(role)) {
                AgentMessage msg = new AgentMessage(text);
                messages.add(msg);
                if (notifySubscribers(uri, text)) {
                    messages.remove(msg);
                }
            }
        }
    }

    private static final long MESSAGE_TTL_MS = 5 * 60 * 1000L;

    private static class AgentMessage {
        final String text;
        final long timestamp;

        AgentMessage(String text) {
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }

}

