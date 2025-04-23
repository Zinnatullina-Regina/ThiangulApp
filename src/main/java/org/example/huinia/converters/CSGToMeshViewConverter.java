package org.example.huinia.converters;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import javafx.scene.DepthTest;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.List;

public class CSGToMeshViewConverter {

    public static MeshView convert(CSG csg) {
        TriangleMesh mesh = new TriangleMesh();
        List<Float> pointsList = new ArrayList<>();
        List<Integer> facesList = new ArrayList<>();

        // Добавляем одну текстурную координату
        mesh.getTexCoords().addAll(0, 0);

        for (Polygon poly : csg.getPolygons()) {
            List<Vertex> vertices = poly.vertices;
            if (vertices.size() < 3) continue;

            int baseIndex = pointsList.size() / 3;
            for (Vertex v : vertices) {
                pointsList.add((float) v.pos.getX());
                pointsList.add((float) v.pos.getY());
                pointsList.add((float) v.pos.getZ());
            }
            for (int i = 1; i < vertices.size() - 1; i++) {
                int idx0 = baseIndex;
                int idx1 = baseIndex + i;
                int idx2 = baseIndex + i + 1;
                facesList.add(idx0);
                facesList.add(0);
                facesList.add(idx1);
                facesList.add(0);
                facesList.add(idx2);
                facesList.add(0);
            }
        }

        float[] pointsArray = new float[pointsList.size()];
        for (int i = 0; i < pointsList.size(); i++) {
            pointsArray[i] = pointsList.get(i);
        }
        int[] facesArray = new int[facesList.size()];
        for (int i = 0; i < facesList.size(); i++) {
            facesArray[i] = facesList.get(i);
        }
        mesh.getPoints().setAll(pointsArray);
        mesh.getFaces().setAll(facesArray);

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(new PhongMaterial(Color.LIGHTGRAY));
        meshView.setDepthTest(DepthTest.ENABLE);
        return meshView;
    }
}
