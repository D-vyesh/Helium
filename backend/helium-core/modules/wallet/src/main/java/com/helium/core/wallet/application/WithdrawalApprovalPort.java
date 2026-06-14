package com.helium.core.wallet.application;

public interface WithdrawalApprovalPort {
    WithdrawalView approve(ApproveWithdrawalCommand command);

    WithdrawalView reject(RejectWithdrawalCommand command);

    WithdrawalView recordBroadcast(RecordBroadcastCommand command);

    void observeWithdrawal(ObserveWithdrawalCommand command);

    WithdrawalView confirm(ConfirmWithdrawalCommand command);
}
