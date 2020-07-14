package com.pfeiffercapital.rst;


import com.ib.client.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.ColorAdjust;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Configuration
@PropertySource("application.properties")
public class MainController implements EWrapper, EnvironmentAware {

    // DEBUG
    static int messagesReceived = 0;
    private static String logBoxText = "";
    static int counter = 0;

    @Autowired
    static Environment env;
    private static Main main;
    private static MainController mainController;

    // Values come from the .properties file
    private static String TWSIP;
    private static int TWSPORT;
    private static int TWSCONNECTIONID;
    static boolean TRADE_LIVE_ON_APP_START;
    private static boolean ALLOW_MANUAL_WORKFLOW_START;
    static int NUMBER_OF_POSITIONS_TO_HOLD;
    static double LEVERAGE;
    static String ACCOUNT_ID;
    private static String TRADING_CRON_EXPRESSION;
    private static String RENAMING_CRON_EXPRESSION;
    private static String LOGFILE_PATH;
    static String SIGNALFILE_PATH;
    static String BALANCEFILE_PATH;
    static String AMBIGUOUS_SYMBOLS_FILE_PATH;
    static String DATA_QUERY_LINK;
    private static String MAIL_SENDER_USER;
    private static String MAIL_SENDER_PASSWORD;
    private static String MAIL_RECIPIENT;
    private static String MAIL_SENDER_SMTP_SERVER;


    // TWS API
    private static EReaderSignal readerSignal;
    static EClientSocket clientSocket;
    private static EReader reader;
    static int nextValidOrderID;

    static {
        nextValidOrderID = -1;
    }

    // Workflows
    private static TradeWorkflow tradeWorkflow = null;
    private static SignalFileRenamingWorkflow renamingWorkflow = null;

    // Application status
    private static boolean connectedToTWS = false;
    private static boolean tradingLive = false;
    static double currentNetLiquidationValue = 0;

    private static ObservableList<Position> UIPositionList;

    //GUI
    @FXML
    private  Button buttonConnectTWS;
    @FXML
    private  Button buttonTradeLive;
    @FXML
    private  Button buttonTradeLiveColor;
    @FXML
    private  Button buttonRequestUpdate;
    @FXML
    private  Button buttonConnectTWSColor;
    @FXML
    private  Button buttonStartWorkflow;
    @FXML
    private  TextArea textAreaLiveLogging;
    @FXML
    private  Label labelCurrentEquity;
    @FXML
    private  Label labelNextSignal;
    @FXML
    private  TableView<Position> tableViewPositions;
    @FXML
    private TableColumn<Position, String> columnTicker;
    @FXML
    private  TableColumn<Position, Double> columnShares;
    @FXML
    private  TableColumn<Position, Double> columnSharePrice;
    @FXML
    private  TableColumn<Position, Double> columnMarketPrice;
    @FXML
    private  TableColumn<Position, Double> columnPositionValue;
    @FXML
    private  TableColumn<Position, Double> columnProfitPercent;
    @FXML
    private  TableColumn<Position, Double> columnAbsoluteProfit;


    //----------------- Initializing -------------------

    public void setMain(Main main) {
        this.main = main;
    }

    public void initialize() {
        //setLogger();
        initializeFromPropertiesFile();
        initializeUI();
        initializeSignalFileRenamingWorkflow();
        updateUI();

    }

    private void initializeSignalFileRenamingWorkflow() {
        ThreadPoolTaskScheduler workflowScheduler = new ThreadPoolTaskScheduler();
        workflowScheduler.setPoolSize(1);
        workflowScheduler.setThreadNamePrefix("SignalFileRenamingWorkflow");
        workflowScheduler.initialize();
        workflowScheduler.schedule(getFileRenamingWorkflow(), new CronTrigger(RENAMING_CRON_EXPRESSION));
        log(LogLevel.BOTH, "Renaming signal file scheduled at: " + RENAMING_CRON_EXPRESSION);
        updateUI();
    }

