package com.konkuk.propit.global.ai.service;

import com.konkuk.propit.global.ai.dto.request.OpenAiRequest;
import com.konkuk.propit.global.ai.dto.response.OpenAiResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    // 한 번 시도할 때 기다리는 시간
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(20);
    // 재시도와 재시도 사이 대기까지 모두 합친 최대 시간
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);

    // 동시에 OpenAI로 나가는 요청 수. 다 차 있으면 PERMIT_WAIT 동안만 기다리고 실패 처리한다
    private static final int MAX_CONCURRENT_CALLS = 10;
    private static final Duration PERMIT_WAIT = Duration.ofSeconds(5);

    /** 호출 한 번이 걸릴 수 있는 최대 시간. 이 호출을 감싸는 락의 TTL을 정할 때 쓴다. */
    public static final Duration MAX_CALL_DURATION = PERMIT_WAIT.plus(TOTAL_TIMEOUT);

    private final Semaphore permits = new Semaphore(MAX_CONCURRENT_CALLS);

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;

    /**
     * @return AI 응답 본문. 호출에 실패하면 비어 있다.
     */
    public Optional<String> requestAnalysis(String prompt) {

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
            OpenAiResponse response = call(request);

            return Optional.of(response.choices().get(0).message().content());

        } catch (Exception e) {
            log.warn("GPT 분석 호출 실패", e);
            return Optional.empty();
        }
    }

    public String analyzeImages(String systemPrompt, String userPrompt, List<String> imageUrls) {

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
            OpenAiResponse response = call(request);

            return response.choices().get(0).message().content();

        } catch (Exception e) {
            log.warn("GPT Vision 호출 실패", e);
            return "{}";
        }
    }

    private OpenAiResponse call(OpenAiRequest request) {

        acquirePermit();
        try {
            return webClientBuilder.baseUrl(apiUrl).build()
                    .post()
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(ATTEMPT_TIMEOUT)
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_BACKOFF).filter(OpenAiService::isRetryable))
                    .timeout(TOTAL_TIMEOUT)
                    .block();
        } finally {
            permits.release();
        }
    }

    private void acquirePermit() {
        try {
            if (!permits.tryAcquire(PERMIT_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("OpenAI 동시 호출 수 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI 호출 대기 중 인터럽트", e);
        }
    }

    // 잠깐 뒤에 다시 보내면 성공할 수 있는 오류만 재시도한다.
    // 400·401 같은 요청 오류는 다시 보내도 똑같이 실패하고, 시간만 늘어난다.
    private static boolean isRetryable(Throwable e) {
        if (e instanceof TimeoutException || e instanceof WebClientRequestException) {
            return true;
        }
        if (e instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == 429;
        }
        return false;
    }

}
