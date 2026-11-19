package com.company.grc.repository;

import com.company.grc.entity.ClientLogoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientLogoRepository extends JpaRepository<ClientLogoEntity, Long> {
    Optional<ClientLogoEntity> findByGstin(String gstin);
    Optional<ClientLogoEntity> findByGstinIgnoreCase(String gstin);
    boolean existsByGstinIgnoreCase(String gstin);
    void deleteByGstinIgnoreCase(String gstin);
}
