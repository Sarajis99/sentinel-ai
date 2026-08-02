package com.sentinel.api.repository;

import com.sentinel.api.entity.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {
    List<IncidentComment> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}
