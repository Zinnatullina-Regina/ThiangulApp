package org.example.huinia.controllers;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.ext.quickhull3d.Point3d;
import eu.mihosoft.vvecmath.Transform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
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
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.scene.DepthTest;
import org.example.huinia.converters.MeshToCSGConverter;
import org.example.huinia.converters.CSGToMeshViewConverter;
import org.example.huinia.Delaunay.Delaunay3D;
import org.example.huinia.Delaunay.DelaunayVisualizer;
import javafx.event.ActionEvent;

import java.util.*;

import java.util.ArrayList;
import java.util.List;

import static org.example.huinia.Delaunay.Delaunay3D.buildSurfaceMesh;

public class ShapeController {


    @FXML private Button btnTriangulate;
    @FXML private AnchorPane canvas3D;
    @FXML private Button btnDeleteAll, btnDeleteLast, btnDeleteSelected;
    @FXML private TextField txtBaseX, txtBaseY, txtWidth, txtDepth, txtHeight;

    private Group contentGroup;
    private Group gridGroup;

    private static final String SPHERE           = "SPHERE";
    private static final String CUBE             = "CUBE";
    private static final String HOLE_ELLIPTICAL  = "HOLE_ELLIPTICAL";
    private static final String HOLE_RECTANGULAR = "HOLE_RECTANGULAR";

    private String selectedShape = "";
    private List<Node> createdShapes = new ArrayList<>();
    private Node lastShape = null;
    private Node selectedNode = null;

    private enum CreationState { WAITING, SETTING_BASE, SETTING_HEIGHT }
    private CreationState creationState = CreationState.WAITING;

    private final Map<UUID, CSG> currentCSGMap = new HashMap<>();

    private final Map<UUID, List<CSG>> holesMap  = new HashMap<>();

    // для построения «основания»
    private double baseInitX, baseInitY;
    private Rectangle baseProjection;

    // для задания «высоты»
    private double heightInitLocal;
    private double shapeHeight;
    private Line heightIndicator;

