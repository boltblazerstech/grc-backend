package com.company.grc.service;

import com.company.grc.dto.ClientDto;
import com.company.grc.entity.ClientEntity;
import com.company.grc.entity.ClientLogoEntity;
import com.company.grc.entity.ClientVendorMappingEntity;
import com.company.grc.entity.GrcScoreEntity;
import com.company.grc.entity.GstDetailsEntity;
import com.company.grc.repository.ClientLogoRepository;
import com.company.grc.repository.ClientRepository;
import com.company.grc.repository.ClientVendorMappingRepository;
import com.company.grc.repository.GrcScoreRepository;
import com.company.grc.repository.GstDetailsRepository;
import com.company.grc.repository.PanHsnConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final ClientLogoRepository clientLogoRepository;
    private final ClientVendorMappingRepository mappingRepository;
    private final GstDetailsRepository gstDetailsRepository;
    private final GrcScoreRepository grcScoreRepository;
    private final PanHsnConfigRepository panHsnConfigRepository;
    private final String frontendUrl;

    @Autowired
    public ClientService(ClientRepository clientRepository,
                         ClientLogoRepository clientLogoRepository,
                         ClientVendorMappingRepository mappingRepository,
                         GstDetailsRepository gstDetailsRepository,
                         GrcScoreRepository grcScoreRepository,
                         PanHsnConfigRepository panHsnConfigRepository,
                         @Value("${app.frontend.url:https://grc.scrapli.in}") String frontendUrl) {
        this.clientRepository = clientRepository;
        this.clientLogoRepository = clientLogoRepository;
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

        // Auto-link persistent logo mapped to this GSTIN if available
        String logoData = null;
        String logoContentType = null;
        Optional<ClientLogoEntity> existingLogo = clientLogoRepository.findByGstinIgnoreCase(gstin);
        if (existingLogo.isPresent()) {
            logoData = existingLogo.get().getLogoData();
            logoContentType = existingLogo.get().getLogoContentType();
        }

        ClientEntity client = ClientEntity.builder()
                .clientGstin(gstin)
                .clientName(name)
                .logoData(logoData)
                .logoContentType(logoContentType)
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

            // If client has no logo but the new GSTIN has a mapped logo, attach it
            if ((client.getLogoData() == null || client.getLogoData().isBlank())) {
                Optional<ClientLogoEntity> existingLogo = clientLogoRepository.findByGstinIgnoreCase(gstin);
                if (existingLogo.isPresent()) {
                    client.setLogoData(existingLogo.get().getLogoData());
                    client.setLogoContentType(existingLogo.get().getLogoContentType());
                }
            } else {
                // Keep the persistent map synchronized with new GSTIN
                saveOrUpdateLogoEntity(gstin, client.getClientName(), client.getLogoData(), client.getLogoContentType());
            }
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
    public void saveOrUpdateLogoEntity(String gstin, String clientName, String base64Data, String contentType) {
        if (gstin == null || gstin.trim().isBlank() || base64Data == null || base64Data.trim().isBlank()) return;
        String cleanGstin = gstin.trim().toUpperCase();
        Optional<ClientLogoEntity> existing = clientLogoRepository.findByGstinIgnoreCase(cleanGstin);
        ClientLogoEntity entity = existing.orElseGet(() -> ClientLogoEntity.builder().gstin(cleanGstin).build());
        if (clientName != null && !clientName.isBlank()) {
            entity.setClientName(clientName.trim());
        }
        entity.setLogoData(base64Data);
        entity.setLogoContentType(contentType != null ? contentType : "image/png");
        clientLogoRepository.save(entity);
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

        // Permanently map logo with client GSTIN for all future invoices & reports
        if (saved.getClientGstin() != null && !saved.getClientGstin().isBlank()) {
            saveOrUpdateLogoEntity(saved.getClientGstin(), saved.getClientName(), base64Data, determinedContentType);
        }

        return mapToResponseDto(saved);
    }

    @Transactional
    public ClientDto.ClientLogoInfoDto uploadLogoByGstin(String gstin, String clientName, MultipartFile file) throws IOException {
        if (gstin == null || gstin.trim().isBlank()) {
            throw new IllegalArgumentException("Client GSTIN is required.");
        }
        String cleanGstin = gstin.trim().toUpperCase();

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

        // 1. Permanently map in client_logos table
        saveOrUpdateLogoEntity(cleanGstin, clientName, base64Data, determinedContentType);

        // 2. Also update in clients table if present
        Optional<ClientEntity> clientOpt = clientRepository.findByClientGstin(cleanGstin);
        if (clientOpt.isPresent()) {
            ClientEntity client = clientOpt.get();
            client.setLogoData(base64Data);
            client.setLogoContentType(determinedContentType);
            if (clientName != null && !clientName.isBlank() && (client.getClientName() == null || client.getClientName().isBlank())) {
                client.setClientName(clientName.trim());
            }
            clientRepository.save(client);
        }

        return ClientDto.ClientLogoInfoDto.builder()
                .gstin(cleanGstin)
                .clientName(clientName)
                .hasLogo(true)
                .logoUrl("/api/admin/clients/logo/by-gstin/" + cleanGstin)
                .build();
    }

    @Transactional
    public void deleteLogo(Long id) {
        ClientEntity client = getClientEntityById(id);
        client.setLogoData(null);
        client.setLogoContentType(null);
        clientRepository.save(client);

        if (client.getClientGstin() != null && !client.getClientGstin().isBlank()) {
            clientLogoRepository.deleteByGstinIgnoreCase(client.getClientGstin().trim());
        }
    }

    @Transactional
    public void deleteLogoByGstin(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) return;
        String cleanGstin = gstin.trim().toUpperCase();
        clientLogoRepository.deleteByGstinIgnoreCase(cleanGstin);

        Optional<ClientEntity> clientOpt = clientRepository.findByClientGstin(cleanGstin);
        if (clientOpt.isPresent()) {
            ClientEntity client = clientOpt.get();
            client.setLogoData(null);
            client.setLogoContentType(null);
            clientRepository.save(client);
        }
    }

    public LogoData getLogo(Long id) {
        ClientEntity client = getClientEntityById(id);
        if (client.getLogoData() != null && !client.getLogoData().isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(client.getLogoData());
            String contentType = client.getLogoContentType() != null ? client.getLogoContentType() : "image/png";
            return new LogoData(imageBytes, contentType);
        }

        // Check persistent client_logos table by GSTIN
        if (client.getClientGstin() != null && !client.getClientGstin().isBlank()) {
            Optional<ClientLogoEntity> logoOpt = clientLogoRepository.findByGstinIgnoreCase(client.getClientGstin());
            if (logoOpt.isPresent() && logoOpt.get().getLogoData() != null && !logoOpt.get().getLogoData().isBlank()) {
                byte[] imageBytes = Base64.getDecoder().decode(logoOpt.get().getLogoData());
                String contentType = logoOpt.get().getLogoContentType() != null ? logoOpt.get().getLogoContentType() : "image/png";
                return new LogoData(imageBytes, contentType);
            }
        }

        throw new NoSuchElementException("No logo found for client id: " + id);
    }

    public LogoData getLogoByGstin(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) {
            throw new NoSuchElementException("GSTIN is required.");
        }
        String cleanGstin = gstin.trim().toUpperCase();

        // 1. Check client_logos table first
        Optional<ClientLogoEntity> logoOpt = clientLogoRepository.findByGstinIgnoreCase(cleanGstin);
        if (logoOpt.isPresent() && logoOpt.get().getLogoData() != null && !logoOpt.get().getLogoData().isBlank()) {
            ClientLogoEntity entity = logoOpt.get();
            byte[] imageBytes = Base64.getDecoder().decode(entity.getLogoData());
            String ct = entity.getLogoContentType() != null ? entity.getLogoContentType() : "image/png";
            return new LogoData(imageBytes, ct);
        }

        // 2. Fallback to clients table
        Optional<ClientEntity> clientOpt = clientRepository.findByClientGstin(cleanGstin);
        if (clientOpt.isPresent() && clientOpt.get().getLogoData() != null && !clientOpt.get().getLogoData().isBlank()) {
            ClientEntity c = clientOpt.get();
            // Automatically cache in client_logos
            saveOrUpdateLogoEntity(cleanGstin, c.getClientName(), c.getLogoData(), c.getLogoContentType());
            byte[] imageBytes = Base64.getDecoder().decode(c.getLogoData());
            String ct = c.getLogoContentType() != null ? c.getLogoContentType() : "image/png";
            return new LogoData(imageBytes, ct);
        }

        throw new NoSuchElementException("No logo found for GSTIN: " + cleanGstin);
    }

    public ClientDto.ClientLogoInfoDto getLogoInfoByGstin(String gstin) {
        if (gstin == null || gstin.trim().isBlank()) {
            return ClientDto.ClientLogoInfoDto.builder().hasLogo(false).build();
        }
        String cleanGstin = gstin.trim().toUpperCase();

        Optional<ClientLogoEntity> logoOpt = clientLogoRepository.findByGstinIgnoreCase(cleanGstin);
        if (logoOpt.isPresent() && logoOpt.get().getLogoData() != null && !logoOpt.get().getLogoData().isBlank()) {
            return ClientDto.ClientLogoInfoDto.builder()
                    .gstin(cleanGstin)
                    .clientName(logoOpt.get().getClientName())
                    .hasLogo(true)
                    .logoUrl("/api/admin/clients/logo/by-gstin/" + cleanGstin)
                    .build();
        }

        Optional<ClientEntity> clientOpt = clientRepository.findByClientGstin(cleanGstin);
        if (clientOpt.isPresent() && clientOpt.get().getLogoData() != null && !clientOpt.get().getLogoData().isBlank()) {
            return ClientDto.ClientLogoInfoDto.builder()
                    .gstin(cleanGstin)
                    .clientName(clientOpt.get().getClientName())
                    .hasLogo(true)
                    .logoUrl("/api/admin/clients/logo/by-gstin/" + cleanGstin)
                    .build();
        }

        return ClientDto.ClientLogoInfoDto.builder()
                .gstin(cleanGstin)
                .hasLogo(false)
                .logoUrl(null)
                .build();
    }

    public List<ClientDto.MappedVendorDto> getMappedVendors(Long clientId) {
        // Ensure client exists
        getClientEntityById(clientId);

        List<ClientVendorMappingEntity> mappings = mappingRepository.findByClientId(clientId);
        List<ClientDto.MappedVendorDto> result = new ArrayList<>();

        for (ClientVendorMappingEntity mapping : mappings) {
            String vendorGstin = mapping.getVendorGstin();
            Optional<GstDetailsEntity> gstOpt = gstDetailsRepository.findById(vendorGstin);
            if (gstOpt.isEmpty()) {
                gstOpt = gstDetailsRepository.findByGstinNormalized(vendorGstin);
            }
            Optional<GrcScoreEntity> scoreOpt = grcScoreRepository.findById(vendorGstin);
            if (scoreOpt.isEmpty()) {
                scoreOpt = grcScoreRepository.findByGstinNormalized(vendorGstin);
            }

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
            Integer delayCountGstr1 = gstOpt.map(GstDetailsEntity::getDelayCountGstr1).orElse(null);
            Integer delayCountGstr3b = gstOpt.map(GstDetailsEntity::getDelayCountGstr3b).orElse(null);
            String delayedMonthsGstr1 = gstOpt.map(GstDetailsEntity::getDelayedMonthsGstr1).orElse(null);
            String delayedMonthsGstr3b = gstOpt.map(GstDetailsEntity::getDelayedMonthsGstr3b).orElse(null);

            // If GST status is Unknown or unverified, return filing counts must be null (missing / N/A)
            if (gstStatus == null || "Unknown".equalsIgnoreCase(gstStatus.trim()) || "Error".equalsIgnoreCase(gstStatus.trim())) {
                delayCountGstr1 = null;
                delayCountGstr3b = null;
                delayedMonthsGstr1 = null;
                delayedMonthsGstr3b = null;
            }
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
                    .delayedMonthsGstr1(delayedMonthsGstr1)
                    .delayedMonthsGstr3b(delayedMonthsGstr3b)
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
     * Replaces all mapped vendors for a specific client with a new list of vendors.
     * All other clients and their existing vendor data remain completely safe and untouched.
     */
    @Transactional
    public List<String> replaceClientVendors(Long clientId, List<String> vendorGstins) {
        getClientEntityById(clientId);
        mappingRepository.deleteByClientId(clientId);
        mappingRepository.flush();
        return mapVendors(clientId, vendorGstins);
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

        boolean isGlobalReplace = Boolean.TRUE.equals(request.getReplaceAll()) 
                || "GLOBAL_REPLACE".equalsIgnoreCase(request.getImportMode());
        boolean replaceClientVendors = request.getReplaceClientVendors() == null 
                || Boolean.TRUE.equals(request.getReplaceClientVendors()) 
                || "CLIENT_REPLACE".equalsIgnoreCase(request.getImportMode());

        if (isGlobalReplace) {
            // Backup all existing client logos into client_logos table before clearing clients!
            List<ClientEntity> existingClients = clientRepository.findAll();
            for (ClientEntity ec : existingClients) {
                if (ec.getClientGstin() != null && !ec.getClientGstin().isBlank()
                        && ec.getLogoData() != null && !ec.getLogoData().isBlank()) {
                    saveOrUpdateLogoEntity(ec.getClientGstin(), ec.getClientName(), ec.getLogoData(), ec.getLogoContentType());
                }
            }
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

            // If not found in memory and not doing a global replace, check DB
            if (client == null && !isGlobalReplace) {
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
                        if (!isGlobalReplace) {
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

                    // Auto-restore mapped logo for this GSTIN if present
                    String logoData = null;
                    String logoContentType = null;
                    if (!clientGstin.isBlank()) {
                        Optional<ClientLogoEntity> existingLogo = clientLogoRepository.findByGstinIgnoreCase(clientGstin);
                        if (existingLogo.isPresent()) {
                            logoData = existingLogo.get().getLogoData();
                            logoContentType = existingLogo.get().getLogoContentType();
                        }
                    }

                    client = ClientEntity.builder()
                            .clientName(clientName)
                            .clientGstin(clientGstin)
                            .logoData(logoData)
                            .logoContentType(logoContentType)
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

            // 2. Map vendors for this client (client-wise replace or append)
            List<String> vendorGstins = item.getVendorGstins() != null ? item.getVendorGstins() : List.of();
            if (!isNew && replaceClientVendors && !isGlobalReplace) {
                // Replace ONLY this client's vendor mappings: all other clients remain completely safe & untouched!
                mappingRepository.deleteByClientId(client.getId());
                mappingRepository.flush();
            }
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

    /**
     * Search companies by name or GSTIN across clients and database records (for invoice creation).
     */
    @Transactional(readOnly = true)
    public List<ClientDto.CompanySearchResultDto> searchCompanies(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String cleanQuery = query.trim();
        Map<String, ClientDto.CompanySearchResultDto> resultMap = new LinkedHashMap<>();

        // 1. Search clients
        List<ClientEntity> matchedClients = clientRepository.searchByGstinOrName(cleanQuery);
        for (ClientEntity c : matchedClients) {
            String gstin = c.getClientGstin() != null ? c.getClientGstin().toUpperCase() : "";
            if (gstin.isBlank()) continue;

            String address = "";
            String gstType = "";
            Optional<GstDetailsEntity> gd = gstDetailsRepository.findById(gstin);
            if (gd.isPresent()) {
                address = gd.get().getAddress() != null ? gd.get().getAddress() : "";
                gstType = gd.get().getGstType() != null ? gd.get().getGstType() : "";
            }

            boolean clientHasLogo = (c.getLogoData() != null && !c.getLogoData().isBlank())
                    || clientLogoRepository.existsByGstinIgnoreCase(gstin);

            resultMap.put(gstin, ClientDto.CompanySearchResultDto.builder()
                    .gstin(gstin)
                    .name(c.getClientName())
                    .tradeName(c.getClientName())
                    .legalName(c.getClientName())
                    .address(address)
                    .gstType(gstType)
                    .source("Client")
                    .hasLogo(clientHasLogo)
                    .logoUrl(clientHasLogo ? "/api/admin/clients/logo/by-gstin/" + gstin : null)
                    .build());
        }

        // 2. Search gst_details (limit 25)
        Pageable pageable = PageRequest.of(0, 25);
        List<GstDetailsEntity> matchedGst = gstDetailsRepository.searchByGstinOrName(cleanQuery, pageable);
        for (GstDetailsEntity g : matchedGst) {
            String gstin = g.getGstin() != null ? g.getGstin().toUpperCase() : "";
            if (gstin.isBlank() || resultMap.containsKey(gstin)) continue;

            String displayName = (g.getTradeName() != null && !g.getTradeName().isBlank())
                    ? g.getTradeName()
                    : (g.getLegalName() != null ? g.getLegalName() : gstin);

            boolean dbHasLogo = clientLogoRepository.existsByGstinIgnoreCase(gstin);

            resultMap.put(gstin, ClientDto.CompanySearchResultDto.builder()
                    .gstin(gstin)
                    .name(displayName)
                    .tradeName(g.getTradeName())
                    .legalName(g.getLegalName())
                    .address(g.getAddress() != null ? g.getAddress() : "")
                    .gstType(g.getGstType())
                    .source("Database")
                    .hasLogo(dbHasLogo)
                    .logoUrl(dbHasLogo ? "/api/admin/clients/logo/by-gstin/" + gstin : null)
                    .build());
        }

        return new ArrayList<>(resultMap.values());
    }

    private ClientDto.ClientResponseDto mapToResponseDto(ClientEntity entity) {
        long vendorCount = mappingRepository.countByClientId(entity.getId());
        boolean hasLogo = (entity.getLogoData() != null && !entity.getLogoData().isBlank())
                || (entity.getClientGstin() != null && clientLogoRepository.existsByGstinIgnoreCase(entity.getClientGstin()));
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
