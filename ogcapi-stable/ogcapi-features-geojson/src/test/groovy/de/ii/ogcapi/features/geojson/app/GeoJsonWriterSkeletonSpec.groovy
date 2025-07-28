/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson
import de.ii.ogcapi.features.geojson.domain.FeatureEncoderGeoJson
import spock.lang.Ignore
import spock.lang.Specification

@Ignore //TODO
class GeoJsonWriterSkeletonSpec extends Specification {

    def "GeoJsonWriterSkeleton middleware given a feature collection"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                "  \"features\" : [ {" + System.lineSeparator() +
                "    \"type\" : \"Feature\"" + System.lineSeparator() +
                "  } ]" + System.lineSeparator() +
                "}"


        when:
        writeFeature(outputStream, true)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }


    def "GeoJsonWriterSkeleton middleware given a single feature"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"Feature\"" + System.lineSeparator() +
                "}"


        when:
        writeFeature(outputStream, false)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }


    private void writeFeature(ByteArrayOutputStream outputStream,
                              boolean isCollection) throws IOException, URISyntaxException {
        EncodingAwareContextGeoJson context = GeoJsonWriterSetupUtil.createTransformationContext(outputStream, isCollection)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new GeoJsonWriterSkeleton()));

        encoder.onStart(context)
        encoder.onFeatureStart(context)
        encoder.onFeatureEnd(context)
        encoder.onEnd(context)
    }

}
