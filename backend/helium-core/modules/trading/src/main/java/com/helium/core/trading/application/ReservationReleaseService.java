package com.helium.core.trading.application;

import com.helium.core.ledger.application.FundsReservationPort;
import com.helium.core.ledger.application.ReleaseFundsCommand;
import com.helium.core.trading.domain.Order;
import com.helium.core.trading.domain.OrderReservation;
import com.helium.core.trading.domain.ReservationStatus;
import com.helium.core.trading.domain.TradingInvariantViolationException;
import com.helium.core.trading.infrastructure.OrderReservationRepository;
import com.helium.core.trading.infrastructure.SettlementInstructionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
class ReservationReleaseService {
    private final OrderReservationRepository reservationRepository;
    private final SettlementInstructionRepository settlementInstructionRepository;
    private final FundsReservationPort fundsReservationPort;
    private final Clock clock;

    ReservationReleaseService(
        OrderReservationRepository reservationRepository,
        SettlementInstructionRepository settlementInstructionRepository,
        FundsReservationPort fundsReservationPort,
        Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.settlementInstructionRepository = settlementInstructionRepository;
        this.fundsReservationPort = fundsReservationPort;
        this.clock = clock;
    }

    void releaseRemaining(Order order, String reason, String idempotencyKey, String actorId) {
        reservationRepository.findByOrderIdForUpdate(order.id()).ifPresent(reservation -> {
            if (reservation.status() == ReservationStatus.RELEASED) {
                return;
            }
            BigDecimal consumed = settlementInstructionRepository.sumReserveConsumedAmountByOrderId(order.id());
            BigDecimal releaseAmount = reservation.reservedAmount().subtract(consumed).stripTrailingZeros();
            if (releaseAmount.signum() < 0) {
                throw new TradingInvariantViolationException("Consumed amount exceeds reservation");
            }
            if (releaseAmount.signum() == 0) {
                reservation.release(null, clock.instant());
                reservationRepository.save(reservation);
                return;
            }
            var releaseResult = fundsReservationPort.release(new ReleaseFundsCommand(
                order.userId(),
                reservation.assetCode(),
                releaseAmount,
                idempotencyKey,
                idempotencyKey,
                actorId,
                reason
            ));
            reservation.release(releaseResult.transactionId(), clock.instant());
            reservationRepository.save(reservation);
        });
    }
}
