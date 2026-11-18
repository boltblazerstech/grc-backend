package com.company.grc.repository;

import com.company.grc.entity.GeneratedInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
