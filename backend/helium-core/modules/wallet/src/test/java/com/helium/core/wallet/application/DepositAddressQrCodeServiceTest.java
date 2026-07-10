package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DepositAddressQrCodeServiceTest {
    private final DepositAddressQrCodeService service = new DepositAddressQrCodeService();

    @Test
    void generatesAPngForABitcoinPaymentUri() {
        var qrCode = service.generate("BTC", "BTC", "bc1qexampleaddress");

        assertThat(qrCode.paymentUri()).isEqualTo("bitcoin:bc1qexampleaddress");
        assertThat(qrCode.qrCodeDataUrl()).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(qrCode.qrCodeDataUrl().substring("data:image/png;base64,".length()));
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }
}
