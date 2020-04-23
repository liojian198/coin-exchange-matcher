package com.bin.coin.exchange.trade.enums;

public enum ExchangeOrderDirection {
    BUY(1, "买入"),
    SELL(2, "卖出");

    private int value;
    private String des;

    ExchangeOrderDirection(int value, String des) {
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
