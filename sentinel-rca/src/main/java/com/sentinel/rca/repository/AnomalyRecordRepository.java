package com.sentinel.rca.repository;

import com.sentinel.rca.entity.AnomalyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Update access to the 'anomalies' table.
 * Used only to update the status field as part of the anomaly lifecycle.
 * sentinel-detector owns this table; sentinel-rca only updates status.
 */
@Repository
public interface AnomalyRecordRepository extends JpaRepository<AnomalyRecord, Long> {

    Optional<AnomalyRecord> findByAnomalyId(UUID anomalyId);

    @Modifying
    @Transactional
    @Query("UPDATE AnomalyRecord a SET a.status = :status WHERE a.anomalyId = :anomalyId")
    int updateStatus(@Param("anomalyId") UUID anomalyId, @Param("status") String status);
}
