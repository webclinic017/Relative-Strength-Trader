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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Configuration
@PropertySource("application.properties")
public class MainController implements EWrapper, EnvironmentAware {

    // DEBUG
    static int messagesReceived = 0;
    static String logBoxText = "";

    @Autowired
    static Environment env;
    static public Main main;
    //static Logger logger;


    // Values come from properties file
    static String TWSIP;
    static int TWSPORT;
    static int TWSCONNECTIONID;
    static int NUMBER_OF_POSITIONS_TO_HOLD;
    static double LEVERAGE;
    static String ACCOUNT_ID;
    static String TRADING_CRON_EXPRESSION;
    static String RENAMING_CRON_EXPRESSION;
    static String LOGFILE_PATH;
    static String SIGNALFILE_PATH;
    static String BALANCEFILE_PATH;
    static String AMBIGUOUS_SYMBOLS_FILE_PATH;
    static String DATA_QUERY_LINK;
    static String MAIL_SENDER_USER;
    static String MAIL_SENDER_PASSWORD;
    static String MAIL_RECIPIENT;
    static String MAIL_SENDER_SMTP_SERVER;


    // TWS API
    static com.ib.client.EReaderSignal readerSignal;
    static com.ib.client.EClientSocket clientSocket;
    static com.ib.client.EReader reader;
    static int nextValidOrderID = -1;

    // Workflows
    static TradeWorkflow tradeWorkflow = null;
    static SignalFileRenamingWorkflow renamingWorkflow = null;

    // Application status
    static boolean connectedToTWS = false;
    static boolean tradingLive = false;
    static double currentNetLiquidationValue = 0;

    static ObservableList<Position> UIPositionList;

    //GUI
    @FXML
    private Button buttonConnectTWS;
    @FXML
    private Button buttonTradeLive;
    @FXML
    private Button buttonTradeLiveColor;
    @FXML
    private Button buttonRequestUpdate;
    @FXML
    private Button buttonConnectTWSColor;
    @FXML
    private Button buttonStartWorkflow;
    @FXML
    private TextArea textAreaLiveLogging;
    @FXML
    private Label labelCurrentEquity;
    @FXML
    private Label labelNextSignal;
    @FXML
    private TableView<Position> tableViewPositions;
    @FXML
    private TableColumn<Position, String> columnTicker;
    @FXML
    private TableColumn<Position, Double> columnShares;
    @FXML
    private TableColumn<Position, Double> columnSharePrice;
    @FXML
    private TableColumn<Position, Double> columnMarketPrice;
    @FXML
    private TableColumn<Position, Double> columnPositionValue;
    @FXML
    private TableColumn<Position, Double> columnProfitPercent;
    @FXML
    private TableColumn<Position, Double> columnAbsoluteProfit;


    //----------------- Initializing -------------------

    public void setMain(Main main) {
        this.main = main;
    }

    public void initialize() {
        //setLogger();
        initializeFromPropertiesFile();
        initializeUI();
        initializeSignalFileRenamingWorkflow();
        Platform.runLater(this::updateUI);
    }

    private void initializeSignalFileRenamingWorkflow() {
        ThreadPoolTaskScheduler workflowScheduler = new ThreadPoolTaskScheduler();
        workflowScheduler.setPoolSize(1);
        workflowScheduler.setThreadNamePrefix("SignalFileRenamingWorkflow");
        workflowScheduler.initialize();
        workflowScheduler.schedule(getFileRenamingWorkflow(), new CronTrigger(RENAMING_CRON_EXPRESSION));
        log(LogLevel.GUI, "Renaming signal file scheduled at: " + RENAMING_CRON_EXPRESSION);
        log(LogLevel.FILE, "Renaming signal file scheduled at: " + RENAMING_CRON_EXPRESSION);
        Platform.runLater(this::updateUI);
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

        // TODO: implement columnProfitPercent

        UIPositionList = FXCollections.observableArrayList(TradeWorkflow.getCurrentlyHeldPositions());
        tableViewPositions.setItems(UIPositionList);
        buttonStartWorkflow.setDisable(!ALLOW_MANUAL_WORKFLOW_START);

    }

    private void initializeFromPropertiesFile() {
        TWSIP = env.getProperty("tws.ip");
        TWSPORT = Integer.valueOf(env.getProperty("tws.port"));
        TWSCONNECTIONID = Integer.valueOf(env.getProperty("tws.clientid"));
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
        TradeWorkflow.setTestMode(Boolean.valueOf(env.getProperty("test.mode")));
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
    }

