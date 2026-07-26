package com.sentinel.detector.repository;

import com.sentinel.detector.entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the anomalies table.
 */
@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

    boolean existsByAnomalyId(UUID anomalyId);
}
