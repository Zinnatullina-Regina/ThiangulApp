package org.example.huinia.controllers;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Line;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.List;

public class ShapeController {

    @FXML private AnchorPane canvas3D;
    @FXML private Button btnDeleteAll, btnDeleteLast, btnDeleteSelected;
    @FXML private TextField txtBaseX, txtBaseY, txtWidth, txtDepth, txtHeight;

    private String selectedShape = "";

    private List<Node> createdShapes = new ArrayList<>();
    private Node lastShape = null;
    private Node selectedNode = null;

    private enum CreationState { WAITING, SETTING_BASE, SETTING_HEIGHT }
    private CreationState creationState = CreationState.WAITING;

    private double baseInitX, baseInitY;
    private Rectangle baseProjection;

    private double heightInitScreen;
    private double shapeHeight;
    private Line heightIndicator;

    private double lastMouseX, lastMouseY;
    private Rotate rotateX = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
    private Rotate rotateY = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);

    private Group gridGroup;

    @FXML
    public void initialize() {
        gridGroup = createFullGrid(canvas3D.getPrefWidth(), canvas3D.getPrefHeight(), 50);
        canvas3D.getChildren().add(0, gridGroup);
        gridGroup.translateXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        gridGroup.translateYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));

        rotateX.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateX.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        rotateY.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateY.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        canvas3D.getTransforms().addAll(rotateX, rotateY);

        canvas3D.setOnMousePressed(event -> {
            if (creationState == CreationState.WAITING && event.getButton() == MouseButton.SECONDARY) {
                lastMouseX = event.getSceneX();
                lastMouseY = event.getSceneY();
            }
        });
        canvas3D.setOnMouseDragged(event -> {
            if (creationState == CreationState.WAITING && event.getButton() == MouseButton.SECONDARY) {
                double deltaX = event.getSceneX() - lastMouseX;
                double deltaY = event.getSceneY() - lastMouseY;
                rotateX.setAngle(rotateX.getAngle() - deltaY);
                rotateY.setAngle(rotateY.getAngle() + deltaX);
                lastMouseX = event.getSceneX();
                lastMouseY = event.getSceneY();
            } else if (creationState == CreationState.SETTING_BASE && event.getButton() == MouseButton.PRIMARY) {
                updateBaseProjection(event);
            } else if (creationState == CreationState.SETTING_HEIGHT && event.getButton() == MouseButton.PRIMARY) {
                updateHeightIndicator(event);
            }
        });
        canvas3D.setOnMouseReleased(event -> {
            if (creationState == CreationState.SETTING_BASE && event.getButton() == MouseButton.PRIMARY) {
                creationState = CreationState.SETTING_HEIGHT;
                heightInitScreen = event.getY();
                System.out.println("Основание зафиксировано. Теперь задайте высоту (ось Z).");
                createHeightIndicator(heightInitScreen);
            } else if (creationState == CreationState.SETTING_HEIGHT && event.getButton() == MouseButton.PRIMARY) {
                shapeHeight = heightInitScreen - event.getY(); // инвертированное задание
                System.out.println("Высота установлена: " + shapeHeight + ". Создаем фигуру.");
                create3DShapeFromBaseAndHeight();
                canvas3D.getChildren().remove(baseProjection);
                canvas3D.getChildren().remove(heightIndicator);
                resetCreation();
            }
        });
        canvas3D.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY &&
                    event.getClickCount() == 2 &&
                    creationState == CreationState.WAITING &&
                    !selectedShape.isEmpty()) {
                Node target = event.getPickResult().getIntersectedNode();
                if (target != null && createdShapes.contains(target)) {
                    baseInitX = target.getTranslateX();
                    baseInitY = target.getTranslateY();
                } else {
                    baseInitX = event.getX();
                    baseInitY = event.getY();
                }
                creationState = CreationState.SETTING_BASE;
                System.out.println("Начало создания основания зафиксировано: (" + baseInitX + ", " + baseInitY + ").");
                createBaseProjection(baseInitX, baseInitY);
            }
        });
        canvas3D.setOnScroll((ScrollEvent event) -> {
            double delta = event.getDeltaY();
            double scale = canvas3D.getScaleX();
            double newScale = scale + delta / 500;
            if (newScale < 0.1) newScale = 0.1;
            canvas3D.setScaleX(newScale);
            canvas3D.setScaleY(newScale);
            canvas3D.setScaleZ(newScale);
        });
        canvas3D.setOnMousePressed(event -> {
            if (creationState == CreationState.WAITING && event.getButton() == MouseButton.PRIMARY) {
                Node picked = event.getPickResult().getIntersectedNode();
                if (picked != null && createdShapes.contains(picked)) {
                    selectShape(picked);
                    event.consume();
                } else {
                    deselectShape();
                }
            }
        });

        btnDeleteAll.setOnAction(e -> deleteAllShapes());
        btnDeleteLast.setOnAction(e -> deleteLastShape());
        btnDeleteSelected.setOnAction(e -> deleteSelectedShape());
    }

    private Group createFullGrid(double width, double height, double step) {
        Group group = new Group();
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        for (double x = -halfWidth; x <= halfWidth; x += step) {
            Line line = new Line(x, -halfHeight, x, halfHeight);
            line.setStroke(Color.LIGHTGRAY);
            group.getChildren().add(line);
            if (((int)x) % ((int)(step * 2)) == 0) {
                Label label = new Label(String.valueOf((int)x));
                label.setFont(new Font(10));
                label.setTextFill(Color.RED);
                label.setLayoutX(x);
                label.setLayoutY(0);
                group.getChildren().add(label);
            }
        }
        for (double y = -halfHeight; y <= halfHeight; y += step) {
            Line line = new Line(-halfWidth, y, halfWidth, y);
            line.setStroke(Color.LIGHTGRAY);
            group.getChildren().add(line);
            if (((int)y) % ((int)(step * 2)) == 0) {
                Label label = new Label(String.valueOf((int)y));
                label.setFont(new Font(10));
                label.setTextFill(Color.GREEN);
                label.setLayoutX(0);
                label.setLayoutY(y);
                group.getChildren().add(label);
            }
        }
        Line axisX = new Line(-halfWidth, 0, halfWidth, 0);
        axisX.setStroke(Color.RED);
        axisX.setStrokeWidth(2);
        group.getChildren().add(axisX);
        Line axisY = new Line(0, -halfHeight, 0, halfHeight);
        axisY.setStroke(Color.GREEN);
        axisY.setStrokeWidth(2);
        group.getChildren().add(axisY);
        Line axisZ = new Line(-halfWidth + 20, 0, -halfWidth + 20, -halfHeight / 2);
        axisZ.setStroke(Color.BLUE);
        axisZ.setStrokeWidth(2);
        group.getChildren().add(axisZ);
        Label labelZ = new Label("Z ↑");
        labelZ.setFont(new Font(12));
        labelZ.setTextFill(Color.BLUE);
        labelZ.setLayoutX(-halfWidth + 10);
        labelZ.setLayoutY(-halfHeight / 2 - 15);
        group.getChildren().add(labelZ);
        return group;
    }

    private void createBaseProjection(double x, double y) {
        baseProjection = new Rectangle(x, y, 0, 0);
        baseProjection.setFill(Color.color(0, 0, 1, 0.3));
        baseProjection.setStroke(Color.BLUE);
        canvas3D.getChildren().add(baseProjection);
    }

    private void updateBaseProjection(MouseEvent event) {
        double currentX = event.getX();
        double currentY = event.getY();
        double newX = Math.min(baseInitX, currentX);
        double newY = Math.min(baseInitY, currentY);
        double width = Math.abs(currentX - baseInitX);
        double height = Math.abs(currentY - baseInitY);
        baseProjection.setX(newX);
        baseProjection.setY(newY);
        baseProjection.setWidth(width);
        baseProjection.setHeight(height);
    }

    private void updateHeightIndicator(MouseEvent event) {
        double currentY = event.getY();
        double currentHeight = Math.abs(currentY - heightInitScreen);
        if (heightIndicator != null) {
            heightIndicator.setEndY(heightInitScreen + (currentY >= heightInitScreen ? currentHeight : -currentHeight));
        }
    }

    private void createHeightIndicator(double startY) {
        heightIndicator = new Line();
        double centerX = baseProjection.getX() + baseProjection.getWidth() / 2;
        heightIndicator.setStartX(centerX);
        heightIndicator.setStartY(startY);
        heightIndicator.setEndX(centerX);
        heightIndicator.setEndY(startY);
        heightIndicator.setStroke(Color.RED);
        heightIndicator.setStrokeWidth(2);
        canvas3D.getChildren().add(heightIndicator);
    }

    private void create3DShapeFromBaseAndHeight() {
        double baseX = baseProjection.getX();
        double baseY = baseProjection.getY();
        double baseWidth = baseProjection.getWidth();
        double baseDepth = baseProjection.getHeight();
        double centerX = baseX + baseWidth / 2;
        double centerY = baseY + baseDepth / 2;

        Node shape = null;
        switch (selectedShape) {
            case "SPHERE": {
                if (Math.abs(baseWidth - baseDepth) < 1e-3) {
                    double diameter = (baseWidth + baseDepth) / 2;
                    Sphere sphere = new Sphere(diameter / 2);
                    sphere.setTranslateX(centerX);
                    sphere.setTranslateY(centerY);
                    sphere.setTranslateZ(diameter / 2);
                    sphere.setMaterial(new PhongMaterial(Color.BLUE));
                    shape = sphere;
                } else {
                    float radiusX = (float)(baseWidth / 2);
                    float radiusY = (float)(baseDepth / 2);
                    float radiusZ = (float)(Math.abs(shapeHeight) / 2);
                    MeshView ellipsoidalSphere = createEllipsoidalSphere(radiusX, radiusY, radiusZ, 36, Color.BLUE);
                    ellipsoidalSphere.setTranslateX(centerX);
                    ellipsoidalSphere.setTranslateY(centerY);
                    ellipsoidalSphere.setTranslateZ(radiusZ);
                    shape = ellipsoidalSphere;
                }
                break;
            }
            case "CUBE": {
                Box cube = new Box(baseWidth, baseDepth, Math.abs(shapeHeight));
                cube.setTranslateX(centerX);
                cube.setTranslateY(centerY);
                cube.setTranslateZ(Math.abs(shapeHeight) / 2);
                cube.setMaterial(new PhongMaterial(Color.RED));
                shape = cube;
                break;
            }
            case "PYRAMID": {
                MeshView pyramid = createPyramid((float) baseWidth, (float) baseDepth, (float) Math.abs(shapeHeight), Color.GREEN);
                pyramid.setTranslateX(centerX);
                pyramid.setTranslateY(centerY);
                pyramid.setTranslateZ(Math.abs(shapeHeight) / 2);
                shape = pyramid;
                break;
            }
            case "CYLINDER": {
                if (Math.abs(baseWidth - baseDepth) < 1e-3) {
                    double radius = baseWidth / 2;
                    Cylinder cylinder = new Cylinder(radius, Math.abs(shapeHeight));
                    cylinder.setTranslateX(centerX);
                    cylinder.setTranslateY(centerY);
                    // Нижняя грань цилиндра на плоскости XY (Z = 0)
                    cylinder.setTranslateZ(Math.abs(shapeHeight) / 2);
                    cylinder.setMaterial(new PhongMaterial(Color.ORANGE));
                    shape = cylinder;
                } else {
                    MeshView ellipseCylinder = createEllipticalCylinder((float)(baseWidth/2), (float)(baseDepth/2), (float)Math.abs(shapeHeight), 36, Color.ORANGE);
                    ellipseCylinder.setTranslateX(centerX);
                    ellipseCylinder.setTranslateY(centerY);
                    ellipseCylinder.setTranslateZ(Math.abs(shapeHeight) / 2);
                    shape = ellipseCylinder;
                }
                break;
            }
            default:
                System.out.println("Тип фигуры не выбран");
        }
        if (shape != null) {
            final Node finalShape = shape;
            finalShape.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    selectShape(finalShape);
                    e.consume();
                }
            });
            createdShapes.add(finalShape);
            lastShape = finalShape;
            canvas3D.getChildren().add(finalShape);
        }
    }

    private MeshView createEllipticalCylinder(float radiusX, float radiusY, float height, int divisions, Color color) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        for (int i = 0; i < divisions; i++) {
            double angle = 2 * Math.PI * i / divisions;
            float x = (float)(radiusX * Math.cos(angle));
            float y = (float)(radiusY * Math.sin(angle));
            mesh.getPoints().addAll(x, y, 0);
        }
        for (int i = 0; i < divisions; i++) {
            double angle = 2 * Math.PI * i / divisions;
            float x = (float)(radiusX * Math.cos(angle));
            float y = (float)(radiusY * Math.sin(angle));
            mesh.getPoints().addAll(x, y, height);
        }
        for (int i = 0; i < divisions; i++) {
            int next = (i + 1) % divisions;
            int bottomCurrent = i;
            int bottomNext = next;
            int topCurrent = i + divisions;
            int topNext = next + divisions;
            mesh.getFaces().addAll(bottomCurrent,0, bottomNext,0, topCurrent,0);
            mesh.getFaces().addAll(bottomNext,0, topNext,0, topCurrent,0);
        }
        int bottomCenterIndex = mesh.getPoints().size() / 3;
        mesh.getPoints().addAll(0f, 0f, 0f);
        int topCenterIndex = bottomCenterIndex + 1;
        mesh.getPoints().addAll(0f, 0f, height);
        for (int i = 0; i < divisions; i++) {
            int next = (i + 1) % divisions;
            mesh.getFaces().addAll(bottomCenterIndex,0, next,0, i,0);
        }
        for (int i = 0; i < divisions; i++) {
            int next = (i + 1) % divisions;
            mesh.getFaces().addAll(topCenterIndex,0, i + divisions,0, next + divisions,0);
        }
        MeshView ellipticalCylinder = new MeshView(mesh);
        ellipticalCylinder.setMaterial(new PhongMaterial(color));
        return ellipticalCylinder;
    }

    private MeshView createEllipsoidalSphere(float radiusX, float radiusY, float radiusZ, int divisions, Color color) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        for (int i = 0; i <= divisions; i++) {
            float u = (float)(Math.PI * i / divisions);
            for (int j = 0; j <= divisions; j++) {
                float v = (float)(2 * Math.PI * j / divisions);
                float x = (float)(radiusX * Math.sin(u) * Math.cos(v));
                float y = (float)(radiusY * Math.sin(u) * Math.sin(v));
                float z = (float)(radiusZ * Math.cos(u));
                mesh.getPoints().addAll(x, y, z);
            }
        }
        for (int i = 0; i < divisions; i++) {
            for (int j = 0; j < divisions; j++) {
                int p0 = i * (divisions + 1) + j;
                int p1 = p0 + 1;
                int p2 = p0 + (divisions + 1);
                int p3 = p2 + 1;
                mesh.getFaces().addAll(p0,0, p2,0, p1,0);
                mesh.getFaces().addAll(p1,0, p2,0, p3,0);
            }
        }
        MeshView ellipsoidalSphere = new MeshView(mesh);
        ellipsoidalSphere.setMaterial(new PhongMaterial(color));
        return ellipsoidalSphere;
    }

    private MeshView createPyramid(float baseWidth, float baseDepth, float height, Color color) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        float halfW = baseWidth / 2;
        float halfD = baseDepth / 2;
        mesh.getPoints().addAll(0, height, 0);
        mesh.getPoints().addAll(
                -halfW, 0, -halfD,
                halfW, 0, -halfD,
                halfW, 0,  halfD,
                -halfW, 0,  halfD
        );
        mesh.getFaces().addAll(
                0,0, 1,0, 2,0,
                0,0, 2,0, 3,0,
                0,0, 3,0, 4,0,
                0,0, 4,0, 1,0
        );
        mesh.getFaces().addAll(
                1,0, 2,0, 3,0,
                1,0, 3,0, 4,0
        );
        MeshView pyramid = new MeshView(mesh);
        pyramid.setMaterial(new PhongMaterial(color));
        return pyramid;
    }

    private void resetCreation() {
        creationState = CreationState.WAITING;
    }

    private void selectShape(Node shape) {
        if (selectedNode != null) {
            selectedNode.setStyle("");
        }
        selectedNode = shape;
        shape.setStyle("-fx-effect: dropshadow(gaussian, yellow, 10, 0.5, 0, 0);");
    }

    private void deselectShape() {
        if (selectedNode != null) {
            selectedNode.setStyle("");
            selectedNode = null;
        }
    }

    private void deleteAllShapes() {
        for (Node n : createdShapes) {
            canvas3D.getChildren().remove(n);
        }
        createdShapes.clear();
        lastShape = null;
        deselectShape();
    }

    private void deleteLastShape() {
        if (lastShape != null) {
            canvas3D.getChildren().remove(lastShape);
            createdShapes.remove(lastShape);
            lastShape = createdShapes.isEmpty() ? null : createdShapes.get(createdShapes.size() - 1);
        }
    }

    private void deleteSelectedShape() {
        if (selectedNode != null) {
            canvas3D.getChildren().remove(selectedNode);
            createdShapes.remove(selectedNode);
            selectedNode = null;
        }
    }

    @FXML
    public void selectSphere() {
        selectedShape = "SPHERE";
        resetCreation();
        System.out.println("Выбрана фигура: Сфера / Эллипсоид");
    }

    @FXML
    public void selectCube() {
        selectedShape = "CUBE";
        resetCreation();
        System.out.println("Выбрана фигура: Куб");
    }

    @FXML
    public void selectPyramid() {
        selectedShape = "PYRAMID";
        resetCreation();
        System.out.println("Выбрана фигура: Пирамида");
    }

    @FXML
    public void selectCylinder() {
        selectedShape = "CYLINDER";
        resetCreation();
        System.out.println("Выбрана фигура: Цилиндр / Эллиптический цилиндр");
    }

    @FXML
    public void createShapeFromInput() {
        try {
            // Интерпретируем введенные координаты как отсчитанные от центра (0,0)
            double inputX = Double.parseDouble(txtBaseX.getText());
            double inputY = Double.parseDouble(txtBaseY.getText());
            double width = Double.parseDouble(txtWidth.getText());
            double depth = Double.parseDouble(txtDepth.getText());
            double h = Double.parseDouble(txtHeight.getText());
            double centerOffsetX = canvas3D.getWidth() / 2;
            double centerOffsetY = canvas3D.getHeight() / 2;
            double x = inputX + centerOffsetX;
            double y = inputY + centerOffsetY;
            baseInitX = x;
            baseInitY = y;
            createBaseProjection(x, y);
            baseProjection.setWidth(width);
            baseProjection.setHeight(depth);
            shapeHeight = h;
            create3DShapeFromBaseAndHeight();
            canvas3D.getChildren().remove(baseProjection);
        } catch (NumberFormatException ex) {
            System.out.println("Неверный формат входных данных");
        }
    }
}