    private Runnable getFileRenamingWorkflow() {
        if (renamingWorkflow == null) {
            renamingWorkflow = new SignalFileRenamingWorkflow();
        }
        return renamingWorkflow;
    }

    private void initializeUI() {
        columnTicker.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        columnShares.setCellValueFactory(new PropertyValueFactory<>("shares"));
        columnSharePrice.setCellValueFactory(new PropertyValueFactory<>("averageCost"));
        columnMarketPrice.setCellValueFactory(new PropertyValueFactory<>("marketPrice"));
        columnPositionValue.setCellValueFactory(new PropertyValueFactory<>("marketValue"));
        columnAbsoluteProfit.setCellValueFactory(new PropertyValueFactory<>("unrealizedPNL"));
        columnProfitPercent.setCellValueFactory(new PropertyValueFactory<>("PNLinPercent"));
        UIPositionList = FXCollections.observableArrayList(TradeWorkflow.getCurrentlyHeldPositions());
        tableViewPositions.setItems(UIPositionList);
        buttonStartWorkflow.setDisable(!ALLOW_MANUAL_WORKFLOW_START);
        textAreaLiveLogging.appendText("[for logs, see log files in 'log_files' folder]");

    }

    private void initializeFromPropertiesFile() {
        TWSIP = env.getProperty("tws.ip");
        TWSPORT = Integer.valueOf(env.getProperty("tws.port"));
        TWSCONNECTIONID = Integer.valueOf(env.getProperty("tws.clientid"));
        TRADE_LIVE_ON_APP_START = Boolean.valueOf((env.getProperty("workflow.tradeLiveOnStart")));
        boolean CONNECT_ON_APP_START = Boolean.valueOf(((env.getProperty(("workflow.connectOnStart")))));
        ACCOUNT_ID = env.getProperty("workflow.accountID");
        NUMBER_OF_POSITIONS_TO_HOLD = Integer.valueOf(env.getProperty("workflow.numberOfPositionsToHold"));
        LEVERAGE = Double.valueOf(env.getProperty("trading.regT.leverage"));
        TRADING_CRON_EXPRESSION = env.getProperty("workflow.trading.crontrigger");
        RENAMING_CRON_EXPRESSION = env.getProperty("workflow.renaming.crontrigger");
        ALLOW_MANUAL_WORKFLOW_START = Boolean.valueOf((env.getProperty("workflow.trading.allowManualWorkflowStart")));
        LOGFILE_PATH = env.getProperty("path.logfile");
        SIGNALFILE_PATH = env.getProperty("path.signalfile");
        BALANCEFILE_PATH = env.getProperty("path.balancefile");
        AMBIGUOUS_SYMBOLS_FILE_PATH = env.getProperty("path.ambiguoussymbolsfile");
        DATA_QUERY_LINK = env.getProperty("util.dataquerylink");
        MAIL_SENDER_SMTP_SERVER = env.getProperty("util.mail.sender.smtp");
        MAIL_SENDER_USER = env.getProperty("util.mail.sender.user");
        MAIL_SENDER_PASSWORD = env.getProperty("util.mail.sender.password");
        MAIL_RECIPIENT = env.getProperty("util.mail.recipient");

        TradeWorkflow.setTransmitFlag(Boolean.valueOf(env.getProperty("workflow.transmitflag")));
        TradeWorkflow.setMKTorders(Boolean.valueOf(env.getProperty("workflow.useMarketOrders")));
        TradeWorkflow.setMainController(this);
    }

    @Override
    public void setEnvironment(Environment environment) {
        MainController.env = environment;
    }


    //----------------- UI handlers and updaters --------------------


    @FXML
    public void buttonConnectTWSClick() {
        System.out.println("Button click thread: " + Thread.currentThread().getName());
        if (connectedToTWS) {
            if (tradingLive) {
                stopLiveTrading();
            }
            disconnectTWS();
        } else {
            connectTWS();
        }
    }

    @FXML
    public void buttonTradeLiveClick() {
        if (!tradingLive) {
            startLiveTrading();
        } else {
            stopLiveTrading();
        }
    }

