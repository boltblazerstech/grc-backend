package com.company.grc.service;

import com.company.grc.entity.GeneratedInvoiceEntity;
import com.company.grc.repository.GeneratedInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Records a generated invoice number in the database list.
     */
    @Transactional
    public GeneratedInvoiceEntity recordInvoice(String invoiceNumber, String companyName, Integer month, Integer year) {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Invoice number cannot be empty");
        }

        String cleanNumber = invoiceNumber.trim();

        return invoiceRepository.findByInvoiceNumber(cleanNumber)
                .orElseGet(() -> {
                    GeneratedInvoiceEntity entity = GeneratedInvoiceEntity.builder()
                            .invoiceNumber(cleanNumber)
                            .companyName(companyName != null ? companyName.trim() : null)
                            .billingMonth(month)
                            .billingYear(year)
                            .build();
                    log.info("Recording new generated invoice number: {}", cleanNumber);
                    return invoiceRepository.save(entity);
                });
    }

    @Transactional(readOnly = true)
    public List<String> getAllInvoiceNumbers() {
        return invoiceRepository.findAllInvoiceNumbers();
    }
}
