package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByUserIdAndClientOrderId(UUID userId, String clientOrderId);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tradingOrder from Order tradingOrder where tradingOrder.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
