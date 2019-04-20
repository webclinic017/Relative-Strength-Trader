package com.pfeiffercapital.rst;

import com.ib.client.Contract;

import java.text.DecimalFormat;

public class Position {

    private Contract contract;
    private String symbol;
    double shares;
    double marketPrice;
    double marketValue;
    double averageCost;
    double unrealizedPNL;
    double realizedPNL;
    String accountName;

    public Position(Contract c, double v, double v1, double v2, double v3, double v4, double v5, String account) {
        this.contract = c;
        this.symbol = c.symbol();
        this.shares = v;
        this.marketPrice = v1;
        this.marketValue = v2;
        this.averageCost = v3;
        this.unrealizedPNL = v4;
        this.realizedPNL = v5;
        this.accountName = account;

    }

    public Position() {
        this.contract = null;
        this.symbol = "CASH";
        this.shares = 1;
        this.marketPrice = 0;
        this.marketValue = 0;
        this.averageCost = 0;
        this.unrealizedPNL = 0;
        this.realizedPNL = 0;
        this.accountName = "";

    }

    // Only used for GUI purposes
    public Position(Position position, boolean rounded) {
        if (rounded) {
            DecimalFormat df = new DecimalFormat("###########.##");
            this.contract = position.contract;
            this.symbol = position.symbol;
            this.shares = Double.parseDouble(df.format(position.shares).replaceAll(",", "."));
            this.marketPrice = Double.parseDouble(df.format(position.marketPrice).replaceAll(",", "."));
            this.marketValue = Double.parseDouble(df.format(position.marketValue).replaceAll(",", "."));
            this.averageCost = Double.parseDouble(df.format(position.averageCost).replaceAll(",", "."));
            this.unrealizedPNL = Double.parseDouble(df.format(position.unrealizedPNL).replaceAll(",", "."));
            this.realizedPNL = Double.parseDouble(df.format(position.realizedPNL).replaceAll(",", "."));
            this.accountName = position.accountName;
        } else {
            this.contract = position.contract;
            this.symbol = position.symbol;
            this.shares = position.shares;
            this.marketPrice = position.marketPrice;
            this.marketValue = position.marketValue;
            this.averageCost = position.averageCost;
            this.unrealizedPNL = position.unrealizedPNL;
            this.realizedPNL = position.realizedPNL;
            this.accountName = position.accountName;
        }
    }

    @Override
    public String toString() {
        return symbol + " " + shares;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getShares() {
        return shares;
    }

    public void setShares(double shares) {
        this.shares = shares;
    }

    public double getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(double marketPrice) {
        this.marketPrice = marketPrice;
    }

    public double getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(double marketValue) {
        this.marketValue = marketValue;
    }

    public double getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(double averageCost) {
        this.averageCost = averageCost;
    }

    public double getUnrealizedPNL() {
        return unrealizedPNL;
    }

    public void setUnrealizedPNL(double unrealizedPNL) {
        this.unrealizedPNL = unrealizedPNL;
    }

    public double getRealizedPNL() {
        return realizedPNL;
    }

    public void setRealizedPNL(double realizedPNL) {
        this.realizedPNL = realizedPNL;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}
