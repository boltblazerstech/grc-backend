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
import com.company.grc.repository.PanHsnConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9A-Z]{15}$");

    private final ClientRepository clientRepository;
    private final ClientVendorMappingRepository mappingRepository;
    private final GstDetailsRepository gstDetailsRepository;
    private final GrcScoreRepository grcScoreRepository;
    private final PanHsnConfigRepository panHsnConfigRepository;
    private final String frontendUrl;

    @Autowired
    public ClientService(ClientRepository clientRepository,
                         ClientVendorMappingRepository mappingRepository,
                         GstDetailsRepository gstDetailsRepository,
                         GrcScoreRepository grcScoreRepository,
                         PanHsnConfigRepository panHsnConfigRepository,
                         @Value("${app.frontend.url:https://grc.scrapli.in}") String frontendUrl) {
        this.clientRepository = clientRepository;
        this.mappingRepository = mappingRepository;
        this.gstDetailsRepository = gstDetailsRepository;
        this.grcScoreRepository = grcScoreRepository;
        this.panHsnConfigRepository = panHsnConfigRepository;
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

            String rawTrade = gstOpt.map(GstDetailsEntity::getTradeName).orElse(null);
            String rawLegal = gstOpt.map(GstDetailsEntity::getLegalName).orElse(null);
            String cleanTrade = isInvalidName(rawTrade) ? null : rawTrade.trim();
            String cleanLegal = isInvalidName(rawLegal) ? null : rawLegal.trim();

            // If tradeName is null/NA, fallback to legalName; if legalName is null/NA, fallback to tradeName
            String tradeName = cleanTrade != null ? cleanTrade : cleanLegal;
            String legalName = cleanLegal != null ? cleanLegal : cleanTrade;

            String gstType = gstOpt.map(GstDetailsEntity::getGstType).orElse(null);
            LocalDate regDate = gstOpt.map(GstDetailsEntity::getRegistrationDate).orElse(null);
            Double ageYears = null;
            if (regDate != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(regDate, LocalDate.now());
                ageYears = Math.round((days / 365.25) * 10.0) / 10.0;
            }

            String gstStatus = gstOpt.map(GstDetailsEntity::getGstStatus).orElse("Unknown");
            Integer delayCountGstr1 = gstOpt.map(GstDetailsEntity::getDelayCountGstr1).orElse(0);
            Integer delayCountGstr3b = gstOpt.map(GstDetailsEntity::getDelayCountGstr3b).orElse(0);
            Integer grcScore = scoreOpt.map(GrcScoreEntity::getScore).orElse(null);

            String gstdNo = gstOpt.map(GstDetailsEntity::getGstdNo).orElse(null);
            String gstr7Status = gstOpt.map(GstDetailsEntity::getGstr7Status).orElse(null);
            Integer gstr7DelayCount = gstOpt.map(GstDetailsEntity::getGstr7DelayCount).orElse(0);
            Integer gstr7MissedCount = gstOpt.map(GstDetailsEntity::getGstr7MissedCount).orElse(0);
            String aadhaarValidation = gstOpt.map(GstDetailsEntity::getAadhaarValidation).orElse(null);
            String coreActivity = gstOpt.map(GstDetailsEntity::getCoreActivity).orElse(null);
            String aggregateTurnover = gstOpt.map(GstDetailsEntity::getAggregateTurnover).orElse(null);
            String panNumber = gstOpt.map(GstDetailsEntity::getPanNumber).orElse(null);

            Boolean tdsApplicable = null;
            if (panNumber != null && !panNumber.isBlank()) {
                tdsApplicable = panHsnConfigRepository.findById(panNumber)
                        .map(com.company.grc.entity.PanHsnConfigEntity::getIsApplicable)
                        .orElse(null);
            }

            String reportUrl = frontendUrl + "/vendor-report/" + vendorGstin;

            result.add(ClientDto.MappedVendorDto.builder()
                    .vendorGstin(vendorGstin)
                    .tradeName(tradeName)
                    .legalName(legalName)
                    .gstType(gstType)
                    .registrationDate(regDate)
                    .ageYears(ageYears)
                    .gstStatus(gstStatus)
                    .delayCountGstr1(delayCountGstr1)
                    .delayCountGstr3b(delayCountGstr3b)
                    .grcScore(grcScore)
                    .mappedAt(mapping.getMappedAt())
                    .reportUrl(reportUrl)
                    .gstdNo(gstdNo)
                    .gstr7Status(gstr7Status)
                    .gstr7DelayCount(gstr7DelayCount)
                    .gstr7MissedCount(gstr7MissedCount)
                    .tdsApplicable(tdsApplicable)
                    .aadhaarValidation(aadhaarValidation)
                    .coreActivity(coreActivity)
                    .aggregateTurnover(aggregateTurnover)
                    .panNumber(panNumber)
                    .build());
        }

        result.sort(Comparator.comparing(ClientDto.MappedVendorDto::getMappedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * Map vendors to a client (strict 1-to-many: one vendor mapped with one client only).
     * If a vendor is already mapped to this client, it is kept.
     * If a vendor was previously mapped to another client, it is moved to this client.
     */
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

            Optional<ClientVendorMappingEntity> existingOpt = mappingRepository.findByVendorGstin(gstin);
            if (existingOpt.isPresent()) {
                ClientVendorMappingEntity existing = existingOpt.get();
                if (existing.getClient().getId().equals(clientId)) {
                    // Already mapped to this client
                    continue;
                } else {
                    // Was mapped to another client: reassign exclusively to this client
                    existing.setClient(client);
                    existing.setMappedAt(LocalDateTime.now());
                    mappingRepository.saveAndFlush(existing);
                    newlyMapped.add(gstin);
                }
            } else {
                ClientVendorMappingEntity mapping = ClientVendorMappingEntity.builder()
                        .client(client)
                        .vendorGstin(gstin)
                        .build();
                mappingRepository.saveAndFlush(mapping);
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

    /**
     * Returns a dictionary of all assigned vendors across all clients:
     * { "VENDOR_GSTIN": { "clientId": 1, "clientName": "Tata", "clientGstin": "..." } }
     */
    public Map<String, Map<String, Object>> getAllAssignedVendors() {
        List<ClientVendorMappingEntity> all = mappingRepository.findAll();
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (ClientVendorMappingEntity m : all) {
            if (m.getClient() != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("clientId", m.getClient().getId());
                info.put("clientName", m.getClient().getClientName());
                info.put("clientGstin", m.getClient().getClientGstin());
                result.put(m.getVendorGstin(), info);
            }
        }
        return result;
    }

    /**
     * Bulk import clients and map their vendors from an uploaded sheet payload.
     * When replaceExisting is true (default), all existing clients and vendor mappings
     * are completely wiped and replaced with the new data from the sheet.
     */
    @Transactional
    public ClientDto.BulkImportResultDto bulkImportFromSheet(ClientDto.BulkImportRequestDto request) {
        if (request == null || request.getClients() == null || request.getClients().isEmpty()) {
            throw new IllegalArgumentException("Import data cannot be empty.");
        }

        boolean replaceExisting = request.getReplaceExisting() == null || Boolean.TRUE.equals(request.getReplaceExisting());
        if (replaceExisting) {
            mappingRepository.deleteAllInBatch();
            clientRepository.deleteAllInBatch();
            mappingRepository.flush();
            clientRepository.flush();
        }

        int createdCount = 0;
        int updatedCount = 0;
        int totalVendorsMapped = 0;
        List<Map<String, Object>> detailsList = new ArrayList<>();

        Map<String, ClientEntity> memoryByName = new HashMap<>();
        Map<String, ClientEntity> memoryByGstin = new HashMap<>();

        for (ClientDto.BulkImportClientItem item : request.getClients()) {
            String clientName = item.getClientName() != null ? item.getClientName().trim() : "";
            if (clientName.isBlank()) continue;

            String clientGstin = item.getClientGstin() != null ? item.getClientGstin().trim().toUpperCase() : "";

            // 1. Resolve or create client
            ClientEntity client = null;
            boolean isNew = false;

            // Check in-memory first (for duplicate rows in the sheet)
            if (!clientGstin.isBlank() && memoryByGstin.containsKey(clientGstin)) {
                client = memoryByGstin.get(clientGstin);
            } else if (memoryByName.containsKey(clientName.toLowerCase())) {
                client = memoryByName.get(clientName.toLowerCase());
            }

            // If not found in memory and not replacing, check DB
            if (client == null && !replaceExisting) {
                if (!clientGstin.isBlank()) {
                    Optional<ClientEntity> existingByGstin = clientRepository.findByClientGstin(clientGstin);
                    if (existingByGstin.isPresent()) {
                        client = existingByGstin.get();
                    }
                }
                if (client == null) {
                    Optional<ClientEntity> existingByName = clientRepository.findByClientNameIgnoreCase(clientName);
                    if (existingByName.isPresent()) {
                        client = existingByName.get();
                    }
                }
            }

            // If still not found, search gst_details for matching GSTIN
            if (client == null) {
                if (clientGstin.isBlank()) {
                    List<GstDetailsEntity> matches = gstDetailsRepository.searchByName(clientName);
                    if (!matches.isEmpty()) {
                        clientGstin = matches.get(0).getGstin();
                        if (!replaceExisting) {
                            Optional<ClientEntity> used = clientRepository.findByClientGstin(clientGstin);
                            if (used.isPresent()) {
                                client = used.get();
                            }
                        }
                    }
                }

                // If still no client entity, create new
                if (client == null) {
                    if (clientGstin.isBlank() || clientGstin.length() != 15) {
                        String clean = clientName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                        if (clean.length() > 7) clean = clean.substring(0, 7);
                        String suffix = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6).toUpperCase();
                        clientGstin = ("99" + clean + suffix);
                        if (clientGstin.length() > 15) clientGstin = clientGstin.substring(0, 15);
                        while (clientGstin.length() < 15) clientGstin = clientGstin + "X";
                    }

                    client = ClientEntity.builder()
                            .clientName(clientName)
                            .clientGstin(clientGstin)
                            .build();
                    client = clientRepository.saveAndFlush(client);
                    createdCount++;
                    isNew = true;
                }
            }

            if (!isNew) {
                updatedCount++;
            }

            memoryByName.put(clientName.toLowerCase(), client);
            if (client.getClientGstin() != null) {
                memoryByGstin.put(client.getClientGstin(), client);
            }

            // 2. Map vendors for this client (strict 1-to-many)
            List<String> vendorGstins = item.getVendorGstins() != null ? item.getVendorGstins() : List.of();
            List<String> mappedGstins = mapVendors(client.getId(), vendorGstins);
            totalVendorsMapped += mappedGstins.size();

            Map<String, Object> detail = new HashMap<>();
            detail.put("clientId", client.getId());
            detail.put("clientName", client.getClientName());
            detail.put("clientGstin", client.getClientGstin());
            detail.put("mappedCount", mappedGstins.size());
            detail.put("totalRequested", vendorGstins.size());
            detailsList.add(detail);
        }

        return ClientDto.BulkImportResultDto.builder()
                .clientsCreated(createdCount)
                .clientsUpdated(updatedCount)
                .vendorsMapped(totalVendorsMapped)
                .details(detailsList)
                .build();
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
            throw new IllegalArgumentException("GSTIN must contain 15 alphanumeric characters.");
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

    private boolean isInvalidName(String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() ||
                t.equalsIgnoreCase("NA") ||
                t.equalsIgnoreCase("N/A") ||
                t.equalsIgnoreCase("null") ||
                t.equalsIgnoreCase("none") ||
                t.equalsIgnoreCase("-") ||
                t.equalsIgnoreCase("undefined");
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
