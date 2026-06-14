package com.helium.core.trading.domain;

import java.util.Objects;
import java.util.UUID;

public record ReservationId(UUID value) {
    public ReservationId {
        Objects.requireNonNull(value, "value");
    }
}
