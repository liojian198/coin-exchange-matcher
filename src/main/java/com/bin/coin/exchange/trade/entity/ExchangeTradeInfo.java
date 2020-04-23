package com.bin.coin.exchange.trade.entity;

import com.bin.coin.exchange.trade.enums.ExchangeOrderDirection;
import com.bin.coin.exchange.trade.enums.TradePairSymbol;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ExchangeTradeInfo implements Serializable {
    private TradePairSymbol symbol;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal buyTurnover;
    private BigDecimal sellTurnover;
    private ExchangeOrderDirection direction;
    private String buyOrderId;
    private String sellOrderId;
    private Long time;

}
