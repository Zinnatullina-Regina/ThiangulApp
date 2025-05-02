// CSGUtils.java
        package org.example.huinia.triangulation;

import eu.mihosoft.jcsg.CSG;
import eu.mihosoft.jcsg.Polygon;
import eu.mihosoft.jcsg.Vertex;
import javafx.geometry.Point3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Утилита: перевод CSG-модели в нашу Shape3D для последующей триангуляции */
public class CSGUtils {

    public static Shape3 csgToShape3D(CSG csg) {
        Shape3 shape = new Shape3();
        Map<Point3D, Integer> idxMap = new HashMap<>();

        for (Polygon poly : csg.getPolygons()) {
            List<Integer> faceIdx = new ArrayList<>();
            for (Vertex v : poly.vertices) {
                Point3D p = new Point3D(
                        v.pos.getX(), v.pos.getY(), v.pos.getZ()
                );
                Integer idx = idxMap.get(p);
                if (idx == null) {
                    idx = shape.addVertex(p);
                    idxMap.put(p, idx);
                }
                faceIdx.add(idx);
            }
            // никаких дыр внутри CSG-полигонов не бывает
            shape.addFace(faceIdx, /*holes=*/ null);
        }
        return shape;
    }
}
