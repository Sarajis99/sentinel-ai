package com.sentinel.api.repository;

import com.sentinel.api.entity.AnomalyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the 'anomalies' table — used by sentinel-api
 * for data retention cleanup and dashboard queries.
 */
@Repository
public interface AnomalyRecordRepository extends JpaRepository<AnomalyRecord, Long> {

    Optional<AnomalyRecord> findByAnomalyId(UUID anomalyId);

    long countByStatus(String status);

    // Data retention — delete anomalies older than X hours
    @Modifying
    @Transactional
    @Query("DELETE FROM AnomalyRecord a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
