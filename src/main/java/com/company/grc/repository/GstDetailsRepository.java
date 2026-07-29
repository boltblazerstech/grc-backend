package com.company.grc.repository;

import com.company.grc.entity.GstDetailsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

@Repository
public interface GstDetailsRepository extends JpaRepository<GstDetailsEntity, String> {

    @Query("SELECT g.gstin FROM GstDetailsEntity g")
    List<String> findAllGstins();

    List<GstDetailsEntity> findByApiErrorFalseOrApiErrorIsNull();

    @Query("SELECT g FROM GstDetailsEntity g WHERE g.createdAt IS NOT NULL ORDER BY g.createdAt DESC")
    List<GstDetailsEntity> findNewVendors(Pageable pageable);

    @Query(value = "SELECT * FROM gst_details WHERE is_trashed = true", nativeQuery = true)
    List<GstDetailsEntity> findAllTrashedNative();

    @Modifying
    @Query(value = "UPDATE gst_details SET is_trashed = true WHERE gstin = :gstin", nativeQuery = true)
    void trashGstinNative(@Param("gstin") String gstin);

    @Modifying
    @Query(value = "UPDATE gst_details SET is_trashed = false WHERE gstin = :gstin", nativeQuery = true)
    void restoreGstinNative(@Param("gstin") String gstin);
}
