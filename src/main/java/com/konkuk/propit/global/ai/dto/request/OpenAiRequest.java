package com.konkuk.propit.global.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record OpenAiRequest(
        String model,
        List<Message> messages,
        double temperature
) {
    public record Message(
            String role,
            Object content
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentPart(
            String type,
            String text,
            ImageUrl image_url
    ) {
        public static ContentPart ofText(String text) {
            return new ContentPart("text", text, null);
        }

        public static ContentPart ofImage(String base64, String mimeType) {
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            return new ContentPart("image_url", null, new ImageUrl(dataUrl));
        }
    }

    public record ImageUrl(String url) {}
}
