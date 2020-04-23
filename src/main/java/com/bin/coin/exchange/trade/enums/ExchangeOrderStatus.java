package com.bin.coin.exchange.trade.enums;


public enum ExchangeOrderStatus {
    TRADING(1,"订单交易中"),
    COMPLETED(2, "订单完成"),
    CANCELED(3, "订单取消"),
    OVERTIMED(4, "订单超时");

    private int value;
    private String des;

    ExchangeOrderStatus(int value, String des) {
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
