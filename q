[33mtag v0.0.4[m
Tagger: hannespfeiffer <hannes.pfeiffer@gmx.at>
Date:   Wed Jun 5 18:51:00 2019 +0200

Opening/reading more than one position not yet working

[33mcommit 4ed701acc8f0f4d0ceeb406d4dbc5fc4947adcfb[m[33m ([m[1;33mtag: v0.0.4[m[33m)[m
Author: hannespfeiffer <hannes.pfeiffer@gmx.at>
Date:   Wed Jun 5 18:10:14 2019 +0200

    Fix transmit-flag bug, Add "test.mode" to properties file which sends MKT orders instead of MOC ones
    
    Signed-off-by: hannespfeiffer <hannes.pfeiffer@gmx.at>

[1mdiff --git a/src/main/java/com/pfeiffercapital/rst/Main.java b/src/main/java/com/pfeiffercapital/rst/Main.java[m
[1mindex 515093a..61f337d 100644[m
[1m--- a/src/main/java/com/pfeiffercapital/rst/Main.java[m
[1m+++ b/src/main/java/com/pfeiffercapital/rst/Main.java[m
[36m@@ -38,7 +38,7 @@[m [mpublic class Main extends Application {[m
         controller.setMain(this);[m
         controller.initialize();[m
 [m
[31m-        primaryStage.setTitle("RST v0.0.1");[m
[32m+[m[32m        primaryStage.setTitle("RST v0.0.4");[m
         primaryStage.setScene(new Scene(root,700,530));[m
         primaryStage.show();[m
     }[m
[1mdiff --git a/src/main/java/com/pfeiffercapital/rst/MainController.java b/src/main/java/com/pfeiffercapital/rst/MainController.java[m
[1mindex fa188d1..cf54cd3 100644[m
[1m--- a/src/main/java/com/pfeiffercapital/rst/MainController.java[m
[1m+++ b/src/main/java/com/pfeiffercapital/rst/MainController.java[m
[36m@@ -164,6 +164,7 @@[m [mpublic class MainController implements EWrapper, EnvironmentAware {[m
         DATA_QUERY_LINK = env.getProperty("util.dataquerylink");[m
 [m
         TradeWorkflow.setTransmitFlag(Boolean.valueOf(env.getProperty("workflow.transmitflag")));[m
[32m+[m[32m        TradeWorkflow.setTestMode(Boolean.valueOf(env.getProperty("test.mode")));[m
     }[m
 [m
     @Override[m
[1mdiff --git a/src/main/java/com/pfeiffercapital/rst/TradeWorkflow.java b/src/main/java/com/pfeiffercapital/rst/TradeWorkflow.java[m
[1mindex f1b3bd3..83852f1 100644[m
[1m--- a/src/main/java/com/pfeiffercapital/rst/TradeWorkflow.java[m
[1m+++ b/src/main/java/com/pfeiffercapital/rst/TradeWorkflow.java[m
[36m@@ -24,6 +24,7 @@[m [mpublic class TradeWorkflow implements Runnable {[m
     private static boolean active = false;[m
     private static boolean running = false;[m
     private static boolean transmitFlag = false;[m
[32m+[m[32m    private static boolean testMode = false;[m
     private static ArrayList<Position> currentlyHeldPositions = new ArrayList<>();[m
     private static ArrayList<String> signals = new ArrayList<>();[m
     private static ArrayList<String> signalsToBuy = new ArrayList<>();[m
[36m@@ -32,6 +33,10 @@[m [mpublic class TradeWorkflow implements Runnable {[m
     private static Map<String, Integer> shareAmountsToBuy = new HashMap<>();[m
     private static String accountCurrency = "USD";[m
 [m
[32m+[m[32m    public static void setTestMode(boolean testMode) {[m
[32m+[m[32m        TradeWorkflow.testMode = testMode;[m
[32m+[m[32m    }[m
[32m+[m
 [m
     @Override[m
     public void run() {[m
[36m@@ -137,8 +142,10 @@[m [mpublic class TradeWorkflow implements Runnable {[m
             Order order = new Order();[m
             order.action("SELL");[m
             order.orderType("MOC");[m
[32m+[m[32m            if(testMode)[m
[32m+[m[32m                order.orderType("MKT");[m
             order.totalQuantity(pos.shares);[m
[31m-            order.tif("OPG");[m
[32m+[m[32m            order.tif("DAY");[m
             order.account(pos.accountName);[m
             order.orderRef("test order");[m
             order.transmit(transmitFlag);[m
[36m@@ -256,6 +263,8 @@[m [mpublic class TradeWorkflow implements Runnable {[m
             Order order = new Order();[m
             order.action("BUY");[m
             order.orderType("MOC");[m
[32m+[m[32m            if(testMode)[m
[32m+[m[32m                order.orderType("MKT");[m
             order.totalQuantity(amount);[m
             order.transmit(transmitFlag);[m
             order.account(MainController.ACCOUNT_ID);[m
[1mdiff --git a/src/main/resources/application.properties b/src/main/resources/application.properties[m
[1mindex 0146f9a..13889f7 100644[m
[1m--- a/src/main/resources/application.properties[m
[1m+++ b/src/main/resources/application.properties[m
[36m@@ -2,11 +2,18 @@[m [mtws.ip=127.0.0.1[m
 tws.port=7497[m
 tws.clientid=1[m
 workflow.numberOfPositionsToHold=1[m
[31m-workflow.transmitflag=0[m
[32m+[m[32mworkflow.transmitflag=true[m
 #workflow.trading.crontrigger=0 30 21 ? * MON-FRI[m
[31m-workflow.trading.crontrigger=0 03 * ? * *[m
[31m-workflow.renaming.crontrigger=0 47 * ? * *[m
[32m+[m
[32m+[m[32m#-----------------------------[m
[32m+[m[32mworkflow.trading.crontrigger=0 06 * ? * *[m
[32m+[m[32m#-----------------------------[m
[32m+[m
[32m+[m[32mworkflow.renaming.crontrigger=0 29 * ? * *[m
 workflow.accountID=DU1466074[m
 path.logfile=D:/pfeiffer-trading/log_files/[m
 path.signalfile=D:/pfeiffer-trading/signal_files/[m
[31m-util.dataquerylink=https://query1.finance.yahoo.com/v7/finance/quote[m
\ No newline at end of file[m
[32m+[m[32mutil.dataquerylink=https://query1.finance.yahoo.com/v7/finance/quote[m
[32m+[m
[32m+[m[32m#-----------------[m
[32m+[m[32mtest.mode=true[m
\ No newline at end of file[m
