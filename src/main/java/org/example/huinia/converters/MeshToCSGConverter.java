package org.example.huinia.converters;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import eu.mihosoft.vvecmath.Vector3d;
import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableIntegerArray;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.List;

public class MeshToCSGConverter {

    public static CSG convert(MeshView meshView) {
        if (!(meshView.getMesh() instanceof TriangleMesh)) {
            throw new UnsupportedOperationException("Поддерживаются только объекты на основе TriangleMesh");
        }
        TriangleMesh triangleMesh = (TriangleMesh) meshView.getMesh();
        ObservableFloatArray pointsArray = triangleMesh.getPoints();
        ObservableIntegerArray facesArray = triangleMesh.getFaces();

        List<Polygon> polygons = new ArrayList<>();

        int faceElementSize = triangleMesh.getFaceElementSize();
        int faceCount = facesArray.size() / faceElementSize;

        for (int i = 0; i < faceCount; i++) {
            int indexStart = i * faceElementSize;
            int pIndex1 = facesArray.get(indexStart);
            int pIndex2 = facesArray.get(indexStart + 2);
            int pIndex3 = facesArray.get(indexStart + 4);

            double x1 = pointsArray.get(pIndex1 * 3);
            double y1 = pointsArray.get(pIndex1 * 3 + 1);
            double z1 = pointsArray.get(pIndex1 * 3 + 2);
            double x2 = pointsArray.get(pIndex2 * 3);
            double y2 = pointsArray.get(pIndex2 * 3 + 1);
            double z2 = pointsArray.get(pIndex2 * 3 + 2);
            double x3 = pointsArray.get(pIndex3 * 3);
            double y3 = pointsArray.get(pIndex3 * 3 + 1);
            double z3 = pointsArray.get(pIndex3 * 3 + 2);

            Vector3d p1 = Vector3d.xyz(x1, y1, z1);
            Vector3d p2 = Vector3d.xyz(x2, y2, z2);
            Vector3d p3 = Vector3d.xyz(x3, y3, z3);

            Vector3d edge1 = p2.minus(p1);
            Vector3d edge2 = p3.minus(p1);

            double crossX = edge1.getY() * edge2.getZ() - edge1.getZ() * edge2.getY();
            double crossY = edge1.getZ() * edge2.getX() - edge1.getX() * edge2.getZ();
            double crossZ = edge1.getX() * edge2.getY() - edge1.getY() * edge2.getX();
            Vector3d normal = Vector3d.xyz(crossX, crossY, crossZ).normalized();

            Vertex v1 = new Vertex(p1, normal);
            Vertex v2 = new Vertex(p2, normal);
            Vertex v3 = new Vertex(p3, normal);

            List<Vertex> vertices = new ArrayList<>();
            vertices.add(v1);
            vertices.add(v2);
            vertices.add(v3);

            polygons.add(new Polygon(vertices));
        }
        return CSG.fromPolygons(polygons);
    }
}
