package com.sentinel.api.repository;

import com.sentinel.api.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for the 'log_events' table — used by sentinel-api
 * for data retention cleanup and log context viewing.
 */
@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, Long> {

    // For incident detail view — raw log context
    @Query("""
            SELECT l FROM LogEvent l
            WHERE l.serviceName = :serviceName
              AND l.timestamp BETWEEN :from AND :to
            ORDER BY l.timestamp DESC
            """)
    List<LogEvent> findByServiceAndWindow(
            @Param("serviceName") String serviceName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Data retention — delete log events older than X hours
    @Modifying
    @Transactional
    @Query("DELETE FROM LogEvent l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // Count for dashboard stats
    long count();
}
