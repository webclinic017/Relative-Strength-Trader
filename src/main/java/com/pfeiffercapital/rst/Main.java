package com.pfeiffercapital.rst;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;


@SpringBootApplication
public class Main extends Application {

    private Stage primaryStage;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        mainWindow();

    }

    public void mainWindow() throws IOException {
        //URL url = new File("src/main/resources/main.fxml").toURL();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));

        Parent root = loader.load();

        MainController controller = loader.getController();
        controller.setMain(this);
        controller.initialize();

        primaryStage.setTitle("RST v0.0.5");
        primaryStage.setScene(new Scene(root,700,530));
        primaryStage.show();
    }

}
