package org.example.huinia;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.PerspectiveCamera;
import javafx.stage.Stage;


import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.PerspectiveCamera;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/views/ShapeDrawerUI.fxml"));
        Scene scene = new Scene(root, 900, 600, true);

        // Настройка перспективной камеры
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.05);
        camera.setFarClip(100000.0);
        camera.setTranslateZ(-1450);
//        camera.setTranslateY(-1500);
// смещение камеры по оси Z (подберите значение по необходимости)
        camera.setTranslateX(scene.getWidth() / 2 );
        camera.setTranslateY(scene.getHeight() / 2 + 100);
        scene.setCamera(camera);

        stage.setTitle("3D Shape Drawer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

//public class Main extends Application {
//    @Override
//    public void start(Stage stage) throws Exception {
//        Parent root = FXMLLoader.load(getClass().getResource("/views/ShapeDrawerUI.fxml"));
//        Scene scene = new Scene(root, 600, 600, true);
//
//        // Устанавливаем перспективную камеру для корректного 3D‑отображения
//        PerspectiveCamera camera = new PerspectiveCamera(true);
//        camera.setNearClip(0.01);
//        camera.setFarClip(100000.0);
//        camera.setTranslateX(scene.getWidth() / 2);
//        camera.setTranslateY(scene.getHeight() / 2);
//        camera.setTranslateZ(-2000); // смещение камеры по оси Z (при необходимости подберите значение)
//        scene.setCamera(camera);
//
//        stage.setTitle("3D Shape Drawer");
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    public static void main(String[] args) {
//        launch(args);
//    }
//}
