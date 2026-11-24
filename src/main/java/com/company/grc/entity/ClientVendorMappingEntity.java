package com.company.grc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_vendor_mappings", uniqueConstraints = {
    @UniqueConstraint(name = "uq_client_vendor", columnNames = {"client_id", "vendor_gstin"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientVendorMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @Column(name = "vendor_gstin", nullable = false, length = 15)
    private String vendorGstin;

    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    @PrePersist
    protected void onCreate() {
        if (mappedAt == null) {
            mappedAt = LocalDateTime.now();
        }
    }
}