    // для вращения сцены правой кнопкой
    private double lastMouseX, lastMouseY;
    private Rotate rotateX = new Rotate(0, 0, 0, 0, Rotate.X_AXIS);
    private Rotate rotateY = new Rotate(0, 0, 0, 0, Rotate.Y_AXIS);
    private boolean canMoveShapes = true;
    int first_hole = 0;
    @FXML
    public void initialize() {
        // Настройка 3D-канваса

        canvas3D.setDepthTest(DepthTest.ENABLE);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(canvas3D.widthProperty());
        clip.heightProperty().bind(canvas3D.heightProperty());
        canvas3D.setClip(clip);

        contentGroup = new Group();
        canvas3D.getChildren().add(contentGroup);

        // Сетка
        gridGroup = createFullGrid(canvas3D.getPrefWidth(), canvas3D.getPrefHeight(), 50);
        gridGroup.setMouseTransparent(true);
        contentGroup.getChildren().add(0, gridGroup);
        gridGroup.translateXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        gridGroup.translateYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));

        // Поворот сцены
        rotateX.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateX.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        rotateY.pivotXProperty().bind(Bindings.divide(canvas3D.widthProperty(), 2));
        rotateY.pivotYProperty().bind(Bindings.divide(canvas3D.heightProperty(), 2));
        contentGroup.getTransforms().addAll(rotateX, rotateY);

        // Обработчики мыши/клавиш
        canvas3D.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        canvas3D.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        canvas3D.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        canvas3D.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        canvas3D.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        canvas3D.setFocusTraversable(true);
        canvas3D.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        // Кнопки удаления
        btnDeleteAll.setOnAction(e -> deleteAllShapes());
        btnDeleteLast.setOnAction(e -> deleteLastShape());
        btnDeleteSelected.setOnAction(e -> deleteSelectedShape());
        System.out.println("initialize() вызван");       // <<-- временный отладочный вывод
        System.out.println("btnTriangulate = " + btnTriangulate);
        btnTriangulate.setOnAction(this::onTriangulate);
    }

    @FXML
    private void onTriangulate(ActionEvent event) {
        System.out.println("onTriangulate() вызван");

        // 1) Объединяем все CSG (включая дырки)
        CSG totalCSG = null;
        for (CSG c : currentCSGMap.values()) {
            totalCSG = (totalCSG == null) ? c : totalCSG.union(c);
        }
        if (totalCSG == null) {
            System.err.println("Нет объектов для триангуляции");
            return;
        }
        System.out.println("Всего CSG-объектов: " + currentCSGMap.size());

        // 2) Конвертируем в MeshView и собираем список вершин
        MeshView unionView = CSGToMeshViewConverter.convert(totalCSG);
        TriangleMesh unionMesh = (TriangleMesh) unionView.getMesh();
        float[] rawPoints = unionMesh.getPoints().toArray(null);
        List<Point3d> points3d = new ArrayList<>(rawPoints.length / 3);
        for (int i = 0; i < rawPoints.length; i += 3) {
            points3d.add(new Point3d(rawPoints[i], rawPoints[i + 1], rawPoints[i + 2]));
        }
        System.out.println("Всего вершин: " + points3d.size());

        // 3) Запускаем 3D-Delaunay
        List<Delaunay3D.Tetrahedron> allTets = Delaunay3D.triangulate(points3d);
        System.out.println("Сгенерировано тетраэдров всего: " + allTets.size());

        // 4) Отфильтровываем тетраэдры, чьи центроиды вне объёма (в дырках или снаружи)
        double eps = 0.05; // радиус «тестового» шарика — подберите под масштаб вашей сцены
        // создаём прототип маленького шарика
        CSG protoSphere = CSG.sphere((float) eps, 8, 8);
        List<Delaunay3D.Tetrahedron> filtered = new ArrayList<>();
        for (Delaunay3D.Tetrahedron t : allTets) {
            // центроид тетраэдра
            Point3d c = new Point3d(
                    (t.a.x + t.b.x + t.c.x + t.d.x) / 4.0,
                    (t.a.y + t.b.y + t.c.y + t.d.y) / 4.0,
                    (t.a.z + t.b.z + t.c.z + t.d.z) / 4.0
            );
            // сдвигаем шарик в эту точку
            CSG tiny = protoSphere.transformed(
                    Transform.unity().translate((float) c.x, (float) c.y, (float) c.z)
            );
            // если пересечение шарика с totalCSG непустое — центроид внутри «твёрдого» объёма
            if (!tiny.intersect(totalCSG).getPolygons().isEmpty()) {
                filtered.add(t);
            }
        }
        System.out.println("Тетраэдров после фильтрации: " + filtered.size());

        // 5) Строим проволочный каркас из оставшихся тетраэдров
        TriangleMesh wireMesh = buildAllTetraMesh(filtered);
        MeshView wireView = new MeshView(wireMesh);
        wireView.setDrawMode(DrawMode.LINE);
        wireView.setCullFace(CullFace.NONE);
        wireView.setMaterial(new PhongMaterial(Color.BLACK));
        wireView.setUserData("wireAll");

        // Удаляем старую визуализацию и добавляем новую
        contentGroup.getChildren().removeIf(n -> "wireAll".equals(n.getUserData()));
        contentGroup.getChildren().add(wireView);
    }

    /**
     * Вспомогательный метод, собирающий из списка тетраэдров один TriangleMesh,
     * в котором каждая грань каждого тетраэдра добавлена к mesh.getFaces().
     */
    private TriangleMesh buildAllTetraMesh(List<Tetrahedron> tets) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);  // фиктивный UV

        Map<Point3d,Integer> idx = new HashMap<>();
        int nextIndex = 0;

        for (Tetrahedron t : tets) {
            Point3d[] P = new Point3d[]{t.a, t.b, t.c, t.d};
            int[] faceIdx = new int[4];

            // регистрируем 4 вершины
            for (int i = 0; i < 4; i++) {
                Point3d p = P[i];
                if (!idx.containsKey(p)) {
                    mesh.getPoints().addAll((float) p.x, (float) p.y, (float) p.z);
                    idx.put(p, nextIndex++);
                }
                faceIdx[i] = idx.get(p);
            }

            // добавляем 4 грани (каждая — треугольник из трёх индексов)
            int[][] faces = {
                    {faceIdx[0], faceIdx[1], faceIdx[2]},
                    {faceIdx[0], faceIdx[1], faceIdx[3]},
                    {faceIdx[0], faceIdx[2], faceIdx[3]},
                    {faceIdx[1], faceIdx[2], faceIdx[3]}
            };
            for (int[] f : faces) {
                mesh.getFaces().addAll(f[0], 0, f[1], 0, f[2], 0);
            }
        }

        return mesh;
    }



    private void onMousePressed(MouseEvent e) {
        if (creationState == CreationState.WAITING) {
            if (e.getButton() == MouseButton.SECONDARY) {
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
            } else if (e.getButton() == MouseButton.PRIMARY) {
                Node picked = e.getPickResult().getIntersectedNode();
                if (picked != null && createdShapes.contains(picked)) {
                    selectShape(picked);
                    e.consume();
                } else {
                    deselectShape();
                }
            }
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (creationState == CreationState.WAITING
                && e.getButton() == MouseButton.SECONDARY
                && canMoveShapes) {

            double dx = e.getSceneX() - lastMouseX;
            double dy = e.getSceneY() - lastMouseY;
            rotateX.setAngle(rotateX.getAngle() - dy);
            rotateY.setAngle(rotateY.getAngle() + dx);
            lastMouseX = e.getSceneX();
            lastMouseY = e.getSceneY();

        } else if (creationState == CreationState.SETTING_BASE
                && e.getButton() == MouseButton.PRIMARY) {

            updateBaseProjection(e);

        } else if (creationState == CreationState.SETTING_HEIGHT
                && e.getButton() == MouseButton.PRIMARY) {

            updateHeightIndicator(e);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (creationState == CreationState.SETTING_BASE
                && e.getButton() == MouseButton.PRIMARY) {

            Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
            creationState = CreationState.SETTING_HEIGHT;
            heightInitLocal = p.getY();
            createHeightIndicator(heightInitLocal);

        } else if (creationState == CreationState.SETTING_HEIGHT
                && e.getButton() == MouseButton.PRIMARY) {

            Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
            shapeHeight = Math.abs(heightInitLocal - p.getY());
            create3DShapeFromBaseAndHeight();
            contentGroup.getChildren().removeAll(baseProjection, heightIndicator);
            resetCreation();
            canMoveShapes = true;
        }
    }

    private void onMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY
                && e.getClickCount() == 2
                && creationState == CreationState.WAITING
                && !selectedShape.isEmpty()) {

            Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
            baseInitX = p.getX();
            baseInitY = p.getY();
            creationState = CreationState.SETTING_BASE;
            createBaseProjection(baseInitX, baseInitY);
            canMoveShapes = false;
        }

    }

    private void onScroll(ScrollEvent e) {
        double delta = e.getDeltaY();
        double scale = canvas3D.getScaleX() + delta / 500;
        scale = Math.max(scale, 0.1);
        canvas3D.setScaleX(scale);
        canvas3D.setScaleY(scale);
        canvas3D.setScaleZ(scale);
    }

    private void onKeyPressed(KeyEvent e) {
        if (selectedNode != null
                && creationState == CreationState.WAITING
                && canMoveShapes) {

            Rotate rx = (Rotate) selectedNode.getProperties().get("rotateX");
            Rotate ry = (Rotate) selectedNode.getProperties().get("rotateY");
            if (rx == null || ry == null) return;

            double step = 5;
            if (e.getCode() == KeyCode.UP)    rx.setAngle(rx.getAngle() - step);
            if (e.getCode() == KeyCode.DOWN)  rx.setAngle(rx.getAngle() + step);
            if (e.getCode() == KeyCode.LEFT)  ry.setAngle(ry.getAngle() - step);
            if (e.getCode() == KeyCode.RIGHT) ry.setAngle(ry.getAngle() + step);
            if (e.getCode() == KeyCode.W)     selectedNode.setTranslateZ(selectedNode.getTranslateZ() + 10);
            if (e.getCode() == KeyCode.S)     selectedNode.setTranslateZ(selectedNode.getTranslateZ() - 10);
        }
    }

    private Group createFullGrid(double width, double height, double step) {
        Group g = new Group();
        double hw = width / 2, hh = height / 2;
        for (double x = -hw; x <= hw; x += step) {
            Line l = new Line(x, -hh, x, hh);
            l.setStroke(Color.LIGHTGRAY);
            g.getChildren().add(l);
            if (((int)x) % ((int)(step * 2)) == 0) {
                Label lbl = new Label(String.valueOf((int)x));
                lbl.setFont(new Font(10));
                lbl.setTextFill(Color.RED);
                lbl.setLayoutX(x);
                lbl.setLayoutY(0);
                g.getChildren().add(lbl);
            }
        }
        for (double y = -hh; y <= hh; y += step) {
            Line l = new Line(-hw, y, hw, y);
            l.setStroke(Color.LIGHTGRAY);
            g.getChildren().add(l);
            if (((int)y) % ((int)(step * 2)) == 0) {
                Label lbl = new Label(String.valueOf((int)y));
                lbl.setFont(new Font(10));
                lbl.setTextFill(Color.GREEN);
                lbl.setLayoutX(0);
                lbl.setLayoutY(y);
                g.getChildren().add(lbl);
            }
        }
        Line axisX = new Line(-hw,0, hw,0); axisX.setStroke(Color.RED); axisX.setStrokeWidth(2); g.getChildren().add(axisX);
        Line axisY = new Line(0,-hh,0, hh); axisY.setStroke(Color.GREEN); axisY.setStrokeWidth(2); g.getChildren().add(axisY);
        Line axisZ = new Line(-hw+20,0, -hw+20, -hh/2); axisZ.setStroke(Color.BLUE); axisZ.setStrokeWidth(2); g.getChildren().add(axisZ);
        Label lblZ = new Label("Z ↑"); lblZ.setFont(new Font(12)); lblZ.setTextFill(Color.BLUE);
        lblZ.setLayoutX(-hw+10); lblZ.setLayoutY(-hh/2 -15); g.getChildren().add(lblZ);
        return g;
    }

    private void createBaseProjection(double x, double y) {
        baseProjection = new Rectangle(x, y, 0, 0);
        baseProjection.setFill(Color.color(0,0,1,0.3));
        baseProjection.setStroke(Color.BLUE);
        contentGroup.getChildren().add(baseProjection);
    }

    private void updateBaseProjection(MouseEvent e) {
        Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = Math.min(baseInitX, p.getX());
        double ny = Math.min(baseInitY, p.getY());
        baseProjection.setX(nx);
        baseProjection.setY(ny);
        baseProjection.setWidth(Math.abs(p.getX() - baseInitX));
        baseProjection.setHeight(Math.abs(p.getY() - baseInitY));
    }

    private void createHeightIndicator(double startY) {
        heightIndicator = new Line();
        double cx = baseProjection.getX() + baseProjection.getWidth()/2;
        heightIndicator.setStartX(cx);
        heightIndicator.setStartY(startY);
        heightIndicator.setEndX(cx);
        heightIndicator.setEndY(startY);
        heightIndicator.setStroke(Color.RED);
        heightIndicator.setStrokeWidth(2);
        contentGroup.getChildren().add(heightIndicator);
    }

    private void updateHeightIndicator(MouseEvent e) {
        Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
        heightIndicator.setEndY(p.getY());
    }

    private void create3DShapeFromBaseAndHeight() {

        double x0 = baseProjection.getX(), y0 = baseProjection.getY();
        double w = baseProjection.getWidth(), d = baseProjection.getHeight();
        double cx = x0 + w/2, cy = y0 + d/2;

        if (HOLE_ELLIPTICAL.equals(selectedShape) || HOLE_RECTANGULAR.equals(selectedShape)) {
            if (selectedNode == null) {
                System.out.println("Выберите основную фигуру для отверстия");
                return;
            }
            double mainH = getMainShapeHeight(selectedNode);
            boolean through = shapeHeight >= mainH;
            Node hole = createHoleShape(w, d, shapeHeight, cx, cy, mainH, through);
            MeshView cut;
            if (first_hole == 0 && (selectedNode instanceof Box)) {
                cut = applyHole(selectedNode, hole);
                first_hole++;
            } else  {
                cut = applyHoleFromCurrent((MeshView) selectedNode, hole);
            }
            if (cut != null) {
                setupDraggable(cut);
                contentGroup.getChildren().remove(selectedNode);
                createdShapes.remove(selectedNode);

                if (!contentGroup.getChildren().contains(cut)) {
                    contentGroup.getChildren().add(cut);
                    createdShapes.add(cut);
                    selectedNode = cut;
                } else {
                    System.err.println("Ошибка: повторное добавление фигуры в сцену");
                }
            }
        } else {
            Node shape = null;
            switch (selectedShape) {
                case SPHERE:
                    float rx = (float)(w  / 2);
                    float ry = (float)(d  / 2);
                    float rz = (float)(shapeHeight / 2);

                    MeshView m = createEllipsoidalSphere(rx, ry, rz, 36, Color.BLUE);
                    // центрирование
                    m.setTranslateX(cx);
                    m.setTranslateY(cy);
                    m.setTranslateZ(rz);

                    shape = m;
                    first_hole = 0;
                    break;

                case CUBE:
                    Box b = new Box(w, d, shapeHeight);
                    b.setTranslateX(cx);
                    b.setTranslateY(cy);
                    b.setTranslateZ(shapeHeight / 2);
                    b.setMaterial(new PhongMaterial(Color.RED));
                    shape = b;
                    first_hole = 0;
                    break;
                default:
                    System.out.println("Неизвестный тип: " + selectedShape);
            }

            if (shape != null) {
                setupDraggable(shape);

                UUID shapeId = UUID.randomUUID();
                shape.getProperties().put("shapeId", shapeId);

                MeshView mv = convertToMeshView(shape);
                Transform t = fxToCSGTransform(shape);
                CSG firstCSG = MeshToCSGConverter.convert(mv).transformed(t);
                currentCSGMap.put(shapeId, firstCSG);

                createdShapes.add(shape);
                lastShape = shape;
                contentGroup.getChildren().add(shape);

                selectedNode = shape;

                System.out.println(">>> Main node = " + shape);
            }
        }
    }

    private MeshView applyHole(Node currentNode, Node newHoleShape) {
        try {
            UUID shapeId = getOrAssignShapeId(currentNode);

            // Преобразуем текущую фигуру в CSG
            MeshView currentMesh = convertToMeshView(currentNode);
            Transform tCurrent = fxToCSGTransform(currentNode);
            CSG currentCSG = MeshToCSGConverter.convert(currentMesh).transformed(tCurrent);

            // Конвертируем новое отверстие в CSG
            MeshView holeMesh = convertToMeshView(newHoleShape);
            Transform tHole = fxToCSGTransform(newHoleShape);
            CSG newHoleCSG = MeshToCSGConverter.convert(holeMesh).transformed(tHole);

            CSG resultCSG = newHoleCSG.difference(currentCSG);

            // Переводим результат обратно в локальные координаты JavaFX
            Transform invT = invertFxToCSGTransform(currentNode);
            CSG localResult = resultCSG.transformed(invT);
            MeshView result = CSGToMeshViewConverter.convert(localResult);

            // Копируем трансформации и визуальные свойства
            copyNodeTransforms(currentNode, result);
            if (currentNode instanceof Shape3D) {
                result.setMaterial(((Shape3D) currentNode).getMaterial());
            }
            result.getProperties().put("shapeId", shapeId);

            return result;
        } catch (Exception ex) {
            System.err.println("Ошибка в applyHoleToModified: " + ex.getMessage());
            return null;
        }
    }

    private MeshView applyHoleFromCurrent(MeshView currentNode, Node newHoleShape) {
        try {
            UUID shapeId = getOrAssignShapeId(currentNode);

            // Преобразуем текущую фигуру в CSG
            MeshView currentMesh = convertToMeshView(currentNode);
            Transform tCurrent = fxToCSGTransform(currentNode);
            CSG currentCSG = MeshToCSGConverter.convert(currentMesh).transformed(tCurrent);

            // Конвертируем новое отверстие в CSG
            MeshView holeMesh = convertToMeshView(newHoleShape);
            Transform tHole = fxToCSGTransform(newHoleShape);
            CSG newHoleCSG = MeshToCSGConverter.convert(holeMesh).transformed(tHole);

            CSG resultCSG = currentCSG.intersect(newHoleCSG);

            // Переводим результат обратно в локальные координаты JavaFX
            Transform invT = invertFxToCSGTransform(currentNode);
            CSG localResult = resultCSG.transformed(invT);
            MeshView result = CSGToMeshViewConverter.convert(localResult);

            // Копируем трансформации и визуальные свойства
            copyNodeTransforms(currentNode, result);
            if (currentNode instanceof Shape3D) {
                result.setMaterial(((Shape3D) currentNode).getMaterial());
            }
            result.getProperties().put("shapeId", shapeId);

            return result;
        } catch (Exception ex) {
            System.err.println("Ошибка в applyHoleToModified: " + ex.getMessage());
            return null;
        }
    }

    private Node createHoleShape(double w, double d, double h, double x, double y, double mainH, boolean through) {
        double z = through ? mainH/2 : mainH - h/2;
        if (HOLE_ELLIPTICAL.equals(selectedShape)) {
            MeshView m = createEllipsoidalSphere((float)(w/2), (float)(d/2), (float)h, 36, Color.BLUE);
            m.setTranslateX(x); m.setTranslateY(y); m.setTranslateZ(z);
            return m;
        } else {
            Box box = new Box(w, d, h);
            box.setMaterial(new PhongMaterial(Color.BLUE));
            box.setTranslateX(x); box.setTranslateY(y); box.setTranslateZ(z);
            return box;
        }
    }

    private UUID getOrAssignShapeId(Node shape) {
        Object o = shape.getProperties().get("shapeId");
        if (o instanceof UUID) {
            return (UUID) o;
        } else {
            UUID id = UUID.randomUUID();
            shape.getProperties().put("shapeId", id);
            return id;
        }
    }

    private MeshView convertToMeshView(Node shape) {
        if (shape instanceof MeshView) {
            return (MeshView) shape;
        } else if (shape instanceof Box) {
            return boxToMeshView((Box) shape);
        }
        throw new IllegalArgumentException(
                "Неподдерживаемый тип для конвертации в MeshView: "
                        + shape.getClass().getSimpleName()
        );

    }

    private MeshView boxToMeshView(Box box) {
        TriangleMesh mesh = new TriangleMesh();
        float hw = (float)box.getWidth()/2;
        float hh = (float)box.getHeight()/2;
        float hd = (float)box.getDepth()/2;
        mesh.getPoints().setAll(
                -hw,-hh,-hd,  hw,-hh,-hd,  hw, hh,-hd,  -hw, hh,-hd,
                -hw,-hh, hd,  hw,-hh, hd,  hw, hh, hd,  -hw, hh, hd
        );
        mesh.getTexCoords().addAll(0,0);
        mesh.getFaces().setAll(
                0,0,1,0,2,0, 0,0,2,0,3,0,
                4,0,6,0,5,0, 4,0,7,0,6,0,
                0,0,4,0,5,0, 0,0,5,0,1,0,
                3,0,2,0,6,0, 3,0,6,0,7,0,
                1,0,5,0,6,0, 1,0,6,0,2,0,
                0,0,3,0,7,0, 0,0,7,0,4,0
        );
        MeshView mv = new MeshView(mesh);
        mv.setMaterial(box.getMaterial());
        mv.setTranslateX(box.getTranslateX());
        mv.setTranslateY(box.getTranslateY());
        mv.setTranslateZ(box.getTranslateZ());
        mv.setDepthTest(DepthTest.ENABLE);
        return mv;
    }

    private MeshView createEllipsoidalSphere(
            float rx, float ry, float rz, int divisions, Color c) {

        TriangleMesh mesh = new TriangleMesh();
        // Нужен хотя бы один UV-кординат
        mesh.getTexCoords().addAll(0, 0);

        int latDiv = divisions;     // число сегментов по «меридиану» (от полюса к полюсу)
        int lonDiv = divisions;     // число сегментов по «параллели» (вокруг экватора)

        // 1) Генерация вершин
        for (int i = 0; i <= latDiv; i++) {
            float theta = (float) (Math.PI * i / latDiv);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);

            for (int j = 0; j <= lonDiv; j++) {
                float phi = (float) (2 * Math.PI * j / lonDiv);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                float x = rx * sinTheta * cosPhi;
                float y = ry * sinTheta * sinPhi;
                float z = rz * cosTheta;

                mesh.getPoints().addAll(x, y, z);
            }
        }

        // 2) Генерация лиц — для каждого «квадрата» два треугольника
        for (int i = 0; i < latDiv; i++) {
            for (int j = 0; j < lonDiv; j++) {
                int p0 = i     * (lonDiv + 1) + j;
                int p1 = p0    + 1;
                int p2 = p0    + (lonDiv + 1);
                int p3 = p2    + 1;

                // треугольник (p0, p2, p1)
                mesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                // треугольник (p1, p2, p3)
                mesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
            }
        }

        MeshView mv = new MeshView(mesh);
        mv.setMaterial(new PhongMaterial(c));
        mv.setDepthTest(DepthTest.ENABLE);
        return mv;
    }


    private Transform fxToCSGTransform(Node node) {
        Transform t = Transform.unity()
                .scale(node.getScaleX(), node.getScaleY(), node.getScaleZ());
        Rotate rx = (Rotate) node.getProperties().get("rotateX");
        Rotate ry = (Rotate) node.getProperties().get("rotateY");
        if (rx != null) t = t.rotX(Math.toRadians(rx.getAngle()));
        if (ry != null) t = t.rotY(Math.toRadians(ry.getAngle()));
        return t.translate(node.getTranslateX(), node.getTranslateY(), node.getTranslateZ());
    }

    private Transform invertFxToCSGTransform(Node node) {
        Transform t = Transform.unity()
                .translate(-node.getTranslateX(), -node.getTranslateY(), -node.getTranslateZ());
        Rotate rx = (Rotate) node.getProperties().get("rotateX");
        Rotate ry = (Rotate) node.getProperties().get("rotateY");
        if (ry != null) t = t.rotY(Math.toRadians(-ry.getAngle()));
        if (rx != null) t = t.rotX(Math.toRadians(-rx.getAngle()));
        return t.scale(
                node.getScaleX() != 0 ? 1/node.getScaleX() : 1,
                node.getScaleY() != 0 ? 1/node.getScaleY() : 1,
                node.getScaleZ() != 0 ? 1/node.getScaleZ() : 1
        );
    }

    private void copyNodeTransforms(Node src, MeshView tgt) {
        tgt.setTranslateX(src.getTranslateX());
        tgt.setTranslateY(src.getTranslateY());
        tgt.setTranslateZ(src.getTranslateZ());
        tgt.setScaleX(src.getScaleX());
        tgt.setScaleY(src.getScaleY());
        tgt.setScaleZ(src.getScaleZ());
        Rotate rX = (Rotate) src.getProperties().get("rotateX");
        Rotate rY = (Rotate) src.getProperties().get("rotateY");
        if (rX != null && rY != null) {
            Rotate cX = new Rotate(rX.getAngle(), rX.getPivotX(), rX.getPivotY(), rX.getPivotZ(), rX.getAxis());
            Rotate cY = new Rotate(rY.getAngle(), rY.getPivotX(), rY.getPivotY(), rY.getPivotZ(), rY.getAxis());
            tgt.getTransforms().addAll(cX, cY);
            tgt.getProperties().put("rotateX", cX);
            tgt.getProperties().put("rotateY", cY);
        }
    }

    private void setupDraggable(Node shape) {
        Rotate rx = new Rotate(0, Rotate.X_AXIS);
        Rotate ry = new Rotate(0, Rotate.Y_AXIS);
        shape.getTransforms().addAll(rx, ry);
        shape.getProperties().put("rotateX", rx);
        shape.getProperties().put("rotateY", ry);

        shape.setOnMousePressed(e -> {
            if (creationState != CreationState.WAITING) { e.consume(); return; }
            if (e.getButton() == MouseButton.PRIMARY) {
                selectShape(shape);
                e.consume();
            }
        });

        shape.setOnMouseDragged(e -> {
            if (creationState != CreationState.WAITING) { e.consume(); return; }
            if (e.getButton() == MouseButton.PRIMARY) {
                Point2D p = contentGroup.sceneToLocal(e.getSceneX(), e.getSceneY());
                shape.setTranslateX(p.getX());
                shape.setTranslateY(p.getY());
                e.consume();
            }
        });

        shape.setOnScroll(e -> {
            if (creationState != CreationState.WAITING) { e.consume(); return; }
            double scale = shape.getScaleX() + e.getDeltaY() / 500;
            scale = Math.max(scale, 0.1);
            shape.setScaleX(scale);
            shape.setScaleY(scale);
            shape.setScaleZ(scale);
            e.consume();
        });
    }

    private double getMainShapeHeight(Node n) {
        if (n instanceof Box) {
            return ((Box) n).getDepth();
        } else {
            Bounds b = n.getBoundsInLocal();
            return b.getHeight();
        }
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
        canvas3D.requestFocus();
    }

    private void deselectShape() {
        if (selectedNode != null) {
            selectedNode.setStyle("");
            selectedNode = null;
        }
    }

    private void deleteAllShapes() {
        createdShapes.forEach(n -> contentGroup.getChildren().remove(n));
        createdShapes.clear();
        lastShape = null;
        deselectShape();
    }

    private void deleteLastShape() {
        if (lastShape != null) {
            contentGroup.getChildren().remove(lastShape);
            createdShapes.remove(lastShape);
            lastShape = createdShapes.isEmpty() ? null : createdShapes.get(createdShapes.size()-1);
        }
    }

    private void deleteSelectedShape() {
        if (selectedNode != null) {
            contentGroup.getChildren().remove(selectedNode);
            createdShapes.remove(selectedNode);
            selectedNode = null;
        }
    }

    @FXML public void selectSphere() {
        selectedShape = SPHERE;
        resetCreation();
        System.out.println("Выбрана фигура: Сфера");
    }

    @FXML public void selectCube() {
        selectedShape = CUBE;
        resetCreation();
        System.out.println("Выбран куб");
    }

    @FXML public void selectEllipticalHole() {
        selectedShape = HOLE_ELLIPTICAL;
        resetCreation();
        System.out.println("Режим: Эллиптическая дырка");
    }

    @FXML public void selectRectangularHole() {
        selectedShape = HOLE_RECTANGULAR;
        resetCreation();
        System.out.println("Режим: Прямоугольная дырка");
    }

    @FXML public void createShapeFromInput() {
        try {
            double ix = Double.parseDouble(txtBaseX.getText());
            double iy = Double.parseDouble(txtBaseY.getText());
            double w  = Double.parseDouble(txtWidth.getText());
            double d  = Double.parseDouble(txtDepth.getText());
            double h  = Double.parseDouble(txtHeight.getText());

            double cx = canvas3D.getWidth()/2, cy = canvas3D.getHeight()/2;
            baseInitX = ix + cx;
            baseInitY = iy + cy;
            createBaseProjection(baseInitX, baseInitY);
            baseProjection.setWidth(w);
            baseProjection.setHeight(d);
            shapeHeight = h;
            create3DShapeFromBaseAndHeight();
            contentGroup.getChildren().remove(baseProjection);
        } catch (NumberFormatException ex) {
            System.err.println("Неверный формат входных данных");
        }
    }
}