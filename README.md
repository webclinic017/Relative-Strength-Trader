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

In the following diagram, our application is the "RST". That's how a workflow could look like:
(1) Get EOD Data from data provider (e.g. Yahoo finance)
(2) Produce trading signals based on rules
(3) Write those signals into a .txt file called signals.txt
(4) Our application reads the file and automatically executes the trades via a locally running TWS


![a](https://user-images.githubusercontent.com/20343898/114101172-a4745a00-98c5-11eb-8d11-21f6bc6b4cb9.png)
