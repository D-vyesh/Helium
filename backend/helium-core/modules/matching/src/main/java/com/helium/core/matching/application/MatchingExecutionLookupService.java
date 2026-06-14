package com.helium.core.matching.application;

import com.helium.core.matching.domain.Execution;
import com.helium.core.matching.infrastructure.ExecutionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class MatchingExecutionLookupService implements MatchingExecutionLookupPort {
    private final ExecutionRepository repository;

    MatchingExecutionLookupService(ExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<MatchingExecutionView> findByExecutionId(String executionId) {
        return repository.findByExecutionId(executionId).map(this::toView);
    }

    private MatchingExecutionView toView(Execution execution) {
        return new MatchingExecutionView(
            execution.executionId(),
            execution.matchId(),
            execution.marketSymbol(),
            execution.buyerOrderId(),
            execution.sellerOrderId(),
            execution.makerOrderId(),
            execution.takerOrderId(),
            execution.quantity(),
            execution.price(),
            execution.sequenceNumber(),
            execution.buyerOrderOffset(),
            execution.sellerOrderOffset()
        );
    }
}
