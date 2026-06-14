package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.OrderReservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReservationRepository extends JpaRepository<OrderReservation, UUID> {
    Optional<OrderReservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from OrderReservation reservation where reservation.orderId = :orderId")
    Optional<OrderReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
