package com.company.grc.repository;

import com.company.grc.entity.GeneratedInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedInvoiceRepository extends JpaRepository<GeneratedInvoiceEntity, Long> {

    Optional<GeneratedInvoiceEntity> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    @Query("SELECT g.invoiceNumber FROM GeneratedInvoiceEntity g WHERE g.invoiceNumber = :baseName OR g.invoiceNumber LIKE CONCAT(:baseName, '/%')")
    List<String> findExistingForBasePrefix(@Param("baseName") String baseName);

    @Query("SELECT g.invoiceNumber FROM GeneratedInvoiceEntity g ORDER BY g.id DESC")
    List<String> findAllInvoiceNumbers();

    /**
     * Finds invoices matching any of the specified 3 month/year pairs or created recently.
     */
    @Query("SELECT g FROM GeneratedInvoiceEntity g WHERE " +
           "( (g.billingYear = :y0 AND g.billingMonth = :m0) OR " +
           "  (g.billingYear = :y1 AND g.billingMonth = :m1) OR " +
           "  (g.billingYear = :y2 AND g.billingMonth = :m2) OR " +
           "  (g.createdAt >= :cutoffDate) ) " +
           "ORDER BY g.createdAt DESC")
    List<GeneratedInvoiceEntity> findRecentInvoices(
            @Param("m0") int m0, @Param("y0") int y0,
            @Param("m1") int m1, @Param("y1") int y1,
            @Param("m2") int m2, @Param("y2") int y2,
            @Param("cutoffDate") LocalDateTime cutoffDate);
}
