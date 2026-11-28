/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.CrsInfo
import de.ii.xtraplatform.crs.domain.CrsTransformer
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

class StorageCrsBboxSpec extends Specification {

    static final EpsgCrs STORAGE_CRS = EpsgCrs.of(25832)
    // ETRS89 / UTM zone 32N + DHHN2016 height
    static final EpsgCrs STORAGE_CRS_3D = EpsgCrs.of(5555)

    CrsInfo crsInfo = Stub()
    CrsTransformerFactory factory = Stub()

    def setup() {
        crsInfo.isSupported(_) >> true
        crsInfo.is3d(STORAGE_CRS_3D) >> true
        crsInfo.is3d(_) >> false
    }

    def "no box for a storage CRS that is CRS84 or CRS84h"() {
        given:
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        expect:
        storageCrsBbox.of(BoundingBox.of(5, 50, 6, 51, OgcCrs.CRS84), OgcCrs.CRS84).isEmpty()
        storageCrsBbox.of(BoundingBox.of(5, 50, 0, 6, 51, 100, OgcCrs.CRS84h), OgcCrs.CRS84h).isEmpty()
    }

    def "the box encloses edges that curve in the storage CRS"() {
        given: "a transformation that bends the top edge upwards, with the maximum at its middle"
        factory.getTransformer(OgcCrs.CRS84, STORAGE_CRS, true) >> Optional.of(transformer { x, y -> [x, y + 25 - (x - 5) * (x - 5)] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        when:
        Optional<double[]> bbox = storageCrsBbox.of(BoundingBox.of(0, 0, 10, 10, OgcCrs.CRS84), STORAGE_CRS)

        then: "the corners alone would give a maximum of 10"
        bbox.isPresent()
        bbox.get() as List == [0d, 0d, 10d, 35d]
    }

    def "the box is in the axis order of the storage CRS"() {
        given: "EPSG:4326 without a forced axis order, latitude first"
        EpsgCrs storageCrs = EpsgCrs.of(4326)
        factory.getTransformer(OgcCrs.CRS84, storageCrs, true) >> Optional.of(transformer { x, y -> [y, x] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        when:
        Optional<double[]> bbox = storageCrsBbox.of(BoundingBox.of(5, 50, 6, 51, OgcCrs.CRS84), storageCrs)

        then:
        bbox.get() as List == [50d, 5d, 51d, 6d]
    }

    def "a forced axis order of the provider is ignored, the box follows the storageCrs URI"() {
        given:
        factory.getTransformer(OgcCrs.CRS84, STORAGE_CRS, true) >> Optional.of(transformer { x, y -> [x * 2, y * 2] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        when:
        Optional<double[]> bbox = storageCrsBbox.of(BoundingBox.of(1, 2, 3, 4, OgcCrs.CRS84), EpsgCrs.of(25832, EpsgCrs.Force.LON_LAT))

        then:
        bbox.get() as List == [2d, 4d, 6d, 8d]
    }

    def "a 3D extent gives a 2D box for a 2D storage CRS"() {
        given:
        factory.getTransformer(OgcCrs.CRS84, STORAGE_CRS, true) >> Optional.of(transformer { x, y -> [x, y] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        when:
        Optional<double[]> bbox = storageCrsBbox.of(BoundingBox.of(1, 2, 10, 3, 4, 20, OgcCrs.CRS84h), STORAGE_CRS)

        then:
        bbox.get() as List == [1d, 2d, 3d, 4d]
    }

    def "a 3D extent gives a 3D box for a 3D storage CRS"() {
        given: "a transformation that also changes the heights"
        factory.getTransformer(OgcCrs.CRS84h, STORAGE_CRS_3D) >> Optional.of(transformer3d { x, y, z -> [x * 2, y * 2, z - 40] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        when:
        Optional<double[]> bbox = storageCrsBbox.of(BoundingBox.of(1, 2, 10, 3, 4, 20, OgcCrs.CRS84h), STORAGE_CRS_3D)

        then:
        bbox.get() as List == [2d, 4d, -30d, 6d, 8d, -20d]
    }

    def "no box for a 3D storage CRS if the extent has no heights"() {
        given:
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        expect:
        storageCrsBbox.of(BoundingBox.of(1, 2, 3, 4, OgcCrs.CRS84), STORAGE_CRS_3D).isEmpty()
    }

    def "no box if a point cannot be transformed"() {
        given:
        factory.getTransformer(OgcCrs.CRS84, STORAGE_CRS, true) >> Optional.of(transformer { x, y -> [x > 2 ? Double.POSITIVE_INFINITY : x, y] })
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        expect:
        storageCrsBbox.of(BoundingBox.of(1, 2, 3, 4, OgcCrs.CRS84), STORAGE_CRS).isEmpty()
    }

    def "no box for an extent that crosses the antimeridian"() {
        given:
        StorageCrsBbox storageCrsBbox = new StorageCrsBbox(factory, crsInfo)

        expect:
        storageCrsBbox.of(BoundingBox.of(170, 2, -170, 4, OgcCrs.CRS84), STORAGE_CRS).isEmpty()
    }

    private CrsTransformer transformer(Closure<List<Double>> point) {
        CrsTransformer transformer = Stub()
        transformer.transform(_ as double[], _ as int, _ as int) >> { double[] coordinates, int numberOfPoints, int dimension ->
            double[] result = new double[coordinates.length]
            for (int i = 0; i < numberOfPoints; i++) {
                List<Double> transformed = point.call(coordinates[i * dimension], coordinates[i * dimension + 1])
                result[i * dimension] = transformed[0]
                result[i * dimension + 1] = transformed[1]
            }
            return result
        }
        return transformer
    }

    private CrsTransformer transformer3d(Closure<List<Double>> point) {
        CrsTransformer transformer = Stub()
        transformer.transform(_ as double[], _ as int, _ as int) >> { double[] coordinates, int numberOfPoints, int dimension ->
            assert dimension == 3
            double[] result = new double[coordinates.length]
            for (int i = 0; i < numberOfPoints; i++) {
                List<Double> transformed = point.call(coordinates[i * 3], coordinates[i * 3 + 1], coordinates[i * 3 + 2])
                (0..2).each { result[i * 3 + it] = transformed[it] }
            }
            return result
        }
        return transformer
    }
}
