package com.sentinel.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false, unique = true)
    private UUID commentId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "author", nullable = false, length = 100)
    @Builder.Default
    private String author = "Analyst";

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
