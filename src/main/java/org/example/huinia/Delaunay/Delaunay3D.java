
// File: src/main/java/org/example/huinia/Delaunay/Delaunay3D.java
package org.example.huinia.Delaunay;

import eu.mihosoft.jcsg.ext.quickhull3d.Point3d;
import javafx.scene.shape.TriangleMesh;

import java.util.*;
import java.util.stream.Collectors;

public class Delaunay3D {
    public static class Tetrahedron {
        public final Point3d a, b, c, d;
        private final double[][] circMatrix;

        public Tetrahedron(Point3d a, Point3d b, Point3d c, Point3d d) {
            this.a = a; this.b = b; this.c = c; this.d = d;
            this.circMatrix = buildCircumMatrix(a, b, c, d);
        }

        private double[][] buildCircumMatrix(Point3d... P) {
            double[][] M = new double[4][4];
            for (int i = 0; i < 4; i++) {
                Point3d p = P[i];
                M[i][0] = p.x; M[i][1] = p.y; M[i][2] = p.z;
                M[i][3] = p.x*p.x + p.y*p.y + p.z*p.z;
            }
            return M;
        }

        public boolean containsInCircumsphere(Point3d p) {
            double[][] A = new double[5][5];
            for (int i = 0; i < 4; i++) {
                System.arraycopy(circMatrix[i], 0, A[i], 0, 4);
                A[i][4] = 1.0;
            }
            A[4][0] = p.x; A[4][1] = p.y; A[4][2] = p.z;
            A[4][3] = p.x*p.x + p.y*p.y + p.z*p.z;
            A[4][4] = 1.0;
            return determinant5(A) > 1e-6;
        }

        private double determinant5(double[][] M) {
            return determinant(M, 5);
        }

        private double determinant(double[][] M, int n) {
            if (n == 1) return M[0][0];
            double det = 0;
            double[][] sub = new double[n-1][n-1];
            for (int col = 0; col < n; col++) {
                for (int i = 1; i < n; i++) {
                    int idx = 0;
                    for (int j = 0; j < n; j++) {
                        if (j == col) continue;
                        sub[i-1][idx++] = M[i][j];
                    }
                }
                det += ((col % 2 == 0) ? 1 : -1) * M[0][col] * determinant(sub, n-1);
            }
            return det;
        }
    }

