package com.bin.coin.exchange.trade.algorithm;

import com.bin.coin.exchange.trade.entity.*;
import com.bin.coin.exchange.trade.enums.ExchangeOrderDirection;
import com.bin.coin.exchange.trade.enums.ExchangeOrderType;
import com.bin.coin.exchange.trade.enums.TradePairSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

public class TradeMatch {
    private Logger logger = LoggerFactory.getLogger(TradeMatch.class);

    private TradePairSymbol symbol;

    //买入限价订单链表，价格从高到低排列
    private TreeMap <BigDecimal, NodeMergeOrders> buyLimitPriceQueue;
    //卖出限价订单链表，价格从低到高排列
    private TreeMap <BigDecimal, NodeMergeOrders> sellLimitPriceQueue;
    //买入市价订单链表，按时间从小到大排序
    private LinkedList<ExchangeOrder> buyMarketQueue;
    //卖出市价订单链表，按时间从小到大排序
    private LinkedList<ExchangeOrder> sellMarketQueue;
    //买盘盘口
    private TradePlate buyTradePlate;
    //卖盘盘口
    private TradePlate sellTradePlate;
    //是否暂停交易
    private boolean tradingHalt = false;
    private boolean ready = false;


    public TradeMatch(TradePairSymbol symbol) {
        this.symbol = symbol;
        init();
    }

    private void init() {
        logger.info("init tradematch for symbol {}", symbol);
        this.buyLimitPriceQueue = new TreeMap<>(Comparator.reverseOrder());
        this.sellLimitPriceQueue = new TreeMap<>(Comparator.naturalOrder());
        this.buyMarketQueue = new LinkedList<>();
        this.sellMarketQueue = new LinkedList<>();
        this.sellTradePlate = new TradePlate(symbol, ExchangeOrderDirection.SELL);
        this.buyTradePlate = new TradePlate(symbol, ExchangeOrderDirection.BUY);
    }

    public boolean isTradingHalt() {
        return tradingHalt;
    }

