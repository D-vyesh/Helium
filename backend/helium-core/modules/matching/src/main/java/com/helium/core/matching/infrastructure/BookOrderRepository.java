package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.BookOrder;
import com.helium.core.matching.domain.MatchingOrderSide;
import java.math.BigDecimal;
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

    /**
     * Finds STOP_LIMIT orders that should be triggered by a trade at {@code lastTradePrice}.
     *
     * Convention:
     *   BUY  stop fires when lastTradePrice >= stopPrice  (price rose to the trigger level)
     *   SELL stop fires when lastTradePrice <= stopPrice  (price fell to the trigger level)
     *
     * Lock is pessimistic-write because the caller will immediately call release() on each result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select bookOrder from BookOrder bookOrder
        where bookOrder.marketSymbol = :marketSymbol
          and bookOrder.orderType = 'STOP_LIMIT'
          and bookOrder.status = 'STOP_PENDING'
          and (
              (bookOrder.side = 'BUY'  and bookOrder.stopPrice <= :lastTradePrice)
              or
              (bookOrder.side = 'SELL' and bookOrder.stopPrice >= :lastTradePrice)
          )
        order by bookOrder.stopPrice asc, bookOrder.receivedSequence asc
        """)
    List<BookOrder> findTriggeredStops(
        @Param("marketSymbol") String marketSymbol,
        @Param("lastTradePrice") BigDecimal lastTradePrice
    );
}
