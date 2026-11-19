package com.company.grc.service;

import com.company.grc.entity.GeneratedInvoiceEntity;
import com.company.grc.repository.GeneratedInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final GeneratedInvoiceRepository invoiceRepository;

    /**
     * Formats company name into exact 9 characters:
     * - Strips leading "M/s " if present
     * - Removes special characters and spaces
     * - Pads with '0' if less than 9 characters (e.g. Scrap -> Scrap0000)
     * - Truncates to first 9 characters if longer (e.g. Scrapdmss -> Scrapdmss)
     */
    public String formatCompanyCode(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return "Company00";
        }

        String cleaned = companyName.trim();
        if (cleaned.toLowerCase().startsWith("m/s.")) {
            cleaned = cleaned.substring(4).trim();
        } else if (cleaned.toLowerCase().startsWith("m/s ")) {
            cleaned = cleaned.substring(4).trim();
        } else if (cleaned.toLowerCase().startsWith("m/s")) {
            cleaned = cleaned.substring(3).trim();
        }

        // Keep alphanumeric characters only
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]", "");

        if (cleaned.isEmpty()) {
            cleaned = "Company";
        }

        if (cleaned.length() < 9) {
            StringBuilder sb = new StringBuilder(cleaned);
            while (sb.length() < 9) {
                sb.append('0');
            }
            return sb.toString();
        }

        return cleaned.substring(0, 9);
    }

    /**
     * Generates next invoice number:
     * - 1st invoice: [Code]/[MM]/[YY] (e.g. Scrapdmss/10/26)
     * - 2nd invoice: [Code]/[MM]/[YY]/2
     * - 3rd invoice: [Code]/[MM]/[YY]/3
     */
    @Transactional(readOnly = true)
    public String generateNextInvoiceNumber(String companyName, int month, int year) {
        String companyCode = formatCompanyCode(companyName);
        String monthStr = String.format("%02d", Math.max(1, Math.min(12, month)));
        String yearStr = String.format("%02d", Math.abs(year) % 100);

        String baseName = companyCode + "/" + monthStr + "/" + yearStr;

        List<String> existingList = invoiceRepository.findExistingForBasePrefix(baseName);
        Set<String> existingSet = new HashSet<>(existingList);

        // 1st Invoice has no counter suffix
        if (!existingSet.contains(baseName)) {
            return baseName;
        }

        // 2nd, 3rd, 4th onwards have /2, /3, /4...
        int counter = 2;
        while (existingSet.contains(baseName + "/" + counter)) {
            counter++;
        }

        return baseName + "/" + counter;
    }

    /**
     * Records a generated invoice with full details in the database.
     */
    @Transactional
    public GeneratedInvoiceEntity recordInvoice(Map<String, Object> payload) {
        String invoiceNumber = (String) payload.get("invoiceNumber");
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Invoice number cannot be empty");
        }

        String cleanNumber = invoiceNumber.trim();
        String companyName = (String) payload.get("companyName");
        if (companyName == null) companyName = (String) payload.get("customerName");

        Integer month = payload.get("billingMonth") != null ? ((Number) payload.get("billingMonth")).intValue()
                : (payload.get("month") != null ? ((Number) payload.get("month")).intValue() : null);
        Integer year = payload.get("billingYear") != null ? ((Number) payload.get("billingYear")).intValue()
                : (payload.get("year") != null ? ((Number) payload.get("year")).intValue() : null);

        String customerGstin = (String) payload.get("customerGstin");
        String customerAddress = (String) payload.get("customerAddress");
        String natureOfSupply = (String) payload.get("natureOfSupply");
        String sacCode = (String) payload.get("sacCode");
        String invoiceDate = (String) payload.get("invoiceDate");
        String billingPeriod = (String) payload.get("billingPeriod");
        String poNumber = (String) payload.get("poNumber");
        String poDate = (String) payload.get("poDate");
        String itemDescription = (String) payload.get("itemDescription");
        String amountInWords = (String) payload.get("amountInWords");
        String jsonData = (String) payload.get("jsonData");
        Boolean isInterState = payload.get("isInterState") != null ? (Boolean) payload.get("isInterState") : null;

        BigDecimal price = payload.get("price") != null ? new BigDecimal(String.valueOf(payload.get("price"))) : null;
        BigDecimal discountPercent = payload.get("discountPercent") != null ? new BigDecimal(String.valueOf(payload.get("discountPercent"))) : null;
        BigDecimal finalRate = payload.get("finalRate") != null ? new BigDecimal(String.valueOf(payload.get("finalRate"))) : null;
        BigDecimal taxAmount = payload.get("taxAmount") != null ? new BigDecimal(String.valueOf(payload.get("taxAmount"))) : null;
        BigDecimal totalAmount = payload.get("totalAmount") != null ? new BigDecimal(String.valueOf(payload.get("totalAmount"))) : null;

        Optional<GeneratedInvoiceEntity> existing = invoiceRepository.findByInvoiceNumber(cleanNumber);
        GeneratedInvoiceEntity entity = existing.orElseGet(GeneratedInvoiceEntity::new);

        entity.setInvoiceNumber(cleanNumber);
        if (companyName != null) entity.setCompanyName(companyName.trim());
        if (customerGstin != null) entity.setCustomerGstin(customerGstin.trim());
        if (customerAddress != null) entity.setCustomerAddress(customerAddress.trim());
        if (natureOfSupply != null) entity.setNatureOfSupply(natureOfSupply);
        if (sacCode != null) entity.setSacCode(sacCode);
        if (invoiceDate != null) entity.setInvoiceDate(invoiceDate);
        if (billingPeriod != null) entity.setBillingPeriod(billingPeriod);
        if (poNumber != null) entity.setPoNumber(poNumber.trim());
        if (poDate != null) entity.setPoDate(poDate.trim());
        if (month != null) entity.setBillingMonth(month);
        if (year != null) entity.setBillingYear(year);
        if (itemDescription != null) entity.setItemDescription(itemDescription);
        if (price != null) entity.setPrice(price);
        if (discountPercent != null) entity.setDiscountPercent(discountPercent);
        if (finalRate != null) entity.setFinalRate(finalRate);
        if (taxAmount != null) entity.setTaxAmount(taxAmount);
        if (totalAmount != null) entity.setTotalAmount(totalAmount);
        if (isInterState != null) entity.setIsInterState(isInterState);
        if (amountInWords != null) entity.setAmountInWords(amountInWords);
        if (jsonData != null) entity.setJsonData(jsonData);

        log.info("Recording/updating generated invoice details: {}", cleanNumber);
        return invoiceRepository.save(entity);
    }

    /**
     * Retrieves generated invoices for the previous two months and the current month.
     */
    @Transactional(readOnly = true)
    public List<GeneratedInvoiceEntity> getRecentInvoices() {
        LocalDate today = LocalDate.now();

        // Month 0 (Current Month)
        int m0 = today.getMonthValue();
        int y0 = today.getYear();

        // Month 1 (Previous Month)
        LocalDate prev1 = today.minusMonths(1);
        int m1 = prev1.getMonthValue();
        int y1 = prev1.getYear();

        // Month 2 (Two Months Ago)
        LocalDate prev2 = today.minusMonths(2);
        int m2 = prev2.getMonthValue();
        int y2 = prev2.getYear();

        LocalDateTime cutoff = today.minusMonths(3).atStartOfDay();

        return invoiceRepository.findRecentInvoices(m0, y0, m1, y1, m2, y2, cutoff);
    }

    @Transactional(readOnly = true)
    public List<String> getAllInvoiceNumbers() {
        return invoiceRepository.findAllInvoiceNumbers();
    }
}
