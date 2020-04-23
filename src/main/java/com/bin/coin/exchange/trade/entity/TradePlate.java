package com.bin.coin.exchange.trade.entity;

import com.bin.coin.exchange.trade.enums.ExchangeOrderDirection;
import com.bin.coin.exchange.trade.enums.ExchangeOrderType;
import com.bin.coin.exchange.trade.enums.TradePairSymbol;
import lombok.Data;
import lombok.Synchronized;

import java.math.BigDecimal;
import java.util.LinkedList;

/**
 *  后续再优化
 */
@Data
public class TradePlate {

    private LinkedList<TradePlateItem>  tradePlateItems; //treeMAp 是不是更优？？？
    //最大深度
    private int maxDepth = 100;
    //方向
    private ExchangeOrderDirection direction;
    //交易对信息
    private TradePairSymbol symbol;

    public TradePlate () {

    }

    public TradePlate (TradePairSymbol symbol, ExchangeOrderDirection direction) {
        this.symbol = symbol;
        this.direction = direction;
        tradePlateItems = new LinkedList<>();
    }

    public boolean add (ExchangeOrder order) {

        synchronized(tradePlateItems) {
            int index = 0;
            if(order.getType() == ExchangeOrderType.MARKET_PRICE) {
                return false;
            }
            if(order.getDirection() != direction) {
                return false;
            }
            if(tradePlateItems.size() > 0) {
                for(index = 0; index < tradePlateItems.size(); index ++) {
                    TradePlateItem item = tradePlateItems.get(index);
                    if(order.getDirection() == ExchangeOrderDirection.BUY && item.getPrice().compareTo(order.getPrice()) > 0
                            || order.getDirection() == ExchangeOrderDirection.SELL && item.getPrice().compareTo(order.getPrice()) < 0) {
                        continue;
                    } else if (item.getPrice().compareTo(order.getPrice()) == 0) {
                        BigDecimal deltaAmount = order.getAmount().subtract(order.getTradedAmount());
                        item.setAmount(item.getAmount().add(deltaAmount));
                        return true;
                    } else {
                        break;
                    }
                }
            }
            if(index < maxDepth) {
                TradePlateItem newItem = new TradePlateItem();
                newItem.setAmount(order.getAmount().subtract(order.getTradedAmount()));
                newItem.setPrice(order.getPrice());
                tradePlateItems.add(index, newItem);
            }
        }

        return true;
    }
    public void remove(ExchangeOrder order, BigDecimal amount) {
        synchronized (tradePlateItems) {
            for (int index = 0; index < tradePlateItems.size(); index++) {
                TradePlateItem item = tradePlateItems.get(index);
                if (item.getPrice().compareTo(order.getPrice()) == 0) {
                    item.setAmount(item.getAmount().subtract(amount));
                    if (item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        tradePlateItems.remove(index);
                    }
                    //log.info("items>>final_size={},itemAmount={},itemPrice={}",items.size(),item.getAmount(),item.getPrice());
                    return;
                }
            }
        }
    }

    public void remove(ExchangeOrder order){
        remove(order,order.getAmount().subtract(order.getTradedAmount()));
    }

    /**
     * 获取委托量最大的档位
     * @return
     */
    public BigDecimal getMaxAmount(){
        if(tradePlateItems.size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = BigDecimal.ZERO;
        for(TradePlateItem item : tradePlateItems){
            if(item.getAmount().compareTo(amount)>0){
                amount = item.getAmount();
            }
        }
        return amount;
    }

    /**
     * 获取委托量最小的档位
     * @return
     */
    public BigDecimal getMinAmount(){
        if(tradePlateItems.size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = tradePlateItems.getFirst().getAmount();
        for(TradePlateItem item : tradePlateItems){
            if(item.getAmount().compareTo(amount) < 0){
                amount = item.getAmount();
            }
        }
        return amount;
    }
}
