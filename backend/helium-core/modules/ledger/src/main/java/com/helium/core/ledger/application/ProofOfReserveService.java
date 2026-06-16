package com.helium.core.ledger.application;

import com.helium.core.ledger.domain.BalanceSnapshot;
import com.helium.core.ledger.infrastructure.BalanceSnapshotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProofOfReserveService {
    private final BalanceSnapshotRepository balanceSnapshotRepository;

    public ProofOfReserveService(BalanceSnapshotRepository balanceSnapshotRepository) {
        this.balanceSnapshotRepository = balanceSnapshotRepository;
    }

    public PoRSnapshot generateSnapshot(String assetCode) {
        List<BalanceSnapshot> userBalances = balanceSnapshotRepository.findAllUserAvailableBalances(assetCode);

        BigDecimal totalLiabilities = userBalances.stream()
            .map(BalanceSnapshot::currentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> leafHashes = userBalances.stream()
            .map(b -> hash(b.account().ownerId() + ":" + b.currentBalance().toPlainString()))
            .collect(Collectors.toList());

        String rootHash = computeRoot(leafHashes);
        
        return new PoRSnapshot(assetCode, totalLiabilities, rootHash);
    }

    private String computeRoot(List<String> hashes) {
        if (hashes.isEmpty()) return hash("empty");
        if (hashes.size() == 1) return hashes.get(0);
        
        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i += 2) {
            if (i + 1 < hashes.size()) {
                nextLevel.add(hash(hashes.get(i) + hashes.get(i+1)));
            } else {
                nextLevel.add(hashes.get(i));
            }
        }
        return computeRoot(nextLevel);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHexString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not found", e);
        }
    }
    
    private String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record PoRSnapshot(String assetCode, BigDecimal totalLiabilities, String merkleRoot) {}
}
