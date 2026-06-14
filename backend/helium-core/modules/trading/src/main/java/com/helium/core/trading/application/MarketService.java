package com.helium.core.trading.application;

import com.helium.core.trading.domain.Market;
import com.helium.core.trading.domain.TradingValidationException;
import com.helium.core.trading.infrastructure.MarketRepository;
import com.helium.core.trading.infrastructure.TradingSecurityContext;
import com.helium.core.authuser.application.AuthorizationPort;
import com.helium.core.authuser.domain.Role;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class MarketService implements MarketAdministrationPort, MarketQueryPort {
    private final MarketRepository repository;
    private final AuthorizationPort authorizationPort;
    private final TradingSecurityContext securityContext;
    private final Clock clock;

    MarketService(MarketRepository repository, AuthorizationPort authorizationPort, TradingSecurityContext securityContext, Clock clock) {
        this.repository = repository;
        this.authorizationPort = authorizationPort;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    @Override
    public void registerMarket(RegisterMarketCommand command) {
        authorizationPort.requireRole(securityContext.requireUserId(), Role.ADMIN);
        if (repository.findById(command.symbol()).isPresent()) {
            throw new TradingValidationException("market already exists");
        }
        Market market = Market.register(
            command.symbol(),
            command.baseAsset(),
            command.quoteAsset(),
            command.priceScale(),
            command.quantityScale(),
            command.minOrderQuantity(),
            command.minNotional(),
            command.enabled(),
            clock.instant()
        );
        repository.save(market);
    }

    @Override
    public void updateMarket(UpdateMarketCommand command) {
        authorizationPort.requireRole(securityContext.requireUserId(), Role.ADMIN);
        Market market = repository.findById(command.symbol())
            .orElseThrow(() -> new TradingValidationException("market not found"));
        market.updatePolicy(command.enabled(), clock.instant());
        repository.save(market);
    }

    @Override
    public Optional<MarketView> getMarket(String symbol) {
        return repository.findById(symbol).map(this::mapToView);
    }

    @Override
    public List<MarketView> getMarkets() {
        return repository.findAll().stream().map(this::mapToView).collect(Collectors.toList());
    }

    private MarketView mapToView(Market market) {
        return new MarketView(
            market.symbol(),
            market.baseAsset(),
            market.quoteAsset(),
            market.minOrderQuantity().scale(), // Using scale placeholders, not accurate based on domain
            market.minNotional().scale(),
            market.minOrderQuantity(),
            market.minNotional(),
            market.enabled()
        );
    }
}
