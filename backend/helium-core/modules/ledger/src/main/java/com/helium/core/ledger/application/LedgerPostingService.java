package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceSnapshot;
import com.helium.core.ledger.domain.IdempotencyRecord;
import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerInvariantViolationException;
import com.helium.core.ledger.domain.LedgerTransaction;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.domain.PostingLineDraft;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import com.helium.core.ledger.infrastructure.IdempotencyRecordRepository;
import com.helium.core.ledger.infrastructure.LedgerAccountRepository;
import com.helium.core.ledger.infrastructure.LedgerTransactionRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerPostingService implements LedgerPostingPort {
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final BalanceSnapshotRepository balanceSnapshotRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final JdbcTemplate jdbcTemplate;

    public LedgerPostingService(
        LedgerAccountRepository ledgerAccountRepository,
        LedgerTransactionRepository ledgerTransactionRepository,
        BalanceSnapshotRepository balanceSnapshotRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.balanceSnapshotRepository = balanceSnapshotRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public LedgerPostingResult post(LedgerPostingCommand command) {
        String requestHash = requestHash(command);
        lockIdempotencyKey(command.idempotencyKey());

        return idempotencyRecordRepository.findById(command.idempotencyKey())
            .map(existing -> replayExisting(command, requestHash, existing))
            .orElseGet(() -> postNewTransaction(command, requestHash));
    }

    private LedgerPostingResult replayExisting(
        LedgerPostingCommand command,
        String requestHash,
        IdempotencyRecord existing
    ) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new LedgerValidationException("idempotency key was reused with a different ledger posting payload");
        }
        return new LedgerPostingResult(existing.transactionId(), command.idempotencyKey(), true);
    }

    private LedgerPostingResult postNewTransaction(LedgerPostingCommand command, String requestHash) {
        List<UUID> accountIds = command.lines().stream()
            .map(PostingLineCommand::accountId)
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();

        Map<UUID, LedgerAccount> accounts = ledgerAccountRepository.findAllByIdInForUpdate(accountIds).stream()
            .collect(Collectors.toMap(LedgerAccount::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        if (accounts.size() != accountIds.size()) {
            throw new LedgerValidationException("one or more ledger accounts were not found");
        }

        List<PostingLineDraft> drafts = command.lines().stream()
            .map(line -> new PostingLineDraft(accounts.get(line.accountId()), line.direction(), line.amount()))
            .toList();

        LedgerTransaction transaction = LedgerTransaction.create(
            command.type(),
            command.businessReference(),
            command.idempotencyKey(),
            command.description(),
            command.auditMetadata(),
            drafts
        );

        Map<UUID, BalanceSnapshot> snapshots = accounts.values().stream()
            .collect(Collectors.toMap(
                LedgerAccount::id,
                account -> balanceSnapshotRepository.findByAccount_Id(account.id())
                    .orElseThrow(() -> new LedgerInvariantViolationException("balance snapshot is missing for account " + account.id())),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        transaction.postingLines().forEach(line -> snapshots.get(line.account().id()).apply(line));

        LedgerTransaction savedTransaction = ledgerTransactionRepository.save(transaction);
        balanceSnapshotRepository.saveAll(snapshots.values());
        idempotencyRecordRepository.save(IdempotencyRecord.posted(command.idempotencyKey(), requestHash, savedTransaction));

        return new LedgerPostingResult(savedTransaction.id(), command.idempotencyKey(), false);
    }

    private void lockIdempotencyKey(String idempotencyKey) {
        jdbcTemplate.queryForList(
            "select pg_advisory_xact_lock(hashtext('helium-ledger-idempotency'), hashtext(?))",
            idempotencyKey
        );
    }

    static String requestHash(LedgerPostingCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashField(digest, command.type().name());
            updateHashField(digest, command.businessReference());
            updateHashField(digest, command.description());
            AuditMetadata audit = command.auditMetadata();
            updateHashField(digest, audit.actorId());
            updateHashField(digest, audit.sourceModule());
            updateHashField(digest, audit.correlationId());
            updateHashField(digest, audit.causationId());
            updateHashField(digest, audit.reason());
            command.lines().stream()
                .sorted(Comparator
                    .comparing((PostingLineCommand line) -> line.accountId().toString())
                    .thenComparing(line -> line.direction().name())
                    .thenComparing(line -> line.amount().stripTrailingZeros().toPlainString()))
                .forEach(line -> {
                    updateHashField(digest, line.accountId().toString());
                    updateHashField(digest, line.direction().name());
                    updateHashField(digest, line.amount().stripTrailingZeros().toPlainString());
                });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void updateHashField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
