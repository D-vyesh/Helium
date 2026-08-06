package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.helium.core.wallet.application.KeyRotationService;
import com.helium.core.wallet.domain.SigningAlgorithm;
import org.springframework.stereotype.Component;

@Component
public class CustodyKeyRotationCommandHandler implements GovernanceCommandHandler {

    private final KeyRotationService keyRotationService;

    public CustodyKeyRotationCommandHandler(KeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    @Override
    public String supportedRequestType() {
        return "CUSTODY_KEY_ROTATION";
    }

    @Override
    public void execute(JsonNode payload) {
        String assetCode = payload.path("assetCode").asText();
        String newKeyAlias = payload.path("newKeyAlias").asText();
        String newKeyVersion = payload.path("newKeyVersion").asText();
        String provider = payload.path("provider").asText("LOCAL");
        String algorithmStr = payload.path("algorithm").asText("ECDSA_SECP256K1");
        SigningAlgorithm algorithm = SigningAlgorithm.valueOf(algorithmStr);
        String publicKeyHex = payload.path("publicKeyHex").asText();
        String actorId = payload.path("actorId").asText("GOVERNANCE");

        keyRotationService.rotateActiveKey(
            assetCode,
            newKeyAlias,
            newKeyVersion,
            provider,
            algorithm,
            publicKeyHex,
            actorId
        );
    }
}
