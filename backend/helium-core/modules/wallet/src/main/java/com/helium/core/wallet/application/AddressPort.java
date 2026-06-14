package com.helium.core.wallet.application;

public interface AddressPort {
    DepositAddressView assignDepositAddress(AssignDepositAddressCommand command);
}

