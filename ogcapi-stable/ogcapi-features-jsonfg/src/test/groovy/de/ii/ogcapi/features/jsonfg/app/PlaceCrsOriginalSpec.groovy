/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson
import de.ii.ogcapi.features.geojson.domain.ImmutableFeatureTransformationContextGeoJson
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration
import de.ii.ogcapi.foundation.app.OgcApiEntity
import de.ii.ogcapi.foundation.domain.*
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableCrsVariants
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.geometries.domain.GeometryType
import de.ii.xtraplatform.geometries.domain.Point
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.function.Consumer

/**
 * {@link JsonFgWriterPlace} with the crs-original profile: a stored original position is written
 * to "place" with the verbatim CRS identifier in "coordRefSys" and the coordinates in the
 * convention of that identifier (inverse false-easting shift); the default place behaviour is
 * suppressed for the feature; without the profile nothing changes.
 */
class PlaceCrsOriginalSpec extends Specification {

    static final String GK3_HE100 = 'urn:adv:crs:DE_DHDN_3GK3_HE100'

    static FeatureSchema featureSchema() {
        new ImmutableFeatureSchema.Builder()
                .name("ax_punktortau")
                .sourcePath("/o14003")
                .type(SchemaBase.Type.OBJECT)
                .putProperties2("pos_srs", new ImmutableFeatureSchema.Builder()
                        .addPath("pos_srs")
                        .sourcePath("position_srs")
                        .type(SchemaBase.Type.STRING)
                        .role(SchemaBase.Role.ORIGINAL_CRS_IDENTIFIER))
                .putProperties2("pos_gk3", new ImmutableFeatureSchema.Builder()
                        .addPath("pos_gk3")
                        .sourcePath("position_gk3")
                        .type(SchemaBase.Type.GEOMETRY)
                        .geometryType(GeometryType.POINT)
                        .role(SchemaBase.Role.ORIGINAL_GEOMETRY)
                        .nativeCrs(EpsgCrs.of(5677))
                        .addOriginalCrsIdentifiers(GK3_HE100)
                        .falseEastingDifference(3000000d))
                .putProperties2("position", new ImmutableFeatureSchema.Builder()
                        .addPath("position")
                        .sourcePath("position")
                        .type(SchemaBase.Type.GEOMETRY)
                        .role(SchemaBase.Role.PRIMARY_GEOMETRY)
                        .geometryType(GeometryType.POINT)
                        .crsVariants(new ImmutableCrsVariants.Builder()
                                .crsProperty("pos_srs")
                                .addGeometryProperties("pos_gk3")
                                .build()))
                .build()
    }

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
    FeatureTransformationContextGeoJson transformationContext
    JsonFgWriterPlace writer

    def setup() {
        def crsOriginalProfile = Stub(Profile)
        crsOriginalProfile.getId() >> 'crs-original'
        def schema = featureSchema()
        transformationContext = ImmutableFeatureTransformationContextGeoJson.builder()
                .crsTransformer(Optional.empty())
                .defaultCrs(OgcCrs.CRS84)
                .addProfiles(new ProfileJsonFg(null) {
                    @Override
                    String getId() {
                        return "jsonfg"
                    }
                })
                .addProfiles(crsOriginalProfile)
                .api(new OgcApiEntity(null, null, null, new AppContextTest(), null, new CacheTest(), null))
                .apiData(new ImmutableOgcApiDataV2.Builder()
                        .id("s")
                        .serviceType("OGC_API")
                        .addExtensions(new ImmutableJsonFgConfiguration.Builder().enabled(true).build())
                        .build())
                .featureSchemas(ImmutableMap.of("xyz", Optional.of(schema)))
                .outputStream(outputStream)
                .links(ImmutableList.of())
                .isFeatureCollection(false)
                .limit(10)
                .offset(0)
                .ogcApiRequest(Stub(ApiRequestContext))
                .build()
        writer = new JsonFgWriterPlace()
    }

    def context(FeatureSchema prop, Object geometryOrValue) {
        def ctx = Stub(EncodingAwareContextGeoJson)
        ctx.encoding() >> transformationContext
        ctx.schema() >> Optional.of(prop)
        ctx.type() >> "xyz"
        if (geometryOrValue instanceof de.ii.xtraplatform.geometries.domain.Geometry) {
            ctx.geometry() >> geometryOrValue
        } else {
            ctx.value() >> (geometryOrValue as String)
        }
        return ctx
    }

    def startFeature() {
        def startCtx = Stub(EncodingAwareContextGeoJson)
        startCtx.encoding() >> transformationContext
        writer.onStart(startCtx, {} as Consumer)
        writer.onFeatureStart(startCtx, {} as Consumer)
        // the feature object is opened on the real generator before buffering starts, so the
        // writer's direct (pause-buffered) writes land inside an open JSON object
        transformationContext.getJson().writeStartObject()
        transformationContext.pushBuffer(featureSchema(), false)
    }

    String output() {
        transformationContext.getJson().flush()
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8)
    }

    def 'a stored original position is written to place with coordRefSys and inverse shift'() {
        given:
        def schema = featureSchema()
        def next = Mock(Consumer)
        startFeature()

        when:
        writer.onValue(context(schema.getPropertyMap()['pos_srs'], GK3_HE100), next)
        writer.onGeometry(context(schema.getPropertyMap()['pos_gk3'],
                Point.of(3446104.62d, 5551059.77d, EpsgCrs.of(5677))), next)
        def json = output()

        then: 'the value token is forwarded, the variant geometry token is consumed'
        1 * next.accept(_)

        and:
        json.contains('"place":{"coordRefSys":"' + GK3_HE100 + '","type":"Point","coordinates":[446104.62,5551059.77]}')
    }

    def 'the default place behaviour is suppressed after a variant was written'() {
        given:
        def schema = featureSchema()
        def next = Mock(Consumer)
        startFeature()

        when:
        writer.onValue(context(schema.getPropertyMap()['pos_srs'], GK3_HE100), next)
        writer.onGeometry(context(schema.getPropertyMap()['pos_gk3'],
                Point.of(3446104.62d, 5551059.77d, EpsgCrs.of(5677))), next)
        writer.onGeometry(context(schema.getPropertyMap()['position'],
                Point.of(446051.2d, 5549279.4d, EpsgCrs.of(25832))), next)
        def json = output()

        then: 'the primary geometry token passes through to the next writer without a second place'
        2 * next.accept(_)

        and:
        json.count('"place"') == 1
    }

    def 'a native row keeps the standard behaviour'() {
        given:
        def schema = featureSchema()
        def next = Mock(Consumer)
        startFeature()

        when: 'no variant token for this feature, target CRS is CRS84'
        writer.onGeometry(context(schema.getPropertyMap()['position'],
                Point.of(8.19d, 50.04d, OgcCrs.CRS84)), next)
        def json = output()

        then: 'the primary geometry is not written to place (CRS84 goes to "geometry")'
        1 * next.accept(_)

        and:
        !json.contains('"place"')
    }
}
