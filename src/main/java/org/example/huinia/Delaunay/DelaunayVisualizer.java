package org.example.huinia.Delaunay;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.List;

public class DelaunayVisualizer {
    public static Group toMeshGroup(List<Delaunay3D.Tetrahedron> tetrahedra) {
        Group g = new Group();
        PhongMaterial mat = new PhongMaterial(Color.color(0,0.8,0.2,0.3));
        for (Delaunay3D.Tetrahedron t : tetrahedra) {
            TriangleMesh mesh = new TriangleMesh();
            // добавляем 4 вершины t.a, t.b, t.c, t.d
            // добавляем 4 грани (каждая — треугольник из 3 индексов)
            MeshView mv = new MeshView(mesh);
            mv.setMaterial(mat);
            g.getChildren().add(mv);
        }
        return g;
    }
}
