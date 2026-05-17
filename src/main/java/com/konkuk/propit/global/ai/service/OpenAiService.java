package com.konkuk.propit.global.ai.service;

import com.konkuk.propit.global.ai.dto.request.OpenAiRequest;
import com.konkuk.propit.global.ai.dto.response.OpenAiResponse;
import java.time.Duration;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;

    public String requestAnalysis(String prompt) {

        WebClient webClient = webClientBuilder.baseUrl(apiUrl).build();

        OpenAiRequest request = new OpenAiRequest(
                "gpt-4.1-mini",
                List.of(
                        new OpenAiRequest.Message("system",
                                "당신은 투자 분석 AI입니다. 반드시 JSON 형식으로만 응답하세요."),
                        new OpenAiRequest.Message("user", prompt)
                ),
                0.3
        );

        try {
            OpenAiResponse response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(
                            reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(3))
                    )
                    .block();

            return response.choices().get(0).message().content();

        } catch (Exception e) {
            return """
        {
          "aiInsight": {
            "strengthPattern": "AI 분석 지연",
            "improvementPoint": "잠시 후 다시 시도해주세요",
            "cautionTime": "현재 요청이 많습니다"
          },
          "todayAdvice": ["잠시 후 다시 요청해주세요"]
        }
        """;
        }
    }

    public String analyzeImages(String systemPrompt, String userPrompt, List<String> imageUrls) {

        WebClient webClient = webClientBuilder.baseUrl(apiUrl).build();

        List<OpenAiRequest.ContentPart> contentParts = new ArrayList<>();
        contentParts.add(OpenAiRequest.ContentPart.ofText(userPrompt));

        for (String url : imageUrls) {
            contentParts.add(
                    new OpenAiRequest.ContentPart(
                            "image_url",
                            null,
                            new OpenAiRequest.ImageUrl(url)
                    )
            );
        }

        OpenAiRequest request = new OpenAiRequest(
                "gpt-4o-mini",
                List.of(
                        new OpenAiRequest.Message("system", systemPrompt),
                        new OpenAiRequest.Message("user", contentParts)
                ),
                0.0
        );

        try {
            OpenAiResponse response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(
                            reactor.util.retry.Retry.backoff(2, java.time.Duration.ofSeconds(3))
                    )
                    .block();

            return response.choices().get(0).message().content();

        } catch (Exception e) {
            log.warn("GPT Vision 호출 실패", e);
            return "{}";
        }
    }

}
