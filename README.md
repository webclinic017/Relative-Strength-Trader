# Relative-Strength-Trader
The purpose of this piece of software is automated stock trade execution, using the Interactive Brokers API. 
The program reads ticker symbols from a .txt file and executes the respective trades with evenly distributed capital allocation, at specific times (defined via a cron-expression).

Given a .txt file of the form:
```javascript
TSLA
AAPL
MSFT
```
and a cron-expression of the form

```
workflow.trading.crontrigger = 0 30 15,16,17,18,19,20,21 ? * MON-FRI
```

the program will sell all currently held positions and buy these three tickers with 1/3 of the available capital each, at each Monday to Friday from 15:30 to 21:30 in 60 minute intervals.
