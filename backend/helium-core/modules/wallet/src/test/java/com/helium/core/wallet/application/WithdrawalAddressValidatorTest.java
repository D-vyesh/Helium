package com.helium.core.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helium.core.wallet.domain.WalletValidationException;
import org.junit.jupiter.api.Test;

class WithdrawalAddressValidatorTest {
    private final WithdrawalAddressValidator validator = new WithdrawalAddressValidator();

    @Test
    void acceptsSupportedNativeAddressFormats() {
        assertThat(validator.validate("BTC", "BTC", "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"))
            .isEqualTo("1BoatSLRHtKNngkdXEeobR76b53LETtpyT");
        assertThat(validator.validate("ETH", "ETH", "0x52908400098527886E0F7030069857D2E4169EE7"))
            .isEqualTo("0x52908400098527886E0F7030069857D2E4169EE7");
        assertThat(validator.validate("SOL", "SOL", "11111111111111111111111111111111"))
            .isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void rejectsInvalidNativeAddressesBeforeReservation() {
        assertThatThrownBy(() -> validator.validate("BTC", "BTC", "not-a-bitcoin-address"))
            .isInstanceOf(WalletValidationException.class);
        assertThatThrownBy(() -> validator.validate("ETH", "ETH", "0x52908400098527886E0F7030069857D2E4169Ee7"))
            .isInstanceOf(WalletValidationException.class)
            .hasMessageContaining("EIP-55");
        assertThatThrownBy(() -> validator.validate("SOL", "SOL", "short"))
            .isInstanceOf(WalletValidationException.class);
    }
}
