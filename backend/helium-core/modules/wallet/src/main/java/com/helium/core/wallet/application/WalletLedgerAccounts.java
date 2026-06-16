package com.helium.core.wallet.application;

import com.helium.core.ledger.application.CreateLedgerAccountCommand;
import com.helium.core.ledger.application.LedgerAccountPort;
import com.helium.core.ledger.application.LedgerAccountView;
import com.helium.core.ledger.domain.BalanceType;
import com.helium.core.ledger.domain.LedgerAccountOwnerType;
import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import org.springframework.stereotype.Service;

@Service
public class WalletLedgerAccounts {
    private final LedgerAccountPort ledgerAccountPort;

    public WalletLedgerAccounts(LedgerAccountPort ledgerAccountPort) {
        this.ledgerAccountPort = ledgerAccountPort;
    }

    public LedgerAccountView userAvailable(String userId, String assetCode) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            userId,
            Asset.normalizeCode(assetCode),
            BalanceType.AVAILABLE
        ));
    }

    public LedgerAccountView userLocked(String userId, String assetCode) {
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.USER,
            userId,
            Asset.normalizeCode(assetCode),
            BalanceType.LOCKED
        ));
    }

    public LedgerAccountView external(String networkCode, String assetCode) {
        String normalizedNetwork = BlockchainNetwork.normalizeNetworkCode(networkCode);
        String normalizedAsset = Asset.normalizeCode(assetCode);
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.EXTERNAL,
            "chain:" + normalizedNetwork + ":" + normalizedAsset,
            normalizedAsset,
            BalanceType.EXTERNAL
        ));
    }

    LedgerAccountView fee(String assetCode) {
        String normalizedAsset = Asset.normalizeCode(assetCode);
        return ledgerAccountPort.openAccount(new CreateLedgerAccountCommand(
            LedgerAccountOwnerType.FEE,
            "fee:" + normalizedAsset,
            normalizedAsset,
            BalanceType.FEE
        ));
    }
}

