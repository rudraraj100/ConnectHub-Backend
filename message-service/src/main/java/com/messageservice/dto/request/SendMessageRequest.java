package com.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for sending a new message.
 * Case study §2.3: content, type, mediaUrl, replyToMessageId
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content cannot be empty")
    @Size(max = 5000, message = "Message content too long")
    private String content;

    /** TEXT | IMAGE | FILE | REACTION | SYSTEM */
    private String type = "TEXT";

    /** S3 URL — set when type is IMAGE or FILE */
    private String mediaUrl;

    /** messageId of the message being quoted/replied to */
    private String replyToMessageId;
}
