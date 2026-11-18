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
     * Record an invoice in the database upon generation/download with full metadata.
     */
    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> recordInvoice(
            @RequestBody Map<String, Object> payload) {

        GeneratedInvoiceEntity saved = invoiceService.recordInvoice(payload);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", saved.getId(),
                "invoiceNumber", saved.getInvoiceNumber()
        ));
    }

    /**
     * Get generated invoices for the previous two months and the current month.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<GeneratedInvoiceEntity>> getRecentInvoices() {
        return ResponseEntity.ok(invoiceService.getRecentInvoices());
    }

    /**
     * List all recorded invoice numbers.
     */
    @GetMapping("/all")
    public ResponseEntity<List<String>> getAllInvoiceNumbers() {
        return ResponseEntity.ok(invoiceService.getAllInvoiceNumbers());
    }
}
