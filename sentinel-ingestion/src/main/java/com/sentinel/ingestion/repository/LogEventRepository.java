package com.sentinel.ingestion.repository;

import com.sentinel.common.enums.LogLevel;
import com.sentinel.ingestion.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, Long> {

    /**
     * Find logs for a service within a time window — used by detector + RCA context gatherer
     */
    List<LogEvent> findByServiceNameAndTimestampBetweenOrderByTimestampDesc(
            String serviceName, LocalDateTime from, LocalDateTime to);

    /**
     * Count errors for a service in a time window — used by error rate analyzer
     */
    @Query("SELECT COUNT(l) FROM LogEvent l WHERE l.serviceName = :service " +
           "AND l.logLevel = :level AND l.timestamp >= :from")
    long countByServiceAndLevelSince(
            @Param("service") String service,
            @Param("level") LogLevel level,
            @Param("from") LocalDateTime from);

    /**
     * Get average latency for a service in a time window
     */
    @Query("SELECT AVG(l.latencyMs) FROM LogEvent l WHERE l.serviceName = :service " +
           "AND l.latencyMs IS NOT NULL AND l.timestamp >= :from")
    Double avgLatencyByServiceSince(
            @Param("service") String service,
            @Param("from") LocalDateTime from);

    /**
     * Get recent error logs for RCA context
     */
    @Query("SELECT l FROM LogEvent l WHERE l.serviceName = :service " +
           "AND l.logLevel IN ('ERROR', 'WARN') " +
           "AND l.timestamp BETWEEN :from AND :to " +
           "ORDER BY l.timestamp DESC")
    List<LogEvent> findErrorLogsInWindow(
            @Param("service") String service,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
