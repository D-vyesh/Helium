package com.helium.core.compliancelite.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regulatory filing data must originate from the contracted compliance system
 * and an authorized officer. These routes deliberately reject requests until
 * that provider is configured; they never emit fabricated reports.
 */
@RestController
@RequestMapping("/api/v1/regulatory/reports")
public class RegulatoryReportingController {

    @GetMapping("/sar")
    public void generateSuspiciousActivityReport() {
        unavailable("SAR");
    }

    @GetMapping("/fatf/travel-rule")
    public void getTravelRuleData() {
        unavailable("FATF Travel Rule");
    }

    @GetMapping("/mica")
    public void generateMicaReport() {
        unavailable("MiCA");
    }

    @GetMapping("/ofac")
    public void generateOfacScreeningReport() {
        unavailable("OFAC");
    }

    private static void unavailable(String reportType) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            reportType + " reporting requires a configured compliance provider and authorized reviewer"
        );
    }
}
