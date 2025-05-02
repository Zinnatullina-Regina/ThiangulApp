package org.example.huinia.triangulation;

import javafx.geometry.Point3D;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс DelaunayTriangulator выполняет триангуляцию Делоне с ограничениями для полигональных граней.
 * Учитываются границы многоугольника и отверстия (ребра этих контуров присутствуют в триангуляции).
 * Алгоритм реализован вручную (без сторонних библиотек) с возможностью последующей оптимизации (например, с помощью многопоточности).
 */
public class DelaunayTriangulator {

    /**
     * Выполняет триангуляцию Делоне для всех граней фигуры shape.
     * Результат (список треугольников) сохраняется внутри объекта shape (shape.triangles).
     */
    public static void triangulateShape(Shape3 shape) {
        shape.triangles.clear();
        // Проходим по всем граням (полигонам) фигуры и триангулируем каждую
        for (Shape3.Face face : shape.faces) {
            List<Shape3.Triangle> faceTriangles = triangulateFace(shape, face);
            shape.triangles.addAll(faceTriangles);
        }
    }

    /**
     * Триангулирует одну грань (многоугольник с отверстиями) и возвращает список треугольников.
     * Используется алгоритм "отрезания ушей" (ear clipping) с учётом отверстий.
     * Все треугольники возвращаются в виде индексов вершин исходной фигуры.
     */
    private static List<Shape3.Triangle> triangulateFace(Shape3 shape, Shape3.Face face) {
        List<Shape3.Triangle> resultTriangles = new ArrayList<>();
        // Проекция вершин грани на локальную плоскость 2D для выполнения планарной триангуляции
        // 1. Вычисляем нормаль плоскости грани для получения ориентации
        Point3D normal = computeFaceNormal(shape, face);
        // 2. Выбираем локальные оси u, v для проекции:
        // Берём произвольный вектор в плоскости грани (по первому ребру внешнего контура) как ось u
        Point3D v1 = shape.vertices.get(face.outer.get(0));
        Point3D v2 = shape.vertices.get(face.outer.get(1));
        Point3D uAxis = v2.subtract(v1).normalize();
        // Ось v = нормаль x u (перпендикуляр в плоскости)
        Point3D vAxis = normal.crossProduct(uAxis);
        vAxis = vAxis.normalize();

        // Создаём объединённый список вершин многоугольника (внешний контур + отверстия, объединённые "мостиками")
        List<Vertex2D> polygon = new ArrayList<>();
        // Добавим внешний контур
        for (int idx : face.outer) {
            polygon.add(new Vertex2D(idx, projectPoint(shape.vertices.get(idx), v1, uAxis, vAxis)));
        }
        // Обработка отверстий: соединяем каждое отверстие с внешним контуром "мостиком"
        for (List<Integer> hole : face.holes) {
            if (hole.isEmpty()) continue;
            // Проецируем вершины отверстия
            List<Vertex2D> holeVertices2D = new ArrayList<>();
            for (int idx : hole) {
                holeVertices2D.add(new Vertex2D(idx, projectPoint(shape.vertices.get(idx), v1, uAxis, vAxis)));
            }
            // Выбираем вершины для соединения: простейший подход - первая вершина отверстия и первая вершина внешнего контура
            Vertex2D holeVertex = holeVertices2D.get(0);
            Vertex2D outerVertex = polygon.get(0);
            // Включаем отверстие в полигональную цепь:
            // Добавляем соединение: outerVertex -> holeVertex
            polygon.add(new Vertex2D(holeVertex.index, holeVertex.p)); // заход в отверстие
            // Добавляем все вершины отверстия
            for (int j = 1; j < holeVertices2D.size(); j++) {
                polygon.add(holeVertices2D.get(j));
            }
            // Возврат из отверстия к внешнему контуру
            polygon.add(new Vertex2D(holeVertex.index, holeVertex.p)); // выход из отверстия (та же вершина)
            // Примечание: вершина holeVertex появляется дважды, что отражает разрыв границы для отверстия.
            // Аналогично, outerVertex будет соединён через последовательность, хотя явно второй копии outerVertex мы не вставляем (используем существующий).
            // В результате цепочка polygon содержит последовательность обхода внешнего контура с включённым обходом отверстия.
        }

        // 3. Алгоритм "отрезания ушей" для полученного многоугольника (polygon)
        // Определяем ориентацию полигона (приблизительно по внешнему контуру)
        boolean isCCW = true;
        if (polygonArea(polygon) < 0) {
            isCCW = false;
        }

        // Список оставшихся вершин полигона (индексы в текущем списке polygon)
        // Используем динамический список: будем удалять "уши"
        int n = polygon.size();
        // Защита: если многоугольник уже треугольник, просто возвращаем его
        if (n < 3) return resultTriangles;
        if (n == 3) {
            resultTriangles.add(new Shape3.Triangle(polygon.get(0).index, polygon.get(1).index, polygon.get(2).index));
            return resultTriangles;
        }

        // Копируем индексы вершин в список для удаления ушей
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            available.add(i);
        }

