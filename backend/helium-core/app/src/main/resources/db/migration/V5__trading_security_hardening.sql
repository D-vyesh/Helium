-- 2. Duplicate execution protection
ALTER TABLE trading_settlement_instructions 
    ADD CONSTRAINT uq_trading_settlement_instructions_execution UNIQUE (execution_id);

-- 6. Database constraints
ALTER TABLE trading_orders ADD CONSTRAINT chk_trading_orders_quantity_positive CHECK (quantity >= 0);
ALTER TABLE trading_orders ADD CONSTRAINT chk_trading_orders_filled_positive CHECK (filled_quantity >= 0);
-- Check remaining quantity >= 0
ALTER TABLE trading_orders ADD CONSTRAINT chk_trading_orders_remaining_positive CHECK (quantity - filled_quantity >= 0);

ALTER TABLE trading_settlement_instructions ADD CONSTRAINT chk_trading_settlement_qty_positive CHECK (quantity >= 0);
ALTER TABLE trading_settlement_instructions ADD CONSTRAINT chk_trading_settlement_price_positive CHECK (price >= 0);
ALTER TABLE trading_settlement_instructions ADD CONSTRAINT chk_trading_settlement_fee_positive CHECK (fee_amount >= 0);

-- Append-only trigger for trading_order_history
CREATE OR REPLACE FUNCTION forbid_update_trading_order_history()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'trading_order_history is append-only and cannot be updated';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trading_order_history_append_only
BEFORE UPDATE OR DELETE ON trading_order_history
FOR EACH ROW
EXECUTE FUNCTION forbid_update_trading_order_history();

-- Immutable triggers for owner_id, order_side, order_type
CREATE OR REPLACE FUNCTION prevent_immutable_order_fields_update()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.user_id IS DISTINCT FROM NEW.user_id THEN
        RAISE EXCEPTION 'trading_orders user_id cannot be updated';
    END IF;
    IF OLD.side IS DISTINCT FROM NEW.side THEN
        RAISE EXCEPTION 'trading_orders side cannot be updated';
    END IF;
    IF OLD.order_type IS DISTINCT FROM NEW.order_type THEN
        RAISE EXCEPTION 'trading_orders order_type cannot be updated';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trading_orders_immutable_fields
BEFORE UPDATE ON trading_orders
FOR EACH ROW
EXECUTE FUNCTION prevent_immutable_order_fields_update();
