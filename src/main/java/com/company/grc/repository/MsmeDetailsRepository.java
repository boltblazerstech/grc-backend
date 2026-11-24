package com.company.grc.repository;

import com.company.grc.entity.MsmeDetailsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MsmeDetailsRepository extends JpaRepository<MsmeDetailsEntity, Long> {

    Optional<MsmeDetailsEntity> findByGstinIgnoreCase(String gstin);

    Optional<MsmeDetailsEntity> findByPanNumberIgnoreCase(String panNumber);

    boolean existsByGstinIgnoreCase(String gstin);
}
