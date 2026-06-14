package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.MatchingOrderSide;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BookOrderRepository extends JpaRepository<BookOrder, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bookOrder from BookOrder bookOrder where bookOrder.orderId = :orderId")
    Optional<BookOrder> findByIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select bookOrder from BookOrder bookOrder
        where bookOrder.marketSymbol = :marketSymbol
          and bookOrder.side = :side
          and bookOrder.status in ('ACTIVE', 'PARTIALLY_FILLED')
        """)
    List<BookOrder> findMatchableForUpdate(@Param("marketSymbol") String marketSymbol, @Param("side") MatchingOrderSide side);

    @Query("""
        select bookOrder from BookOrder bookOrder
        where bookOrder.marketSymbol = :marketSymbol
          and bookOrder.status in ('ACTIVE', 'PARTIALLY_FILLED')
        """)
    List<BookOrder> findOpenByMarket(@Param("marketSymbol") String marketSymbol);
}
