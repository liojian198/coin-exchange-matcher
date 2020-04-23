package com.bin.coin.exchange.trade.entity;

import com.bin.coin.exchange.trade.enums.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeOrder implements Serializable {
    //订单类型
    private String orderId;
    //挂单类型
    private ExchangeOrderType type;
    //买入或卖出量，对于市价买入单表
    private BigDecimal amount = BigDecimal.ZERO;
    //交易对符号
    private TradePairSymbol symbol;
    //成交量
    private BigDecimal tradedAmount = BigDecimal.ZERO;
    //成交额，对市价买单有用
    private BigDecimal turnover = BigDecimal.ZERO;
    //币单位
    private CoinSymbol coinSymbol;
    //计价币单位
    private CoinSymbol baseSymbol;
    //订单状态
    private ExchangeOrderStatus status;
    //订单方向
    private ExchangeOrderDirection direction;
    //挂单价格
    private BigDecimal price = BigDecimal.ZERO;
    //挂单时间，时间戳
    private Long time;
    //交易完成时间戳
    private Long completedTime;
    //取消时间戳
    private Long canceledTime;


    public boolean isCompleted(){
        if(status != ExchangeOrderStatus.TRADING) {
            return true;
        } else{
            if(type == ExchangeOrderType.MARKET_PRICE && direction == ExchangeOrderDirection.BUY){
                return amount.compareTo(turnover) <= 0;
            }
            else{
                return amount.compareTo(tradedAmount) <= 0;
            }
        }
    }
}