    @FXML
    public void buttonStartWorkflowClick() {
        System.out.println("Start workflow button");
        Thread thread = new Thread(new TradeWorkflow());
        thread.run();


    }

    static void sendMail(String subject, String content) {
        // Recipient's email ID needs to be mentioned.
        String to = MAIL_RECIPIENT;

        // Sender's email ID needs to be mentioned
        String from = MAIL_SENDER_USER;

        // Assuming you are sending email from localhost
        String host = MAIL_SENDER_SMTP_SERVER;

        // Get system properties
        Properties properties = System.getProperties();

        // Setup mail server
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

            //Transport transport = session.getTransport("smtp");
            //transport.connect("mail.gmx.net", "hannes.pfeiffer@gmx.at", "xlt3j.xlt3j.xlt3j");

            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Set Subject: header field
            message.setSubject(subject);

            // Now set the actual message
            DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            Date date = new Date();
            content = "[" + dateFormat.format(date) + "] " + content;
            message.setText(content);

            // Send message

            Transport.send(message);
            System.out.println("Sent message successfully....");
        } catch (MessagingException mex) {
            mex.printStackTrace();
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
                    System.out.println("Exception: " + e.getMessage());
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
    }

    private void disconnectTWS() {
        clientSocket.eDisconnect();
        connectedToTWS = false;
        log(LogLevel.GUI, "TWS connection closed");
        log(LogLevel.FILE, "TWS connection closed");
        Platform.runLater(this::updateUI);
    }

    private void startLiveTrading() {
        getTradeWorkflow().setActive(true);
        ThreadPoolTaskScheduler workflowScheduler = new ThreadPoolTaskScheduler();
        workflowScheduler.setPoolSize(5);
        workflowScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        workflowScheduler.initialize();
        workflowScheduler.schedule(getTradeWorkflow(), new CronTrigger(TRADING_CRON_EXPRESSION));
        log(LogLevel.GUI, "Live trading started; scheduled at: " + TRADING_CRON_EXPRESSION);
        log(LogLevel.FILE, "Live trading started; scheduled at: " + TRADING_CRON_EXPRESSION);
        tradingLive = true;
        Platform.runLater(this::updateUI);
    }

    private void stopLiveTrading() {
        tradingLive = false;
        getTradeWorkflow().setActive(false);
        log(LogLevel.GUI, "Live trading stopped");
        log(LogLevel.FILE, "Live trading stopped");
        Platform.runLater(this::updateUI);
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
                labelCurrentEquity.setText(Double.toString(currentNetLiquidationValue) + " " + TradeWorkflow.getAccountCurrency());
                labelNextSignal.setText(TradeWorkflow.getSignals().toString());
            }

            // Position table update
            UIPositionList.setAll(TradeWorkflow.getCurrentlyHeldPositions());
            UIPositionList.replaceAll(position -> new Position(position, true));
            UIPositionList.sort(Comparator.comparing(Position::getMarketValue).reversed());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //-------------------- Utils -----------------------

    public String createTradeWorkFlowSummary(){
        String summary = "";
        for (String s : TradeWorkflow.GUILogQueue) {
            summary += "\n";
            summary += s;
        }
        summary = summary + "\n\nBalance after workflow: " + currentNetLiquidationValue + "\n"
                + "Percent change from yesterday: [to be implemented]";
        return summary;
    }

    public TradeWorkflow getTradeWorkflow() {
        if (tradeWorkflow == null) {
            tradeWorkflow = new TradeWorkflow();

        }
        return tradeWorkflow;
    }

    public void flushTradingWorkflowQueues() {
        for (String s : TradeWorkflow.GUILogQueue) {
            log(LogLevel.GUI, s);
        }
        for (String s : TradeWorkflow.FILELogQueue) {
            log(LogLevel.FILE, s);
        }
        TradeWorkflow.GUILogQueue.clear();
        TradeWorkflow.FILELogQueue.clear();
    }

