package com.company.grc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedInvoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 100)
    private String invoiceNumber;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "customer_gstin", length = 20)
    private String customerGstin;

    @Column(name = "customer_address", columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "nature_of_supply", length = 255)
    private String natureOfSupply;

    @Column(name = "sac_code", length = 50)
    private String sacCode;

    @Column(name = "invoice_date", length = 50)
    private String invoiceDate;

    @Column(name = "billing_period", length = 50)
    private String billingPeriod;

    @Column(name = "billing_month")
    private Integer billingMonth;

    @Column(name = "billing_year")
    private Integer billingYear;

    @Column(name = "item_description", length = 255)
    private String itemDescription;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "final_rate", precision = 12, scale = 2)
    private BigDecimal finalRate;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "is_inter_state")
    private Boolean isInterState;

    @Column(name = "amount_in_words", length = 500)
    private String amountInWords;

    @Column(name = "json_data", columnDefinition = "TEXT")
    private String jsonData;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
