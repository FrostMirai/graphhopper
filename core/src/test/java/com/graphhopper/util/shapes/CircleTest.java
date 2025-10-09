/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util.shapes;

import com.github.javafaker.Faker;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

/**
 * @author Peter Karich
 */
public class CircleTest {

    @Test
    public void testIntersectCircleBBox() {
        assertTrue(new Circle(10, 10, 120000).intersects(new BBox(9, 11, 8, 9)));

        assertFalse(new Circle(10, 10, 110000).intersects(new BBox(9, 11, 8, 9)));
    }

    @Test
    public void testIntersectPointList() {
        Circle circle = new Circle(1.5, 0.3, DistanceCalcEarth.DIST_EARTH.calcDist(0, 0, 0, 0.7));
        PointList pointList = new PointList();
        pointList.add(5, 5);
        pointList.add(5, 0);
        assertFalse(circle.intersects(pointList));

        pointList.add(-5, 0);
        assertTrue(circle.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 1);
        pointList.add(-1, 0);
        assertTrue(circle.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 0);
        pointList.add(-1, 3);
        assertFalse(circle.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 0);
        pointList.add(2, 0);
        assertTrue(circle.intersects(pointList));

        pointList = new PointList();
        pointList.add(1.5, -2);
        pointList.add(1.5, 2);
        assertTrue(circle.intersects(pointList));
    }

    @Test
    public void testContains() {
        Circle c = new Circle(10, 10, 120000);
        assertTrue(c.contains(new BBox(9, 11, 10, 10.1)));
        assertFalse(c.contains(new BBox(9, 11, 8, 9)));
        assertFalse(c.contains(new BBox(9, 12, 10, 10.1)));
    }

    @Test
    public void testContainsCircle() {
        Circle c = new Circle(10, 10, 120000);
        assertTrue(c.contains(new Circle(9.9, 10.2, 90000)));
        assertFalse(c.contains(new Circle(10, 10.4, 90000)));
    }



    // ==============================================
    // NOUVEAUX TESTS AJOUTÉS
    // ==============================================

    /**
     * Test 1: contains avec coordonnées précises
     */
    @Test
    public void testContainsCoordinates() {
        Circle circle = new Circle(0, 0, 100000);
        assertTrue(circle.contains(0, 0));
        assertTrue(circle.contains(0, 0.5));
        assertFalse(circle.contains(0, 1.5));
        assertFalse(circle.contains(10, 10));
    }

    /**
     * Test 2: Test des propriétés géographiques basiques
     */
    @Test
    public void testGeographicProperties() {
        Circle circle = new Circle(48.8566, 2.3522, 5000);
        assertTrue(circle.contains(48.8566, 2.3522));
        assertEquals(48.8566, circle.getLat(), 0.0001);
        assertEquals(2.3522, circle.getLon(), 0.0001);
        assertEquals(5000, circle.radiusInMeter, 0.0001);
    }

    /**
     * Test 3: Constructeur avec DistanceCalc personnalisé
     */
    @Test
    public void testCustomDistanceCalcConstructor() {
        DistanceCalc customCalc = DistanceCalcEarth.DIST_EARTH;
        Circle circle = new Circle(45.0, -75.0, 50000, customCalc);
        assertEquals(45.0, circle.getLat(), 0.0001);
        assertEquals(-75.0, circle.getLon(), 0.0001);
        assertEquals(50000, circle.radiusInMeter, 0.0001);
    }

    /**
     * Test 4: Test d'intersection BBox basique
     */
    @Test
    public void testBasicBBoxIntersections() {
        Circle circle = new Circle(0, 0, 100000);
        BBox insideBBox = new BBox(-0.5, 0.5, -0.5, 0.5);
        BBox outsideBBox = new BBox(10, 11, 10, 11);
        BBox centerBBox = new BBox(-0.1, 0.1, -0.1, 0.1);
        
        assertTrue(circle.intersects(insideBBox));
        assertFalse(circle.intersects(outsideBBox));
        assertTrue(circle.intersects(centerBBox));
    }

    /**
     * Test 5: Test de robustesse du cercle 
     */
    @Test
    public void testCircleRobustness() {
        Circle circle = new Circle(0, 0, 100000);
        Circle tinyCircle = new Circle(10, 20, 1); 
        Circle hugeCircle = new Circle(0, 0, 1000000);
        
        assertTrue(circle.contains(0, 0));
        assertFalse(circle.contains(5, 5));
        assertTrue(tinyCircle.contains(10, 20));
        assertTrue(hugeCircle.contains(5, 5));
        assertNotNull(circle.getBounds());
        assertNotNull(tinyCircle.getBounds());
        assertNotNull(hugeCircle.getBounds());
        
        String circleStr = circle.toString();
        assertTrue(circleStr.contains("0.0"));
        assertTrue(circleStr.contains("100000"));
    }

    /**
     * Test 6: Test avec différentes tailles de cercles
     */
    @Test
    public void testDifferentCircleSizes() {
        Circle smallCircle = new Circle(0, 0, 100);
        Circle largeCircle = new Circle(0, 0, 1000000);
        
        assertTrue(smallCircle.contains(0, 0));
        assertFalse(smallCircle.contains(0, 0.001));
        assertTrue(largeCircle.contains(0, 0));
        assertTrue(largeCircle.contains(5, 5));
        assertFalse(largeCircle.contains(20, 20));
    }

    /**
     * Test 7: Test des getters et toString
     */
    @Test
    public void testGettersAndToString() {
        Circle circle = new Circle(12.34, 56.78, 12345.67);
        assertEquals(12.34, circle.getLat(), 0.0001);
        assertEquals(56.78, circle.getLon(), 0.0001);
        
        String toString = circle.toString();
        assertTrue(toString.contains("12.34"));
        assertTrue(toString.contains("56.78"));
        assertTrue(toString.contains("12345.67"));
    }

    /**
     * Test avec Java Faker
     */
    @Test
    public void testCircleWithFakerData() {
        Faker faker = new Faker(new Locale("fr-FR"));
        double centerLat = faker.number().randomDouble(4, 41, 51);
        double centerLon = faker.number().randomDouble(4, -5, 9);
        double radius = faker.number().randomDouble(0, 1000, 100000);
        
        Circle circle = new Circle(centerLat, centerLon, radius);
        assertEquals(centerLat, circle.getLat(), 0.0001);
        assertEquals(centerLon, circle.getLon(), 0.0001);
        assertTrue(circle.contains(centerLat, centerLon));
        assertNotNull(circle.toString());
        
        // Test contextuel avec Faker
        System.out.println("Test avec données de: " + faker.address().cityName() + ", " + faker.address().country());
    }
}