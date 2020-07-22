package com.pfeiffercapital.rst;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.Set;


@SpringBootApplication
public class Main extends Application {

    private Stage primaryStage;
    FXMLLoader loader;
    MainController controller;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        mainWindow();
        if(MainController.TRADE_LIVE_ON_APP_START){
            try {
                Thread.sleep(1000);
                controller.buttonConnectTWSClick();
                Thread.sleep(1000);
                controller.buttonTradeLiveClick();
                Thread.sleep(1000);
                controller.buttonRequestUpdateClick();
                controller.updateUI();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        primaryStage.setOnCloseRequest(we -> {
            primaryStage.close();
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            System.out.println("Stage is closing. Threads:");
            System.out.println("KILLING NOW: " + Thread.currentThread().getName());
            for(Thread t : threadSet){
               if(t.isAlive())
                   System.out.println("ALIVE: " + t.getName() );
               else
                   System.out.println("DEAD: " + t.getName() );
               if(t.getName().equals("main"))
                    t.stop();
            }
        });
    }

    public void mainWindow() throws IOException {
        //URL url = new File("src/main/resources/main.fxml").toURL();
        loader = new FXMLLoader(getClass().getResource("/main.fxml"));

        Parent root = loader.load();

        controller = loader.getController();
        controller.setMain(this);
        controller.initialize();
        controller.setMainController(controller);

        primaryStage.setTitle("RST v0.0.9");
        primaryStage.setScene(new Scene(root,700,530));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

}
