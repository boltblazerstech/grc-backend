package com.company.grc.service;

import com.company.grc.dto.PanGstr7DataResponse;
import com.company.grc.entity.GstDetailsEntity;
import com.company.grc.entity.PanHsnConfigEntity;
import com.company.grc.repository.GstDetailsRepository;
import com.company.grc.repository.HsnCategoryRepository;
import com.company.grc.repository.PanHsnConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gstr7Service {

    private final PanHsnConfigRepository panHsnConfigRepository;
    private final GstDetailsRepository gstDetailsRepository;
    private final HsnCategoryRepository hsnCategoryRepository;

    @Transactional
    public PanHsnConfigEntity saveOrUpdateHsn(String pan, Long categoryId, String updatedBy) {
        PanHsnConfigEntity config = panHsnConfigRepository.findById(pan)
                .orElse(new PanHsnConfigEntity());
        config.setPan(pan);
        config.setCategoryId(categoryId);

        boolean isScrap = false;
        if (categoryId != null) {
            isScrap = hsnCategoryRepository.findById(categoryId)
                    .map(c -> "Scrap".equalsIgnoreCase(c.getName() != null ? c.getName().trim() : ""))
                    .orElse(false);
        }
        config.setIsApplicable(isScrap);

        config.setUpdatedBy(updatedBy);
        return panHsnConfigRepository.save(config);
    }

    @Transactional
    public GstDetailsEntity markGstinAsGstd(String gstin, String gstdNo) {
        GstDetailsEntity gstDetails = gstDetailsRepository.findById(gstin)
                .orElseThrow(() -> new IllegalArgumentException("GSTIN not found: " + gstin));

        if (gstdNo != null && !gstdNo.trim().isEmpty()) {
            gstDetails.setGstdNo(gstdNo.trim());
        } else {
            gstDetails.setGstdNo(null);
        }

        return gstDetailsRepository.save(gstDetails);
    }

    @Transactional
    public GstDetailsEntity updateGstr7Data(String gstin, String status, Integer delayCount, Integer missedCount) {
        GstDetailsEntity gstDetails = gstDetailsRepository.findById(gstin)
                .orElseThrow(() -> new IllegalArgumentException("GSTIN not found: " + gstin));

        gstDetails.setGstr7Status(status);
        if (delayCount != null) {
            gstDetails.setGstr7DelayCount(delayCount);
        }
        if (missedCount != null) {
            gstDetails.setGstr7MissedCount(missedCount);
        }
        gstDetails.setGstr7LastUpdated(LocalDateTime.now());

        return gstDetailsRepository.save(gstDetails);
    }

    @Transactional
    public PanHsnConfigEntity setTdsApplicability(String pan, Boolean isApplicable, String updatedBy) {
        PanHsnConfigEntity config = panHsnConfigRepository.findById(pan)
                .orElse(new PanHsnConfigEntity());
        config.setPan(pan);
        config.setIsApplicable(Boolean.TRUE.equals(isApplicable));
        config.setUpdatedBy(updatedBy);
        return panHsnConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public List<PanGstr7DataResponse> fetchAllPanGstr7Data() {
        List<GstDetailsEntity> allGstDetails = gstDetailsRepository.findAll();

        Map<String, List<GstDetailsEntity>> groupedByPan = allGstDetails.stream()
                .filter(g -> g.getPanNumber() != null && !g.getPanNumber().trim().isEmpty())
                .collect(Collectors.groupingBy(GstDetailsEntity::getPanNumber));

        Map<String, PanHsnConfigEntity> allConfigs = panHsnConfigRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getPan() != null ? c.getPan().trim().toUpperCase() : "", c -> c, (a, b) -> a));

        log.info("[GSTR7] pan_hsn_config rows: {}, applicable PANs: {}",
                allConfigs.size(),
                allConfigs.entrySet().stream()
                        .filter(e -> Boolean.TRUE.equals(e.getValue().getIsApplicable()))
                        .map(Map.Entry::getKey).collect(Collectors.toList()));
        log.info("[GSTR7] gst_details PANs sample: {}",
                groupedByPan.keySet().stream().limit(5).collect(Collectors.toList()));

        return groupedByPan.entrySet().stream()
                .map(entry -> {
                    String pan = entry.getKey() != null ? entry.getKey().trim().toUpperCase() : "";
                    PanHsnConfigEntity config = allConfigs.get(pan);

                    List<PanGstr7DataResponse.GstinData> gstinDataList = entry.getValue().stream()
                            .map(g -> PanGstr7DataResponse.GstinData.builder()
                                    .gstin(g.getGstin())
                                    .gstr7Status(g.getGstr7Status())
                                    .gstr7DelayCount(g.getGstr7DelayCount())
                                    .gstr7MissedCount(g.getGstr7MissedCount())
                                    .gstr7LastUpdated(g.getGstr7LastUpdated())
                                    .gstType(g.getGstType())
                                    .gstdNo(g.getGstdNo())
                                    .tradeName(g.getTradeName())
                                    .legalName(g.getLegalName())
                                    .createdAt(g.getCreatedAt())
                                    .build())
                            .collect(Collectors.toList());

                    String companyName = entry.getValue().stream()
                            .filter(g -> g.getLegalName() != null && !g.getLegalName().trim().isEmpty())
                            .map(GstDetailsEntity::getLegalName)
                            .findFirst()
                            .orElse(entry.getValue().stream()
                                    .filter(g -> g.getTradeName() != null && !g.getTradeName().trim().isEmpty())
                                    .map(GstDetailsEntity::getTradeName)
                                    .findFirst()
                                    .orElse(""));

                    return PanGstr7DataResponse.builder()
                            .panNumber(pan)
                            .companyName(companyName)
                            .isApplicable(config != null ? config.getIsApplicable() : null)
                            .gstins(gstinDataList)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