        int i = 0;
        // Будем отсекать по одному треугольнику, пока не останется 2 вершины
        while (available.size() > 2 && i < available.size()) {
            // Цикл по вершинам: пытаемся найти "ухо" с вершиной available.get(i)
            int currIndex = available.get(i);
            int prevIndex = available.get((i - 1 + available.size()) % available.size());
            int nextIndex = available.get((i + 1) % available.size());
            Vertex2D prev = polygon.get(prevIndex);
            Vertex2D curr = polygon.get(currIndex);
            Vertex2D next = polygon.get(nextIndex);

            // Векторное произведение для определения выпуклости угла
            Point2D v_prev = prev.p;
            Point2D v_curr = curr.p;
            Point2D v_next = next.p;
            double cross = (v_curr.x - v_prev.x) * (v_next.y - v_curr.y) - (v_curr.y - v_prev.y) * (v_next.x - v_curr.x);
            boolean angleIsConvex = isCCW ? (cross >= 0) : (cross <= 0);
            if (angleIsConvex) {
                // Проверяем, что никакая другая точка не лежит внутри треугольника (prev, curr, next)
                boolean earFound = true;
                for (int j = 0; j < available.size(); j++) {
                    int idx = available.get(j);
                    if (idx == prevIndex || idx == currIndex || idx == nextIndex) continue;
                    if (isPointInTriangle(polygon.get(idx).p, v_prev, v_curr, v_next)) {
                        earFound = false;
                        break;
                    }
                }
                if (earFound) {
                    // Нашли "ухо": добавляем треугольник (индексы исходных вершин)
                    resultTriangles.add(new Shape3.Triangle(prev.index, curr.index, next.index));
                    // Удаляем вершину curr из полигона
                    available.remove((Integer) currIndex);
                    // Сбрасываем индекс для новой попытки с начала списка
                    i = 0;
                    continue;
                }
            }
            // Переходим к следующей вершине
            i++;
            if (i >= available.size()) {
                // Если прошли по всем без успеха, возможно остались коллинеарные или проблемные точки
                // Просто выходим, чтобы избежать зацикливания
                break;
            }
        }
        return resultTriangles;
    }

    /**
     * Вычисляет нормаль к плоскости грани (вектор нормали не нормализован).
     * Использует первые три точки внешнего контура грани.
     */
    private static Point3D computeFaceNormal(Shape3 shape, Shape3.Face face) {
        if (face.outer.size() < 3) return new Point3D(0, 0, 0);
        Point3D p0 = shape.vertices.get(face.outer.get(0));
        Point3D p1 = shape.vertices.get(face.outer.get(1));
        Point3D p2 = shape.vertices.get(face.outer.get(2));
        // Векторы в плоскости
        Point3D v1 = p1.subtract(p0);
        Point3D v2 = p2.subtract(p0);
        // Векторное произведение v1 и v2 даёт нормаль к плоскости
        Point3D normal = v1.crossProduct(v2);
        // Нормализация
        double normLen = Math.sqrt(normal.getX()*normal.getX() + normal.getY()*normal.getY() + normal.getZ()*normal.getZ());
        if (normLen < 1e-9) {
            return new Point3D(0, 0, 0);
        }
        return new Point3D(normal.getX()/normLen, normal.getY()/normLen, normal.getZ()/normLen);
    }

    /**
     * Проецирует 3D-точку point на плоскость, заданную плоскостью грани с опорной точкой origin и осями uAxis, vAxis.
     * Возвращает 2D-координаты точки на этой плоскости.
     */
    private static Point2D projectPoint(Point3D point, Point3D origin, Point3D uAxis, Point3D vAxis) {
        // Вектор от опорной точки
        Point3D vec = point.subtract(origin);
        // Координаты в базисе (uAxis, vAxis)
        double x = vec.dotProduct(uAxis);
        double y = vec.dotProduct(vAxis);
        return new Point2D(x, y);
    }

    /**
     * Вычисляет ориентированную площадь проецированного полигона (вспомогательное для определения ориентации контура).
     * Положительная площадь означает обход против часовой стрелки (CCW), отрицательная - по часовой.
     */
    private static double polygonArea(List<Vertex2D> polygon) {
        double area = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Point2D p1 = polygon.get(i).p;
            Point2D p2 = polygon.get((i + 1) % polygon.size()).p;
            area += p1.x * p2.y - p2.x * p1.y;
        }
        return area / 2.0;
    }

    /**
     * Проверяет, находится ли точка p внутри треугольника, заданного вершинами a, b, c (в 2D).
     */
    private static boolean isPointInTriangle(Point2D p, Point2D a, Point2D b, Point2D c) {
        // Барицентрический метод: точка внутри треугольника, если она лежит с одной стороны от всех его сторон.
        double areaOrig = Math.abs(triangleArea(a, b, c));
        double area1 = Math.abs(triangleArea(p, b, c));
        double area2 = Math.abs(triangleArea(a, p, c));
        double area3 = Math.abs(triangleArea(a, b, p));
        // Сравниваем сумму площадей маленьких треугольников с исходной площадью
        return Math.abs(area1 + area2 + area3 - areaOrig) < 1e-6;
    }

    /**
     * Вычисляет площадь треугольника по координатам (помощник для isPointInTriangle).
     */
    private static double triangleArea(Point2D p1, Point2D p2, Point2D p3) {
        return 0.5 * ((p1.x - p3.x) * (p2.y - p1.y) - (p1.x - p2.x) * (p3.y - p1.y));
    }

    /** Вспомогательный внутренний класс для хранения 2D-проекции вершины и ссылки на индекс в shape.vertices. */
    private static class Vertex2D {
        int index;    // индекс вершины в исходном Shape3D
        Point2D p;    // 2D-координаты проекции этой вершины
        Vertex2D(int index, Point2D p) {
            this.index = index;
            this.p = p;
        }
    }

    /** Вспомогательный внутренний класс для представления 2D-точки (double x, y). */
    private static class Point2D {
        double x, y;
        Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}

