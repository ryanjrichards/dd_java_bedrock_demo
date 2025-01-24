package com.datadoghq;

import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

public class AppTest {

    @Test
    public void testSendTraceSpan() {
        String samplePayload = """
        {
          "data": {
            "type": "span",
            "attributes": {
              "ml_app": "weather-bot",
              "session_id": "1",
              "tags": [
                "service:weather-bot",
                "env:staging",
                "user_handle:example-user@example.com",
                "user_id:1234"
              ],
              "spans": [
                {
                  "parent_id": "undefined",
                  "trace_id": "<TEST_TRACE_ID>",
                  "span_id": "<AGENT_SPAN_ID>",
                  "name": "health_coach_agent",
                  "meta": {
                    "kind": "agent",
                    "input": {
                      "value": "What is the weather like today and do i wear a jacket?"
                    },
                    "output": {
                      "value": "It's very hot and sunny, there is no need for a jacket"
                    }
                  },
                  "start_ns": 1713889389104152000,
                  "duration": 10000000000
                },
                {
                  "parent_id": "<AGENT_SPAN_ID>",
                  "trace_id": "<TEST_TRACE_ID>",
                  "span_id": "<WORKFLOW_ID>",
                  "name": "qa_workflow",
                  "meta": {
                    "kind": "workflow",
                    "input": {
                      "value": "What is the weather like today and do i wear a jacket?"
                    },
                    "output": {
                      "value":  "It's very hot and sunny, there is no need for a jacket"
                    }
                  },
                  "start_ns": 1713889389104152000,
                  "duration": 5000000000
                },
                {
                  "parent_id": "<WORKFLOW_SPAN_ID>",
                  "trace_id": "<TEST_TRACE_ID>",
                  "span_id": "<LLM_SPAN_ID>",
                  "name": "generate_response",
                  "meta": {
                    "kind": "llm",
                    "input": {
                      "messages": [
                        {
                          "role": "system",
                          "content": "Your role is to ..."
                        },
                        {
                          "role": "user",
                          "content": "What is the weather like today and do i wear a jacket?"
                        }
                      ]
                    },
                    "output": {
                      "messages": [
                        {
                          "content": "It's very hot and sunny, there is no need for a jacket",
                          "role": "assistant"
                        }
                      ]
                    }
                  },
                  "start_ns": 1713889389104152000,
                  "duration": 2000000000
                }
              ]
            }
          }
        }
        """;

        JSONObject data = new JSONObject(samplePayload).getJSONObject("data");

        assertDoesNotThrow(() -> InvokeModelWithResponseStream.sendTraceSpan(data));
    }
}
