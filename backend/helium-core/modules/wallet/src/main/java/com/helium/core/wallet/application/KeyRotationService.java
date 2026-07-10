package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.CustodyKey;
import com.helium.core.wallet.domain.CustodyKeyStatus;
import com.helium.core.wallet.domain.SigningAlgorithm;
import com.helium.core.wallet.domain.WalletAuditEventType;
import com.helium.core.wallet.infrastructure.CustodyKeyRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KeyRotationService {
    private final CustodyKeyRepository custodyKeyRepository;
    private final WalletAuditService auditService;
    private final Clock clock;

    public KeyRotationService(CustodyKeyRepository custodyKeyRepository, WalletAuditService auditService, Clock clock) {
        this.custodyKeyRepository = custodyKeyRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public CustodyKey rotateActiveKey(
        String assetCode,
        String newKeyAlias,
        String newKeyVersion,
        String provider,
        SigningAlgorithm algorithm,
        String publicKeyHex,
        String actorId
    ) {
        String asset = Asset.normalizeCode(assetCode);
        custodyKeyRepository.findByAssetCodeAndStatus(asset, CustodyKeyStatus.ACTIVE)
            .ifPresent(oldKey -> oldKey.verifyOnly(clock.instant()));
        CustodyKey newKey = custodyKeyRepository.save(CustodyKey.register(
            asset,
            newKeyAlias,
            newKeyVersion,
            provider,
            algorithm,
            publicKeyHex,
            CustodyKeyStatus.ACTIVE,
            clock.instant()
        ));
        auditService.record(WalletAuditEventType.CUSTODY_KEY_ROTATED, null, actorId, asset + ":" + newKeyAlias);
        return newKey;
    }
}
