package com.company.grc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "msme_api_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsmeApiLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_date", nullable = false)
    private LocalDate callDate;

    @Column(name = "call_timestamp", nullable = false)
    private LocalDateTime callTimestamp;

    @Column(name = "call_type", nullable = false, length = 20)
    private String callType; // 'AUTO' or 'MANUAL'

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan_number", length = 10)
    private String panNumber;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'SUCCESS', 'FAILED', 'LIMIT_REACHED', 'NO_RECORD'

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (callTimestamp == null) {
            callTimestamp = LocalDateTime.now();
        }
        if (callDate == null) {
            callDate = callTimestamp.toLocalDate();
        }
        if (gstin != null) {
            gstin = gstin.trim().toUpperCase();
        }
        if (panNumber != null) {
            panNumber = panNumber.trim().toUpperCase();
        }
    }
}
