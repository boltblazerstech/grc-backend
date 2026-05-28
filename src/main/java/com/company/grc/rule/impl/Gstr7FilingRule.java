package com.company.grc.rule.impl;

import com.company.grc.entity.GstDetailsEntity;
import com.company.grc.rule.GrcRule;
import com.company.grc.service.GrcRuleConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class Gstr7FilingRule implements GrcRule {

    private final GrcRuleConfigService configService;

    @Autowired
    public Gstr7FilingRule(GrcRuleConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getRuleName() { return "GSTR-7 Filing"; }

    @Override
    public BigDecimal apply(GstDetailsEntity entity) {
        Map<String, Double> cfg = configService.getConfigMap();
        double maxScore = cfg.getOrDefault("G7_MAX", 10.0);
        
        // If status is NA or missing entirely
        String status = entity.getGstr7Status();
        if (status == null || status.isBlank() || "NA".equalsIgnoreCase(status.trim())) {
            return BigDecimal.valueOf(maxScore * cfg.getOrDefault("G7_NA_MULT", 0.3));
        }

        int missed = entity.getGstr7MissedCount() != null ? entity.getGstr7MissedCount() : 0;
        int delayed = entity.getGstr7DelayCount() != null ? entity.getGstr7DelayCount() : 0;

        double multiplier;
        if (missed == 0 && delayed == 0) {
            multiplier = cfg.getOrDefault("G7_REGULAR_MULT", 0.0);
        } else if (missed == 0 && delayed > 0) {
            multiplier = cfg.getOrDefault("G7_DELAYED_MULT", 0.3);
        } else if (missed > 0 && delayed == 0) {
            multiplier = cfg.getOrDefault("G7_MISSED_MULT", 0.5);
        } else {
            multiplier = cfg.getOrDefault("G7_MISSED_DELAYED_MULT", 0.8);
        }

        return BigDecimal.valueOf(maxScore * multiplier);
    }
}
