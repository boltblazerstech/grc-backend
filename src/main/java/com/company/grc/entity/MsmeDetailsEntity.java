package com.company.grc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "msme_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsmeDetailsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gstin", nullable = false, unique = true, length = 15)
    private String gstin;

    @Column(name = "pan_number", nullable = false, length = 10)
    private String panNumber;

    @Column(name = "msme_status", nullable = false, length = 50)
    private String msmeStatus; // 'YES', 'NO', 'DATA_UNAVAILABLE', 'VERIFIED', etc.

    @Column(name = "udyam_number", length = 100)
    private String udyamNumber;

    @Column(name = "enterprise_name", length = 255)
    private String enterpriseName;

    @Column(name = "enterprise_type", length = 50)
    private String enterpriseType; // Micro, Small, Medium

    @Column(name = "organization_type", length = 100)
    private String organizationType;

    @Column(name = "major_activity", length = 100)
    private String majorActivity; // Services, Manufacturing

    @Column(name = "date_of_incorporation", length = 50)
    private String dateOfIncorporation;

    @Column(name = "date_of_commencement", length = 50)
    private String dateOfCommencement;

    @Column(name = "date_of_udyam_registration", length = 50)
    private String dateOfUdyamRegistration;

    @Column(name = "social_category", length = 50)
    private String socialCategory;

    @Column(name = "dic", length = 100)
    private String dic;

    @Column(name = "msme_di", length = 100)
    private String msmeDi;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "pincode", length = 20)
    private String pincode;

    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "raw_response_json", columnDefinition = "TEXT")
    private String rawResponseJson;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @Column(name = "is_manual")
    private Boolean isManual;

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
        if (lastFetchedAt == null) {
            lastFetchedAt = LocalDateTime.now();
        }
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
        }
        if (panNumber != null) {
            panNumber = panNumber.trim().toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
        }
        if (panNumber != null) {
            panNumber = panNumber.trim().toUpperCase();
        }
    }
}
