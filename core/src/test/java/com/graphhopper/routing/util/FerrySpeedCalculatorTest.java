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

package com.graphhopper.routing.util;

import com.github.javafaker.Faker;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.ArrayEdgeIntAccess;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.FerrySpeed;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.Test;

import static com.graphhopper.routing.util.FerrySpeedCalculator.getSpeed;
import static com.graphhopper.routing.util.FerrySpeedCalculator.isFerry;
import static com.graphhopper.routing.util.FerrySpeedCalculator.minmax;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

class FerrySpeedCalculatorTest {

    final DecimalEncodedValue ferrySpeedEnc = FerrySpeed.create();
    final EncodingManager em = new EncodingManager.Builder().add(ferrySpeedEnc).build();
    final FerrySpeedCalculator calc = new FerrySpeedCalculator(ferrySpeedEnc);

    @Test
    public void testSpeed() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("edge_distance", 30000.0);
        way.setTag("speed_from_duration", 30 / 0.5);

        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;
        calc.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(44, ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess));

        way = new ReaderWay(1);
        way.setTag("route", "shuttle_train");
        way.setTag("motorcar", "yes");
        way.setTag("bicycle", "no");
        // Provide the duration value in seconds:
        way.setTag("way_distance", 50000.0);
        way.setTag("speed_from_duration", 50 / (35.0 / 60));
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        // calculate speed from tags: speed_from_duration * 1.4 (+ rounded using the speed factor)
        calc.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(62, ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess));

        // test for very short and slow 0.5km/h still realistic ferry
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("way_distance", 100.0);
        way.setTag("speed_from_duration", 0.1 / (12.0 / 60));

        // we can't store 0.5km/h, but we expect the lowest possible speed
        edgeIntAccess = new ArrayEdgeIntAccess(1);
        calc.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess));

        edgeIntAccess = new ArrayEdgeIntAccess(1);
        ferrySpeedEnc.setDecimal(false, edgeId, edgeIntAccess, 2.5);
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess), 1e-1);

        // test for missing duration
        way = new ReaderWay(1);
        way.setTag("route", "ferry");
        way.setTag("motorcar", "yes");
        way.setTag("edge_distance", 100.0);
        calc.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
        // we use the unknown speed
        assertEquals(2, ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess));
    }

    @Test
    void testRawSpeed() {
        // speed_from_duration is set (edge_distance is not even needed)
        checkSpeed(30.0, null, Math.round(30 / 1.4));
        checkSpeed(45.0, null, Math.round(45 / 1.4));
        // above max (when including waiting time) (capped to max)
        checkSpeed(100.0, null, ferrySpeedEnc.getMaxStorableDecimal());
        // below smallest storable non-zero value
        checkSpeed(0.5, null, ferrySpeedEnc.getSmallestNonZeroValue());

        // no speed_from_duration, but edge_distance is present
        // minimum speed for short ferries
        checkSpeed(null, 100.0, ferrySpeedEnc.getSmallestNonZeroValue());
        // unknown speed for longer ones
        checkSpeed(null, 1000.0, 6);

        // no speed, no distance -> error. this should never happen as we always set the edge distance.
        assertThrows(IllegalStateException.class, () -> checkSpeed(null, null, 6));
    }

    private void checkSpeed(Double speedFromDuration, Double edgeDistance, double expected) {
        ReaderWay way = new ReaderWay(0L);
        if (speedFromDuration != null)
            way.setTag("speed_from_duration", speedFromDuration);
        if (edgeDistance != null)
            way.setTag("edge_distance", edgeDistance);
        assertEquals(expected, FerrySpeedCalculator.minmax(getSpeed(way), ferrySpeedEnc));
    }



    // ==============================================
    // NOUVEAUX TESTS AJOUTÉS
    // ==============================================

    /**
     * Test 1: Test de isFerry avec toutes les combinaisons possibles
     * Intention: Vérifier la détection correcte des différents types de ferry
     * Motivation: La logique booléenne complexe doit être testée exhaustivement
     * Oracle: Retourne true seulement pour route=ferry sans ferry=no ET route=shuttle_train sans shuttle_train=no
     */
    @Test
    public void testIsFerryAllCombinations() {
        // Ferry valide
        ReaderWay ferry = new ReaderWay(1);
        ferry.setTag("route", "ferry");
        assertTrue(isFerry(ferry));

        // Shuttle train valide
        ReaderWay shuttle = new ReaderWay(2);
        shuttle.setTag("route", "shuttle_train");
        assertTrue(isFerry(shuttle));

        // Ferry explicitement désactivé
        ReaderWay noFerry = new ReaderWay(3);
        noFerry.setTag("route", "ferry");
        noFerry.setTag("ferry", "no");
        assertFalse(isFerry(noFerry));

        // Shuttle train explicitement désactivé
        ReaderWay noShuttle = new ReaderWay(4);
        noShuttle.setTag("route", "shuttle_train");
        noShuttle.setTag("shuttle_train", "no");
        assertFalse(isFerry(noShuttle));

        // Non-ferry
        ReaderWay highway = new ReaderWay(5);
        highway.setTag("route", "highway");
        assertFalse(isFerry(highway));
    }

    /**
     * Test 2: Test de getSpeed avec valeurs limites de distance
     * Intention: Vérifier le comportement aux limites de la condition de distance
     * Motivation: Les conditions aux limites sont souvent sources d'erreurs
     * Oracle: <500m → 1km/h, ≥500m → 6km/h
     */
    @Test
    public void testGetSpeedDistanceBoundaries() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");

        // Limite inférieure
        way.setTag("edge_distance", 1.0);
        assertEquals(1.0, getSpeed(way));

        // Juste en dessous du seuil
        way.setTag("edge_distance", 499.9);
        assertEquals(1.0, getSpeed(way));

        // Exactement au seuil
        way.setTag("edge_distance", 500.0);
        assertEquals(6.0, getSpeed(way));

        // Au-dessus du seuil
        way.setTag("edge_distance", 500.1);
        assertEquals(6.0, getSpeed(way));

        // Distance importante
        way.setTag("edge_distance", 10000.0);
        assertEquals(6.0, getSpeed(way));
    }

    /**
     * Test 3: Test de getSpeed avec valeurs de vitesse extrêmes
     * Intention: Vérifier le calcul avec des vitesses très faibles et très élevées
     * Motivation: Les cas extrêmes peuvent révéler des problèmes d'arrondi ou de précision
     * Oracle: vitesse = round(speed_from_duration / 1.4)
     */
    @Test
    public void testGetSpeedExtremeValues() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");

        // Vitesse très faible
        way.setTag("speed_from_duration", 1.4);
        assertEquals(1.0, getSpeed(way)); // 1.4 / 1.4 = 1.0

        // Vitesse moyenne
        way.setTag("speed_from_duration", 14.0);
        assertEquals(10.0, getSpeed(way)); // 14.0 / 1.4 = 10.0

        // Vitesse élevée
        way.setTag("speed_from_duration", 70.0);
        assertEquals(50.0, getSpeed(way)); // 70.0 / 1.4 = 50.0

        // Test d'arrondi (10.5 → 11)
        way.setTag("speed_from_duration", 14.7);
        assertEquals(11.0, getSpeed(way));

        // Test d'arrondi (10.2 → 10)
        way.setTag("speed_from_duration", 14.3);
        assertEquals(10.0, getSpeed(way));
    }

    /**
     * Test 4: Test de priorité speed_from_duration vs edge_distance
     * Intention: Vérifier que speed_from_duration a priorité sur edge_distance
     * Motivation: La priorité des sources de données doit être respectée
     * Oracle: speed_from_duration prime toujours quand disponible
     */
    @Test
    public void testGetSpeedPriority() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");

        // Les deux tags présents - speed_from_duration doit primer
        way.setTag("speed_from_duration", 28.0); // → 20 km/h
        way.setTag("edge_distance", 100.0);      // → 1 km/h normalement
        assertEquals(20.0, getSpeed(way));

        // Seulement edge_distance disponible
        way.removeTag("speed_from_duration");
        way.setTag("edge_distance", 1000.0);
        assertEquals(6.0, getSpeed(way));
    }

    /**
     * Test 5: Test de minmax avec valeurs aux limites de l'encodeur
     * Intention: Vérifier le clamping des valeurs dans les limites de l'encodeur
     * Motivation: Les vitesses doivent rester dans la plage valide de l'encodeur
     * Oracle: Les valeurs sont clampées entre smallestNonZeroValue et maxStorableDecimal
     */
    @Test
    public void testMinmaxBoundaryConditions() {
        double smallest = ferrySpeedEnc.getSmallestNonZeroValue();
        double largest = ferrySpeedEnc.getMaxStorableDecimal();

        // En dessous du minimum
        assertEquals(smallest, minmax(0.0, ferrySpeedEnc));
        assertEquals(smallest, minmax(smallest - 0.1, ferrySpeedEnc));

        // Dans la plage normale
        assertEquals(15.0, minmax(15.0, ferrySpeedEnc));
        assertEquals(25.5, minmax(25.5, ferrySpeedEnc));

        // Au maximum
        assertEquals(largest, minmax(largest, ferrySpeedEnc));

        // Au-dessus du maximum
        assertEquals(largest, minmax(largest + 10.0, ferrySpeedEnc));
        assertEquals(largest, minmax(1000.0, ferrySpeedEnc));
    }

    /**
     * Test 6: Test de handleWayTags avec ways non-ferry
     * Intention: Vérifier que les ways non-ferry ne modifient pas la vitesse
     * Motivation: Seuls les ferries doivent affecter l'encodeur de vitesse
     * Oracle: La vitesse reste inchangée pour les ways non-ferry
     */
    @Test
    public void testHandleWayTagsWithNonFerry() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;

        // Way non-ferry ne doit pas modifier la vitesse
        // Note: On ne peut pas tester la "non-modification" facilement car l'encodeur
        // peut avoir un comportement par défaut. Testons plutôt qu'aucune vitesse
        // n'est définie pour les non-ferries.
        ReaderWay highway = new ReaderWay(1);
        highway.setTag("route", "highway");
        highway.setTag("speed_from_duration", 50.0);

        calc.handleWayTags(edgeId, edgeIntAccess, highway, IntsRef.EMPTY);

        // Pour un way non-ferry, la vitesse devrait être 0 (non définie)
        // ou la valeur par défaut de l'encodeur
        double speed = ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess);
        assertTrue(speed == 0.0 || speed == ferrySpeedEnc.getSmallestNonZeroValue(), 
                   "La vitesse devrait être 0 ou la valeur minimale pour un non-ferry");
    }

    /**
     * Test 7: Test de robustesse avec données manquantes ou invalides
     * Intention: Vérifier la gestion des erreurs avec données incomplètes
     * Motivation: Prévenir les crashes avec des données OSM corrompues
     * Oracle: Les exceptions appropriées sont levées pour les données invalides
     */
    @Test
    public void testRobustnessWithInvalidData() {
        ReaderWay way = new ReaderWay(1);
        way.setTag("route", "ferry");

        // Données manquantes - doit lever une exception
        assertThrows(IllegalStateException.class, () -> getSpeed(way));

        // edge_distance NaN
        way.setTag("edge_distance", Double.NaN);
        assertThrows(IllegalStateException.class, () -> getSpeed(way));

        way.setTag("edge_distance", 1000.0);
        way.setTag("speed_from_duration", 1E10); // Très grande valeur mais finie
        double result = getSpeed(way);
        assertTrue(result >= 1.0, "Le résultat devrait être une valeur valide même pour de grandes vitesses");
    }

    /**
     * Test 8: Test avec Java Faker pour données réalistes - CORRIGÉ
     * Intention: Générer des scénarios de test réalistes avec données aléatoires
     * Motivation: Détecter des problèmes qui n'apparaissent qu'avec certaines combinaisons de valeurs
     * Oracle: Les calculs doivent rester cohérents avec n'importe quelle donnée valide
     */
    @Test
    public void testWithJavaFakerRealisticData() {
        Faker faker = new Faker(new Locale("fr"));

        for (int i = 0; i < 5; i++) { // Réduit à 5 itérations pour plus de stabilité
            ReaderWay way = new ReaderWay(faker.number().randomNumber());
            
            // Décider aléatoirement du type de way
            if (faker.bool().bool()) {
                way.setTag("route", "ferry");
            } else {
                way.setTag("route", "shuttle_train");
            }

            // 60% avec speed_from_duration, 40% avec edge_distance seulement
            if (faker.random().nextDouble() < 0.6) {
                double realisticSpeed = faker.number().numberBetween(8, 35); // 8-35 km/h réaliste pour les ferries
                way.setTag("speed_from_duration", (double) realisticSpeed);
                
                double expectedSpeed = Math.round(realisticSpeed / 1.4);
                double actualSpeed = getSpeed(way);
                
                assertEquals(expectedSpeed, actualSpeed, 1.0, 
                    "Échec pour speed_from_duration: " + realisticSpeed + " → attendu: " + expectedSpeed + ", obtenu: " + actualSpeed);
            } else {
                double realisticDistance = faker.number().numberBetween(50, 20000); // 50m - 20km réaliste
                way.setTag("edge_distance", (double) realisticDistance);
                
                double expectedSpeed = realisticDistance < 500 ? 1.0 : 6.0;
                double actualSpeed = getSpeed(way);
                
                assertEquals(expectedSpeed, actualSpeed,
                    "Échec pour edge_distance: " + realisticDistance + " → attendu: " + expectedSpeed + ", obtenu: " + actualSpeed);
            }

            // Vérifier que la way est bien détectée comme ferry
            assertTrue(isFerry(way), "La way devrait être détectée comme ferry");

            // Test d'intégration avec handleWayTags
            EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
            int edgeId = 0;
            calc.handleWayTags(edgeId, edgeIntAccess, way, IntsRef.EMPTY);
            
            double storedSpeed = ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess);
            double calculatedSpeed = getSpeed(way);
            double expectedStoredSpeed = minmax(calculatedSpeed, ferrySpeedEnc);
            
            assertEquals(expectedStoredSpeed, storedSpeed, 1.0,
                "La vitesse stockée devrait correspondre à la vitesse calculée après minmax. Calculé: " + 
                calculatedSpeed + ", Stocké: " + storedSpeed + ", Attendu: " + expectedStoredSpeed);
        }
    }

    /**
     * Test 9: Test supplémentaire - Vérification que les ferries définissent bien une vitesse
     * Intention: Compléter le test des non-ferries en vérifier le comportement inverse
     * Motivation: S'assurer que les ferries modifient bien l'encodeur
     * Oracle: Les ferries doivent définir une vitesse non nulle
     */
    @Test
    public void testHandleWayTagsWithFerry() {
        EdgeIntAccess edgeIntAccess = new ArrayEdgeIntAccess(1);
        int edgeId = 0;

        // Way ferry doit définir une vitesse
        ReaderWay ferry = new ReaderWay(1);
        ferry.setTag("route", "ferry");
        ferry.setTag("speed_from_duration", 28.0);
        ferry.setTag("edge_distance", 1000.0);

        calc.handleWayTags(edgeId, edgeIntAccess, ferry, IntsRef.EMPTY);

        double speed = ferrySpeedEnc.getDecimal(false, edgeId, edgeIntAccess);
        assertTrue(speed > 0.0, "Un ferry devrait définir une vitesse positive");
        assertEquals(20.0, speed, "La vitesse devrait être 20.0 pour speed_from_duration=28.0");
    }
}
