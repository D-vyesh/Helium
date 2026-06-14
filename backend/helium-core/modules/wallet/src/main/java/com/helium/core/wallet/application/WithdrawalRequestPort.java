package com.helium.core.wallet.application;

public interface WithdrawalRequestPort {
    WithdrawalView requestWithdrawal(RequestWithdrawalCommand command);
}