    public void setTradingHalt(boolean tradingHalt) {
        this.tradingHalt = tradingHalt;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    /**、
     * 触发撮合算法
     * @param exchangeOrder 用户订单
     */
    public void trade(ExchangeOrder exchangeOrder) {
        if (tradingHalt) {
            return;
        }
        if(symbol != exchangeOrder.getSymbol()) {
            return;
        }
        if (exchangeOrder.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || exchangeOrder.getAmount().subtract(exchangeOrder.getTradedAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        TreeMap<BigDecimal, NodeMergeOrders> limitPriceOrderList = null;
        LinkedList<ExchangeOrder> marketPriceOrderList = null;
        if(exchangeOrder.getDirection() == ExchangeOrderDirection.BUY) {
            limitPriceOrderList = sellLimitPriceQueue;
            marketPriceOrderList = sellMarketQueue;
        } else {
            limitPriceOrderList = buyLimitPriceQueue;
            marketPriceOrderList = buyMarketQueue;
        }

        if(exchangeOrder.getType() == ExchangeOrderType.MARKET_PRICE) {
            //与限价单交易
            matchMarketPriceWithLimitPriceList(limitPriceOrderList, exchangeOrder);
        } else if(exchangeOrder.getType() == ExchangeOrderType.LIMIT_PRICE) {
            //先与限价单交易
            matchLimitPriceWithLimitPriceList(limitPriceOrderList, exchangeOrder);
            if( exchangeOrder.getAmount().compareTo(exchangeOrder.getTradedAmount()) > 0) {
                //后与市价单交易
                matchLimitPriceWithMarketPriceList(marketPriceOrderList, exchangeOrder);
            }
        }
    }

    /**
     * 市价委托单与限价对手单列表交易
     * @param limitPriceList
     * @param exchangeOrder
     */
    public void matchMarketPriceWithLimitPriceList(TreeMap<BigDecimal,NodeMergeOrders> limitPriceList, ExchangeOrder exchangeOrder){
        List<ExchangeTradeInfo> exchangeTradeInfoList = new ArrayList<>();
        List<ExchangeOrder> completeOrders = new ArrayList<>();

        synchronized (limitPriceList) {
            Iterator<Map.Entry<BigDecimal, NodeMergeOrders>> mergerOrderIterator =limitPriceList.entrySet().iterator();
            boolean exitLoop = false;
            while (!exitLoop && mergerOrderIterator.hasNext()) {
                NodeMergeOrders entry = mergerOrderIterator.next().getValue();
                Iterator<ExchangeOrder> orderIterator = entry.iterator();
                while(orderIterator.hasNext()) {
                    ExchangeOrder matchOrder = orderIterator.next();
                    //处理匹配
                    ExchangeTradeInfo tradeInfo = processMatch(exchangeOrder, matchOrder);

                    if(null != tradeInfo) {
                        exchangeTradeInfoList.add(tradeInfo);
                    }

                    //判断匹配单是否完成
                    if(matchOrder.isCompleted()) {
                        //当前匹配的订单完成交易，删除该订单
                        orderIterator.remove();
                        completeOrders.add(matchOrder);
                    }

                    //判断聚焦订单是否完成
                    if(exchangeOrder.isCompleted()) {
                        completeOrders.add(exchangeOrder);
                        exitLoop = true;
                        break;
                    }
                }
                if(entry.getSize() == 0) {
                    mergerOrderIterator.remove();
                }
            }
        }
        //如果还没有交易完，订单压入列表中,市价买单按成交量算
        if ( exchangeOrder.getDirection() == ExchangeOrderDirection.BUY && exchangeOrder.getTurnover().compareTo(exchangeOrder.getAmount()) < 0
                || exchangeOrder.getDirection() == ExchangeOrderDirection.SELL && exchangeOrder.getTradedAmount().compareTo(exchangeOrder.getAmount()) < 0) {
            addMarketPriceOrder(exchangeOrder);
        }

        //发送每个订单的匹配批量推送
        //1. 本次撮合的成交信息。
        //2. 本次撮合的完成了订单信息。
        //3. 。。。。
    }

    public void addMarketPriceOrder(ExchangeOrder exchangeOrder) {
        if(exchangeOrder.getType() != ExchangeOrderType.MARKET_PRICE){
            return ;
        }
        LinkedList<ExchangeOrder> list = exchangeOrder.getDirection() == ExchangeOrderDirection.BUY ? buyMarketQueue : sellMarketQueue;
        synchronized (list) {
            list.addLast(exchangeOrder);
        }
    }


    /**
     *处理两个匹配的委托订单
     * @param exchangeOrder 用户单
     * @param matchOrder 匹配单
     * @return
     */
    private ExchangeTradeInfo processMatch(ExchangeOrder exchangeOrder, ExchangeOrder matchOrder) {
        //需要交易的数量，成交量,成交价，可用数量
        BigDecimal needAmount,dealPrice,availAmount;
        //如果匹配单是限价单，则以其价格为成交价
        if(matchOrder.getType() == ExchangeOrderType.LIMIT_PRICE) {
            dealPrice = matchOrder.getPrice();
        } else {
            dealPrice = exchangeOrder.getPrice();
        }

        //成交价必须大于0
        if (dealPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        needAmount = calculateTradedAmount(exchangeOrder, dealPrice);
        availAmount = calculateTradedAmount(matchOrder, dealPrice);
        //计算成交量
        BigDecimal tradedAmount = (availAmount.compareTo(needAmount) >= 0 ? needAmount : availAmount);
        //如果成交额为0说明剩余额度无法成交，退出
        if(tradedAmount.compareTo(BigDecimal.ZERO) == 0){
            return null;
        }
        //计算成交额,成交额要保留足够精度
        BigDecimal turnOver= tradedAmount.multiply(dealPrice);
        matchOrder.setTradedAmount(matchOrder.getTradedAmount().add(tradedAmount));
        matchOrder.setTurnover(matchOrder.getTurnover().add(turnOver));

        exchangeOrder.setTradedAmount(exchangeOrder.getTradedAmount().add(tradedAmount));
        exchangeOrder.setTurnover(exchangeOrder.getTurnover().add(turnOver));

        //创建成交记录
        ExchangeTradeInfo exchangeTradeInfo =new ExchangeTradeInfo();
        exchangeTradeInfo.setSymbol(symbol);
        exchangeTradeInfo.setAmount(tradedAmount);
        exchangeTradeInfo.setDirection(exchangeOrder.getDirection());
        exchangeTradeInfo.setPrice(dealPrice);
        exchangeTradeInfo.setBuyTurnover(turnOver);
        exchangeTradeInfo.setSellTurnover(turnOver);

        //校正市价单剩余成交额
        if(ExchangeOrderType.MARKET_PRICE == exchangeOrder.getType() && exchangeOrder.getDirection() == ExchangeOrderDirection.BUY){
            BigDecimal adjustTurnover = adjustMarketOrderTurnover(exchangeOrder,dealPrice);
            exchangeTradeInfo.setBuyTurnover(turnOver.add(adjustTurnover));
        } else if (ExchangeOrderType.MARKET_PRICE == matchOrder.getType() && matchOrder.getDirection() == ExchangeOrderDirection.BUY) {
            BigDecimal adjustTurnover = adjustMarketOrderTurnover(matchOrder,dealPrice);
            exchangeTradeInfo.setBuyTurnover(turnOver.add(adjustTurnover));
        }

        if (exchangeOrder.getDirection() == ExchangeOrderDirection.BUY) {
            exchangeTradeInfo.setBuyOrderId(exchangeOrder.getOrderId());
            exchangeTradeInfo.setSellOrderId(matchOrder.getOrderId());
        } else {
            exchangeTradeInfo.setBuyOrderId(matchOrder.getOrderId());
            exchangeTradeInfo.setSellOrderId(exchangeOrder.getOrderId());
        }

        exchangeTradeInfo.setTime(Calendar.getInstance().getTimeInMillis());

        //调整盘口
        if(matchOrder.getType() == ExchangeOrderType.LIMIT_PRICE){
            if(matchOrder.getDirection() == ExchangeOrderDirection.BUY){
                buyTradePlate.remove(matchOrder,tradedAmount);
            }
            else{
                sellTradePlate.remove(matchOrder,tradedAmount);
            }
        }

        return exchangeTradeInfo;
    }

    /**
     * 调整市价单剩余成交额，当剩余成交额不足时设置订单完成
     * @param order
     * @param dealPrice
     * @return
     */
    private BigDecimal adjustMarketOrderTurnover(ExchangeOrder order, BigDecimal dealPrice){
        if(order.getDirection() == ExchangeOrderDirection.BUY && order.getType() == ExchangeOrderType.MARKET_PRICE){ { }
            BigDecimal leftTurnover = order.getAmount().subtract(order.getTurnover());
            if(leftTurnover.divide(dealPrice, Config.coinScale,BigDecimal.ROUND_DOWN).compareTo(BigDecimal.ZERO)==0) {
                order.setTurnover(order.getAmount());
                return leftTurnover;
            }
        }
        return BigDecimal.ZERO;
    }
    /**
     * 计算委托单剩余可成交的数量
     * @param order 委托单
     * @param dealPrice 成交价
     * @return
     */
    private BigDecimal calculateTradedAmount(ExchangeOrder order, BigDecimal dealPrice) {
        if(order.getDirection() == ExchangeOrderDirection.BUY && order.getType() == ExchangeOrderType.MARKET_PRICE){
            //剩余成交量
            BigDecimal leftTurnover = order.getAmount().subtract(order.getTurnover());
            return leftTurnover.divide(dealPrice, Config.coinScale, BigDecimal.ROUND_DOWN);
        } else {
            return order.getAmount().subtract(order.getTradedAmount());
        }
    }

    /**
     * 限价委托单与限价队列匹配

     */
    public void matchLimitPriceWithLimitPriceList(TreeMap<BigDecimal,NodeMergeOrders> limitPriceList, ExchangeOrder exchangeOrder) {
        List<ExchangeTradeInfo> exchangeTradeInfos = new ArrayList<>();
        List<ExchangeOrder> completedOrders = new ArrayList<>();
        synchronized (limitPriceList) {
            Iterator<Map.Entry<BigDecimal, NodeMergeOrders>> mergerOrderIterator =limitPriceList.entrySet().iterator();
            boolean exitLoop = false;
            while (!exitLoop && mergerOrderIterator.hasNext()) {
                NodeMergeOrders entry = mergerOrderIterator.next().getValue();
                Iterator<ExchangeOrder> orderIterator = entry.iterator();
                //买入单需要匹配的价格不大于委托价，否则退出
                if (exchangeOrder.getDirection() == ExchangeOrderDirection.BUY && entry.getPrice().compareTo(exchangeOrder.getPrice()) > 0) {
                    break;
                }
                //卖出单需要匹配的价格不小于委托价，否则退出
                if (exchangeOrder.getDirection() == ExchangeOrderDirection.SELL && entry.getPrice().compareTo(exchangeOrder.getPrice()) < 0) {
                    break;
                }

                while(orderIterator.hasNext()) {
                    ExchangeOrder matchOrder = orderIterator.next();
                    //处理匹配
                    ExchangeTradeInfo tradeInfo = processMatch(exchangeOrder, matchOrder);

                    if(null != tradeInfo) {
                        exchangeTradeInfos.add(tradeInfo);
                    }

                    //判断匹配单是否完成
                    if (matchOrder.isCompleted()) {
                        //当前匹配的订单完成交易，删除该订单
                        orderIterator.remove();
                        completedOrders.add(matchOrder);
                    }
                    //判断交易单是否完成
                    if (exchangeOrder.isCompleted()) {
                        //交易完成
                        completedOrders.add(exchangeOrder);
                        //退出循环
                        exitLoop = true;
                        break;
                    }
                }
                if(entry.getSize() == 0){
                    mergerOrderIterator.remove();
                }
            }
        }
        //如果还没有交易完，订单压入列表中
        if (exchangeOrder.getTradedAmount().compareTo(exchangeOrder.getAmount()) < 0) {
            addLimitPriceOrder(exchangeOrder);
        }

        //发送每个订单的匹配批量推送
        //1. 本次撮合的成交信息。
        //2. 本次撮合的完成了订单信息。
        //3. 发送盘口信息
    }

    /**
     * 增加限价订单到队列，买入单按从价格高到低排，卖出单按价格从低到高排
     * @param exchangeOrder
     */
    public void addLimitPriceOrder(ExchangeOrder exchangeOrder) {
        if(exchangeOrder.getType() != ExchangeOrderType.LIMIT_PRICE){
            return ;
        }

        TreeMap<BigDecimal,NodeMergeOrders> list;
        if(exchangeOrder.getDirection() == ExchangeOrderDirection.BUY){
            list = buyLimitPriceQueue;
            buyTradePlate.add(exchangeOrder);
            if(ready) {
                sendTradePlateMessage(buyTradePlate);
            }
        } else {
            list = sellLimitPriceQueue;
            sellTradePlate.add(exchangeOrder);
            if(ready) {
                sendTradePlateMessage(sellTradePlate);
            }
        }

        synchronized (list) {
            NodeMergeOrders mergeOrder = list.get(exchangeOrder.getPrice());
            if(mergeOrder == null){
                mergeOrder = new NodeMergeOrders();
                mergeOrder.add(exchangeOrder);
                list.put(exchangeOrder.getPrice(),mergeOrder);
            }
            else {
                mergeOrder.add(exchangeOrder);
            }
        }
    }

    /**
     * 发送盘口变化消息
     * @param plate
     */
    public void sendTradePlateMessage(TradePlate plate){
        //注意同步问题
        //synchronized (plate){}
    }

    /**
     * 限价委托单与市价队列匹配
     * @param marketPriceList 市价单队列
     * @param exchangeOrder 交易订单
     */
    public void matchLimitPriceWithMarketPriceList(LinkedList<ExchangeOrder> marketPriceList,ExchangeOrder exchangeOrder) {
        List<ExchangeTradeInfo> exchangeTradeInfos = new ArrayList<>();
        List<ExchangeOrder> completedOrders = new ArrayList<>();
        synchronized (marketPriceList) {
            Iterator<ExchangeOrder> iterator = marketPriceList.iterator();
            while (iterator.hasNext()) {
                ExchangeOrder matchOrder = iterator.next();
                ExchangeTradeInfo trade = processMatch(exchangeOrder, matchOrder);
                if(trade != null){
                    exchangeTradeInfos.add(trade);
                }
                //判断匹配单是否完成，市价单amount为成交量
                if(matchOrder.isCompleted()){
                    iterator.remove();
                    completedOrders.add(matchOrder);
                }
                //判断吃单是否完成，判断成交量是否完成
                if (exchangeOrder.isCompleted()) {
                    //交易完成
                    completedOrders.add(exchangeOrder);
                    //退出循环
                    break;
                }
            }
        }
        //如果还没有交易完，订单压入列表中
        if (exchangeOrder.getTradedAmount().compareTo(exchangeOrder.getAmount()) < 0) {
            addLimitPriceOrder(exchangeOrder);
        }

        //发送每个订单的匹配批量推送
        //1. 本次撮合的成交信息。
        //2. 本次撮合的完成了订单信息。
        //3. 发送盘口信息
    }

    /**
     * 取消委托订单
     * @param exchangeOrder
     * @return
     */
    public ExchangeOrder cancelOrder(ExchangeOrder exchangeOrder){
        logger.info("cancelOrder,orderId={}", exchangeOrder.getOrderId());
        if(exchangeOrder.getType() == ExchangeOrderType.MARKET_PRICE) {
            //处理市价单
            Iterator<ExchangeOrder> orderIterator;
            List<ExchangeOrder> list = null;
            if(exchangeOrder.getDirection() == ExchangeOrderDirection.BUY){
                list = this.buyMarketQueue;
            } else{
                list = this.sellMarketQueue;
            }
            synchronized (list) {
                orderIterator = list.iterator();
                while ((orderIterator.hasNext())) {
                    ExchangeOrder order = orderIterator.next();
                    if (order.getOrderId().equalsIgnoreCase(exchangeOrder.getOrderId())) {
                        orderIterator.remove();
                        onRemoveOrder(order);
                        return order;
                    }
                }
            }
        } else {
            //处理限价单
            TreeMap<BigDecimal,NodeMergeOrders> list = null;
            Iterator<NodeMergeOrders> mergeOrderIterator;
            if(exchangeOrder.getDirection() == ExchangeOrderDirection.BUY){
                list = this.buyLimitPriceQueue;
            } else{
                list = this.sellLimitPriceQueue;
            }
            synchronized (list) {
                NodeMergeOrders mergeOrder = list.get(exchangeOrder.getPrice());
                if(mergeOrder!=null) {
                    Iterator<ExchangeOrder> orderIterator = mergeOrder.iterator();
                    while (orderIterator.hasNext()) {
                        ExchangeOrder order = orderIterator.next();
                        if (order.getOrderId().equalsIgnoreCase(exchangeOrder.getOrderId())) {
                            orderIterator.remove();
                            if (mergeOrder.getSize() == 0) {
                                list.remove(exchangeOrder.getPrice());
                            }
                            onRemoveOrder(order);
                            return order;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 更新盘口信息
     * @param order
     */
    public void onRemoveOrder(ExchangeOrder order){
        if (order.getType() == ExchangeOrderType.LIMIT_PRICE) {
            if (order.getDirection() == ExchangeOrderDirection.BUY) {
                buyTradePlate.remove(order);
                sendTradePlateMessage(buyTradePlate);
            } else {
                sellTradePlate.remove(order);
                sendTradePlateMessage(sellTradePlate);
            }
        }
    }

    /**
     * 获取盘口信息
     * @param direction
     * @return
     */
    public TradePlate getTradePlate(ExchangeOrderDirection direction){
        if(direction == ExchangeOrderDirection.BUY){
            return buyTradePlate;
        }
        else{
            return sellTradePlate;
        }
    }

    /**
     * 查询交易器里的订单
     * @param orderId
     * @param type
     * @param direction
     * @return
     */
    public ExchangeOrder findOrder(String orderId,ExchangeOrderType type,ExchangeOrderDirection direction){
        if(type == ExchangeOrderType.MARKET_PRICE){
            LinkedList<ExchangeOrder> list;
            if(direction == ExchangeOrderDirection.BUY){
                list = this.buyMarketQueue;
            } else{
                list = this.sellMarketQueue;
            }
            synchronized (list) {
                Iterator<ExchangeOrder> orderIterator = list.iterator();
                while ((orderIterator.hasNext())) {
                    ExchangeOrder order = orderIterator.next();
                    if (order.getOrderId().equalsIgnoreCase(orderId)) {
                        return order;
                    }
                }
            }
        } else {
            TreeMap<BigDecimal,NodeMergeOrders> list;
            if(direction == ExchangeOrderDirection.BUY){
                list = this.buyLimitPriceQueue;
            } else{
                list = this.sellLimitPriceQueue;
            }
            synchronized (list) {
                Iterator<Map.Entry<BigDecimal, NodeMergeOrders>> mergeOrderIterator = list.entrySet().iterator();
                while (mergeOrderIterator.hasNext()) {
                    Map.Entry<BigDecimal, NodeMergeOrders> entry = mergeOrderIterator.next();
                    NodeMergeOrders mergeOrder = entry.getValue();
                    Iterator<ExchangeOrder> orderIterator = mergeOrder.iterator();
                    while ((orderIterator.hasNext())) {
                        ExchangeOrder order = orderIterator.next();
                        if (order.getOrderId().equalsIgnoreCase(orderId)) {
                            return order;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取限价单订单数量
     * @param direction
     * @return
     */
    public int getLimitPriceOrderCount(ExchangeOrderDirection direction){
        int count = 0;
        TreeMap<BigDecimal, NodeMergeOrders> queue = direction == ExchangeOrderDirection.BUY ? buyLimitPriceQueue : sellLimitPriceQueue;
        synchronized (queue) {
            Iterator<Map.Entry<BigDecimal, NodeMergeOrders>> mergeOrderIterator = queue.entrySet().iterator();
            while (mergeOrderIterator.hasNext()) {
                Map.Entry<BigDecimal, NodeMergeOrders> entry = mergeOrderIterator.next();
                NodeMergeOrders mergeOrder = entry.getValue();
                count += mergeOrder.getSize();
            }
        }
        return count;
    }
}
