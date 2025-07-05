package org.example.mcpclient.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TogetherAIService {

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<McpSyncClient> mcpClients;

    public TogetherAIService(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    public String getChatResponse(String userMessage) {
        try {
            List<Map<String, Object>> tools = collectAvailableTools();
            List<Map<String, Object>> messages = buildMessageHistory(userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 1024);

            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
            }

            ResponseEntity<Map> response = sendApiRequest(requestBody);
            return processInitialResponse(response.getBody(), messages);

        } catch (Exception e) {
            return "Error calling AI service: " + e.getMessage();
        }
    }

    private List<Map<String, Object>> buildMessageHistory(String userMessage) {
        String prompt = buildSystemPrompt();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", prompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI assistant limited to project data using available tools.\n")
                .append("RULES:\n")
                .append("1. Always use tools for info.\n")
                .append("2. No general knowledge.\n")
                .append("3. If no tool returns an answer, say so.\n")
                .append("4. Stick to tool outputs.\n\n")
                .append("Available tools:\n");

        for (McpSyncClient client : mcpClients) {
            client.listTools().tools().forEach(tool -> {
                sb.append("- ").append(tool.name())
                        .append(": ").append(tool.description()).append("\n");
            });
        }

        return sb.toString();
    }

    private List<Map<String, Object>> collectAvailableTools() {
        List<Map<String, Object>> toolsList = new ArrayList<>();

        for (McpSyncClient client : mcpClients) {
            client.listTools().tools().forEach(tool -> {
                Map<String, Object> functionDef = Map.of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", parseSchema(tool.inputSchema())
                );

                toolsList.add(Map.of(
                        "type", "function",
                        "function", functionDef
                ));
            });
        }

        return toolsList;
    }

    private Map<String, Object> parseSchema(Object schemaObj) {
        if (schemaObj instanceof Map<?, ?>) {
            return (Map<String, Object>) schemaObj;
        }

        return Map.of(
                "type", "object",
                "properties", new HashMap<>()
        );
    }

    private ResponseEntity<Map> sendApiRequest(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
    }

    private String processInitialResponse(Map<String, Object> response, List<Map<String, Object>> messages) {
        if (response == null || !response.containsKey("choices")) {
            return "No response from AI model.";
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) return "Empty response.";

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        if (message.containsKey("tool_calls")) {
            return processToolCalls(message, messages);
        }

        return (String) message.get("content");
    }

    private String processToolCalls(Map<String, Object> message, List<Map<String, Object>> messages) {
        try {
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            messages.add(message); // add the assistant's tool-call intent

            for (Map<String, Object> call : toolCalls) {
                String callId = (String) call.get("id");
                Map<String, Object> func = (Map<String, Object>) call.get("function");

                String toolName = (String) func.get("name");
                String args = (String) func.get("arguments");

                String result = executeTool(toolName, args);

                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", callId,
                        "content", result
                ));
            }

            return sendFollowUpRequest(messages);

        } catch (Exception e) {
            return "Tool execution failed: " + e.getMessage();
        }
    }

    private String executeTool(String name, String args) {
        for (McpSyncClient client : mcpClients) {
            if (client.listTools().tools().stream().anyMatch(t -> t.name().equals(name))) {
                try {
                    McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, args));

                    if (!result.content().isEmpty() && result.content().get(0) instanceof McpSchema.TextContent text) {
                        return text.text();
                    }

                    return "Tool executed but returned no content.";

                } catch (Exception e) {
                    return "Error executing tool: " + e.getMessage();
                }
            }
        }

        return "Tool not found: " + name;
    }

    private String sendFollowUpRequest(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 1024);

            ResponseEntity<Map> response = sendApiRequest(requestBody);
            return extractMessageContent(response.getBody());

        } catch (Exception e) {
            return "Follow-up call failed: " + e.getMessage();
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
        if (response == null || !response.containsKey("choices")) return "No final response.";
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) return "Empty final response.";
        return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
    }
}
