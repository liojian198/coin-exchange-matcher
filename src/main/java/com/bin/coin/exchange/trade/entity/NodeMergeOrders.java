package com.bin.coin.exchange.trade.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NodeMergeOrders {
    private List<ExchangeOrder> orderList = new ArrayList<>();
    private BigDecimal price = BigDecimal.ZERO;

    public ExchangeOrder getFist() {
        return orderList.size() > 0 ? orderList.get(0) : null;
    }

    public void addLast(ExchangeOrder order) {
        orderList.add(order);
    }

    public int getSize () {
        return orderList.size();
    }

    public BigDecimal getPrice() {
        return orderList.get(0).getPrice();
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Iterator<ExchangeOrder> iterator() {
        return orderList.iterator();
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for(ExchangeOrder order : orderList) {
            total = total.add(order.getAmount());
        }
        return total;
    }

    public void add(ExchangeOrder order){
        orderList.add(order);
    }
}
