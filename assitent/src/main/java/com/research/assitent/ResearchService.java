package com.research.assitent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

@Service
public class ResearchService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ResearchService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                           @Value("${gemini.api.url}") String geminiApiUrl,
                           @Value("${gemini.api.key}") String geminiApiKey) {
        this.webClient = webClientBuilder
                .baseUrl(geminiApiUrl) // Set base URL instead of using path()
                .defaultHeader("Content-Type", "application/json") // Ensure JSON request
                .build();
        this.objectMapper = objectMapper;
        this.geminiApiKey = geminiApiKey;
    }

    private final String geminiApiKey;

    public String processContent(ResearchRequest request) {
        String prompt = buildPrompt(request);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );

        try {
            // Debugging logs
            System.out.println("Sending request to Gemini API...");
            System.out.println("Request Body: " + requestBody);

            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", geminiApiKey) // Attach API key as query parameter
                            .build())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
                            Mono.error(new RuntimeException("Gemini API request failed with status: " + clientResponse.statusCode()))
                    )
                    .bodyToMono(String.class)
                    .block();

            System.out.println("Response from Gemini API: " + response); // Debugging log

            return extractTextFromResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String extractTextFromResponse(String response) {
        try {
            GeminiResponse geminiResponse = objectMapper.readValue(response, GeminiResponse.class);
            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                GeminiResponse.Candidate firstCandidate = geminiResponse.getCandidates().get(0);
                if (firstCandidate.getContent() != null && firstCandidate.getContent().getParts() != null &&
                        !firstCandidate.getContent().getParts().isEmpty()) {

                    // Extract the text response
                    String rawText = firstCandidate.getContent().getParts().get(0).getText();

                    // Format the response in a more user-friendly manner
                    return formatResponse(rawText);
                }
            }
            return "Sorry, I couldn't generate a valid response.";
        } catch (Exception e) {
            return "Error Processing Response: " + e.getMessage();
        }
    }

    private String formatResponse(String rawText) {
        return "✨ Here’s what I found: \n\n" + rawText +
                "\n\n💡 Let me know if you need more details!";
    }



    private String buildPrompt(ResearchRequest request) {
        if (request == null || request.getOperation() == null) {
            throw new IllegalArgumentException("Invalid request: ResearchRequest or operation cannot be null.");
        }

        StringBuilder prompt = new StringBuilder();

        switch (request.getOperation().toLowerCase()) {
            case "summarize":
                prompt.append("Summarize the following research in a concise and structured manner, including key objectives, methodology, and conclusions.");
                break;
            case "suggest":
                prompt.append("Provide recommendations and suggestions for further research based on the following content.");
                break;
            case "analyze":
                prompt.append("Critically analyze the following research, highlighting strengths, weaknesses, and implications.");
                break;
            default:
                throw new IllegalArgumentException("Unknown Operation: " + request.getOperation());
        }

        prompt.append("\n\n").append(request.getContent());
        return prompt.toString();
    }
}
