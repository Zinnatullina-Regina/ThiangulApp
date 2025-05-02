package org.example.huinia.triangulation;

import javafx.geometry.Point3D;

/**
 * Класс IntersectionProcessor выполняет объединение (union) пересекающихся фигур.
 * Объединяет фигуры в единую оболочку и удаляет невидимые внутренние части.
 */
class IntersectionProcessor {

    /**
     * Объединяет две пересекающиеся фигуры shape1 и shape2 в одну.
     * Возвращает новую фигуру, содержащую объединённую поверхность (внешние треугольники).
     */
    public static Shape3 unifyShapes(Shape3 shape1, Shape3 shape2) {
        Shape3 result = new Shape3();
        // 1. Объединение списков вершин
        int n1 = shape1.vertices.size();
        // Добавляем все вершины первой фигуры
        for (Point3D v : shape1.vertices) {
            result.vertices.add(v);
        }
        // Добавляем все вершины второй фигуры
        for (Point3D v : shape2.vertices) {
            result.vertices.add(v);
        }

        // 2. Обработка треугольников: копирование только внешних (не внутренних) треугольников
        // Треугольники первой фигуры (их индексы не требуют смещения)
        for (Shape3.Triangle tri : shape1.triangles) {
            // Проверяем, находится ли треугольник полностью внутри второй фигуры
            if (!isTriangleInsideShape(shape2, shape1, tri)) {
                // Если треугольник НЕ целиком внутри shape2, добавляем его в результат (индексы остаются те же)
                result.triangles.add(new Shape3.Triangle(tri.a, tri.b, tri.c));
            }
        }
        // Треугольники второй фигуры (необходим сдвиг индексов на n1 для указания на правильные вершины в объединённом списке)
        for (Shape3.Triangle tri : shape2.triangles) {
            if (!isTriangleInsideShape(shape1, shape2, tri)) {
                // Добавляем треугольник, смещая индексы на n1
                result.triangles.add(new Shape3.Triangle(tri.a + n1, tri.b + n1, tri.c + n1));
            }
        }
        return result;
    }

    /**
     * Проверяет, находится ли заданный треугольник tri (из shapeSource) целиком внутри объёма фигуры shapeVolume.
     * Использует алгоритм проверки "точка внутри полиэдра" для вершин треугольника.
     */
    private static boolean isTriangleInsideShape(Shape3 shapeVolume, Shape3 shapeSource, Shape3.Triangle tri) {
        // Получаем вершины треугольника (координаты)
        Point3D pA = shapeSource.vertices.get(tri.a);
        Point3D pB = shapeSource.vertices.get(tri.b);
        Point3D pC = shapeSource.vertices.get(tri.c);
        // Проверяем каждую вершину: если хотя бы одна снаружи shapeVolume, то треугольник не полностью внутри
        if (!isPointInside(shapeVolume, pA)) return false;
        if (!isPointInside(shapeVolume, pB)) return false;
        if (!isPointInside(shapeVolume, pC)) return false;
        // Если все вершины треугольника лежат внутри shapeVolume -> считаем весь треугольник внутренним
        return true;
    }

    /**
     * Проверяет, находится ли точка p внутри замкнутой поверхности фигуры shape (лучевой метод).
     * Работает корректно для выпуклых и невыпуклых оболочек.
     */
    private static boolean isPointInside(Shape3 shape, Point3D p) {
        // Бросаем луч из точки p в положительном направлении оси X и считаем пересечения с треугольниками фигуры
        int intersections = 0;
        // Задаём направление луча по X: dx=1, dy=0, dz=0
        double px = p.getX();
        double py = p.getY();
        double pz = p.getZ();
        // Перебираем все треугольники оболочки shape
        for (Shape3.Triangle tri : shape.triangles) {
            Point3D v1 = shape.vertices.get(tri.a);
            Point3D v2 = shape.vertices.get(tri.b);
            Point3D v3 = shape.vertices.get(tri.c);
            // Координаты вершин треугольника
            double x1 = v1.getX(), y1 = v1.getY(), z1 = v1.getZ();
            double x2 = v2.getX(), y2 = v2.getY(), z2 = v2.getZ();
            double x3 = v3.getX(), y3 = v3.getY(), z3 = v3.getZ();

            // Луч параллелен плоскости треугольника, если разница y,z координат не дает решения (детерминант=0)
            // Решаем пересечение луча с плоскостью треугольника в проекции на плоскость YZ:
            // Составляем уравнения для параметров (alpha, beta) в базисе (v2-v1, v3-v1) по координатам Y, Z.
            double by1 = y2 - y1;
            double bz1 = z2 - z1;
            double by2 = y3 - y1;
            double bz2 = z3 - z1;
            double dy = py - y1;
            double dz = pz - z1;
            // Вычисляем детерминант для решения системы
            double det = by1 * bz2 - by2 * bz1;
            if (Math.abs(det) < 1e-9) {
                // Луч параллелен плоскости треугольника или треугольник вырожден в проекции – пропускаем
                continue;
            }
            // Находим барицентрические координаты alpha, beta (доля от векторов v1->v2 и v1->v3)
            double alpha = (dy * bz2 - dz * by2) / det;
            double beta  = (by1 * dz - bz1 * dy) / det;
            // Проверяем, что точка пересечения находится внутри треугольника (alpha, beta между 0 и 1, alpha+beta <=1)
            if (alpha < 0 || beta < 0 || alpha + beta > 1) {
                continue; // пересечение плоскости лежит вне треугольника
            }
            // Находим координату X точки пересечения плоскости треугольника и луча
            double intersectX = x1 + alpha * (x2 - x1) + beta * (x3 - x1);
            if (intersectX >= px) {
                // Если пересечение по X находится на луче (справа от точки p), учитываем его
                // Исключаем случаи пересечения точно по ребру/вершине: требуем alpha и beta не граничные (чтобы не считать дважды)
                if (alpha > 1e-9 && beta > 1e-9 && alpha + beta < 0.999999) {
                    intersections++;
                }
            }
        }
        // Если число пересечений нечетное – точка внутри, если чётное – снаружи
        return (intersections % 2) == 1;
    }
}

