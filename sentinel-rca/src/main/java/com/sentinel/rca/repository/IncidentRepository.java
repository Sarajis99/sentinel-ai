package com.sentinel.rca.repository;

import com.sentinel.rca.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the 'incidents' table — owned by sentinel-rca.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentId(UUID incidentId);

    Optional<Incident> findByAnomalyId(UUID anomalyId);

    boolean existsByAnomalyId(UUID anomalyId);
}
