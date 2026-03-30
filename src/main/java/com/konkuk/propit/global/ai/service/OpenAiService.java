package com.konkuk.propit.global.ai.service;

import com.konkuk.propit.global.ai.dto.request.OpenAiRequest;
import com.konkuk.propit.global.ai.dto.response.OpenAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

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

        OpenAiResponse response = webClient.post()
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .block();

        return response.choices().get(0).message().content();
    }
}
