package org.example.huinia.triangulation;

import javafx.geometry.Point3D;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс Shape3D представляет 3D-фигуру заданную вершинами, гранями и отверстиями.
 * Каждая грань фигуры имеет внешний контур (список индексов вершин) и, возможно, отверстия (списки индексов).
 */
public class Shape3 {
    // Список всех вершин фигуры в мировых координатах
    List<Point3D> vertices = new ArrayList<>();
    // Список граней фигуры (полигонов с отверстиями)
    List<Face> faces = new ArrayList<>();
    // Список треугольников после триангуляции (каждый треугольник задан индексами вершин)
    List<Triangle> triangles = new ArrayList<>();

    /** Добавление вершины в фигуру, возвращает индекс новой вершины. */
    public int addVertex(Point3D point) {
        vertices.add(point);
        return vertices.size() - 1;
    }

    /** Добавление грани (полигона) с указанием списка индексов вершин внешнего контура и списков отверстий. */
    public void addFace(List<Integer> outerLoop, List<List<Integer>> holes) {
        faces.add(new Face(outerLoop, holes));
    }

    /** Внутренний класс Face для представления грани (многоугольника) с возможными отверстиями. */
    static class Face {
        List<Integer> outer;           // Индексы вершин внешнего контура грани
        List<List<Integer>> holes;     // Списки индексов вершин для каждого отверстия внутри грани

        Face(List<Integer> outerLoop, List<List<Integer>> holesList) {
            this.outer = outerLoop;
            this.holes = (holesList != null) ? holesList : new ArrayList<>();
        }
    }

    /** Внутренний класс Triangle для представления треугольной грани через индексы вершин. */
    static class Triangle {
        int a, b, c;
        Triangle(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }
}
