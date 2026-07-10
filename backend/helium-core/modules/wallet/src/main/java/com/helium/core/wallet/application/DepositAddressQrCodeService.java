package com.helium.core.wallet.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.helium.core.wallet.domain.WalletValidationException;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Generates a PNG QR code for a supported native-asset deposit address. */
@Service
public class DepositAddressQrCodeService {
    private static final int QR_SIZE = 280;

    public DepositAddressQrCode generate(String assetCode, String networkCode, String address) {
        String asset = normalize(assetCode, "asset");
        String network = normalize(networkCode, "network");
        if (!asset.equals(network) || !("BTC".equals(asset) || "ETH".equals(asset) || "SOL".equals(asset))) {
            throw new WalletValidationException("QR codes are available only for BTC, ETH, and SOL native addresses");
        }
        if (address == null || address.isBlank()) {
            throw new WalletValidationException("deposit address is required");
        }
        String paymentUri = paymentUri(asset, address.trim());
        return new DepositAddressQrCode(paymentUri, encodePng(paymentUri));
    }

    private static String paymentUri(String asset, String address) {
        return switch (asset) {
            case "BTC" -> "bitcoin:" + address;
            case "ETH" -> "ethereum:" + address;
            case "SOL" -> "solana:" + address;
            default -> throw new WalletValidationException("unsupported wallet asset");
        };
    }

    private static String encodePng(String paymentUri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(paymentUri, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("deposit address QR code generation failed", exception);
        }
    }

    private static String normalize(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new WalletValidationException(label + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record DepositAddressQrCode(String paymentUri, String qrCodeDataUrl) {}
}
