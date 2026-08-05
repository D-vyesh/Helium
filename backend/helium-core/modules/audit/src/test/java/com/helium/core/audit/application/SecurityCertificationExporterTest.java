package com.helium.core.audit.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.helium.core.audit.domain.ImmutableAuditLog;
import com.helium.core.audit.infrastructure.ImmutableAuditLogRepository;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class SecurityCertificationExporterTest {

    @Test
    void createsAnEvidenceArchiveFromImmutableAuditRecords() throws Exception {
        ImmutableAuditLogRepository repository = mock(ImmutableAuditLogRepository.class);
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(
            new ImmutableAuditLog("ACCESS_GRANTED", "admin-1", "{\"scope\":\"audit\"}", "0".repeat(64))
        ));
        SecurityCertificationExporter exporter = new SecurityCertificationExporter(repository);

        File archive = exporter.generateSoc2EvidencePackage();

        assertTrue(archive.isFile());
        try (ZipFile zip = new ZipFile(archive)) {
            assertTrue(zip.getEntry("manifest.json") != null);
            assertTrue(zip.getEntry("immutable-audit-log.csv") != null);
            String manifest = new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"framework\": \"soc2\""));
        } finally {
            Files.deleteIfExists(archive.toPath());
        }
    }
}
