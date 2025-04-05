package org.example.huinia.controllers;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import javafx.scene.DepthTest;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import javafx.scene.AmbientLight;
import javafx.scene.paint.Color;

// Внутри метода initialize() после создания contentGroup:


import javafx.scene.shape.Shape;
import javafx.scene.shape.Rectangle; // для клипа

import java.util.ArrayList;
import java.util.List;

public class ShapeController {

    @FXML private AnchorPane canvas3D;
    @FXML private Button btnDeleteAll, btnDeleteLast, btnDeleteSelected;
    @FXML private TextField txtBaseX, txtBaseY, txtWidth, txtDepth, txtHeight;

    // Группа, содержащая все объекты (сетка, фигуры, подсказки), к которой применяются повороты
    private Group contentGroup;

    private String selectedShape = "";

    private List<Node> createdShapes = new ArrayList<>();
    private Node lastShape = null;
    private Node selectedNode = null;

    private enum CreationState { WAITING, SETTING_BASE, SETTING_HEIGHT }
    private CreationState creationState = CreationState.WAITING;

    private double baseInitX, baseInitY;
    private Rectangle baseProjection;

    private double heightInitLocal;
    private double shapeHeight;
    private Line heightIndicator;

    private double lastMouseX, lastMouseY;
    private Rotate rotateX = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
    private Rotate rotateY = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);

    private Group gridGroup;

    @FXML
    public void initialize() {
        // Включаем depth test для корректного 3D‑отображения
        canvas3D.setDepthTest(DepthTest.ENABLE);

        // Ограничиваем область canvas3D с помощью клипа, чтобы после трансформаций контент не выходил за пределы
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(canvas3D.widthProperty());
        clip.heightProperty().bind(canvas3D.heightProperty());
        canvas3D.setClip(clip);

        // Создаём группу для содержимого, к которой будем применять повороты
        contentGroup = new Group();
        canvas3D.getChildren().add(contentGroup);

        // Создаём сетку и добавляем её в contentGroup
        gridGroup = createFullGrid(canvas3D.getPrefWidth(), canvas3D.getPrefHeight(), 50);
        gridGroup.setMouseTransparent(true); // Сетка не перехватывает события мыши
        contentGroup.getChildren().add(0, gridGroup);
        gridGroup.translateXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        gridGroup.translateYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));

        // Настраиваем точки вращения для поворотов
        rotateX.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateX.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        rotateY.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateY.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        contentGroup.getTransforms().addAll(rotateX, rotateY);

        // Регистрируем обработчики событий через addEventFilter на canvas3D
