package com.bin.coin.exchange.trade.enums;

public enum TradePairSymbol {
    BTC_USDT("btc_ustd", "BTC对USDT"),
    ETH_USDT("btc_usdt", "ETH对USDT");

    private String value;
    private String des;

    TradePairSymbol(String value, String des) {
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
