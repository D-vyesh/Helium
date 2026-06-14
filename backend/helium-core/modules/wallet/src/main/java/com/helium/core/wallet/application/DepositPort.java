package com.helium.core.wallet.application;

public interface DepositPort {
    DepositView detectDeposit(DetectDepositCommand command);

    DepositView updateConfirmations(UpdateDepositConfirmationsCommand command);
}

