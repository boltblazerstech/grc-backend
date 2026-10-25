package com.company.grc.repository;

import com.company.grc.entity.ClientVendorMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientVendorMappingRepository extends JpaRepository<ClientVendorMappingEntity, Long> {

    List<ClientVendorMappingEntity> findByClientId(Long clientId);

    @Modifying
    @Query("DELETE FROM ClientVendorMappingEntity m WHERE m.client.id = :clientId AND m.vendorGstin = :vendorGstin")
    void deleteByClientIdAndVendorGstin(@Param("clientId") Long clientId, @Param("vendorGstin") String vendorGstin);

    @Query("SELECT COUNT(m) > 0 FROM ClientVendorMappingEntity m WHERE m.client.id = :clientId AND m.vendorGstin = :vendorGstin")
    boolean existsByClientIdAndVendorGstin(@Param("clientId") Long clientId, @Param("vendorGstin") String vendorGstin);

    long countByClientId(Long clientId);

    @Modifying
    @Query("DELETE FROM ClientVendorMappingEntity m WHERE m.client.id = :clientId")
    void deleteByClientId(@Param("clientId") Long clientId);
}