    @FXML
    public void buttonRequestUpdateClick() {
        TradeWorkflow.readSignalsFromFile();
        getHeldPositions();
        updateUI();
    }

    @FXML
    public void buttonStartWorkflowClick() {
        Thread thread = new Thread(new TradeWorkflow());
        thread.run();
    }

    static void sendMail(String subject, String content) {
        PrintStream realSystemOut = System.out;
        System.setOut(new PrintStream(new NullOutputStream()));


        String to = MAIL_RECIPIENT;
        String from = MAIL_SENDER_USER;
        Properties properties = System.getProperties();

        properties.setProperty("mail.smtp.host", MAIL_SENDER_SMTP_SERVER);
        properties.setProperty("mail.smtp.user", MAIL_SENDER_USER);
        properties.setProperty("mail.smtp.password", MAIL_SENDER_PASSWORD);
        properties.setProperty("mail.smtps.ssl.enable", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.auth", "true");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MAIL_SENDER_USER,
                        MAIL_SENDER_PASSWORD);
            }
        });
        session.setDebug(true);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            Date date = new Date();
            content = "[" + dateFormat.format(date) + "] " + content;
            message.setText(content);

            Transport.send(message);
            System.out.println("Sent message successfully....");
        } catch (Exception mex) {
            //mex.printStackTrace();
        }
        System.setOut(realSystemOut);
    }

    /**
     * Class for temporarily suppressing console output when sending emails
     */
    private static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b){
            return;
        }
        @Override
        public void write(byte[] b){
            return;
        }
        @Override
        public void write(byte[] b, int off, int len){
            return;
        }
        public NullOutputStream(){
        }
    }

    private void connectTWS() {
        readerSignal = new EJavaSignal();
        clientSocket = new EClientSocket(this, readerSignal);
        clientSocket.eConnect(TWSIP, TWSPORT, TWSCONNECTIONID);
        reader = new EReader(clientSocket, readerSignal);
        reader.start();
        //An additional thread is created in this program design to empty the messaging queue
        new Thread(() -> {
            while (clientSocket.isConnected()) {
                readerSignal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.out.println("Exception JO: " + e.getMessage());
                }
            }
        }).start();
        buttonConnectTWS.setDisable(true);
        // to prevent crashing javafx thread
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        buttonConnectTWS.setDisable(false);
        clientSocket.reqMarketDataType(4);
    }

    private void disconnectTWS() {
        clientSocket.eDisconnect();
        connectedToTWS = false;
        log(LogLevel.BOTH, "TWS connection closed");
        updateUI();
    }

    private static void startLiveTrading() {
        getTradeWorkflow().setActive(true);
        ThreadPoolTaskScheduler workflowScheduler = new ThreadPoolTaskScheduler();
        workflowScheduler.setPoolSize(5);
        workflowScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        workflowScheduler.initialize();
        workflowScheduler.schedule(getTradeWorkflow(), new CronTrigger(TRADING_CRON_EXPRESSION));
        log(LogLevel.BOTH, "Live trading started; scheduled at: " + TRADING_CRON_EXPRESSION);
        tradingLive = true;
        mainController.updateUI();
    }

    private static void stopLiveTrading() {
        tradingLive = false;
        getTradeWorkflow().setActive(false);
        log(LogLevel.BOTH, "Live trading stopped");
        mainController.updateUI();
    }


    public void updateUI() {
        try {
            // Button updates
            if (connectedToTWS) {
                buttonConnectTWSColor.effectProperty().setValue(new ColorAdjust(.55, 1, .01, .1));
                buttonTradeLive.setDisable(false);
                buttonRequestUpdate.setDisable(false);

            } else {
                buttonConnectTWSColor.effectProperty().setValue(new ColorAdjust(.0, 1, .01, .1));
                buttonTradeLive.setDisable(true);
                buttonRequestUpdate.setDisable(true);
            }
            if (tradingLive) {
                buttonTradeLiveColor.effectProperty().setValue(new ColorAdjust(.55, 1, .01, .1));
            } else {
                buttonTradeLiveColor.effectProperty().setValue(new ColorAdjust(.0, 1, .01, .1));
            }

            // To avoid thread exception (not entirely sure why)
            if (Thread.currentThread().getName().equals("JavaFX Application Thread")) {
                labelCurrentEquity.setText(Double.toString(currentNetLiquidationValue) + " " +
                        TradeWorkflow.getAccountCurrency());
                labelNextSignal.setText(TradeWorkflow.getSignals().toString());
            }

            // Position table update
            UIPositionList.setAll(TradeWorkflow.getCurrentlyHeldPositions());
            UIPositionList.replaceAll(position -> new Position(position, true));
            UIPositionList.sort(Comparator.comparing(Position::getMarketValue).reversed());
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }


    //-------------------- Utils -----------------------

    public static String createTradeWorkflowSummary(){
        String summary = "";
        for (String s : TradeWorkflow.LogQueue) {
            summary += "\n";
            summary += s;
        }
        summary = summary + "\n\nBalance after workflow: " + currentNetLiquidationValue + "\n"
                + "Percent change from last workflow execution: [to be implemented]";
        return summary;
    }

    public static TradeWorkflow getTradeWorkflow() {
        if (tradeWorkflow == null) {
            tradeWorkflow = new TradeWorkflow();

        }
        return tradeWorkflow;
    }

    public static void flushTradingWorkflowQueues() {
        try {
            //for (String s : TradeWorkflow.LogQueue) {
            //    log(LogLevel.GUI, s);
            //}
            for (String s : TradeWorkflow.LogQueue) {
                log(LogLevel.FILE, s);
            }
            TradeWorkflow.LogQueue.clear();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void log(LogLevel level, String s) {
        /*try {
            if ((level == LogLevel.GUI || level == LogLevel.BOTH) && textAreaLiveLogging != null) {
                DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                Date date = new Date();
                logBoxText += "[" + dateFormat.format(date) + "] " + s + "\n";
                String text = textAreaLiveLogging.getText();
                textAreaLiveLogging.setText( text + "[" + dateFormat.format(date) + "] " + s + "\n");

            }
            if (level == LogLevel.FILE || level == LogLevel.BOTH) {
                try {
                    DateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy");
                    Date date = new Date();
                    BufferedWriter writer = null;
                    writer = new BufferedWriter(new FileWriter(LOGFILE_PATH + dateFormat.format(date)
                            + "_log.txt", true));
                    dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    writer.append("[" + dateFormat.format(date) + "] " + s);
                    writer.append('\n');
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            textAreaLiveLogging = new TextArea();
            textAreaLiveLogging.setText(logBoxText);
            System.out.println("What I wanted to print: " + s);
            e.printStackTrace();
        }
        textAreaLiveLogging.appendText("");

        */
        try {
            DateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy");
            Date date = new Date();
            BufferedWriter writer = null;
            writer = new BufferedWriter(new FileWriter(LOGFILE_PATH + dateFormat.format(date)
                    + "_log.txt", true));
            dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            writer.append("[" + dateFormat.format(date) + "] " + s);
            writer.append('\n');
            writer.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    public static String toDate(){
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    public void cancelAllOrders() {
        for (Integer orderId : TradeWorkflow.getSellOrderStatuses().keySet()) {
            clientSocket.cancelOrder(orderId);
        }
        log(LogLevel.BOTH, "All remaining open orders have been cancelled");

    }

    //------------------- API callbacks --------------------

    @Override
    public void tickPrice(int i, int i1, double v, TickAttr tickAttr) {
        System.out.println("Received tick price: " + i + ", " + i1 + ", "
         + v + ", " + tickAttr.canAutoExecute());
    }

    @Override
    public void tickSize(int i, int i1, int i2) {

    }

    @Override
    public void tickOptionComputation(int i, int i1, double v, double v1, double v2, double v3, double v4, double v5, double v6, double v7) {

    }

    @Override
    public void tickGeneric(int i, int i1, double v) {

    }

    @Override
    public void tickString(int i, int i1, String s) {

    }

    @Override
    public void tickEFP(int i, int i1, double v, String s, double v1, int i2, String s1, double v2, double v3) {

    }

    @Override
    public void orderStatus(int i, String s, double v, double v1, double v2, int i1, int i2, double v3, int i3, String s1, double v4) {
        counter++;
        if(TradeWorkflow.getOrderStatuses().isEmpty())
            return;

        //not used yet
        TradeWorkflow.getOrderIdToRemainingToBeFilled().put(i, v1);
        if(!TradeWorkflow.getOrderStatuses().get(i).equals("Filled") &&
            s.equals("Filled")){
            log(LogLevel.BOTH, "Successfully executed [" + TradeWorkflow.getOrderIdToSmybol().get(i) +  "] order");
        }
        TradeWorkflow.getOrderStatuses().put(i, s);
        if(TradeWorkflow.getSellOrderStatuses().keySet().contains(i)){
            System.out.println("Orderstatus for sell-order received: "+ i + " " + s +" "+ v + " " + v1);
            TradeWorkflow.getSellOrderStatuses().put(i,s);
        }
        if(TradeWorkflow.getBuyOrderStatuses().keySet().contains(i)){
            System.out.println("Orderstatus for buy-order received: " + i + " " + s +" "+ v + " " + v1);
            TradeWorkflow.getBuyOrderStatuses().put(i,s);
        }
        for(Integer orderId : TradeWorkflow.getSellOrderStatuses().keySet()){
            if(!TradeWorkflow.getSellOrderStatuses().get(orderId).equals("Filled"))
                return;
        }
        TradeWorkflow.setSellOrdersExecuted(true);
        System.out.println("First time that orderStatus() is called and all sell orders are filled. counter = " + counter);

        if(!TradeWorkflow.isBuyOrdersExecuted()) {
            flushTradingWorkflowQueues();
            System.out.println("I'm buying now at counter = " + counter);
            TradeWorkflow.buyPositions();
            System.out.println("Buy orders should have been placed now");
            flushTradingWorkflowQueues();
            return;
        }

        for(Integer orderId : TradeWorkflow.getBuyOrderStatuses().keySet()){
            if(!TradeWorkflow.getBuyOrderStatuses().get(orderId).equals("Filled"))
                return;
        }

        if (TradeWorkflow.getRunning()) {
            System.out.println("All buy orders filled. Stopping workflow now.");
            finishTradingWorkflow(null);
        }
    }

    public static void finishTradingWorkflow(String errormessage) {
        TradeWorkflow.setRunning(false);
        TradeWorkflow.writeBalanceFile();
        if (errormessage != null) {
            TradeWorkflow.LogQueue.add("Error occured in workflow: " + errormessage);
            TradeWorkflow.LogQueue.add("Aborting workflow prematurely");
            TradeWorkflow.LogQueue.add("------------------------------------------------------------------");
            sendMail("RST: Summary " + toDate(), createTradeWorkflowSummary());
            flushTradingWorkflowQueues();
            return;
        }
        TradeWorkflow.LogQueue.add("All orders (if any) have successfully been filled");
        TradeWorkflow.LogQueue.add("Sending summary per email");
        TradeWorkflow.LogQueue.add("TradeWorkflow finished");
        TradeWorkflow.LogQueue.add("------------------------------------------------------------------");
        sendMail("RST: Summary " + toDate(), createTradeWorkflowSummary());
        flushTradingWorkflowQueues();
        mainController.updateUI();
    }

    private void getHeldPositions() {
        if (connectedToTWS) {
            TradeWorkflow.clearCurrentlyHeldPositions();
            clientSocket.reqAccountUpdates(true, ACCOUNT_ID);
            /*try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
            clientSocket.reqAccountUpdates(false, ACCOUNT_ID);


            //DEBUG: see how this operates
            clientSocket.reqPositions();
        }
        flushTradingWorkflowQueues();
    }

    @Override
    public void openOrder(int i, Contract contract, Order order, OrderState orderState) {

    }

    @Override
    public void openOrderEnd() {

    }

    @Override
    public void updateAccountValue(String s, String s1, String s2, String s3) {
        //System.out.println("I'm in updateAccountValue()");
        //flushTradingWorkflowQueues();
        if (s.equals("NetLiquidation")) {
            currentNetLiquidationValue = Double.parseDouble(s1);
            TradeWorkflow.setAccountCurrency(s2);
            log(LogLevel.BOTH, s + " for " + s3 + " received: " + s1 + " " + s2);
            System.out.println("NLV: " + s1);
        }
        log(LogLevel.FILE, s + " for [" + s3 + "] received: " + s1 + " " + s2);
    }

    @Override
    public void updatePortfolio(Contract contract, double v, double v1, double v2, double v3, double v4, double v5, String s) {
        //System.out.println("I'm in updatePortfolio()");
        if (v != 0) {
            TradeWorkflow.getCurrentlyHeldPositions().add(new Position(contract, v, v1, v2, v3, v4, v5, s));
        }
        TradeWorkflow.LogQueue.add("Position in [" + contract.symbol() + "] of [" + (int) v + "] shares of account [" + s + "] received from TWS");
    }

    @Override
    public void updateAccountTime(String s) {

    }

    @Override
    public void accountDownloadEnd(String s) {
        System.out.println("I'm in accountDownloadEnd()");
        mainController.updateUI();

        // fill the missing positions with cash
        while (TradeWorkflow.getCurrentlyHeldPositions().size() < NUMBER_OF_POSITIONS_TO_HOLD) {
            TradeWorkflow.getCurrentlyHeldPositions().add(new Position()); // fill with CASH positions
        }

        if (TradeWorkflow.getRunning()) {
            TradeWorkflow.readSignalsFromFile();
            if(TradeWorkflow.signals.size() != MainController.NUMBER_OF_POSITIONS_TO_HOLD) {
                TradeWorkflow.LogQueue.add("Sending mail with error");
                sendMail("RST: error occurred", "Number of signals [" + TradeWorkflow.signals.size()
                        +"] does not match with positions to hold [" + MainController.NUMBER_OF_POSITIONS_TO_HOLD +
                        "] in properties file. Please correct either one, so they match. Then restart the workflow manually.");
                finishTradingWorkflow("RST: error occurred: Number of signals [" + TradeWorkflow.signals.size()
                        +"] does not match with positions to hold [" + MainController.NUMBER_OF_POSITIONS_TO_HOLD +
                        "] in properties file. Please correct either one, so they match. Then restart the workflow manually.");
                flushTradingWorkflowQueues();
                return;
            }
            TradeWorkflow.determineBuysAndSells();
            if (TradeWorkflow.positionsToSell.isEmpty() && TradeWorkflow.signalsToBuy.isEmpty()) {
                finishTradingWorkflow(null);
                return;
            }
            //TradeWorkflow.requestMarketDataForUsedSymbols(); // only so TWS doesn't block trades (I believe)
            TradeWorkflow.acquireMarketData();
            TradeWorkflow.sellPositions();
            flushTradingWorkflowQueues();
        }
        updateUI();
    }

    @Override
    public void nextValidId(int i) {
        log(LogLevel.GUI, "TWS connection established");
        log(LogLevel.FILE, "TWS connection established");
        connectedToTWS = true;
        updateUI();
        nextValidOrderID = i;
        System.out.println("nextValidId: " + i);
    }

    @Override
    public void contractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void bondContractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void contractDetailsEnd(int i) {

    }

    @Override
    public void execDetails(int i, Contract contract, Execution execution) {

    }

    @Override
    public void execDetailsEnd(int i) {

    }

    @Override
    public void updateMktDepth(int i, int i1, int i2, int i3, double v, int i4) {

    }

    @Override
    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4) {

    }

    @Override
    public void updateNewsBulletin(int i, int i1, String s, String s1) {

    }

    @Override
    public void managedAccounts(String s) {

    }

    @Override
    public void receiveFA(int i, String s) {

    }

    @Override
    public void historicalData(int i, Bar bar) {

    }

    @Override
    public void scannerParameters(String s) {

    }

    @Override
    public void scannerData(int i, int i1, ContractDetails contractDetails, String s, String s1, String s2, String s3) {

    }

    @Override
    public void scannerDataEnd(int i) {

    }

    @Override
    public void realtimeBar(int i, long l, double v, double v1, double v2, double v3, long l1, double v4, int i1) {

    }

    @Override
    public void currentTime(long l) {

    }

    @Override
    public void fundamentalData(int i, String s) {

    }

    @Override
    public void deltaNeutralValidation(int i, DeltaNeutralContract deltaNeutralContract) {

    }

    @Override
    public void tickSnapshotEnd(int i) {

    }

    @Override
    public void marketDataType(int i, int i1) {

    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {

    }

    @Override
    public void position(String s, Contract contract, double v, double v1) {
        System.out.println("position(): received position " + contract.symbol() +
                " of " + v + " shares for avg cost " + v1);
    }

    @Override
    public void positionEnd() {
        System.out.println("positionEnd(): all positions have been sent");
    }

    @Override
    public void accountSummary(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void accountSummaryEnd(int i) {
        //clientSocket.cancelAccountSummary(i);
        //updateUI();
    }

    @Override
    public void verifyMessageAPI(String s) {

    }

    @Override
    public void verifyCompleted(boolean b, String s) {

    }

    @Override
    public void verifyAndAuthMessageAPI(String s, String s1) {

    }

    @Override
    public void verifyAndAuthCompleted(boolean b, String s) {

    }

    @Override
    public void displayGroupList(int i, String s) {

    }

    @Override
    public void displayGroupUpdated(int i, String s) {

    }

    @Override
    public void error(Exception e) {
        //System.out.println(e);
    }

    @Override
    public void error(String s) {
        //System.out.println(s);
    }

    // May be need to be updated with newer TWS versions
    @Override
    public void error(int i, int i1, String s) {

        switch (i1) {
            case 200:
                log(LogLevel.BOTH, "Error 200: Symbol is ambiguous and couldn't be resolved. Order may not have been filled.");
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                log(LogLevel.BOTH, "Sending mail with error to " + MAIL_RECIPIENT );
                sendMail("RST: error occurred", s + " Trade was not executed.\n" +
                        "Please find the contract ID for the desired ticker here: \n\n" +
                        "https://misc.interactivebrokers.com/cstools/contract_info/v3.10/index.php?site=IB&action=Top+Search&symbol=&description= \n\n" +
                        "and add the following line to the file named \"ambiguous_symbols.txt\": \n\n" +
                        "[ticker] [contractID] \n\n" + "Then restart RST and run the workflow manually.");

                log(LogLevel.BOTH,"TradeWorkflow aborted");
                log(LogLevel.BOTH,"------------------------------------------------------------------");

                TradeWorkflow.getOrderStatuses().clear();


                getHeldPositions();
                break;
            case 201:
                //Order rejected - reason:Your order is not accepted because your Equity with Loan Value of
                // [xx USD] is insufficient to cover the Initial Margin requirement of [xx USD]
                log(LogLevel.BOTH, "Error " + i1 + "; " + s);
                log(LogLevel.BOTH, "Sending mail with error to " + MAIL_RECIPIENT );
                cancelAllOrders();
                sendMail("RST: error occurred", s);
                flushTradingWorkflowQueues();
                break;

            case 504:
                // not connected
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                break;

            case 507:
                // connection failed
                log(LogLevel.BOTH, "Error " + i1 + "; connection to TWS lost/failed or tried to connect when already connected");
                flushTradingWorkflowQueues();
                break;

            case 2104:
                // market data farm
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;
            case 2106:
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            case 2108:
                // Error 2108; Market data farm connection is inactive but should be available upon demand.cashfarm
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            case 2100:
                // "API client has been unsubscribed from account data."
                log(LogLevel.BOTH, i + " Error (no real error) " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            case 2148:
                // close to margin call (5%) due to high leverage
                finishTradingWorkflow(i + ", " + i1 + ", " +s);
                cancelAllOrders();
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            case 2158:
                // "Error 2158; Sec-def data farm connection is OK:secdefnj"
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            case 202:
                // Order cancelled (by user - maybe for other reasons as well)
                finishTradingWorkflow(i + ", " + i1 + ", " +s);
                cancelAllOrders(); // cancel all remaining orders to have clean state for next run
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                flushTradingWorkflowQueues();
                break;

            default:
                log(LogLevel.BOTH, i + " Error " + i1 + "; " + s);
                log(LogLevel.BOTH, "Sending mail with error to " + MAIL_RECIPIENT );
                sendMail("RST: error occurred", s);
                flushTradingWorkflowQueues();
                break;
        }

    }

    @Override
    public void connectionClosed() {
    }

    @Override
    public void connectAck() {

    }

    @Override
    public void positionMulti(int i, String s, String s1, Contract contract, double v, double v1) {

    }

    @Override
    public void positionMultiEnd(int i) {

    }

    @Override
    public void accountUpdateMulti(int i, String s, String s1, String s2, String s3, String s4) {

    }

    @Override
    public void accountUpdateMultiEnd(int i) {

    }

    @Override
    public void securityDefinitionOptionalParameter(int i, String s, int i1, String s1, String s2, Set<String> set, Set<Double> set1) {

    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int i) {

    }

    @Override
    public void softDollarTiers(int i, SoftDollarTier[] softDollarTiers) {
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {

    }

    @Override
    public void symbolSamples(int i, ContractDescription[] contractDescriptions) {

    }

    @Override
    public void historicalDataEnd(int i, String s, String s1) {

    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {

    }

    @Override
    public void tickNews(int i, long l, String s, String s1, String s2, String s3) {

    }

    @Override
    public void smartComponents(int i, Map<Integer, Map.Entry<String, Character>> map) {

    }

    @Override
    public void tickReqParams(int i, double v, String s, int i1) {

    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {

    }

    @Override
    public void newsArticle(int i, int i1, String s) {

    }

    @Override
    public void historicalNews(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void historicalNewsEnd(int i, boolean b) {

    }

    @Override
    public void headTimestamp(int i, String s) {

    }

    @Override
    public void histogramData(int i, List<HistogramEntry> list) {

    }

    @Override
    public void historicalDataUpdate(int i, Bar bar) {

    }

    @Override
    public void rerouteMktDataReq(int i, int i1, String s) {

    }

    @Override
    public void rerouteMktDepthReq(int i, int i1, String s) {

    }

    @Override
    public void marketRule(int i, PriceIncrement[] priceIncrements) {

    }

    @Override
    public void pnl(int i, double v, double v1, double v2) {

    }

    @Override
    public void pnlSingle(int i, int i1, double v, double v1, double v2, double v3) {

    }

    @Override
    public void historicalTicks(int i, List<HistoricalTick> list, boolean b) {

    }

    @Override
    public void historicalTicksBidAsk(int i, List<HistoricalTickBidAsk> list, boolean b) {

    }

    @Override
    public void historicalTicksLast(int i, List<HistoricalTickLast> list, boolean b) {

    }

    @Override
    public void tickByTickAllLast(int i, int i1, long l, double v, int i2, TickAttr tickAttr, String s, String s1) {

    }

    @Override
    public void tickByTickBidAsk(int i, long l, double v, double v1, int i1, int i2, TickAttr tickAttr) {

    }

    @Override
    public void tickByTickMidPoint(int i, long l, double v) {

    }

    public static MainController getMainController() {
        return mainController;
    }

    public static void setMainController(MainController mainController) {
        MainController.mainController = mainController;
    }


}


