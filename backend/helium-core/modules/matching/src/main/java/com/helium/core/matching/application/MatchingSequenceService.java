package com.helium.core.matching.application;

import com.helium.core.matching.domain.MatchingSequence;
import com.helium.core.matching.infrastructure.MatchingSequenceRepository;
import org.springframework.stereotype.Service;

@Service
class MatchingSequenceService {
    private final MatchingSequenceRepository repository;

    MatchingSequenceService(MatchingSequenceRepository repository) {
        this.repository = repository;
    }

    long next(String marketSymbol) {
        MatchingSequence sequence = repository.findByMarketForUpdate(marketSymbol)
            .orElseGet(() -> repository.save(MatchingSequence.start(marketSymbol)));
        long next = sequence.next();
        repository.saveAndFlush(sequence);
        return next;
    }
}
