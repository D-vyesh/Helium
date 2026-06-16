package com.helium.core.compliancelite.domain;

import java.math.BigDecimal;

public enum Jurisdiction {
    US("United States", "FinCEN", new BigDecimal("10000.00"), true),
    EU("European Union", "MiCA", new BigDecimal("50000.00"), false),
    UK("United Kingdom", "FCA", new BigDecimal("25000.00"), false),
    SG("Singapore", "MAS", new BigDecimal("100000.00"), false),
    UAE("United Arab Emirates", "VARA", new BigDecimal("200000.00"), false);

    private final String countryName;
    private final String regulatorName;
    private final BigDecimal defaultWithdrawalLimitUsd;
    private final boolean restrictsPrivacyCoins;

    Jurisdiction(String countryName, String regulatorName, BigDecimal defaultWithdrawalLimitUsd, boolean restrictsPrivacyCoins) {
        this.countryName = countryName;
        this.regulatorName = regulatorName;
        this.defaultWithdrawalLimitUsd = defaultWithdrawalLimitUsd;
        this.restrictsPrivacyCoins = restrictsPrivacyCoins;
    }

    public String countryName() { return countryName; }
    public String regulatorName() { return regulatorName; }
    public BigDecimal defaultWithdrawalLimitUsd() { return defaultWithdrawalLimitUsd; }
    public boolean restrictsPrivacyCoins() { return restrictsPrivacyCoins; }
}
