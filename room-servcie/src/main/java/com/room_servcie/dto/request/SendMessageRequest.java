package com.room_servcie.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank(message = "Message content cannot be empty")
    @Size(max = 5000)
    private String content;

    private String type = "TEXT"; // TEXT | IMAGE | FILE

    private String fileUrl;

    private String replyToId;
}