    public void log(LogLevel level, String s) {
        try {
            if (level == LogLevel.GUI && textAreaLiveLogging != null) {
                DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                Date date = new Date();
                logBoxText += "[" + dateFormat.format(date) + "] " + s + "\n";
                String text = textAreaLiveLogging.getText();
                textAreaLiveLogging.setText( text + "[" + dateFormat.format(date) + "] " + s + "\n");

            }
            if (level == LogLevel.FILE) {
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
    }

    public String toDate(){
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date date = new Date();
        return dateFormat.format(date);
    }


    //------------------- API callbacks --------------------

    @Override
    public void tickPrice(int i, int i1, double v, TickAttr tickAttr) {

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
        //DEBUG
        //System.out.print("\n__________________________________\n");
        //System.out.print(i + "\n" + s +"\n" + v + "\n" + v1 );

        if(TradeWorkflow.getOrderStatuses().isEmpty())
            return;

        //not used yet
        TradeWorkflow.getOrderIdToRemainingToBeFilled().put(i, v1);

        if(!TradeWorkflow.getOrderStatuses().get(i).equals("Filled") &&
            s.equals("Filled")){
            log(LogLevel.GUI, "Successfully executed [" + TradeWorkflow.getOrderIdToSmybol().get(i) +  "] order");
        }
        TradeWorkflow.getOrderStatuses().put(i, s);

        for(Integer orderId : TradeWorkflow.getOrderStatuses().keySet()){
            if(!TradeWorkflow.getOrderStatuses().get(orderId).equals("Filled"))
                return;
        }

        TradeWorkflow.writeBalanceFile();

        log(LogLevel.GUI, "Orders have successfully been filled");
        log(LogLevel.FILE, "Orders have successfully been filled");

        log(LogLevel.GUI, "Sending summary per email");
        log(LogLevel.FILE, "Sending summary per email");

        sendMail("RST: Summary " + toDate(), createTradeWorkFlowSummary());

        log(LogLevel.GUI,"TradeWorkflow finished");
        log(LogLevel.FILE,"TradeWorkflow finished");
        log(LogLevel.GUI,"------------------------------------------------------------------");
        log(LogLevel.FILE,"------------------------------------------------------------------");

        TradeWorkflow.getOrderStatuses().clear();

        getHeldPositions();

    }

    private void getHeldPositions() {
        if (connectedToTWS) {
            TradeWorkflow.clearCurrentlyHeldPositions();
            clientSocket.reqAccountUpdates(true, ACCOUNT_ID);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            clientSocket.reqAccountUpdates(false, ACCOUNT_ID);
        }
        Platform.runLater(this::flushTradingWorkflowQueues);
    }


    @Override
    public void openOrder(int i, Contract contract, Order order, OrderState orderState) {

    }

    @Override
    public void openOrderEnd() {

    }

    @Override
    public void updateAccountValue(String s, String s1, String s2, String s3) {
        //System.out.println("updateAccountValue: " + s + ", value: " + s1 + " " + s2);

        if (s.equals("AvailableFunds")) {
            currentNetLiquidationValue = Double.parseDouble(s1);
            TradeWorkflow.setAccountCurrency(s2);
            log(LogLevel.GUI, s + " for " + s3 + " received: " + s1 + " " + s2);
        }
        log(LogLevel.FILE, s + " for [" + s3 + "] received: " + s1 + " " + s2);
    }

    @Override
    public void updatePortfolio(Contract contract, double v, double v1, double v2, double v3, double v4, double v5, String s) {

        if (v != 0) {
            TradeWorkflow.getCurrentlyHeldPositions().add(new Position(contract, v, v1, v2, v3, v4, v5, s));
        }
        log(LogLevel.GUI, "Position in [" + contract.symbol() + "] of [" + (int) v + "] shares of account [" + s + "] received");
        log(LogLevel.FILE, "Position in [" + contract.symbol() + "] of [" + (int) v + "] shares of account [" + s + "] received");
    }

    @Override
    public void updateAccountTime(String s) {

    }

    @Override
    public void accountDownloadEnd(String s) {
        System.out.println("In accountDownloadEnd");
        // fill the missing positions with cash
        while (TradeWorkflow.getCurrentlyHeldPositions().size() < NUMBER_OF_POSITIONS_TO_HOLD) {
            TradeWorkflow.getCurrentlyHeldPositions().add(new Position());
        }

        if (TradeWorkflow.getRunning()) {
            TradeWorkflow.readSignalsFromFile();
            if(TradeWorkflow.signals.size() != MainController.NUMBER_OF_POSITIONS_TO_HOLD) {
                flushTradingWorkflowQueues();
                TradeWorkflow.setRunning(false);



                log(LogLevel.GUI, "Sending mail with error");
                log(LogLevel.FILE, "Sending mail with error");
                sendMail("RST: error occurred", "Number of signals [" + TradeWorkflow.signals.size()
                        +"] does not match with positions to hold [" + MainController.NUMBER_OF_POSITIONS_TO_HOLD +
                        "] in properties file. Please correct either one, so they match. Then restart the workflow manually.");

                log(LogLevel.GUI, "Sending summary per email");
                log(LogLevel.FILE, "Sending summary per email");

                sendMail("RST: Summary " + toDate(), createTradeWorkFlowSummary());

                log(LogLevel.GUI, "TradeWorkflow aborted");
                log(LogLevel.FILE, "TradeWorkflow aborted");
                log(LogLevel.GUI,"------------------------------------------------------------------");
                log(LogLevel.FILE,"------------------------------------------------------------------");

                return;
            }
            TradeWorkflow.sellPositions();
            TradeWorkflow.acquireMarketData();
            TradeWorkflow.buyPositions();
            TradeWorkflow.setRunning(false);

            flushTradingWorkflowQueues();
            if(TradeWorkflow.getOrderIdToSmybol().isEmpty()){
                TradeWorkflow.writeBalanceFile();

                log(LogLevel.GUI, "No orders need to be filled");
                log(LogLevel.FILE, "No orders need to be filled");

                log(LogLevel.GUI, "Sending summary per email");
                log(LogLevel.FILE, "Sending summary per email");

                sendMail("RST: Summary " + toDate(), createTradeWorkFlowSummary());

                log(LogLevel.GUI,"TradeWorkflow finished");
                log(LogLevel.FILE,"TradeWorkflow finished");
                log(LogLevel.GUI,"------------------------------------------------------------------");
                log(LogLevel.FILE,"------------------------------------------------------------------");
            }
        }

        Platform.runLater(this::updateUI);
    }

    @Override
    public void nextValidId(int i) {
        // Connection established
        //System.out.println("Message: " + (++messagesReceived) + " with id: " + i);
        //if(i == 27){

        log(LogLevel.GUI, "TWS connection established");
        log(LogLevel.FILE, "TWS connection established");
        connectedToTWS = true;
        Platform.runLater(this::updateUI);
        nextValidOrderID = i;
        System.out.println("nextValidId: " + i);
        //}
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

    }

    @Override
    public void positionEnd() {

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
                log(LogLevel.GUI, "Order couldn't be filled");
                log(LogLevel.FILE, "Order couldn't be filled");

                log(LogLevel.GUI, "Error " + i1 + "; " + s);
                log(LogLevel.FILE, "Error " + i1 + "; " + s);

                log(LogLevel.GUI, "Sending mail with error to " + MAIL_RECIPIENT );
                log(LogLevel.FILE, "Sending mail with error to " + MAIL_RECIPIENT);

                sendMail("RST: error occurred", s + " Trade was not executed.\n" +
                        "Please find the contract ID for the desired ticker here: \n\n" +
                        "https://misc.interactivebrokers.com/cstools/contract_info/v3.10/index.php?site=IB&action=Top+Search&symbol=&description= \n\n" +
                        "and add the following line to the file named \"ambiguous_symbols.txt\": \n\n" +
                        "[ticker] [contractID] \n\n" + "Then restart RST and run the workflow manually.");

                log(LogLevel.GUI,"TradeWorkflow aborted");
                log(LogLevel.FILE,"TradeWorkflow aborted");
                log(LogLevel.GUI,"------------------------------------------------------------------");
                log(LogLevel.FILE,"------------------------------------------------------------------");

                TradeWorkflow.getOrderStatuses().clear();

                getHeldPositions();
                break;
            //connection failed
            case 507:
                log(LogLevel.GUI, "Error " + i1 + "; connection to TWS lost/failed or tried to connect when already connected");
                log(LogLevel.FILE, "Error " + i1 + "; connection to TWS lost/failed or tried to connect when already connected");

                break;
            //market data farm
            case 2104:
            case 2106:
                //TradeWorkflow.addToGUIQueue(s);
                //TradeWorkflow.addToFILEQueue(s);
                log(LogLevel.GUI, s);
                log(LogLevel.FILE, s);
                break;
            //"API client has been unsubscribed from account data."
            case 2100:
                //log(LogLevel.FILE,"Error "+i1+"; "+s);
                //TradeWorkflow.addToGUIQueue(s);
                // TradeWorkflow.addToFILEQueue(s);
                break;
            case 202:

            default:
                //TradeWorkflow.addToGUIQueue(s);
                //TradeWorkflow.addToFILEQueue(s);
                log(LogLevel.GUI, "Error " + i1 + "; " + s);
                log(LogLevel.FILE, "Error " + i1 + "; " + s);

                log(LogLevel.GUI, "Sending mail with error to " + MAIL_RECIPIENT );
                log(LogLevel.FILE, "Sending mail with error to " + MAIL_RECIPIENT);
                sendMail("RST: error occurred", s);
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


}


