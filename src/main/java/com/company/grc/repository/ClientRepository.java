package com.company.grc.repository;

import com.company.grc.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    Optional<ClientEntity> findByClientGstin(String clientGstin);

    Optional<ClientEntity> findByClientNameIgnoreCase(String clientName);

    Optional<ClientEntity> findFirstByClientNameIgnoreCaseContaining(String clientName);

    boolean existsByClientGstin(String clientGstin);

    boolean existsByClientGstinAndIdNot(String clientGstin, Long id);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM ClientEntity c WHERE LOWER(c.clientGstin) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :query, '%'))")
    java.util.List<ClientEntity> searchByGstinOrName(@org.springframework.data.repository.query.Param("query") String query);
}
