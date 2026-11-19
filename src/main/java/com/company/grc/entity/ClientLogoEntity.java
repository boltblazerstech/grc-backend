package com.company.grc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_logos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientLogoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gstin", nullable = false, unique = true, length = 15)
    private String gstin;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "logo_data", columnDefinition = "TEXT", nullable = false)
    private String logoData;

    @Column(name = "logo_content_type", length = 50, nullable = false)
    private String logoContentType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
        }
    }
}
