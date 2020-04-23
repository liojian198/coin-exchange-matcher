package com.bin.coin.exchange.trade.enums;

public enum ExchangeOrderType {
    MARKET_PRICE(1, "市价单"),
    LIMIT_PRICE(2, "限价单");

    private int value;
    private String des;

    ExchangeOrderType(int value, String des) {
        this.value = value;
        this.des = des;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getDes() {
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }
}
