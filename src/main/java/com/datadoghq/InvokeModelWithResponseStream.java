package com.datadoghq;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import org.json.JSONObject;
import org.json.JSONPointer;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler.Visitor;

public class InvokeModelWithResponseStream {

    private static final List<JSONObject> conversationHistory = new ArrayList<>();

    public static String invokeModelWithResponseStream(String prompt) throws ExecutionException, InterruptedException {

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
                    "max_tokens": 512,
                    "temperature": 0.5,
                    "messages": {{messages}}
                }""";

        // Embed the conversation history in the model's native request payload.
        String nativeRequest = nativeRequestTemplate.replace("{{messages}}", messagesArray.toString());

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
            client.invokeModelWithResponseStream(request, responseStreamHandler).get();

            // Get the complete response text.
            String responseText = completeResponseTextBuffer.toString();

            // Add the assistant's response to the conversation history.
            conversationHistory.add(new JSONObject().put("role", "assistant").put("content", responseText));

            // Return the response text.
            return responseText;

        } catch (ExecutionException | InterruptedException e) {
            System.err.printf("Can't invoke '%s': %s", modelId, e.getCause().getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String prompt;
        String response;

        while (true) {
            System.out.print("You: ");
            prompt = scanner.nextLine();

            if (prompt.equalsIgnoreCase("exit")) {
                break;
            }

            response = invokeModelWithResponseStream(prompt);
            System.out.println("Claude: " + response);
        }

        scanner.close();
    }
}
