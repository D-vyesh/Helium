ALTER TABLE trading_markets
    ADD COLUMN day_close_time_utc TIME DEFAULT '23:59:59';
