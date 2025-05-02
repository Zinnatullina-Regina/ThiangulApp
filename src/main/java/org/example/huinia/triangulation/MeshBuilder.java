package org.example.huinia.triangulation;

import javafx.geometry.Point3D;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/**
 * Класс MeshBuilder создает объект MeshView (TriangleMesh) для визуализации 3D-меш-сетки.
 * Используется список вершин и треугольников фигуры для заполнения TriangleMesh.
 */
public class MeshBuilder {

    /**
     * Создает TriangleMesh на основе фигуры shape и заворачивает в MeshView для отображения.
     * Назначает простой материал для видимости сетки.
     */
    public static MeshView buildMesh(Shape3 shape) {
        TriangleMesh mesh = new TriangleMesh();
        // Добавляем все точки (преобразуем в float для Mesh)
        for (Point3D point : shape.vertices) {
            mesh.getPoints().addAll((float) point.getX(), (float) point.getY(), (float) point.getZ());
        }
        // Координаты текстуры (не используются, но должны быть хотя бы одна запись)
        mesh.getTexCoords().addAll(0.0f, 0.0f);
        // Добавляем грани (каждая грань - индекс вершины и индекс текстуры)
        for (Shape3.Triangle tri : shape.triangles) {
            mesh.getFaces().addAll(
                    tri.a, 0,
                    tri.b, 0,
                    tri.c, 0
            );
        }
        // Создаем 3D объект MeshView для отображения меша
        MeshView meshView = new MeshView(mesh);
        // Назначаем материал (цвет) для меша, чтобы он был виден
        PhongMaterial wireMat = new PhongMaterial(Color.BLACK);
        meshView.setMaterial(wireMat);
        // рисуем только рёбра
        meshView.setDrawMode(DrawMode.LINE);
        // чтобы не обрезались обратные стороны
        meshView.setCullFace(CullFace.NONE);
        // ————————————————

        return meshView;
    }
}
