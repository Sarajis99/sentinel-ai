package com.sentinel.api.repository;

import com.sentinel.api.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the 'incidents' table — used by sentinel-api for
 * dashboard queries, status updates, and data retention.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentId(UUID incidentId);

    // Paginated + filterable queries for dashboard
    Page<Incident> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Page<Incident> findBySeverityOrderByDetectedAtDesc(String severity, Pageable pageable);

    Page<Incident> findByStatusOrderByDetectedAtDesc(String status, Pageable pageable);

    Page<Incident> findByServiceNameOrderByDetectedAtDesc(String serviceName, Pageable pageable);

    // For manual triage queue — UNKNOWN root cause incidents
    @Query("SELECT i FROM Incident i WHERE i.rootCause = 'UNKNOWN' ORDER BY i.detectedAt DESC")
    List<Incident> findUnknownIncidents();

    // Count by status for dashboard stats
    long countByStatus(String status);

    long countBySeverity(String severity);

    // For data retention — find IDs beyond the cap
    @Query(value = "SELECT i.id FROM incidents i ORDER BY i.created_at DESC OFFSET :offset", nativeQuery = true)
    List<Long> findIdsOlderThanOffset(@Param("offset") int offset);

    // Bulk delete for retention
    @Modifying
    @Transactional
    @Query("DELETE FROM Incident i WHERE i.id IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);

    // Update status
    @Modifying
    @Transactional
    @Query("UPDATE Incident i SET i.status = :status, i.resolvedAt = CURRENT_TIMESTAMP WHERE i.incidentId = :incidentId")
    int updateStatus(@Param("incidentId") UUID incidentId, @Param("status") String status);

    // For MTTR calculation
    @Query("SELECT AVG(i.mttrSeconds) FROM Incident i WHERE i.mttrSeconds IS NOT NULL")
    Double averageMttr();
}
