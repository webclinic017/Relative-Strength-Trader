package com.pfeiffercapital.rst;

import com.ib.client.Contract;
import com.ib.client.Order;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TradeWorkflow implements Runnable {

    //DEBUG
    static int counter = 0;

    static ArrayList<String> GUILogQueue = new ArrayList<>();
    static ArrayList<String> FILELogQueue = new ArrayList<>();


    private static boolean active = false;
    private static boolean running = false;
    private static boolean transmitFlag = false;
    private static boolean testMode = false;
    private static ArrayList<Position> currentlyHeldPositions = new ArrayList<>();
    private static ArrayList<String> signals = new ArrayList<>();
    private static ArrayList<String> signalsToBuy = new ArrayList<>();
    private static ArrayList<Position> positionsToSell = new ArrayList<>();

    private static HashMap<Integer,Double> orderIdToRemainingToBeFilled = new HashMap<>();
    private static HashMap<Integer,String> orderStatuses = new HashMap<>();
    private static HashMap<Integer,String> orderIdToSmybol = new HashMap<>();
    private static Map<String, Double> signalPrices = new HashMap<>();
    private static Map<String, Integer> shareAmountsToBuy = new HashMap<>();
    private static Map<String, Integer> symbolToContractId = new HashMap<>();
    private static String accountCurrency = "USD";

    public static void setTestMode(boolean testMode) {
        TradeWorkflow.testMode = testMode;
    }


    @Override
    public void run() {

        if (active) {
            running = true;

            GUILogQueue.add("------------------------------------------------------------------");
            FILELogQueue.add("------------------------------------------------------------------");
            GUILogQueue.add("TradeWorkflow started");
            FILELogQueue.add("TradeWorkflow started");

            /*
            MainController.logger.log(LogLevel.GUI,"------------------------------------------------------------------");
            MainController.logger.log(LogLevel.FILE,"------------------------------------------------------------------");
            MainController.logger.log(LogLevel.GUI,"TradeWorkflow started");
            MainController.logger.log(LogLevel.FILE,"TradeWorkflow started");
            */

            currentlyHeldPositions.clear();
            signals.clear();
            signalsToBuy.clear();
            positionsToSell.clear();
            orderIdToRemainingToBeFilled.clear();
            orderStatuses.clear();
            orderIdToSmybol.clear();

            MainController.clientSocket.reqAccountUpdates(true, MainController.env.getProperty("workflow.accountID"));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            MainController.clientSocket.reqAccountUpdates(false, MainController.env.getProperty("workflow.accountID"));
        }

    }

    public static void sellPositions() {
        System.out.println("Selling positions");


        // determining which tickers to sell and buy
        for (String signal : signals) {
            List<Position> list = currentlyHeldPositions.stream().filter(p -> p.getSymbol().equals(signal))
                    .collect(Collectors.toList());
            if (list.size() == 0) {
                signalsToBuy.add(signal);
            }
        }
        for (Position pos : currentlyHeldPositions) {
            if (!signals.contains(pos.getSymbol())) {
                positionsToSell.add(pos);
            }
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        GUILogQueue.add("Currently holding: " +
                currentlyHeldPositions.stream().map(p -> p.getSymbol()).collect(Collectors.toList()));
        FILELogQueue.add("Currently holding: " +
                currentlyHeldPositions.stream().map(p -> p.getSymbol()).collect(Collectors.toList()));
        GUILogQueue.add("Selling: " + positionsToSell);
        FILELogQueue.add("Selling: " + positionsToSell);
        GUILogQueue.add("Buying: " + signalsToBuy);
        FILELogQueue.add("Selling: " + signalsToBuy);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        // selling
        for (Position pos : positionsToSell) {
            if (pos.getSymbol().equals("CASH")) {
                continue;
            }
            Order order = new Order();
            order.action("SELL");
            order.orderType("MOC");
            if(testMode)
                order.orderType("MKT");
            order.totalQuantity(pos.shares);
            order.tif("DAY");
            order.account(pos.accountName);
            order.orderRef("MOC sell order for " + pos);
            if(testMode)
                order.orderRef("MKT sell order for " + pos);
            order.transmit(transmitFlag);
            pos.getContract().exchange("SMART");

            MainController.clientSocket.placeOrder(++MainController.nextValidOrderID, pos.getContract(), order);
            orderIdToRemainingToBeFilled.put(MainController.nextValidOrderID, pos.shares);
            orderStatuses.put(MainController.nextValidOrderID, "none");
            orderIdToSmybol.put(MainController.nextValidOrderID, pos.getSymbol());
            System.out.println("OrderID when selling " + pos.getSymbol() + ": " + MainController.nextValidOrderID);

            GUILogQueue.add("Placed order for selling [" + pos + "]");
            FILELogQueue.add("Placed order for selling [" + pos + "]");
        }

    }

    // uses yahoo finance. needs to be adjusted if new data source is used
    public static void acquireMarketData() {
        if (signals.isEmpty()) {
            return;
        }

        URL url = null;
        try {
            url = new URL(MainController.DATA_QUERY_LINK);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            con.setRequestMethod("GET");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }

        Map<String, String> parameters = new HashMap<>();
        String symbols = "";
        for (String sig : signals) {
            if (sig.equals("CASH")) {
                continue;
            }
            symbols = symbols + sig + ",";
        }
        symbols.substring(0, symbols.length() - 1);
        System.out.println(symbols);

        parameters.put("symbols", symbols);

        con.setDoOutput(true);
        DataOutputStream out = null;
        StringBuffer content = null;
        try {
            out = new DataOutputStream(con.getOutputStream());
            out.writeBytes(ParameterStringBuilder.getParamsString(parameters));
            out.flush();
            out.close();

            int status = con.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        con.disconnect();

        JSONObject obj = new JSONObject(content.toString());
        JSONObject quoteResponse = obj.getJSONObject("quoteResponse");
        JSONArray result = quoteResponse.getJSONArray("result");

        System.out.println("JSON response: " + quoteResponse);

        for (String signal : signalsToBuy) {
            for (int i = 0; i < result.length(); i++) {
                if (result.getJSONObject(i).getString("symbol").equals(signal)) {
                    if (result.getJSONObject(i).getDouble("ask") != 0.0)
                        signalPrices.put(signal, result.getJSONObject(i).getDouble("ask"));
                    else
                        signalPrices.put(signal, result.getJSONObject(i).getDouble("regularMarketPrice"));

                }
            }
        }
        System.out.println("Retrieved prices from json: " + signalPrices);

        GUILogQueue.add("Received prices from YAHOO finance: " + signalPrices);
        FILELogQueue.add("Received prices from YAHOO finance: " + signalPrices);

    }

    public static void buyPositions() {

        // buying
        for (String signal : signalsToBuy) {
            if (signal.equals("CASH")) {
                continue;
            }
            int amount = (int) Math.floor((MainController.currentNetLiquidationValue / signalPrices.get(signal)) /
                    MainController.NUMBER_OF_POSITIONS_TO_HOLD);
            shareAmountsToBuy.put(signal, amount);

            Order order = new Order();
            order.action("BUY");
            order.orderType("MOC");
            if(testMode)
                order.orderType("MKT");
            order.totalQuantity(amount);
            order.transmit(transmitFlag);
            order.account(MainController.ACCOUNT_ID);
            order.orderRef("test order");

            Contract contract = new Contract();
            contract.secType("STK");
            contract.currency(accountCurrency);
            contract.exchange("SMART");
            //System.out.println("Buying on: " + contract.exchange());
            contract.symbol(signal);

            int id = checkIfSignalAmbiguous(signal);
            if(id != 0){
                contract.symbol("");
                contract.conid(id);
            }
            //if (signal.equals("MSFT")) {
            //    contract.symbol("");
            //    contract.conid(272093);
            //}
            //if (signal.equals("GLD")) {
            //    contract.symbol("");
            //    contract.conid(51529211);
            //}



            MainController.clientSocket.placeOrder(++MainController.nextValidOrderID, contract, order);
            orderIdToRemainingToBeFilled.put(MainController.nextValidOrderID, (double) amount);
            orderStatuses.put(MainController.nextValidOrderID, "none");
            orderIdToSmybol.put(MainController.nextValidOrderID, signal);
            System.out.println("OrderID when buying " + signal + ": " + MainController.nextValidOrderID);

            GUILogQueue.add("Placed order for buying [" + signal + "]");
            FILELogQueue.add("Placed order for buying [" + signal + "]");

            GUILogQueue.add("Waiting for orders to be filled...");
            FILELogQueue.add("Waiting for orders to be filled...");
        }

        //obsolete, since set to false in central control method (accountDownloadEnd)
        running = false;
    }

    private static int checkIfSignalAmbiguous(String signal) {
        GUILogQueue.add("Checking if [" + signal + "] is ambiguous");
        FILELogQueue.add("Checking if [" + signal + "] is ambiguous");

        boolean fileExists = true;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(MainController.AMBIGUOUS_SYMBOLS_FILE_PATH
                    + "ambiguous_symbols.txt"));
        } catch (FileNotFoundException e) {

            GUILogQueue.add("Ambiguous symbol file could not be found");
            FILELogQueue.add(e.getMessage());
            fileExists = false;
        }
        // lines with <6 digits are interpreted as tickers
        if (fileExists) {
            try {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    symbolToContractId.put(line.split(" ")[0], Integer.valueOf(line.split(" ")[1]));
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(symbolToContractId.keySet().contains(signal))
            return symbolToContractId.get(signal);
        else return 0;
    }


    public static void readSignalsFromFile() {
        signals.clear();
        signalsToBuy.clear();

        GUILogQueue.add("Reading signal file...");
        FILELogQueue.add("Reading signal file...");

        boolean fileExists = true;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(MainController.SIGNALFILE_PATH + "signal.txt"));
        } catch (FileNotFoundException e) {
            GUILogQueue.add("Signal file could not be found");
            FILELogQueue.add(e.getMessage());
            fileExists = false;
        }
        // lines with <6 digits are interpreted as tickers
        if (fileExists) {
            try {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (line.length() < 6) {
                        signals.add(line);
                    }
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        GUILogQueue.add("Read from signal file: " + signals.toString());
        FILELogQueue.add("Read from signal file: " + signals.toString());

        /*
        MainController.logger.log(LogLevel.GUI,"Signal file read successfully: "+signals.toString());
        MainController.logger.log(LogLevel.FILE,"Signal file read successfully: "+signals.toString());
        */
    }


    public static void writeBalanceFile() {
        GUILogQueue.add("Updating balance file...");
        FILELogQueue.add("Updating balance file...");

        boolean fileExists = true;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(MainController.BALANCEFILE_PATH + "balance_" +
                    MainController.ACCOUNT_ID + ".csv"));
        } catch (FileNotFoundException e) {

            GUILogQueue.add("Balance file not found; creating new one");
            FILELogQueue.add("Balance file not found; creating new one");

            File file = new File(MainController.BALANCEFILE_PATH + "balance_" +
                    MainController.ACCOUNT_ID + ".csv");
            File file2 = new File(MainController.BALANCEFILE_PATH + "balance_" +
                    MainController.ACCOUNT_ID + "_with_dates.csv");
            try {
                if (file.createNewFile())
                {
                    System.out.println("File is created!");
                } else {
                    System.out.println("File already exists.");
                }
                if (file2.createNewFile())
                {
                    System.out.println("File2 is created!");
                } else {
                    System.out.println("File already exists.");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        String line = String.valueOf(MainController.currentNetLiquidationValue);
        System.out.println("Balance hasn't been written. Needs to be implemented.");
        GUILogQueue.add("Balance hasn't been written; needs to be implemented");
        FILELogQueue.add("Balance hasn't been written; needs to be implemented");
    }


    public static void setActive(boolean a) {
        active = a;
    }

    public static boolean getRunning() {
        return running;
    }

    public static void setRunning(boolean r) {
        running = r;
    }

    public static ArrayList<String> getSignals() {
        return signals;
    }

    public static boolean isTransmitFlag() {
        return transmitFlag;
    }

    public static void setTransmitFlag(boolean transmitFlag) {
        TradeWorkflow.transmitFlag = transmitFlag;
    }

    static class ParameterStringBuilder {
        static public String getParamsString(Map<String, String> params)
                throws UnsupportedEncodingException {
            StringBuilder result = new StringBuilder();

            for (Map.Entry<String, String> entry : params.entrySet()) {
                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                result.append("&");
            }

            String resultString = result.toString();
            return resultString.length() > 0
                    ? resultString.substring(0, resultString.length() - 1)
                    : resultString;
        }
    }

    public static void clearCurrentlyHeldPositions() {
        currentlyHeldPositions.clear();
    }

    public static List<Position> getCurrentlyHeldPositions() {
        return currentlyHeldPositions;
    }

    public static String getAccountCurrency() {
        return accountCurrency;
    }

    public static HashMap<Integer, Double> getOrderIdToRemainingToBeFilled() {
        return orderIdToRemainingToBeFilled;
    }

    public static void setAccountCurrency(String value) {
        accountCurrency = value;
    }

    public static void addToGUIQueue(String s) {
        GUILogQueue.add(s);
    }

    public static void addToFILEQueue(String s) {
        FILELogQueue.add(s);
    }

    public static HashMap<Integer, String> getOrderStatuses() {
        return orderStatuses;
    }

    public static HashMap<Integer, String> getOrderIdToSmybol() {
        return orderIdToSmybol;
    }

    public static void setMKTorders(boolean MKTorders) {
        TradeWorkflow.MKTorders = MKTorders;
    }

}
