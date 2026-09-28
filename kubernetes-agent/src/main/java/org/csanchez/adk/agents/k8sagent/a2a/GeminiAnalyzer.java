package org.csanchez.adk.agents.k8sagent.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.adk.models.BaseLlm;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.LlmRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalyzer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    public static A2AResponse analyze(String modelName, String prompt, String evidenceJson) throws Exception {
        logger.info("Starting Gemini analysis using model: {}", modelName);

        BaseLlm llm = LlmRegistry.getLlm(modelName);
        if (llm == null) {
            throw new IllegalArgumentException("Model not found in registry: " + modelName);
        }

        String fullPrompt = 
            "You are the reasoning engine for a Kubernetes troubleshooting agent.\n" +
            "The Kubernetes evidence below has already been collected deterministically.\n" +
            "Do not request additional Kubernetes tools.\n" +
            "Do not assume missing information. For example, do not invent an arbitrary resource value (like 256Mi) unless the evidence supports it.\n" +
            "For OOMKilled issues:\n" +
            "- clearly identify OOMKilled when Kubernetes reports it\n" +
            "- explain that the container exceeded its memory limit\n" +
            "- propose increasing the memory limit as a possible remediation\n" +
            "- if recommending a specific value, clearly identify it as a proposed value rather than an evidence-derived fact.\n" +
            "For ImagePullBackOff issues:\n" +
            "- The evidence should establish that the current image is invalid.\n" +
            "- The remediation should use a configured/known-valid tag where available. Do not hardcode 'nginx:latest' as a universal rule, prefer explicit stable tags (e.g., '1.27' for nginx) if applicable.\n" +
            "For CrashLoopBackOff issues (especially intentional exits like exit 1 in the command):\n" +
            "- Provide a concrete file change to the manifest (e.g., modifying the deployment yaml).\n" +
            "- For example, replace an intentional failing command with a long-running valid command (e.g., `[\"sh\", \"-c\", \"echo 'Application running'; sleep 3600\"]`) while preserving the image.\n" +
            "- Set 'requiresGitHubPR' to true and populate 'fileChanges' with the valid file modification. Do NOT merely suggest investigating the source code.\n" +
            "For GitHub PRs:\n" +
            "- Only set 'requiresGitHubPR' to true if the User Request EXPLICITLY asks to create a GitHub PR or remediate through GitHub.\n" +
            "- If the User Request only asks to investigate or diagnose (no mention of PR), set 'requiresGitHubPR' to false.\n" +
            "Analyze the supplied evidence and return structured diagnosis and remediation.\n\n" +
            "User Request:\n" + prompt + "\n\n" +
            "Kubernetes Evidence:\n" + evidenceJson + "\n\n" +
            "CRITICAL: You MUST respond with valid JSON matching this exact schema:\n" +
            "{\n" +
            "  \"workloads\": [\n" +
            "    {\n" +
            "      \"affectedWorkload\": \"name of the failing workload/pod\",\n" +
            "      \"namespace\": \"kubernetes namespace\",\n" +
            "      \"failureType\": \"e.g., ImagePullBackOff, CrashLoopBackOff, OOMKilled\",\n" +
            "      \"rootCause\": \"detailed explanation of the root cause\",\n" +
            "      \"evidence\": \"summary of logs/events confirming the root cause\",\n" +
            "      \"remediation\": \"suggested remediation steps\",\n" +
            "      \"confidence\": 100,\n" +
            "      \"fileChanges\": {\"deployment.yaml\": \"... if repository changes are needed ...\"},\n" +
            "      \"requiresGitHubPR\": true/false\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        Content userMsg = Content.fromParts(Part.fromText(fullPrompt));

        LlmRequest request = LlmRequest.builder()
                .contents(java.util.Collections.singletonList(userMsg))
                .build();

        long startTime = System.currentTimeMillis();
        
        // Make the direct API call
        LlmResponse response = llm.generateContent(request, false).blockingSingle();
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Gemini analysis completed in {} ms", duration);

        String responseText = response.content()
            .flatMap(com.google.genai.types.Content::parts)
            .map(parts -> parts.stream()
                .map(part -> part.text().orElse(""))
                .collect(java.util.stream.Collectors.joining()))
            .orElse("");
        if (responseText == null || responseText.isBlank()) {
            throw new IllegalStateException("Empty response from Gemini");
        }

        // Clean up markdown code block if present
        if (responseText.startsWith("```json")) {
            responseText = responseText.substring(7);
        } else if (responseText.startsWith("```")) {
            responseText = responseText.substring(3);
        }
        if (responseText.endsWith("```")) {
            responseText = responseText.substring(0, responseText.length() - 3);
        }
        responseText = responseText.trim();

        // Parse to A2AResponse
        A2AResponse a2aResponse = objectMapper.readValue(responseText, A2AResponse.class);
        return a2aResponse;
    }
}
