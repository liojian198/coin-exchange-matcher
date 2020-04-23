package com.bin.coin.exchange.trade.enums;

public enum CoinSymbol {
    BTC("btc", "比特币"),
    ETH("eth", "以太坊");

    private String value;
    private String des;

    CoinSymbol(String value, String des) {
        this.value = value;
        this.des = des;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDes() {
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }
}