//        PointLight pointLight = new PointLight(Color.rgb(255, 255, 255, 0.5)); // Свет с пониженной яркостью
//        pointLight.setTranslateX(100);   // смещение по оси X
//        pointLight.setTranslateY(-50);   // смещение по оси Y
//        pointLight.setTranslateZ(-300);  // смещение по оси Z
//        contentGroup.getChildren().add(pointLight);

        // Обработка нажатия мыши
        canvas3D.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (creationState == CreationState.WAITING) {
                if (event.getButton() == MouseButton.SECONDARY) {
                    // Начало вращения сцены
                    lastMouseX = event.getSceneX();
                    lastMouseY = event.getSceneY();
                } else if (event.getButton() == MouseButton.PRIMARY) {
                    // Выбор фигуры или снятие выбора
                    Node picked = event.getPickResult().getIntersectedNode();
                    if (picked != null && createdShapes.contains(picked)) {
                        selectShape(picked);
                        event.consume();
                    } else {
                        deselectShape();
                    }
                }
            }
        });

        // Обработка перетаскивания мыши
        canvas3D.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
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

        // Обработка отпускания мыши
        canvas3D.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (creationState == CreationState.SETTING_BASE && event.getButton() == MouseButton.PRIMARY) {
                Point2D localPoint = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
                creationState = CreationState.SETTING_HEIGHT;
                heightInitLocal = localPoint.getY();
                System.out.println("Основание зафиксировано. Теперь задайте высоту (ось Z).");
                createHeightIndicator(heightInitLocal);
            } else if (creationState == CreationState.SETTING_HEIGHT && event.getButton() == MouseButton.PRIMARY) {
                Point2D localPoint = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
                shapeHeight = heightInitLocal - localPoint.getY(); // Вычисляем высоту по разнице Y
                System.out.println("Высота установлена: " + shapeHeight + ". Создаем фигуру.");
                create3DShapeFromBaseAndHeight();
                contentGroup.getChildren().remove(baseProjection);
                contentGroup.getChildren().remove(heightIndicator);
                resetCreation();
            }
        });

        // Обработка двойного клика мыши
        canvas3D.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY &&
                    event.getClickCount() == 2 &&
                    creationState == CreationState.WAITING &&
                    !selectedShape.isEmpty()) {
                Point2D localPoint = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
                baseInitX = localPoint.getX();
                baseInitY = localPoint.getY();
                creationState = CreationState.SETTING_BASE;
                System.out.println("Начало создания основания зафиксировано: (" + baseInitX + ", " + baseInitY + ").");
                createBaseProjection(baseInitX, baseInitY);
            }
        });

        // Обработка скроллинга мыши
        canvas3D.addEventFilter(ScrollEvent.SCROLL, event -> {
            double delta = event.getDeltaY();
            double scale = canvas3D.getScaleX();
            double newScale = scale + delta / 500;
            if (newScale < 0.1) newScale = 0.1;
            canvas3D.setScaleX(newScale);
            canvas3D.setScaleY(newScale);
            canvas3D.setScaleZ(newScale);
        });

        // Обработка нажатий клавиш (перемещение выбранной фигуры)
        canvas3D.setFocusTraversable(true);
        canvas3D.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (selectedNode != null) {
                Rotate rx = (Rotate) selectedNode.getProperties().get("rotateX");
                Rotate ry = (Rotate) selectedNode.getProperties().get("rotateY");
                double angleStep = 5;
                if (rx == null || ry == null) return;
                if (e.getCode() == KeyCode.UP) {
                    rx.setAngle(rx.getAngle() - angleStep);
                } else if (e.getCode() == KeyCode.DOWN) {
                    rx.setAngle(rx.getAngle() + angleStep);
                } else if (e.getCode() == KeyCode.LEFT) {
                    ry.setAngle(ry.getAngle() - angleStep);
                } else if (e.getCode() == KeyCode.RIGHT) {
                    ry.setAngle(ry.getAngle() + angleStep);
                }
            }
        });

        // Кнопки управления фигурами
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
        contentGroup.getChildren().add(baseProjection);
    }

    private void updateBaseProjection(MouseEvent event) {
        Point2D localPoint = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
        double currentX = localPoint.getX();
        double currentY = localPoint.getY();
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
        Point2D localPoint = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
        double currentY = localPoint.getY();
        double currentHeight = Math.abs(currentY - heightInitLocal);
        if (heightIndicator != null) {
            heightIndicator.setEndY(heightInitLocal + (currentY >= heightInitLocal ? currentHeight : -currentHeight));
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
        contentGroup.getChildren().add(heightIndicator);
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
                    cylinder.setTranslateZ(Math.abs(shapeHeight) / 2);
                    cylinder.setMaterial(new PhongMaterial(Color.ORANGE));
                    shape = cylinder;
                } else {
                    MeshView ellipseCylinder = createEllipticalCylinder((float)(baseWidth / 2), (float)(baseDepth / 2), (float)Math.abs(shapeHeight), 36, Color.ORANGE);
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
            finalShape.setDepthTest(DepthTest.ENABLE);

            Rotate shapeRotateX = new Rotate(0, Rotate.X_AXIS);
            Rotate shapeRotateY = new Rotate(0, Rotate.Y_AXIS);
            finalShape.getTransforms().addAll(shapeRotateX, shapeRotateY);
            finalShape.getProperties().put("rotateX", shapeRotateX);
            finalShape.getProperties().put("rotateY", shapeRotateY);

            final double[] dragStart = new double[2];
            final double[] initTranslate = new double[2];

            finalShape.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    selectShape(finalShape);
                    if (e.isControlDown()) {
                        dragStart[0] = e.getSceneX();
                        dragStart[1] = finalShape.getRotate();
                    } else {
                        Point2D localPoint = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                        dragStart[0] = localPoint.getX();
                        dragStart[1] = localPoint.getY();
                        initTranslate[0] = finalShape.getTranslateX();
                        initTranslate[1] = finalShape.getTranslateY();
                    }
                    e.consume();
                }
            });

            finalShape.setOnMouseDragged(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    if (e.isControlDown()) {
                        double dx = e.getSceneX() - dragStart[0];
                        finalShape.setRotate(dragStart[1] + dx);
                    } else {
                        Point2D localPoint = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                        double dx = localPoint.getX() - dragStart[0];
                        double dy = localPoint.getY() - dragStart[1];
                        finalShape.setTranslateX(initTranslate[0] + dx);
                        finalShape.setTranslateY(initTranslate[1] + dy);
                    }
                    e.consume();
                }
            });

            finalShape.setOnScroll(e -> {
                double delta = e.getDeltaY();
                double scale = finalShape.getScaleX() + delta / 500;
                if (scale < 0.1) scale = 0.1;
                finalShape.setScaleX(scale);
                finalShape.setScaleY(scale);
                finalShape.setScaleZ(scale);
                e.consume();
            });

            createdShapes.add(finalShape);
            lastShape = finalShape;
            contentGroup.getChildren().add(finalShape);
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
            mesh.getFaces().addAll(bottomCurrent, 0, bottomNext, 0, topCurrent, 0);
            mesh.getFaces().addAll(bottomNext, 0, topNext, 0, topCurrent, 0);
        }
        int bottomCenterIndex = mesh.getPoints().size() / 3;
        mesh.getPoints().addAll(0f, 0f, 0f);
        int topCenterIndex = bottomCenterIndex + 1;
        mesh.getPoints().addAll(0f, 0f, height);
        for (int i = 0; i < divisions; i++) {
            int next = (i + 1) % divisions;
            mesh.getFaces().addAll(bottomCenterIndex, 0, next, 0, i, 0);
        }
        for (int i = 0; i < divisions; i++) {
            int next = (i + 1) % divisions;
            mesh.getFaces().addAll(topCenterIndex, 0, i + divisions, 0, next + divisions, 0);
        }
        MeshView ellipticalCylinder = new MeshView(mesh);
        ellipticalCylinder.setMaterial(new PhongMaterial(color));
        ellipticalCylinder.setDepthTest(DepthTest.ENABLE);
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
                mesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                mesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
            }
        }
        MeshView ellipsoidalSphere = new MeshView(mesh);
        ellipsoidalSphere.setMaterial(new PhongMaterial(color));
        ellipsoidalSphere.setDepthTest(DepthTest.ENABLE);
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
                0, 0, 1, 0, 2, 0,
                0, 0, 2, 0, 3, 0,
                0, 0, 3, 0, 4, 0,
                0, 0, 4, 0, 1, 0
        );
        mesh.getFaces().addAll(
                1, 0, 2, 0, 3, 0,
                1, 0, 3, 0, 4, 0
        );
        MeshView pyramid = new MeshView(mesh);
        pyramid.setMaterial(new PhongMaterial(color));
        pyramid.setDepthTest(DepthTest.ENABLE);
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
            contentGroup.getChildren().remove(n);
        }
        createdShapes.clear();
        lastShape = null;
        deselectShape();
    }

    private void deleteLastShape() {
        if (lastShape != null) {
            contentGroup.getChildren().remove(lastShape);
            createdShapes.remove(lastShape);
            lastShape = createdShapes.isEmpty() ? null : createdShapes.get(createdShapes.size() - 1);
        }
    }

    private void deleteSelectedShape() {
        if (selectedNode != null) {
            contentGroup.getChildren().remove(selectedNode);
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
            contentGroup.getChildren().remove(baseProjection);
        } catch (NumberFormatException ex) {
            System.out.println("Неверный формат входных данных");
        }
    }
}