    public static class Face {
        final Point3d a, b, c;
        Face(Point3d a, Point3d b, Point3d c) {
            List<Point3d> pts = Arrays.asList(a, b, c);
            pts.sort(Comparator.comparingDouble(p -> p.x + p.y + p.z));
            this.a = pts.get(0); this.b = pts.get(1); this.c = pts.get(2);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Face)) return false;
            Face f = (Face) o;
            return pointEq(a, f.a) && pointEq(b, f.b) && pointEq(c, f.c);
        }
        @Override public int hashCode() {
            return Objects.hash(quant(a), quant(b), quant(c));
        }
        private static long quant(Point3d p) {
            return Math.round((p.x*1e6) + (p.y*1e6) + (p.z*1e6));
        }
        static List<Face> facesOf(Tetrahedron t) {
            return List.of(
                    new Face(t.a, t.b, t.c),
                    new Face(t.a, t.b, t.d),
                    new Face(t.a, t.c, t.d),
                    new Face(t.b, t.c, t.d)
            );
        }
    }

    public static List<Tetrahedron> triangulate(List<Point3d> pts) {
        Tetrahedron superTet = createSuper(pts);
        List<Tetrahedron> mesh = new ArrayList<>();
        mesh.add(superTet);
        for (Point3d p : pts) {
            List<Tetrahedron> bad = new ArrayList<>();
            for (Tetrahedron t : mesh) if (t.containsInCircumsphere(p)) bad.add(t);
            mesh.removeAll(bad);
            Set<Face> boundary = new HashSet<>();
            for (Tetrahedron t : bad) for (Face f : Face.facesOf(t)) {
                if (!boundary.remove(f)) boundary.add(f);
            }
            for (Face f : boundary) mesh.add(new Tetrahedron(f.a, f.b, f.c, p));
        }
        mesh.removeIf(t -> hasVertex(t, superTet));
        return mesh;
    }

    public static List<Face> extractHullFaces(List<Tetrahedron> tets) {
        Map<Face, Integer> cnt = new HashMap<>();
        for (Tetrahedron t : tets) for (Face f : Face.facesOf(t)) cnt.merge(f, 1, Integer::sum);
        return cnt.entrySet().stream()
                .filter(e -> e.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public static TriangleMesh buildSurfaceMesh(List<Face> faces) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        Map<Point3d, Integer> idx = new HashMap<>();
        int n = 0;
        for (Face f : faces) {
            Point3d[] pts = {f.a, f.b, f.c};
            int[] id = new int[3];
            for (int i = 0; i < 3; i++) {
                Point3d p = pts[i];
                if (!idx.containsKey(p)) {
                    mesh.getPoints().addAll((float) p.x, (float) p.y, (float) p.z);
                    idx.put(p, n++);
                }
                id[i] = idx.get(p);
            }
            mesh.getFaces().addAll(id[0], 0, id[1], 0, id[2], 0);
        }
        return mesh;
    }
    private TriangleMesh buildAllTetraMesh(List<Delaunay3D.Tetrahedron> tets) {
        TriangleMesh mesh = new TriangleMesh();
        // один фиктивный UV (нужен, чтобы mesh.getFaces() заработал)
        mesh.getTexCoords().addAll(0, 0);

        // сопоставим каждую Point3d с индексом в mesh.getPoints()
        Map<Point3d, Integer> idx = new HashMap<>();
        int nextIndex = 0;

        for (Delaunay3D.Tetrahedron t : tets) {
            // массив наших 4 вершин
            Point3d[] P = new Point3d[]{ t.a, t.b, t.c, t.d };
            int[] faceIdx = new int[4];
            // 1) регистрируем вершины
            for (int i = 0; i < 4; i++) {
                Point3d p = P[i];
                if (!idx.containsKey(p)) {
                    mesh.getPoints().addAll((float)p.x, (float)p.y, (float)p.z);
                    idx.put(p, nextIndex++);
                }
                faceIdx[i] = idx.get(p);
            }
            // 2) для каждого тетраэдра добавляем 4 грани (по три индекса)
            int[][] faces = {
                    {faceIdx[0], faceIdx[1], faceIdx[2]},
                    {faceIdx[0], faceIdx[1], faceIdx[3]},
                    {faceIdx[0], faceIdx[2], faceIdx[3]},
                    {faceIdx[1], faceIdx[2], faceIdx[3]}
            };
            for (int[] f : faces) {
                mesh.getFaces().addAll(f[0],0, f[1],0, f[2],0);
            }
        }

        return mesh;
    }
    private static boolean hasVertex(Tetrahedron t, Tetrahedron s) {
        return pointEq(t.a, s.a) || pointEq(t.a, s.b) || pointEq(t.a, s.c) || pointEq(t.a, s.d)
                || pointEq(t.b, s.a) || pointEq(t.b, s.b) || pointEq(t.b, s.c) || pointEq(t.b, s.d)
                || pointEq(t.c, s.a) || pointEq(t.c, s.b) || pointEq(t.c, s.c) || pointEq(t.c, s.d)
                || pointEq(t.d, s.a) || pointEq(t.d, s.b) || pointEq(t.d, s.c) || pointEq(t.d, s.d);
    }

    private static boolean pointEq(Point3d p, Point3d q) {
        double eps = 1e-6;
        return Math.abs(p.x - q.x) < eps && Math.abs(p.y - q.y) < eps && Math.abs(p.z - q.z) < eps;
    }

    private static Tetrahedron createSuper(List<Point3d> pts) {
        double minx = Double.POSITIVE_INFINITY, miny = Double.POSITIVE_INFINITY, minz = Double.POSITIVE_INFINITY;
        double maxx = Double.NEGATIVE_INFINITY, maxy = Double.NEGATIVE_INFINITY, maxz = Double.NEGATIVE_INFINITY;
        for (Point3d p : pts) {
            minx = Math.min(minx, p.x); miny = Math.min(miny, p.y); minz = Math.min(minz, p.z);
            maxx = Math.max(maxx, p.x); maxy = Math.max(maxy, p.y); maxz = Math.max(maxz, p.z);
        }
        double span = Math.max(Math.max(maxx - minx, maxy - miny), maxz - minz);
        double D = span * 10;
        Point3d c = new Point3d((minx + maxx) / 2, (miny + maxy) / 2, (minz + maxz) / 2);
        return new Tetrahedron(
                new Point3d(c.x + D, c.y, c.z),
                new Point3d(c.x, c.y + D, c.z),
                new Point3d(c.x, c.y, c.z + D),
                new Point3d(c.x - D, c.y - D, c.z - D)
        );
    }
}