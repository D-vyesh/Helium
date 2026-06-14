package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.TradeSettlementInstruction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementInstructionRepository extends JpaRepository<TradeSettlementInstruction, UUID> {
    Optional<TradeSettlementInstruction> findByExecutionId(String executionId);

    List<TradeSettlementInstruction> findByOrderId(UUID orderId);

    @Query("""
        select coalesce(max(instruction.executionSequence), 0)
        from TradeSettlementInstruction instruction
        where instruction.buyerOrderId = :orderId or instruction.sellerOrderId = :orderId
        """)
    long maxExecutionSequenceByOrderId(@Param("orderId") UUID orderId);

    @Query("select coalesce(sum(instruction.buyerReserveConsumedAmount), 0) from TradeSettlementInstruction instruction where instruction.buyerOrderId = :orderId")
    BigDecimal sumBuyerReserveConsumedAmountByOrderId(@Param("orderId") UUID orderId);

    @Query("select coalesce(sum(instruction.sellerReserveConsumedAmount), 0) from TradeSettlementInstruction instruction where instruction.sellerOrderId = :orderId")
    BigDecimal sumSellerReserveConsumedAmountByOrderId(@Param("orderId") UUID orderId);

    default BigDecimal sumReserveConsumedAmountByOrderId(UUID orderId) {
        return sumBuyerReserveConsumedAmountByOrderId(orderId).add(sumSellerReserveConsumedAmountByOrderId(orderId)).stripTrailingZeros();
    }
}
