package com.company.grc.repository;

import com.company.grc.entity.GrcScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GrcScoreRepository extends JpaRepository<GrcScoreEntity, String> {
    @Query("SELECT s FROM GrcScoreEntity s WHERE UPPER(TRIM(s.gstin)) = UPPER(TRIM(:gstin))")
    Optional<GrcScoreEntity> findByGstinNormalized(@Param("gstin") String gstin);
}
