package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.LedgerTransactionType;
import java.util.List;
import java.util.Objects;

public record LedgerPostingCommand(
    LedgerTransactionType type,
    String businessReference,
    String idempotencyKey,
    String description,
    AuditMetadata auditMetadata,
    List<PostingLineCommand> lines
) {
    public LedgerPostingCommand {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(auditMetadata, "auditMetadata");
        businessReference = requireText(businessReference, "businessReference");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        description = requireText(description, "description");
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("at least two posting lines are required");
        }
        lines = List.copyOf(lines);
    }

    private static String requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}

