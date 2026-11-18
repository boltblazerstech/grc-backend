package com.company.grc.controller;

import com.company.grc.entity.GeneratedInvoiceEntity;
import com.company.grc.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invoices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * Get automatically generated next invoice number for a company, month, and year.
     * e.g. GET /api/admin/invoices/next-number?companyName=Scrap&month=10&year=26
     * -> { "invoiceNumber": "Scrap0000/10/26" }
     */
    @GetMapping("/next-number")
    public ResponseEntity<Map<String, String>> getNextInvoiceNumber(
            @RequestParam(required = false, defaultValue = "") String companyName,
            @RequestParam(required = false, defaultValue = "0") int month,
            @RequestParam(required = false, defaultValue = "0") int year) {

        String nextNumber = invoiceService.generateNextInvoiceNumber(companyName, month, year);
        return ResponseEntity.ok(Map.of("invoiceNumber", nextNumber));
    }

    /**
     * Record an invoice number in the database list upon generation/download.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> recordInvoice(
            @RequestBody Map<String, Object> payload) {

        String invoiceNumber = (String) payload.get("invoiceNumber");
        String companyName = (String) payload.get("companyName");
        Integer month = payload.get("month") != null ? ((Number) payload.get("month")).intValue() : null;
        Integer year = payload.get("year") != null ? ((Number) payload.get("year")).intValue() : null;

        GeneratedInvoiceEntity saved = invoiceService.recordInvoice(invoiceNumber, companyName, month, year);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", saved.getId(),
                "invoiceNumber", saved.getInvoiceNumber()
        ));
    }

    /**
     * List all recorded invoice numbers.
     */
    @GetMapping("/all")
    public ResponseEntity<List<String>> getAllInvoiceNumbers() {
        return ResponseEntity.ok(invoiceService.getAllInvoiceNumbers());
    }
}
