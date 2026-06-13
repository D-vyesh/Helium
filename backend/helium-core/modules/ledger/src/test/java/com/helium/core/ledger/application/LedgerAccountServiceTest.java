package com.helium.core.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccount;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.ledger.domain.LedgerValidationException;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import com.helium.core.ledger.infrastructure.LedgerAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerAccountServiceTest {
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;

    @Mock
    private BalanceSnapshotRepository balanceSnapshotRepository;

    @InjectMocks
    private LedgerAccountService ledgerAccountService;

    @Test
    void opensAccountWithInsertIfAbsentAndSnapshotInsertIfAbsent() {
        LedgerAccount account = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.AVAILABLE);
        CreateLedgerAccountCommand command = new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-1",
            "usdt",
            BalanceType.AVAILABLE
        );

        when(ledgerAccountRepository.findByOwnerTypeAndOwnerIdAndAssetCodeAndBalanceType(
            LedgerAccountOwnerType.USER,
            "user-1",
            "USDT",
            BalanceType.AVAILABLE
        )).thenReturn(Optional.of(account));

        LedgerAccountView view = ledgerAccountService.openAccount(command);

        assertThat(view.accountId()).isEqualTo(account.id());
        verify(ledgerAccountRepository).insertIfAbsent(
            any(UUID.class),
            eq("USER"),
            eq("user-1"),
            eq("USDT"),
            eq("AVAILABLE"),
            eq(false),
            any(Instant.class)
        );
        verify(balanceSnapshotRepository).insertZeroIfAbsent(
            any(UUID.class),
            eq(account.id()),
            eq("USDT"),
            any(Instant.class)
        );
    }

    @Test
    void rejectsExistingAccountWithDifferentNegativeBalancePolicy() {
        LedgerAccount account = LedgerAccount.open(LedgerAccountOwnerType.USER, "user-1", "USDT", BalanceType.AVAILABLE);
        CreateLedgerAccountCommand command = new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            "user-1",
            "USDT",
            BalanceType.AVAILABLE,
            true
        );

        when(ledgerAccountRepository.findByOwnerTypeAndOwnerIdAndAssetCodeAndBalanceType(
            LedgerAccountOwnerType.USER,
            "user-1",
            "USDT",
            BalanceType.AVAILABLE
        )).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> ledgerAccountService.openAccount(command))
            .isInstanceOf(LedgerValidationException.class)
            .hasMessageContaining("negative balance policy");

        verify(balanceSnapshotRepository, never()).insertZeroIfAbsent(
            any(UUID.class),
            any(UUID.class),
            any(String.class),
            any(Instant.class)
        );
    }
}
