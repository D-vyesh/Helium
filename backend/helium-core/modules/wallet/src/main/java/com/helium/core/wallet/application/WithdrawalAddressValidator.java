package com.helium.core.wallet.application;

import com.helium.core.wallet.domain.Asset;
import com.helium.core.wallet.domain.BlockchainNetwork;
import com.helium.core.wallet.domain.WalletValidationException;
import java.math.BigInteger;
import java.util.Locale;
import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;

/** Validates and normalizes native-asset withdrawal destinations before funds are reserved. */
@Component
public class WithdrawalAddressValidator {
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public String validate(String assetCode, String networkCode, String destinationAddress) {
        String asset = Asset.normalizeCode(assetCode);
        String network = BlockchainNetwork.normalizeNetworkCode(networkCode);
        String address = BlockchainNetwork.requireText(destinationAddress, "destinationAddress", 160);
        return switch (asset) {
            case "BTC" -> validateBitcoin(network, address);
            case "ETH" -> validateEthereum(network, address);
            case "SOL" -> validateSolana(network, address);
            default -> address;
        };
    }

    private static String validateBitcoin(String network, String address) {
        requireNativeNetwork("BTC", network);
        try {
            return Address.fromString(MainNetParams.get(), address).toString();
        } catch (RuntimeException exception) {
            throw new WalletValidationException("invalid BTC withdrawal address");
        }
    }

    private static String validateEthereum(String network, String address) {
        requireNativeNetwork("ETH", network);
        if (!address.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new WalletValidationException("invalid ETH withdrawal address");
        }
        String body = address.substring(2);
        String checksumAddress;
        try {
            checksumAddress = Keys.toChecksumAddress(address);
        } catch (RuntimeException exception) {
            throw new WalletValidationException("invalid ETH withdrawal address");
        }
        boolean mixedCase = !body.equals(body.toLowerCase(Locale.ROOT)) && !body.equals(body.toUpperCase(Locale.ROOT));
        if (mixedCase && !checksumAddress.equals(address)) {
            throw new WalletValidationException("ETH withdrawal address has an invalid EIP-55 checksum");
        }
        return checksumAddress;
    }

    private static String validateSolana(String network, String address) {
        requireNativeNetwork("SOL", network);
        if (decodeBase58(address).length != 32) {
            throw new WalletValidationException("invalid SOL withdrawal address");
        }
        return address;
    }

    private static void requireNativeNetwork(String asset, String network) {
        if (!asset.equals(network)) {
            throw new WalletValidationException(asset + " withdrawals require the " + asset + " network");
        }
    }

    private static byte[] decodeBase58(String value) {
        BigInteger number = BigInteger.ZERO;
        for (char character : value.toCharArray()) {
            int index = BASE58_ALPHABET.indexOf(character);
            if (index < 0) {
                throw new WalletValidationException("invalid SOL withdrawal address");
            }
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index));
        }
        byte[] encoded = number.equals(BigInteger.ZERO) ? new byte[0] : unsignedBytes(number);
        int leadingZeros = 0;
        while (leadingZeros < value.length() && value.charAt(leadingZeros) == '1') {
            leadingZeros++;
        }
        byte[] decoded = new byte[leadingZeros + encoded.length];
        System.arraycopy(encoded, 0, decoded, leadingZeros, encoded.length);
        return decoded;
    }

    private static byte[] unsignedBytes(BigInteger number) {
        byte[] bytes = number.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            return unsigned;
        }
        return bytes;
    }
}
