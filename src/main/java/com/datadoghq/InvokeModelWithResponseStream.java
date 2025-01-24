package com.datadoghq;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.json.JSONObject;
import org.json.JSONPointer;

import io.github.cdimascio.dotenv.Dotenv;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler.Visitor;

public class InvokeModelWithResponseStream {

    private static final List<JSONObject> conversationHistory = new ArrayList<>();

    public static String invokeModelWithResponseStream(String prompt, double temperature, int maxTokens) throws ExecutionException, InterruptedException {

        // Add the user's message to the conversation history.
        conversationHistory.add(new JSONObject().put("role", "user").put("content", prompt));

        // Create a Bedrock Runtime client in the AWS Region you want to use.
        var client = BedrockRuntimeAsyncClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();

        // Set the model ID, e.g., Claude 3 Haiku.
        var modelId = "anthropic.claude-3-haiku-20240307-v1:0";

        // Prepare the JSON array of conversation history for the request.
        StringBuilder messagesArray = new StringBuilder("[");
        for (JSONObject message : conversationHistory) {
            messagesArray.append(message.toString()).append(",");
        }
        // Remove the trailing comma and close the array.
        if (messagesArray.length() > 1) {
            messagesArray.setLength(messagesArray.length() - 1);
        }
        messagesArray.append("]");

        // The InvokeModelWithResponseStream API uses the model's native payload.
        var nativeRequestTemplate = """
                {
                    "anthropic_version": "bedrock-2023-05-31",
                    "max_tokens": {{maxTokens}},
                    "temperature": {{temperature}},
                    "messages": {{messages}}
                }""";

        // Embed the conversation history in the model's native request payload.
        String nativeRequest = nativeRequestTemplate
                .replace("{{messages}}", messagesArray.toString())
                .replace("{{temperature}}", String.valueOf(temperature))
                .replace("{{maxTokens}}", String.valueOf(maxTokens));

        // Create a request with the model ID and the model's native request payload.
        var request = InvokeModelWithResponseStreamRequest.builder()
                .body(SdkBytes.fromUtf8String(nativeRequest))
                .modelId(modelId)
                .build();

        // Prepare a buffer to accumulate the generated response text.
        var completeResponseTextBuffer = new StringBuilder();

        // Prepare a handler to extract, accumulate, and print the response text in real-time.
        var responseStreamHandler = InvokeModelWithResponseStreamResponseHandler.builder()
                .subscriber(Visitor.builder().onChunk(chunk -> {
                    var response = new JSONObject(chunk.bytes().asUtf8String());

                    // Extract and append the text from the content blocks.
                    if (Objects.equals(response.getString("type"), "content_block_delta")) {
                        var text = new JSONPointer("/delta/text").queryFrom(response);
                        completeResponseTextBuffer.append(text);
                    }
                }).build()).build();

        try {
            // Send the request and wait for the handler to process the response.
            var modelResponse = client.invokeModelWithResponseStream(request, responseStreamHandler).get();

            // Get the complete response text.
            String responseText = completeResponseTextBuffer.toString();

            // Add the assistant's response to the conversation history.
            conversationHistory.add(new JSONObject().put("role", "assistant").put("content", responseText));


            // Return the response text
            return responseText;

        } catch (ExecutionException | InterruptedException e) {
            System.err.printf("Can't invoke '%s': %s", modelId, e.getCause().getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void sendTraceSpan(JSONObject data) {
        try {
            Dotenv dotenv = Dotenv.load();
            String datadogApiKey = dotenv.get("DD_API_KEY");
            if (datadogApiKey == null || datadogApiKey.isEmpty()) {
                throw new IllegalArgumentException("Datadog API key is not set in environment variables.");
            }

            // Wrap the data object in another object with the key "data"
            JSONObject payload = new JSONObject();
            payload.put("data", data);

            // Print the object before sending
            // System.out.println("Sending trace span: " + payload.toString(2));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.datadoghq.com/api/intake/llm-obs/v1/trace/spans"))
                    .header("Content-Type", "application/json")
                    .header("DD-API-KEY", datadogApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 202) {
                System.out.println("Datadog API response status code: " + response.statusCode());
                System.out.println("Datadog API response body: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to send trace span: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String prompt;
        String response;

        // Generate a random positive numeric session ID
        long sessionId = Math.abs(new Random().nextLong());

        while (true) {
            System.out.print("You: ");
            prompt = scanner.nextLine();

            if (prompt.equalsIgnoreCase("exit")) {
                break;
            }

            // Generate a positive trace ID
            String traceId = UUID.randomUUID().toString().replace("-", "");

            // Set user_handle and user_id
            String userHandle = "ryan.richards@datadoghq.com";
            String userId = "1";

            // Set ml_app, service, and env variables
            String mlApp = "dd_java_bedrock_demo";
            String service = "dd_java_bedrock_demo";
            String env = "staging";

            // Initialize the data object
            JSONObject data = new JSONObject();
            data.put("type", "span");
            JSONObject attributes = new JSONObject();
            attributes.put("ml_app", mlApp); 
            attributes.put("session_id", String.valueOf(sessionId));
            attributes.put("tags", List.of(
                "service:" + service,
                "env:" + env,
                "user_handle:" + userHandle,
                "user_id:" + userId
            ));
            attributes.put("spans", new ArrayList<JSONObject>());
            data.put("attributes", attributes);

            // Create an initial agent span
            JSONObject agentSpan = new JSONObject();
            agentSpan.put("parent_id", "undefined");
            agentSpan.put("trace_id", traceId);
            agentSpan.put("span_id", UUID.randomUUID().toString().replace("-", ""));
            agentSpan.put("name", "demo_agent");
            JSONObject agentMeta = new JSONObject();
            agentMeta.put("kind", "agent");
            JSONObject agentInput = new JSONObject();
            agentInput.put("value", prompt);
            agentMeta.put("input", agentInput);
            agentSpan.put("meta", agentMeta);
            long agentStartNs = System.currentTimeMillis() * 1_000_000L;
            agentSpan.put("start_ns", agentStartNs);

            // Add the agent span to the data object
            attributes.getJSONArray("spans").put(agentSpan);

            // Example values for temperature and maxTokens
            double temperature = 0.5;
            int maxTokens = 512;

            // Record the start time for the LLM span
            long startNs = System.currentTimeMillis() * 1_000_000L;

            response = invokeModelWithResponseStream(prompt, temperature, maxTokens);
            System.out.println("Claude: " + response);

            // Calculate the duration for the LLM span
            long durationNs = System.currentTimeMillis() * 1_000_000L - startNs;

            // Create a new span for the LLM response
            JSONObject llmSpan = new JSONObject();
            llmSpan.put("parent_id", agentSpan.getString("span_id").replace("-", ""));
            llmSpan.put("trace_id", traceId);
            llmSpan.put("span_id", UUID.randomUUID().toString().replace("-", ""));
            llmSpan.put("name", "llm_response");
            JSONObject llmMeta = new JSONObject();
            llmMeta.put("kind", "llm");
            JSONObject input = new JSONObject();
            input.put("messages", List.of(
                new JSONObject().put("role", "user").put("content", prompt)
            ));
            llmMeta.put("input", input);
            JSONObject output = new JSONObject();
            output.put("messages", List.of(
                new JSONObject().put("role", "assistant").put("content", response)
            ));
            llmMeta.put("output", output);
            llmMeta.put("metadata", new JSONObject()
                .put("temperature", temperature)
                .put("max_tokens", maxTokens)
                .put("model_name", "Claude 3 Haiku")
                .put("model_provider", "Anthropic"));
            

            // TODO: Capture token count (input, output, total) for LLM spans
            llmMeta.put("metrics", new JSONObject()
                .put("input_tokens", 0) // Placeholder
                .put("output_tokens", 0) // Placeholder
                .put("total_tokens", 0)); // Placeholder


            llmSpan.put("meta", llmMeta);
            llmSpan.put("start_ns", startNs);
            llmSpan.put("duration", durationNs);

            // Add the LLM span to the data object
            attributes.getJSONArray("spans").put(llmSpan);

            // Update the agent span duration and final output
            long agentDurationNs = System.currentTimeMillis() * 1_000_000L - agentStartNs;
            agentSpan.put("duration", agentDurationNs);
            JSONObject agentOutput = new JSONObject();
            agentOutput.put("value", response);
            agentMeta.put("output", agentOutput);

            // Send the trace span
            sendTraceSpan(data);
        }

        scanner.close();
    }
}
