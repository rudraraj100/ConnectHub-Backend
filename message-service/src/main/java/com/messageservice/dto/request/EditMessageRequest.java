package com.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request body for editing an existing message. Case study §2.3 */
@Data
public class EditMessageRequest {

    @NotBlank(message = "Updated content cannot be empty")
    @Size(max = 5000)
    private String content;
}
