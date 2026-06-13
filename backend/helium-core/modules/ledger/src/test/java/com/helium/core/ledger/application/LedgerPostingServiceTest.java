package com.helium.core.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helium.core.ledger.domain.AuditMetadata;
import com.helium.core.ledger.domain.BalanceSnapshot;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.IdempotencyRecord;
import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerInvariantViolationException;
import com.helium.core.ledger.domain.LedgerTransaction;
import com.helium.core.ledger.domain.LedgerTransactionType;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.domain.PostingDirection;
import com.helium.core.ledger.domain.PostingLineDraft;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import com.helium.core.ledger.infrastructure.IdempotencyRecordRepository;
import com.helium.core.ledger.infrastructure.LedgerAccountRepository;
import com.helium.core.ledger.infrastructure.LedgerTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private BalanceSnapshotRepository balanceSnapshotRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LedgerPostingService ledgerPostingService;

    @Test
    void returnsExistingTransactionForIdempotentReplay() {
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);
        LedgerPostingCommand command = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineCommand(external.id(), PostingDirection.DEBIT, new BigDecimal("1.00")),
                new PostingLineCommand(userAvailable.id(), PostingDirection.CREDIT, new BigDecimal("1.00"))
            )
        );
        LedgerTransaction transaction = LedgerTransaction.create(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("1.00")),
                new PostingLineDraft(userAvailable, PostingDirection.CREDIT, new BigDecimal("1.00"))
            )
        );

        when(idempotencyRecordRepository.findById("idem-1"))
            .thenReturn(Optional.of(IdempotencyRecord.posted("idem-1", LedgerPostingService.requestHash(command), transaction)));

        LedgerPostingResult result = ledgerPostingService.post(command);

        assertThat(result.transactionId()).isEqualTo(transaction.id());
        assertThat(result.idempotentReplay()).isTrue();
        verify(jdbcTemplate).queryForList(anyString(), eq("idem-1"));
        verify(ledgerAccountRepository, never()).findAllByIdInForUpdate(anyCollection());
    }

    @Test
    void rejectsIdempotentReplayWithDifferentPayloadHash() {
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);
        LedgerPostingCommand command = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineCommand(external.id(), PostingDirection.DEBIT, new BigDecimal("1.00")),
                new PostingLineCommand(userAvailable.id(), PostingDirection.CREDIT, new BigDecimal("1.00"))
            )
        );
        LedgerTransaction existingTransaction = LedgerTransaction.create(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineDraft(external, PostingDirection.DEBIT, new BigDecimal("2.00")),
                new PostingLineDraft(userAvailable, PostingDirection.CREDIT, new BigDecimal("2.00"))
            )
        );

        when(idempotencyRecordRepository.findById("idem-1"))
            .thenReturn(Optional.of(IdempotencyRecord.posted("idem-1", "0000000000000000000000000000000000000000000000000000000000000000", existingTransaction)));

        assertThatThrownBy(() -> ledgerPostingService.post(command))
            .isInstanceOf(LedgerValidationException.class)
            .hasMessageContaining("different ledger posting payload");

        verify(ledgerAccountRepository, never()).findAllByIdInForUpdate(anyCollection());
    }

    @Test
    void rejectsPostingWhenBalanceSnapshotIsMissing() {
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);
        LedgerPostingCommand command = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            List.of(
                new PostingLineCommand(external.id(), PostingDirection.DEBIT, new BigDecimal("1.00")),
                new PostingLineCommand(userAvailable.id(), PostingDirection.CREDIT, new BigDecimal("1.00"))
            )
        );

        when(idempotencyRecordRepository.findById("idem-1")).thenReturn(Optional.empty());
        when(ledgerAccountRepository.findAllByIdInForUpdate(anyCollection()))
            .thenReturn(List.of(external, userAvailable));
        when(balanceSnapshotRepository.findByAccount_Id(external.id())).thenReturn(Optional.of(BalanceSnapshot.zeroFor(external)));
        when(balanceSnapshotRepository.findByAccount_Id(userAvailable.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerPostingService.post(command))
            .isInstanceOf(LedgerInvariantViolationException.class)
            .hasMessageContaining("balance snapshot is missing");

        verify(ledgerTransactionRepository, never()).save(any());
    }

    @Test
    void requestHashIncludesAuditMetadata() {
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);
        List<PostingLineCommand> lines = List.of(
            new PostingLineCommand(external.id(), PostingDirection.DEBIT, new BigDecimal("1.00")),
            new PostingLineCommand(userAvailable.id(), PostingDirection.CREDIT, new BigDecimal("1.00"))
        );
        LedgerPostingCommand original = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            auditMetadata(),
            lines
        );
        LedgerPostingCommand changedAudit = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit-1",
            "idem-1",
            "confirmed deposit",
            AuditMetadata.of("system", "ledger-test", "corr-2", "cause-1", "test"),
            lines
        );

        assertThat(LedgerPostingService.requestHash(changedAudit))
            .isNotEqualTo(LedgerPostingService.requestHash(original));
    }

    @Test
    void requestHashDoesNotAllowDelimiterAmbiguityBetweenFields() {
        LedgerAccount external = LedgerAccount.open(LedgerAccountOwnerType.EXTERNAL, "chain:btc", "BTC", BalanceType.EXTERNAL);
        LedgerAccount userAvailable = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "BTC", BalanceType.AVAILABLE);
        List<PostingLineCommand> lines = List.of(
            new PostingLineCommand(external.id(), PostingDirection.DEBIT, BigDecimal.ONE),
            new PostingLineCommand(userAvailable.id(), PostingDirection.CREDIT, BigDecimal.ONE)
        );
        LedgerPostingCommand first = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit|confirmed",
            "idem-1",
            "deposit",
            auditMetadata(),
            lines
        );
        LedgerPostingCommand second = new LedgerPostingCommand(
            LedgerTransactionType.DEPOSIT,
            "deposit",
            "idem-1",
            "confirmed|deposit",
            auditMetadata(),
            lines
        );

        assertThat(LedgerPostingService.requestHash(first))
            .isNotEqualTo(LedgerPostingService.requestHash(second));
    }

    private static AuditMetadata auditMetadata() {
        return AuditMetadata.of("system", "ledger-test", "corr-1", "cause-1", "test");
    }
}
