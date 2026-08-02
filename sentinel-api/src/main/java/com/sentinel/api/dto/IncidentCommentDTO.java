package com.sentinel.api.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCommentDTO {
    private UUID commentId;
    private UUID incidentId;
    private String author;
    private String content;
    private LocalDateTime createdAt;
}
