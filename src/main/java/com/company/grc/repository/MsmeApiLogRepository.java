package com.company.grc.repository;

import com.company.grc.entity.MsmeApiLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface MsmeApiLogRepository extends JpaRepository<MsmeApiLogEntity, Long> {

    long countByCallDateAndCallType(LocalDate callDate, String callType);

    long countByCallDate(LocalDate callDate);

    List<MsmeApiLogEntity> findByCallDateBetweenOrderByCallTimestampDesc(LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query("DELETE FROM MsmeApiLogEntity l WHERE l.callDate < :cutoffDate")
    void deleteByCallDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Query(value = "SELECT l.call_date as callDate, " +
            "COUNT(CASE WHEN l.call_type = 'AUTO' THEN 1 END) as autoCount, " +
            "COUNT(CASE WHEN l.call_type = 'MANUAL' THEN 1 END) as manualCount, " +
            "COUNT(1) as totalCount, " +
            "COUNT(CASE WHEN l.status = 'SUCCESS' THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN l.status != 'SUCCESS' THEN 1 END) as failedCount " +
            "FROM msme_api_logs l " +
            "WHERE l.call_date >= :sinceDate " +
            "GROUP BY l.call_date " +
            "ORDER BY l.call_date DESC", nativeQuery = true)
    List<Map<String, Object>> getDailyUsageStatsSummary(@Param("sinceDate") LocalDate sinceDate);
}
