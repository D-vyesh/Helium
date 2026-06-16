package com.helium.core.wallet.infrastructure.blockchain;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.springframework.stereotype.Component;

@Component
public class HdAddressGenerator {
    
    /**
     * Derives a child key from a parent XPUB.
     */
    public String deriveAddress(String networkId, String xpubBase58, int childIndex) {
        // Parse the base58 xpub into a DeterministicKey
        DeterministicKey parentKey = DeterministicKey.deserializeB58(xpubBase58, org.bitcoinj.params.MainNetParams.get());
        
        // Derive the child key
        DeterministicKey childKey = HDKeyDerivation.deriveChildKey(parentKey, childIndex);
        
        // Depending on networkId, return the formatted address
        if ("BTC".equals(networkId)) {
            return org.bitcoinj.core.Address.fromKey(org.bitcoinj.params.MainNetParams.get(), childKey, org.bitcoinj.script.Script.ScriptType.P2WPKH).toString();
        } else if ("ETH".equals(networkId)) {
            // Simplified ETH address derivation for demonstration
            return "0x" + org.bouncycastle.util.encoders.Hex.toHexString(childKey.getPubKeyHash()).substring(0, 40);
        } else if ("SOL".equals(networkId)) {
            // Simplified SOL address derivation for demonstration
            return org.bitcoinj.core.Base58.encode(childKey.getPubKeyHash());
        }
        
        throw new IllegalArgumentException("Unsupported HD network: " + networkId);
    }
}
