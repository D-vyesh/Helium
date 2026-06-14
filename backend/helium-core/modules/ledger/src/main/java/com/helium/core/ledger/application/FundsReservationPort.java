package com.helium.core.ledger.application;

public interface FundsReservationPort {
    FundsReservationResult reserve(ReserveFundsCommand command);

    FundsReservationResult release(ReleaseFundsCommand command);
}
