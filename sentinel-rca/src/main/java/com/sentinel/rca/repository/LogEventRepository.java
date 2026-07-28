package com.sentinel.rca.repository;

import com.sentinel.rca.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only access to the 'log_events' table for context gathering.
 * Fetches raw logs around an anomaly window to build the LLM prompt.
 */
@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, Long> {

    /**
     * Fetch all log events for a service within a time window, ordered by timestamp.
     * Used by ContextGatherer to build the LLM prompt with relevant log context.
     */
    @Query("""
            SELECT l FROM LogEvent l
            WHERE l.serviceName = :serviceName
              AND l.timestamp BETWEEN :from AND :to
            ORDER BY l.timestamp DESC
            """)
    List<LogEvent> findByServiceNameAndTimestampBetween(
            @Param("serviceName") String serviceName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Fetch only ERROR and WARN logs for a service in the given window.
     * Prioritizes the most critical logs to conserve LLM token budget.
     */
    @Query("""
            SELECT l FROM LogEvent l
            WHERE l.serviceName = :serviceName
              AND l.timestamp BETWEEN :from AND :to
              AND l.logLevel IN ('ERROR', 'WARN')
            ORDER BY l.timestamp DESC
            """)
    List<LogEvent> findCriticalLogsByServiceAndWindow(
            @Param("serviceName") String serviceName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
