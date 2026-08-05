package com.helium.core.audit.application;

import com.helium.core.audit.domain.ImmutableAuditLog;
import com.helium.core.audit.infrastructure.ImmutableAuditLogRepository;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Creates an auditable evidence archive; it never claims an external certification. */
@Service
public class SecurityCertificationExporter {
    private static final Logger log = LoggerFactory.getLogger(SecurityCertificationExporter.class);
    private final ImmutableAuditLogRepository auditLogRepository;

    public SecurityCertificationExporter(ImmutableAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public File generateSoc2EvidencePackage() {
        return generateEvidencePackage("soc2");
    }

    public File generateCcssEvidencePackage() {
        return generateEvidencePackage("ccss");
    }

    private File generateEvidencePackage(String framework) {
        List<ImmutableAuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtAsc();
        Instant generatedAt = Instant.now();
        try {
            Path archive = Files.createTempFile("helium-" + framework + "-evidence-", ".zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
                write(output, "manifest.json", manifest(framework, generatedAt, logs));
                write(output, "immutable-audit-log.csv", auditLogCsv(logs));
            }
            log.info("Generated {} evidence package with {} immutable audit events at {}", framework, logs.size(), archive);
            return archive.toFile();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate " + framework + " evidence package", exception);
        }
    }

    private static void write(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static String manifest(String framework, Instant generatedAt, List<ImmutableAuditLog> logs) {
        String latestHash = logs.isEmpty() ? "" : logs.get(logs.size() - 1).currentHash();
        return "{\n"
            + "  \"framework\": \"" + json(framework) + "\",\n"
            + "  \"generatedAt\": \"" + generatedAt + "\",\n"
            + "  \"immutableAuditEventCount\": " + logs.size() + ",\n"
            + "  \"latestAuditHash\": \"" + json(latestHash) + "\",\n"
            + "  \"notice\": \"Evidence package only; it is not a compliance certification.\"\n"
            + "}\n";
    }

    private static String auditLogCsv(List<ImmutableAuditLog> logs) {
        StringBuilder csv = new StringBuilder("id,eventType,actorId,previousHash,currentHash,createdAt,payloadJson\n");
        for (ImmutableAuditLog log : logs) {
            csv.append(csv(log.id().toString())).append(',')
                .append(csv(log.eventType())).append(',')
                .append(csv(log.actorId())).append(',')
                .append(csv(log.previousHash())).append(',')
                .append(csv(log.currentHash())).append(',')
                .append(csv(log.createdAt().toString())).append(',')
                .append(csv(log.payloadJson())).append('\n');
        }
        return csv.toString();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
