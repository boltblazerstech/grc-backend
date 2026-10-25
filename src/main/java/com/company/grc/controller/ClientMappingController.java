package com.company.grc.controller;

import com.company.grc.dto.ClientDto;
import com.company.grc.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/clients")
@CrossOrigin(origins = "*")
public class ClientMappingController {

    private final ClientService clientService;

    @Autowired
    public ClientMappingController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * List all clients with mapped vendor count.
     */
    @GetMapping
    public ResponseEntity<List<ClientDto.ClientResponseDto>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    /**
     * Get a specific client by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClientDto.ClientResponseDto> getClientById(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    /**
     * Create a new client.
     */
    @PostMapping
    public ResponseEntity<ClientDto.ClientResponseDto> createClient(@RequestBody ClientDto.ClientRequestDto request) {
        ClientDto.ClientResponseDto created = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update client details (GSTIN and/or Name).
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClientDto.ClientResponseDto> updateClient(
            @PathVariable Long id,
            @RequestBody ClientDto.ClientRequestDto request) {
        return ResponseEntity.ok(clientService.updateClient(id, request));
    }

    /**
     * Delete client and its mappings.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok(Map.of("message", "Client and associated mappings deleted successfully."));
    }

    /**
     * Upload client logo (Multipart file, max 2MB, PNG/JPEG/SVG).
     */
    @PostMapping("/{id}/logo")
    public ResponseEntity<ClientDto.ClientResponseDto> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        ClientDto.ClientResponseDto updated = clientService.uploadLogo(id, file);
        return ResponseEntity.ok(updated);
    }

    /**
     * Retrieve raw logo image with proper Content-Type header.
     */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable Long id) {
        ClientService.LogoData logoData = clientService.getLogo(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(logoData.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.IMAGE_PNG;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400, public")
                .body(logoData.getData());
    }

    /**
     * Retrieve all mapped vendors for the client with enriched GST & GRC score data.
     */
    @GetMapping("/{id}/vendors")
    public ResponseEntity<List<ClientDto.MappedVendorDto>> getMappedVendors(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getMappedVendors(id));
    }

    /**
     * Map one or multiple vendors to the client.
     */
    @PostMapping("/{id}/vendors")
    public ResponseEntity<Map<String, Object>> mapVendors(
            @PathVariable Long id,
            @RequestBody ClientDto.VendorMappingRequestDto request) {
        List<String> gstins = request != null ? request.getVendorGstins() : List.of();
        List<String> mapped = clientService.mapVendors(id, gstins);
        return ResponseEntity.ok(Map.of(
                "message", "Mapped " + mapped.size() + " vendor(s) successfully.",
                "mappedGstins", mapped
        ));
    }

    /**
     * Remove a vendor mapping from the client.
     */
    @DeleteMapping("/{id}/vendors/{vendorGstin}")
    public ResponseEntity<Map<String, String>> unmapVendor(
            @PathVariable Long id,
            @PathVariable String vendorGstin) {
        clientService.unmapVendor(id, vendorGstin);
        return ResponseEntity.ok(Map.of(
                "message", "Vendor " + vendorGstin + " unmapped successfully."
        ));
    }
}
