package com.company.grc.service;

import com.company.grc.dto.ClientDto;
import com.company.grc.entity.ClientEntity;
import com.company.grc.entity.ClientVendorMappingEntity;
import com.company.grc.entity.GrcScoreEntity;
import com.company.grc.entity.GstDetailsEntity;
import com.company.grc.repository.ClientRepository;
import com.company.grc.repository.ClientVendorMappingRepository;
import com.company.grc.repository.GrcScoreRepository;
import com.company.grc.repository.GstDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/svg+xml"
    );
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private final ClientRepository clientRepository;
    private final ClientVendorMappingRepository mappingRepository;
    private final GstDetailsRepository gstDetailsRepository;
    private final GrcScoreRepository grcScoreRepository;
    private final String frontendUrl;

    @Autowired
    public ClientService(ClientRepository clientRepository,
                         ClientVendorMappingRepository mappingRepository,
                         GstDetailsRepository gstDetailsRepository,
                         GrcScoreRepository grcScoreRepository,
                         @Value("${app.frontend.url:https://grc.scrapli.in}") String frontendUrl) {
        this.clientRepository = clientRepository;
        this.mappingRepository = mappingRepository;
        this.gstDetailsRepository = gstDetailsRepository;
        this.grcScoreRepository = grcScoreRepository;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public List<ClientDto.ClientResponseDto> getAllClients() {
        List<ClientEntity> clients = clientRepository.findAll();
        return clients.stream()
                .map(this::mapToResponseDto)
                .sorted(Comparator.comparing(ClientDto.ClientResponseDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public ClientEntity getClientEntityById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Client not found with id: " + id));
    }

    public ClientDto.ClientResponseDto getClientById(Long id) {
        return mapToResponseDto(getClientEntityById(id));
    }

    @Transactional
    public ClientDto.ClientResponseDto createClient(ClientDto.ClientRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Client request body cannot be null");
        }

        String gstin = validateAndFormatGstin(request.getClientGstin());
        String name = validateClientName(request.getClientName());

        if (clientRepository.existsByClientGstin(gstin)) {
            throw new IllegalArgumentException("A client with GSTIN " + gstin + " already exists.");
        }

        ClientEntity client = ClientEntity.builder()
                .clientGstin(gstin)
                .clientName(name)
                .build();

        ClientEntity saved = clientRepository.save(client);
        return mapToResponseDto(saved);
    }

    @Transactional
    public ClientDto.ClientResponseDto updateClient(Long id, ClientDto.ClientRequestDto request) {
        ClientEntity client = getClientEntityById(id);

        if (request.getClientGstin() != null && !request.getClientGstin().isBlank()) {
            String gstin = validateAndFormatGstin(request.getClientGstin());
            if (clientRepository.existsByClientGstinAndIdNot(gstin, id)) {
                throw new IllegalArgumentException("Another client with GSTIN " + gstin + " already exists.");
            }
            client.setClientGstin(gstin);
        }

        if (request.getClientName() != null && !request.getClientName().isBlank()) {
            client.setClientName(validateClientName(request.getClientName()));
        }

        ClientEntity updated = clientRepository.save(client);
        return mapToResponseDto(updated);
    }

    @Transactional
    public void deleteClient(Long id) {
        if (!clientRepository.existsById(id)) {
            throw new NoSuchElementException("Client not found with id: " + id);
        }
        mappingRepository.deleteByClientId(id);
        clientRepository.deleteById(id);
    }

    @Transactional
    public ClientDto.ClientResponseDto uploadLogo(Long id, MultipartFile file) throws IOException {
        ClientEntity client = getClientEntityById(id);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is empty or missing.");
        }

        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("File size exceeds 2 MB limit.");
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        if (!isValidImageType(contentType, originalFilename)) {
            throw new IllegalArgumentException("Invalid file type. Only PNG, JPEG, and SVG images are supported.");
        }

        String determinedContentType = determineContentType(contentType, originalFilename);
        byte[] bytes = file.getBytes();
        String base64Data = Base64.getEncoder().encodeToString(bytes);

        client.setLogoData(base64Data);
        client.setLogoContentType(determinedContentType);
        ClientEntity saved = clientRepository.save(client);

        return mapToResponseDto(saved);
    }

    public LogoData getLogo(Long id) {
        ClientEntity client = getClientEntityById(id);
        if (client.getLogoData() == null || client.getLogoData().isBlank()) {
            throw new NoSuchElementException("No logo found for client id: " + id);
        }

        byte[] imageBytes = Base64.getDecoder().decode(client.getLogoData());
        String contentType = client.getLogoContentType() != null ? client.getLogoContentType() : "image/png";

        return new LogoData(imageBytes, contentType);
    }

    public List<ClientDto.MappedVendorDto> getMappedVendors(Long clientId) {
        // Ensure client exists
        getClientEntityById(clientId);

        List<ClientVendorMappingEntity> mappings = mappingRepository.findByClientId(clientId);
        List<ClientDto.MappedVendorDto> result = new ArrayList<>();

        for (ClientVendorMappingEntity mapping : mappings) {
            String vendorGstin = mapping.getVendorGstin();
            Optional<GstDetailsEntity> gstOpt = gstDetailsRepository.findById(vendorGstin);
            Optional<GrcScoreEntity> scoreOpt = grcScoreRepository.findById(vendorGstin);

            String tradeName = gstOpt.map(GstDetailsEntity::getTradeName).orElse(null);
            String legalName = gstOpt.map(GstDetailsEntity::getLegalName).orElse(null);
            String gstStatus = gstOpt.map(GstDetailsEntity::getGstStatus).orElse("Unknown");
            Integer grcScore = scoreOpt.map(GrcScoreEntity::getScore).orElse(null);

            String reportUrl = frontendUrl + "/vendor-report/" + vendorGstin;

            result.add(ClientDto.MappedVendorDto.builder()
                    .vendorGstin(vendorGstin)
                    .tradeName(tradeName)
                    .legalName(legalName)
                    .gstStatus(gstStatus)
                    .grcScore(grcScore)
                    .mappedAt(mapping.getMappedAt())
                    .reportUrl(reportUrl)
                    .build());
        }

        result.sort(Comparator.comparing(ClientDto.MappedVendorDto::getMappedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional
    public List<String> mapVendors(Long clientId, List<String> vendorGstins) {
        ClientEntity client = getClientEntityById(clientId);

        if (vendorGstins == null || vendorGstins.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> newlyMapped = new ArrayList<>();
        for (String rawGstin : vendorGstins) {
            if (rawGstin == null || rawGstin.trim().isBlank()) continue;
            String gstin = rawGstin.trim().toUpperCase();

            if (!mappingRepository.existsByClientIdAndVendorGstin(clientId, gstin)) {
                ClientVendorMappingEntity mapping = ClientVendorMappingEntity.builder()
                        .client(client)
                        .vendorGstin(gstin)
                        .build();
                mappingRepository.save(mapping);
                newlyMapped.add(gstin);
            }
        }

        return newlyMapped;
    }

    @Transactional
    public void unmapVendor(Long clientId, String vendorGstin) {
        getClientEntityById(clientId);
        if (vendorGstin != null && !vendorGstin.trim().isBlank()) {
            mappingRepository.deleteByClientIdAndVendorGstin(clientId, vendorGstin.trim().toUpperCase());
        }
    }

    private ClientDto.ClientResponseDto mapToResponseDto(ClientEntity entity) {
        long vendorCount = mappingRepository.countByClientId(entity.getId());
        boolean hasLogo = entity.getLogoData() != null && !entity.getLogoData().isBlank();
        String logoUrl = hasLogo ? "/api/admin/clients/" + entity.getId() + "/logo" : null;

        return ClientDto.ClientResponseDto.builder()
                .id(entity.getId())
                .clientGstin(entity.getClientGstin())
                .clientName(entity.getClientName())
                .hasLogo(hasLogo)
                .logoUrl(logoUrl)
                .vendorCount(vendorCount)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String validateAndFormatGstin(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) {
            throw new IllegalArgumentException("Client GSTIN is required.");
        }
        String formatted = gstin.trim().toUpperCase();
        if (formatted.length() != 15) {
            throw new IllegalArgumentException("GSTIN must be exactly 15 characters long.");
        }
        if (!GSTIN_PATTERN.matcher(formatted).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: " + formatted);
        }
        return formatted;
    }

    private String validateClientName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Client name is required.");
        }
        return name.trim();
    }

    private boolean isValidImageType(String contentType, String filename) {
        if (contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg");
        }
        return false;
    }

    private String determineContentType(String contentType, String filename) {
        if (contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return contentType.toLowerCase();
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
        }
        return "image/png";
    }

    public static class LogoData {
        private final byte[] data;
        private final String contentType;

        public LogoData(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
