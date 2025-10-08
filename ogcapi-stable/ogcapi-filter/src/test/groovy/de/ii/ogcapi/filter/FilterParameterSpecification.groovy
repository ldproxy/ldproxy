/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter

import de.ii.ogcapi.common.domain.OgcApiDatasetView
import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.cql.domain.Cql
import groovyx.net.http.ContentType
import groovyx.net.http.Method
import groovyx.net.http.RESTClient
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.IntStream

/** These test assume that the following APIs are available:
 * <ul>
 *     <li>daraa (same data and configuration as https://demo.ldproxy.net/daraa)
 *     <li>geoinfodok (same data and configuration as https://demo.ldproxy.net/geoinfodok)
 *     <li>cshapes (same data and configuration as https://demo.ldproxy.net/cshapes)
 * </ul>
 */
@Requires({env['SUT_URL'] != null})
class FilterParameterSpecification extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String API_PATH_DARAA = "/daraa"
    static final String CULTURE_PNT = "CulturePnt"
    static final String AERONAUTIC_CRV = "AeronauticCrv"
    static final String CULTURE_PNT_PATH = API_PATH_DARAA + "/collections/" + CULTURE_PNT + "/items"
    static final String AERONAUTIC_CRV_PATH = API_PATH_DARAA + "/collections/" + AERONAUTIC_CRV + "/items"
    static final String API_PATH_GEOINFODOK = "/geoinfodok"
    static final String AX_GEBAEUDEFUNKTION = "ax_gebaeudefunktion"
    static final String AX_GEBAEUDEFUNKTION_PATH = API_PATH_GEOINFODOK + "/collections/" + AX_GEBAEUDEFUNKTION + "/items"
    static final String API_PATH_CSHAPES = "/cshapes"
    static final String BOUNDARY = "boundary"
    static final String BOUNDARY_PATH = API_PATH_CSHAPES + "/collections/" + BOUNDARY + "/items"
    static final String GEO_JSON = "application/geo+json";
    static final String JSON = "application/json";
    static final String PROBLEM_JSON = "application/problem+json";
    static final boolean json = true // set to true to test CQL JSON, to false to test CQL Text

    static boolean testNestedFilters() {
        return !json;
    }

    @Shared
    Cql cql = new CqlImpl()
    @Shared
    int limit = 250
    @Shared
    RESTClient restClient = new RESTClient(SUT_URL)
    @Shared
    def allCulturePntFeatures = getRequest(restClient, CULTURE_PNT_PATH, null)
    @Shared
    def allAeronauticCrvFeatures = getRequest(restClient, AERONAUTIC_CRV_PATH, [limit:limit])
    @Shared
    def collection = getRequest(restClient, API_PATH_DARAA + "/collections/" + AERONAUTIC_CRV, null)
    @Shared
    def envelopeCollection = "BBOX(" + String.join(",", collection.responseData.extent.spatial.bbox[0].stream().map(n -> String.valueOf(n)).toList()) + ")"
    @Shared
    def idPnt = allCulturePntFeatures.responseData.features[0].id
    @Shared
    def lonPnt = allCulturePntFeatures.responseData.features[0].geometry.coordinates[0] as double
    @Shared
    def latPnt = allCulturePntFeatures.responseData.features[0].geometry.coordinates[1] as double
    @Shared
    def idCrv = allAeronauticCrvFeatures.responseData.features[7].id
    @Shared
    def lonCrv = allAeronauticCrvFeatures.responseData.features[7].geometry.coordinates[0][0][0] as double
    @Shared
    def latCrv = allAeronauticCrvFeatures.responseData.features[7].geometry.coordinates[0][0][1] as double
    @Shared
    def delta = 0.02
    @Shared
    def envelopeCrv = "BBOX(" + String.join(",", String.valueOf(lonCrv-delta), String.valueOf(latCrv-delta), String.valueOf(lonCrv+delta), String.valueOf(latCrv+delta)) + ")"
    @Shared
    def polygonCrv = "POLYGON((" + String.join(",",
            String.valueOf(lonCrv-delta)+" "+String.valueOf(latCrv),
            String.valueOf(lonCrv)+" "+String.valueOf(latCrv-delta),
            String.valueOf(lonCrv+delta)+" "+String.valueOf(latCrv),
            String.valueOf(lonCrv)+" "+String.valueOf(latCrv+delta),
            String.valueOf(lonCrv-delta)+" "+String.valueOf(latCrv)) + "))"
    @Shared
    def pointCrv = "POINT(" + String.valueOf(lonCrv) + " " + String.valueOf(latCrv) + ")"
    @Shared
    def pointPnt = "POINT(" + String.valueOf(lonPnt) + " " + String.valueOf(latPnt) + ")"
    @Shared
    def epsg4326 = "http://www.opengis.net/def/crs/EPSG/0/4326"
    @Shared
    def envelopeCrv4326 = "BBOX(" + String.join(",", String.valueOf(latCrv-delta), String.valueOf(lonCrv-delta), String.valueOf(latCrv+delta), String.valueOf(lonCrv+delta)) + ")"
    @Shared
    def polygonCrv4326 = "POLYGON((" + String.join(",",
            String.valueOf(latCrv)+" "+String.valueOf(lonCrv-delta),
            String.valueOf(latCrv-delta)+" "+String.valueOf(lonCrv),
            String.valueOf(latCrv)+" "+String.valueOf(lonCrv+delta),
            String.valueOf(latCrv+delta)+" "+String.valueOf(lonCrv),
            String.valueOf(latCrv)+" "+String.valueOf(lonCrv-delta)) + "))"
    @Shared
    def pointCrv4326 = "POINT(" + String.valueOf(latCrv) + " " + String.valueOf(lonCrv) + ")"
    @Shared
    def pointPnt4326 = "POINT(" + String.valueOf(latPnt) + " " + String.valueOf(lonPnt) + ")"
    @Shared
    def allAxGebaeudefunktion = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, [limit:limit])
    @Shared
    def allBoundaries = getRequest(restClient, BOUNDARY_PATH, [limit:limit])

    def "Preconditions Daraa"() {
        given: "CulturePnt and AeronauticCrv features in the Daraa dataset"

        when: "Fetch all features"

        then: "Success and returns JSON"
        assertSuccess(allCulturePntFeatures)
        assertSuccess(allAeronauticCrvFeatures)
        assertSuccess(collection)
    }

    def "Preconditions GeoInfoDok"() {
        given: "AX_Gebaeudefunktion records"

        when: "Fetch all records"

        then: "Success and returns JSON"
        assertSuccess(allAxGebaeudefunktion)
    }

    def "Preconditions CShapes"() {
        given: "Boundary features"

        when: "Fetch all features"

        then: "Success and returns JSON"
        assertSuccess(allBoundaries)
    }

    // Comparison predicates

    def "Operator eq"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE=F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE=F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "2. Data is selected using a filter F_CODE='AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE='AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.F_CODE=='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using F_CODE=AL030"
        def usingQueryParam = getRequest(restClient, CULTURE_PNT_PATH, [F_CODE:"AL030"])

        then: "Success and returns GeoJSON"
        assertSuccess(usingQueryParam)

        and: "Returns the same number of features as a filter"
        propertyAndLiteralString.responseData.numberReturned == usingQueryParam.responseData.numberReturned

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], usingQueryParam.responseData.features[i])
        }

        when: "4. Data is selected using a filter ZI037_REL=11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL=11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI037_REL==11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'=F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'=F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns one feature"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected feature"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'='A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'='A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns all features"
        literals.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "7. Data is selected using a filter \"F_CODE\"='AL030' with a double quote"
        def propertyAndLiteral2String = getRequest(restClient, CULTURE_PNT_PATH, getQuery("\"F_CODE\"='AL030'"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2String)

        and: "Returns the same number of features"
        propertyAndLiteral2String.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2String.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2String.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "8. Data is selected using a filter ZI001_SDV=TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV=TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV=='2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator neq"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE<>F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<>F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns no features"
        twoProperties.responseData.numberReturned == 0

        when: "2. Data is selected using a filter F_CODE<>'AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<>'AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE!='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL<>11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<>11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL!=11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL<>10"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<>10"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL!=10 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'<>F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'<>F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'<>'A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'<>'A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns no features"
        literals.responseData.numberReturned == 0

        when: "7. Data is selected using a filter ZI001_SDV<>TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV<>TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV!='2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator lt"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE<F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns no features"
        twoProperties.responseData.numberReturned == 0

        when: "2. Data is selected using a filter F_CODE<'AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<'AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE<'AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL<11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL<11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL<12"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<12"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL<12 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'<F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'<F_CODE"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE>'AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'<'A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'<'A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns no features"
        literals.responseData.numberReturned == 0

        when: "7. Data is selected using a filter ZI001_SDV<TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV<TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV<'2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator gt"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE>F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE>F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns no features"
        twoProperties.responseData.numberReturned == 0

        when: "2. Data is selected using a filter F_CODE>'AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE>'AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE>'AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL>11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL>11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL>0"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL>0"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>0 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'>F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'>F_CODE"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE<'AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'>'A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'>'A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns n0 features"
        literals.responseData.numberReturned == 0

        when: "7. Data is selected using a filter ZI001_SDV>TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV>TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV>'2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator lteq"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE<=F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<=F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "2. Data is selected using a filter F_CODE<='AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE<='AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE<='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL<=11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<=11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL<=11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL<=10"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL<=10"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL<=10 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'<=F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'<=F_CODE"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE>='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'<='A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'<='A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns all features"
        literals.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "7. Data is selected using a filter ZI001_SDV<=TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV<=TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV<='2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator gteq"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE>=F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE>=F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "2. Data is selected using a filter F_CODE<='AL030'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE>='AL030'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE>='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL>=11"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL>=11"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>=11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL>=12"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL>=12"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>=12 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }

        when: "5. Data is selected using a filter 'AL030'>=F_CODE"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'AL030'>=F_CODE"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE<='AL030' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == propertyAndLiteralString.responseData.numberReturned

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "6. Data is selected using a filter 'A'>='A'"
        def literals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("'A'>='A'"))

        then: "Success and returns GeoJSON"
        assertSuccess(literals)

        and: "Returns all features"
        literals.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "7. Data is selected using a filter ZI001_SDV>=TIMESTAMP('2011-12-26T20:55:27Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV>=TIMESTAMP('2011-12-26T20:55:27Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV>='2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
    }

    def "Operator like"() {
        given: "CulturePnt features in the Daraa dataset"

        /* no longer allowed in the grammar
        when: "1. Data is selected using a filter F_CODE LiKe F_CODE"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe F_CODE"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned
         */

        when: "2. Data is selected using a filter F_CODE LiKe 'AL0%'"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe 'AL0%'"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE.startsWith('AL0') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "3. Data is selected using a filter F_CODE LiKe 'AL0%'"
        def propertyAndLiteralString2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe 'AL0%'"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString2)

        and: "Returns the same number of features"
        propertyAndLiteralString2.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString2.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "4. Data is selected using a filter F_CODE LiKe 'AL0__'"
        def propertyAndLiteralString3 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe 'AL0__'"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString3)

        and: "Returns the same number of features"
        propertyAndLiteralString3.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString3.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString3.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "5. Data is selected using a filter CASEI(F_CODE) LiKe casei('al0__')"
        def propertyAndLiteralString4 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("CASEI(F_CODE) LiKe casei('al0__')"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString4)

        and: "Returns the same number of features"
        propertyAndLiteralString4.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString4.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString4.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "6. Data is selected using a filter CASEI(F_CODE) LiKe casei('al0%')"
        def propertyAndLiteralString5 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("CASEI(F_CODE) LiKe casei('al0__')"))
        def propertyAndLiteralString5Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE.toLowerCase().startsWith('al0') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString5)

        and: "Returns the same number of features"
        propertyAndLiteralString5.responseData.numberReturned == propertyAndLiteralString5Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString5.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString5.responseData.features[i], propertyAndLiteralString5Check.get(i))
        }

        when: "7. Data is selected using a filter F_CODE LiKe 'al0%'"
        def propertyAndLiteralString6 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe 'al0%'"))
        def propertyAndLiteralString6Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE.startsWith('al0') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString6)

        and: "Returns the same number of features"
        propertyAndLiteralString6.responseData.numberReturned == propertyAndLiteralString6Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString6.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString6.responseData.features[i], propertyAndLiteralString6Check.get(i))
        }

        when: "8. Data is selected using a filter F_CODE LiKe '%''%'"
        def propertyAndLiteralString7 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE LiKe '%''%'"))
        def propertyAndLiteralString7Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE.contains('\'') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString7)

        and: "Returns the same number of features"
        propertyAndLiteralString7.responseData.numberReturned == propertyAndLiteralString7Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString7.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString7.responseData.features[i], propertyAndLiteralString7Check.get(i))
        }
    }

    def "Case-insensistive comparisons"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter casei(title) LIKE CaSeI('%GEBÄUDE%')"
        def casei = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("casei(title) LIKE CaSeI('%GEBÄUDE%')"))
        def caseiCheck = allAxGebaeudefunktion.responseData.features.stream()
                .filter(f -> f.properties.title.toLowerCase().contains('gebäude'))
                .toList()

        then: "Success and returns GeoJSON"
        assertSuccess(casei)

        and: "Returns the same number of records"
        casei.responseData.numberReturned == caseiCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<casei.responseData.numberReturned; i++) {
            assertFeature(casei.responseData.features[i], caseiCheck.get(i))
        }
    }

    // TODO set up DB with PostgreSQL 12+ and accent-insensitve collation
    def "Accent-insensitive comparisons"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter accenti(title) LIKE aCcEnTi('%gebäude%')"
        def accenti = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("accenti(title) LIKE aCcEnTi('%gebäude%')"))
        def accentiCheck = allAxGebaeudefunktion.responseData.features.stream()
                .filter(f -> f.properties.title.contains('gebäude') || f.properties.title.contains('gebaude'))
                .toList()
        then:
        thrown Exception

        /* TODO
        then: "Success and returns GeoJSON"
        assertSuccess(accenti)

        and: "Returns the same number of records"
        accenti.responseData.numberReturned == accentiCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<accenti.responseData.numberReturned; i++) {
            assertFeature(accenti.responseData.features[i], accentiCheck.get(i))
        }
         */

        // note that LOWER('Ä')='Ä'
        when: "3. Data is selected using a filter casei(accenti(title)) LIKE cAsEi(aCcEnTi('%GEBäUDE%'))"
        def caseiaccenti = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("casei(accenti(title)) LIKE cAsEi(aCcEnTi('%GEBäUDE%'))"))
        def caseiaccentiCheck = allAxGebaeudefunktion.responseData.features.stream()
                .filter(f -> f.properties.title.toLowerCase().contains('gebaude') || f.properties.title.toLowerCase().contains('gebäude'))
                .toList()

        then:
        thrown Exception

        /* TODO
        then: "Success and returns GeoJSON"
        assertSuccess(caseiaccenti)

        and: "Returns the same number of records"
        caseiaccenti.responseData.numberReturned == caseiaccentiCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<caseiaccenti.responseData.numberReturned; i++) {
            assertFeature(caseiaccenti.responseData.features[i], caseiaccentiCheck.get(i))
        }
         */
    }

    def "Operator between"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter ZI037_REL BeTweeN ZI037_REL AnD ZI037_REL"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL BeTweeN ZI037_REL AnD ZI037_REL"))
        def twoPropertiesCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features that are not null"
        twoProperties.responseData.numberReturned == twoPropertiesCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<twoProperties.responseData.numberReturned; i++) {
            assertFeature(twoProperties.responseData.features[i], twoPropertiesCheck.get(i))
        }

        when: "2. Data is selected using a filter ZI037_REL NoT BeTweeN ZI037_REL AnD ZI037_REL"
        def twoProperties2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL NoT BeTweeN ZI037_REL AnD ZI037_REL"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties2)

        and: "Returns no features"
        twoProperties2.responseData.numberReturned == 0

        when: "3. Data is selected using a filter ZI037_REL BeTweeN 0 AnD 10"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL BeTweeN 0 AnD 10"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>=0 && f.properties.ZI037_REL<=10 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL BeTweeN 0 AnD 11"
        def propertyAndLiteral2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL BeTweeN 0 AnD 11"))
        def propertyAndLiteral2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>=0 && f.properties.ZI037_REL<=11 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of features"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteral2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteral2Check.get(i))
        }

        when: "5. Data is selected using a filter ZI037_REL NoT BeTweeN 0 AnD 10"
        def propertyAndLiteral3 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL NoT BeTweeN 0 AnD 10"))
        def propertyAndLiteral3Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && !(f.properties.ZI037_REL>=0 && f.properties.ZI037_REL<=10) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral3)

        and: "Returns the same number of features"
        propertyAndLiteral3.responseData.numberReturned == propertyAndLiteral3Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral3.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral3.responseData.features[i], propertyAndLiteral3Check.get(i))
        }

        when: "6. Data is selected using a filter ZI037_REL NoT BeTweeN 0 AnD 11"
        def propertyAndLiteral4 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL NoT BeTweeN 0 AnD 11"))
        def propertyAndLiteral4Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && !(f.properties.ZI037_REL>=0 && f.properties.ZI037_REL<=11) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral4)

        and: "Returns the same number of features"
        propertyAndLiteral4.responseData.numberReturned == propertyAndLiteral4Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral4.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral4.responseData.features[i], propertyAndLiteral4Check.get(i))
        }

        when: "7. Data is selected using a filter 6 BeTweeN 0 AnD ZI037_REL"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("6 BeTweeN 0 AnD ZI037_REL"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && f.properties.ZI037_REL>=6 ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == literalAndPropertyCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        /* disabled since BETWEEN only supports numbers in CQL2
        when: "7. Data is selected using a filter ZI001_SDV BETWEEN TIMESTAMP('2011-01-01T00:00:00Z') AND TIMESTAMP('2012-01-01T00:00:00Z')"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV BETWEEN TIMESTAMP('2011-01-01T00:00:00Z') AND TIMESTAMP('2012-01-01T00:00:00Z')"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV.startsWith('2011')).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }
         */
    }

    def "Operator in"() {
        given: "CulturePnt features in the Daraa dataset"

        // TODO currently restricted to a property on the left side and literals on the right side

        when: "1. Data is selected using a filter CASEI(F_CODE) iN (CASEI('AL030'), cAsEi('AL012'))"
        def propertyAndLiteralString = getRequest(restClient, CULTURE_PNT_PATH, getQuery("CASEI(F_CODE) iN (CASEI('AL030'), cAsEi('AL012'))"))
        def propertyAndLiteralStringCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.F_CODE.equals('AL012') || f.properties.F_CODE.equals('AL030') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString)

        and: "Returns the same number of features"
        propertyAndLiteralString.responseData.numberReturned == propertyAndLiteralStringCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString.responseData.features[i], propertyAndLiteralStringCheck.get(i))
        }

        when: "2. Data is selected using a filter F_CODE NoT iN ('AL030', 'AL012')"
        def propertyAndLiteralString2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("F_CODE NoT iN ('AL030', 'AL012')"))
        def propertyAndLiteralString2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> !f.properties.F_CODE.equals('AL012') && !f.properties.F_CODE.equals('AL030') ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralString2)

        and: "Returns the same number of features"
        propertyAndLiteralString2.responseData.numberReturned == propertyAndLiteralString2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralString2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralString2.responseData.features[i], propertyAndLiteralString2Check.get(i))
        }

        when: "3. Data is selected using a filter ZI037_REL iN (11, 12)"
        def propertyAndLiteralNumeric = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL in (11, 12)"))
        def propertyAndLiteralNumericCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && (f.properties.ZI037_REL==11 || f.properties.ZI037_REL==12) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric.responseData.numberReturned == propertyAndLiteralNumericCheck.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric.responseData.features[i], propertyAndLiteralNumericCheck.get(i))
        }

        when: "4. Data is selected using a filter ZI037_REL NoT iN (11, 12)"
        def propertyAndLiteralNumeric2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL NoT iN (11, 12)"))
        def propertyAndLiteralNumeric2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) && !(f.properties.ZI037_REL==11 || f.properties.ZI037_REL==12) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNumeric2)

        and: "Returns the same number of features"
        propertyAndLiteralNumeric2.responseData.numberReturned == propertyAndLiteralNumeric2Check.size()

        and: "Returns the expected features"
        for (int i=0; i<propertyAndLiteralNumeric2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNumeric2.responseData.features[i], propertyAndLiteralNumeric2Check.get(i))
        }
        when: "7. Data is selected using a filter ZI001_SDV IN (TIMESTAMP('2011-12-26T20:55:27Z'),TIMESTAMP('2021-10-10T10:10:10Z'),TIMESTAMP('2011-12-27T18:39:59Z'))"
        def temporalProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI001_SDV  IN (TIMESTAMP('2011-12-26T20:55:27Z'),TIMESTAMP('2021-10-10T10:10:10Z'),TIMESTAMP('2011-12-27T18:39:59Z'))"))
        def temporalPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.ZI001_SDV=='2011-12-26T20:55:27Z' ||  f.properties.ZI001_SDV=='2021-10-10T10:10:10Z' || f.properties.ZI001_SDV=='2011-12-27T18:39:59Z').toList()

        then: "Success and returns GeoJSON"
        assertSuccess(temporalProperty)

        and: "Returns the same number of features"
        temporalProperty.responseData.numberReturned == temporalPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<temporalProperty.responseData.numberReturned; i++) {
            assertFeature(temporalProperty.responseData.features[i], temporalPropertyCheck.get(i))
        }

    }

    def "Operator null"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter ZI037_REL iS NulL"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL iS NulL"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.isNull(f.properties.ZI037_REL) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter ZI037_REL iS NoT NulL"
        def propertyAndLiteral2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("ZI037_REL iS NoT NulL"))
        def propertyAndLiteral2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> Objects.nonNull(f.properties.ZI037_REL) ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of features"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteral2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteral2Check.get(i))
        }
    }

    // Spatial predicates, including filter-crs

    def "Operator S_INTERSECTS"() {
        given: "AeronauticCrv features in the Daraa dataset"

        when: "1. Data is selected using a filter s_InterSectS(geometry,geometry)"
        def twoProperties = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(geometry,geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned

        when: "2. Data is selected using a filter s_InterSectS(geometry,<bbox of collection>)"
        def propertyAndLiteral = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(geometry," + envelopeCollection + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned

        when: "3. Data is selected using a filter s_InterSectS(<bbox of collection>,geometry)"
        def propertyAndLiterala = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(" + envelopeCollection + ",geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiterala)

        and: "Returns the same number of features"
        propertyAndLiterala.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned

        when: "4. Data is selected using a filter s_InterSectS(geometry,<bbox around first feature>)"
        def propertyAndLiteral2 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(geometry," + envelopeCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the feature"
        propertyAndLiteral2.responseData.features.stream().anyMatch(f -> f.getName == idCrv)

        when: "5. Data is selected using a filter s_InterSectS(<bbox around first feature>,geometry)"
        def propertyAndLiteral2a = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(" + envelopeCrv + ",geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2a)

        and: "Returns the feature"
        propertyAndLiteral2a.responseData.features.stream().anyMatch(f -> f.getName == idCrv)

        when: "6. The same request using EPSG:4326"
        def propertyAndLiteral2b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326( "s_InterSectS(geometry," + envelopeCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral2, propertyAndLiteral2b)

        when: "7. Data is selected using a filter s_InterSectS(geometry,<polygon around first feature>)"
        def propertyAndLiteral3 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(geometry," + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral3)

        and: "Returns the feature"
        propertyAndLiteral3.responseData.features.stream().anyMatch(f -> f.getName == idCrv)

        when: "8. The same request using EPSG:4326"
        def propertyAndLiteral3b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326( "s_InterSectS(geometry," + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral3b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral3, propertyAndLiteral3b)
    }

    def "Operator S_DISJOINT"() {
        given: "AeronauticCrv features in the Daraa dataset"

        when: "1. Data is selected using a filter NoT s_DisJoinT(geometry,<polygon around first feature>)"
        def propertyAndLiteral4 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("NoT s_DisJoinT(geometry," + polygonCrv + ")"))
        def propertyAndLiteral4Check = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery( "s_InterSectS(geometry," + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral4)

        and: "Returns the same features as INTERSECTS"
        propertyAndLiteral4.responseData.numberReturned == propertyAndLiteral4Check.responseData.numberReturned
        for (int i=0; i<propertyAndLiteral4.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral4.responseData.features[i], propertyAndLiteral4Check.responseData.features[i])
        }

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral4b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("NoT s_DisJoinT(geometry," + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral4b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral4, propertyAndLiteral4b)

        when: "3. Data is selected using a filter s_DisJoinT(geometry,<polygon around first feature>)"
        def propertyAndLiteral5 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_DisJoinT(geometry," + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral5)

        and: "Returns the feature"
        propertyAndLiteral5.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned - propertyAndLiteral4Check.responseData.numberReturned
        propertyAndLiteral5.responseData.features.stream().noneMatch( f -> f.getName == idCrv )

        when: "4. The same request using EPSG:4326"
        def propertyAndLiteral5b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_DisJoinT(geometry," + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral5b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral5, propertyAndLiteral5b)
    }

    def "Operator S_EQUALS"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter s_EqualS(geometry,<point of first feature>)"
        def propertyAndLiteral6 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("s_EqualS(geometry, " + pointPnt + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral6)

        and: "Returns the feature"
        propertyAndLiteral6.responseData.numberReturned == 1
        assertFeature(propertyAndLiteral6.responseData.features[0], allCulturePntFeatures.responseData.features[0])

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral6b = getRequest(restClient, CULTURE_PNT_PATH, getQuery4326("s_EqualS(geometry, " + pointPnt4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral6b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral6, propertyAndLiteral6b)

        when: "3. Data is selected using a filter NoT s_EqualS(geometry,<point of first feature>)"
        def propertyAndLiteral7 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("NoT s_EqualS(geometry, " + pointPnt + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral7)

        and: "Returns everything but the feature"
        propertyAndLiteral7.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned - 1

        when: "4. The same request using EPSG:4326"
        def propertyAndLiteral7b = getRequest(restClient, CULTURE_PNT_PATH, getQuery4326("NoT s_EqualS(geometry, " + pointPnt4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral7b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral7, propertyAndLiteral7b)
    }

    def "Operator S_WITHIN"() {
        given: "AeronauticCrv features in the Daraa dataset"

        when: "1. Data is selected using a filter s_WithiN(geometry,<polygon around first feature>)"
        def propertyAndLiteral8 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_WithiN(geometry, " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral8)

        and: "Returns the feature"
        propertyAndLiteral8.responseData.numberReturned > 0
        propertyAndLiteral8.responseData.features.stream().anyMatch( f -> f.getName == idCrv )

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral8b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_WithiN(geometry, " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral8b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral8, propertyAndLiteral8b)

        when: "3. Data is selected using a filter NoT s_WithiN(geometry,<polygon around first feature>)"
        def propertyAndLiteral9 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("NoT s_WithiN(geometry, " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral9)

        and: "Returns everything but the feature"
        propertyAndLiteral9.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned - propertyAndLiteral8.responseData.numberReturned
        propertyAndLiteral9.responseData.features.stream().noneMatch( f -> f.getName == idCrv )

        when: "4. The same request using EPSG:4326"
        def propertyAndLiteral9b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("NoT s_WithiN(geometry, " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral9b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral9, propertyAndLiteral9b)

        when: "5. Data is selected using a filter s_WithiN(<point of first feature>,<polygon around first feature>)"
        def propertyAndLiteral10 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_WithiN(" + pointCrv + ", " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral10)

        and: "Returns all features"
        propertyAndLiteral10.responseData.numberReturned == allAeronauticCrvFeatures.responseData.numberReturned

        when: "6. The same request using EPSG:4326"
        def propertyAndLiteral10b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_WithiN(" + pointCrv4326 + ", " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral10b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral10, propertyAndLiteral10b)

        when: "7. Data is selected using a filter NoT s_WithiN(geometry,<polygon around first feature>)"
        def propertyAndLiteral11 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("NoT s_WithiN(geometry, " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral11)

        and: "Returns 12 features"
        propertyAndLiteral11.responseData.numberReturned == 12

        when: "8. The same request using EPSG:4326"
        def propertyAndLiteral11b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("NoT s_WithiN(geometry, " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral11b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral11, propertyAndLiteral11b)
    }

    def "Operator S_TOUCHES"() {
        given: "AeronauticCrv features in the Daraa dataset"

        when: "1. Data is selected using a filter s_ToUcHeS(geometry,<start point of first feature>)"
        def propertyAndLiteral12 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_ToUcHeS(geometry, " + pointCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral12)

        and: "Returns the feature"
        propertyAndLiteral12.responseData.numberReturned > 0
        propertyAndLiteral12.responseData.features.stream().anyMatch( f -> f.getName == idCrv )

        and: "Returns also connected features"
        propertyAndLiteral12.responseData.numberReturned > 1

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral12b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_ToUcHeS(geometry, " + pointCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral12b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral12, propertyAndLiteral12b)

        when: "3. Data is selected using a filter s_ToUcHeS(geometry,<polygon around first feature>)"
        def propertyAndLiteral14 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_ToUcHeS(geometry, " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral14)

        and: "Returns no feature"
        propertyAndLiteral14.responseData.numberReturned == 0

        when: "4. The same request using EPSG:4326"
        def propertyAndLiteral14b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_ToUcHeS(geometry, " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral14b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral14, propertyAndLiteral14b)

        when: "5. Data is selected using a filter s_ToUcHeS(geometry,geometry)"
        def propertyAndLiteral15 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_ToUcHeS(geometry,geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral15)

        and: "Returns no feature"
        propertyAndLiteral15.responseData.numberReturned == 0
    }

    def "Operator S_OVERLAPS"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter s_OvErLaPs(geometry,<start point of first feature>)"
        def propertyAndLiteral16 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("s_OvErLaPs(geometry, " + pointPnt + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral16)

        and: "Returns no feature"
        propertyAndLiteral16.responseData.numberReturned == 0

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral16b = getRequest(restClient, CULTURE_PNT_PATH, getQuery4326("s_OvErLaPs(geometry, " + pointPnt4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral16b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral16, propertyAndLiteral16b)

        when: "3. Data is selected using a filter s_OvErLaPs(geometry,geometry)"
        def propertyAndLiteral17 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_OvErLaPs(geometry,geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral17)

        and: "Returns no feature"
        propertyAndLiteral17.responseData.numberReturned == 0
    }

    def "Operator S_CROSSES"() {
        given: "AeronauticCrv features in the Daraa dataset"

        when: "1. Data is selected using a filter s_CrOsSeS(geometry,<polygon around first feature>)"
        def propertyAndLiteral18 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_CrOsSeS(geometry, " + polygonCrv + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral18)

        and: "Returns one feature"
        propertyAndLiteral18.responseData.numberReturned == 1

        when: "2. The same request using EPSG:4326"
        def propertyAndLiteral18b = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery4326("s_CrOsSeS(geometry, " + polygonCrv4326 + ")"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral18b)

        and: "Returns the same result"
        assertSameResult(propertyAndLiteral18, propertyAndLiteral18b)

        when: "5. Data is selected using a filter s_CrOsSeS(geometry,geometry)"
        def propertyAndLiteral19 = getRequest(restClient, AERONAUTIC_CRV_PATH, getQuery("s_CrOsSeS(geometry,geometry)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral19)

        and: "Returns no feature"
        propertyAndLiteral19.responseData.numberReturned == 0
    }


    // Temporal predicates
    // TODO add tests for the other temporal operators

    def "Operator T_INTERSECTS"() {
        given: "CulturePnt features in the Daraa dataset and Boundary features in the CShapes dataset"

        when: "1. Data is selected using a filter t_IntErSectS(ZI001_SDV, INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2011-12' && f.properties.ZI001_SDV < '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter t_IntErSectS(ZI001_SDV, INTERVAL('..','2011-12-31T23:59:59Z'))"
        def propertyAndLiteral2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, INTERVAL('..','2011-12-31T23:59:59Z'))"))
        def propertyAndLiteral2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of features"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteral2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteral2Check.get(i))
        }

        when: "3. Data is selected using a filter t_IntErSectS(ZI001_SDV, INTERVAL('2012-01-01T00:00:00Z','..'))"
        def propertyAndLiteral3 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, INTERVAL('2012-01-01T00:00:00Z','..'))"))
        def propertyAndLiteral3Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral3)

        and: "Returns the same number of features"
        propertyAndLiteral3.responseData.numberReturned == propertyAndLiteral3Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral3.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral3.responseData.features[i], propertyAndLiteral3Check.get(i))
        }

        when: "4. Data is selected using a filter t_IntErSectS(ZI001_SDV, INTERVAL('..','..'))"
        def propertyAndLiteral4 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, INTERVAL('..','..'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral4)

        and: "Returns all features"
        assertSameResult(propertyAndLiteral4, allCulturePntFeatures)

        when: "5. Data is selected using a filter t_IntErSectS(ZI001_SDV,InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'))"
        def propertyAndLiteral5 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV,InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'))"))
        def propertyAndLiteral5Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2011-12-27' && f.properties.ZI001_SDV < '2011-12-28' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral5)

        and: "Returns the same number of features"
        propertyAndLiteral5.responseData.numberReturned == propertyAndLiteral5Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral5.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral5.responseData.features[i], propertyAndLiteral5Check.get(i))
        }

        when: "6. Data is selected using a filter t_IntErSectS(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"
        def propertyAndLiteral6 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"))
        def propertyAndLiteral6Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV == '2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral6)

        and: "Returns the same number of features"
        propertyAndLiteral6.responseData.numberReturned == propertyAndLiteral6Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral6.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral6.responseData.features[i], propertyAndLiteral6Check.get(i))
        }

        when: "7. Data is selected using a filter t_IntErSectS(ZI001_SDV, ZI001_SDV)"
        def propertyAndLiteral7 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(ZI001_SDV, ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral7)

        and: "Returns all features"
        assertSameResult(propertyAndLiteral7, allCulturePntFeatures)

        when: "8. Data is selected using a filter t_IntErSectS(INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral8 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral8)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral8, propertyAndLiteral)

        when: "9. Data is selected using a filter t_IntErSectS(INTERVAL('..','2011-12-31T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral9 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(INTERVAL('..','2011-12-31T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral9)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral9, propertyAndLiteral2)

        when: "10. Data is selected using a filter t_IntErSectS(INTERVAL('2012-01-01T00:00:00Z','..'), ZI001_SDV)"
        def propertyAndLiteral10 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(INTERVAL('2012-01-01T00:00:00Z','..'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral10)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral10, propertyAndLiteral3)

        when: "11. Data is selected using a filter t_IntErSectS(INTERVAL('..','..'), ZI001_SDV)"
        def propertyAndLiteral11 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(INTERVAL('..','..'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral11)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral11, propertyAndLiteral4)

        when: "12. Data is selected using a filter t_IntErSectS(InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral12 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral12)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral12, propertyAndLiteral5)

        when: "13. Data is selected using a filter t_IntErSectS(TIMESTAMP('2011-12-26T20:55:27Z'), ZI001_SDV)"
        def propertyAndLiteral13 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(TIMESTAMP('2011-12-26T20:55:27Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral13)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral13, propertyAndLiteral6)

        when: "14. Data is selected using a filter t_IntErSectS(TIMESTAMP('2011-12-26T20:55:27Z'), INTERVAL('2011-01-01','2011-12-31'))"
        def propertyAndLiteral14 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(TIMESTAMP('2011-12-26T20:55:27Z'), INTERVAL('2011-01-01','2011-12-31'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral14)

        and: "Returns all features"
        assertSameResult(propertyAndLiteral14, allCulturePntFeatures)

        when: "14. Data is selected using a filter t_IntErSectS(INTERVAL('..','2010-12-26'), INTERVAL('2011-01-01','2011-12-31'))"
        def propertyAndLiteral15 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_IntErSectS(INTERVAL('..','2010-12-26'), INTERVAL('2011-01-01','2011-12-31'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral15)

        and: "Returns no features"
        propertyAndLiteral15.responseData.numberReturned == 0

        when: "15. Data is selected using a filter CASEI(name) LIKE CASEI('%Germany%') AND T_INTERSECTS(INTERVAL(cowbegin,cowend),INTERVAL('1955-01-01','1990-10-02'))"
        def boundary = getRequest(restClient, BOUNDARY_PATH, getQuery("CASEI(name) LIKE CASEI('%Germany%') AND T_INTERSECTS(INTERVAL(cowbegin,cowend),INTERVAL('1955-01-01','1990-10-02'))"))
        def boundaryCheck = allBoundaries.responseData.features.stream().filter( f -> f.properties.name.toLowerCase().startsWith("germany") && !(f.properties.cowend < '1955-01-01' || f.properties.cowbegin >= '1990-10-03')).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(boundary)

        and: "Returns the same number of features"
        boundary.responseData.numberReturned == boundaryCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<boundary.responseData.numberReturned; i++) {
            assertFeature(boundary.responseData.features[i], boundaryCheck.get(i))
        }

        when: "16. Data is selected using a filter T_INTERSECTS(INTERVAL(cowbegin,'..'),INTERVAL('..',cowend))"
        def boundary2 = getRequest(restClient, BOUNDARY_PATH, getQuery("T_INTERSECTS(INTERVAL(cowbegin,'..'),INTERVAL('..',cowend))"))

        then: "Success and returns GeoJSON"
        assertSuccess(boundary2)

        and: "Returns the same number of features"
        boundary2.responseData.numberReturned == allBoundaries.responseData.features.size()
    }

    def "Operator T_DISJOINT"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2011-12' || f.properties.ZI001_SDV > '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, INTERVAL('..','2011-12-31T23:59:59Z'))"
        def propertyAndLiteral2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, INTERVAL('..','2011-12-31T23:59:59Z'))"))
        def propertyAndLiteral2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of features"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteral2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteral2Check.get(i))
        }

        when: "3. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, INTERVAL('2012-01-01T00:00:00Z','..'))"
        def propertyAndLiteral3 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, INTERVAL('2012-01-01T00:00:00Z','..'))"))
        def propertyAndLiteral3Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2012' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral3)

        and: "Returns the same number of features"
        propertyAndLiteral3.responseData.numberReturned == propertyAndLiteral3Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral3.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral3.responseData.features[i], propertyAndLiteral3Check.get(i))
        }

        when: "4. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, INTERVAL('..','..'))"
        def propertyAndLiteral4 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, INTERVAL('..','..'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral4)

        and: "Returns no features"
        propertyAndLiteral4.responseData.numberReturned == 0

        when: "5. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'))"
        def propertyAndLiteral5 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'))"))
        def propertyAndLiteral5Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2011-12-27' || f.properties.ZI001_SDV >= '2011-12-28' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral5)

        and: "Returns the same number of features"
        propertyAndLiteral5.responseData.numberReturned == propertyAndLiteral5Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral5.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral5.responseData.features[i], propertyAndLiteral5Check.get(i))
        }

        when: "6. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"
        def propertyAndLiteral6 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"))
        def propertyAndLiteral6Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV != '2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral6)

        and: "Returns the same number of features"
        propertyAndLiteral6.responseData.numberReturned == propertyAndLiteral6Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral6.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral6.responseData.features[i], propertyAndLiteral6Check.get(i))
        }

        when: "7. Data is selected using a filter t_DiSjOiNt(ZI001_SDV, ZI001_SDV)"
        def propertyAndLiteral7 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(ZI001_SDV, ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral7)

        and: "Returns no features"
        propertyAndLiteral7.responseData.numberReturned == 0

        when: "8. Data is selected using a filter t_DiSjOiNt(INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral8 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(INTERVAL('2011-12-01T00:00:00Z','2011-12-31T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral8)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral8, propertyAndLiteral)

        when: "9. Data is selected using a filter t_DiSjOiNt(INTERVAL('..','2011-12-31T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral9 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(INTERVAL('..','2011-12-31T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral9)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral9, propertyAndLiteral2)

        when: "10. Data is selected using a filter t_DiSjOiNt(INTERVAL('2012-01-01T00:00:00Z','..'), ZI001_SDV)"
        def propertyAndLiteral10 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(INTERVAL('2012-01-01T00:00:00Z','..'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral10)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral10, propertyAndLiteral3)

        when: "11. Data is selected using a filter t_DiSjOiNt(INTERVAL('..','..'), ZI001_SDV)"
        def propertyAndLiteral11 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(INTERVAL('..','..'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral11)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral11, propertyAndLiteral4)

        when: "12. Data is selected using a filter t_DiSjOiNt(InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'), ZI001_SDV)"
        def propertyAndLiteral12 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(InterVaL('2011-12-27T00:00:00Z','2011-12-27T23:59:59Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral12)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral12, propertyAndLiteral5)

        when: "13. Data is selected using a filter t_DiSjOiNt(TIMESTAMP('2011-12-26T20:55:27Z'), ZI001_SDV)"
        def propertyAndLiteral13 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(TIMESTAMP('2011-12-26T20:55:27Z'), ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral13)

        and: "Returns the same features"
        assertSameResult(propertyAndLiteral13, propertyAndLiteral6)

        when: "14. Data is selected using a filter t_DiSjOiNt(TIMESTAMP('2011-12-26T20:55:27Z'), INTERVAL('2011-01-01','2011-12-31'))"
        def propertyAndLiteral14 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(TIMESTAMP('2011-12-26T20:55:27Z'), INTERVAL('2011-01-01','2011-12-31'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral14)

        and: "Returns all features"
        assertSameResult(propertyAndLiteral14, allCulturePntFeatures)

        when: "14. Data is selected using a filter t_DiSjOiNt(INTERVAL('..','2010-12-26'), INTERVAL('2011-01-01','2011-12-31'))"
        def propertyAndLiteral15 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DiSjOiNt(INTERVAL('..','2010-12-26'), INTERVAL('2011-01-01','2011-12-31'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral15)

        and: "Returns all features"
        assertSameResult(propertyAndLiteral15, allCulturePntFeatures)

        when: "15. Data is selected using a filter CASEI(name) LIKE CASEI('%Germany%') AND T_DISJOINT(INTERVAL(cowbegin,cowend),INTERVAL('1955-01-01','1990-10-02'))"
        def boundary = getRequest(restClient, BOUNDARY_PATH, getQuery("CASEI(name) LIKE CASEI('%Germany%') AND T_DISJOINT(INTERVAL(cowbegin,cowend),INTERVAL('1955-01-01','1990-10-02'))"))
        def boundaryCheck = allBoundaries.responseData.features.stream().filter( f -> f.properties.name.toLowerCase().startsWith("germany") && (f.properties.cowend < '1955-01-01' || f.properties.cowbegin >= '1990-10-03')).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(boundary)

        and: "Returns the same number of features"
        boundary.responseData.numberReturned == boundaryCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<boundary.responseData.numberReturned; i++) {
            assertFeature(boundary.responseData.features[i], boundaryCheck.get(i))
        }
    }

    def "Operator T_EQUALS"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter T_EqualS(ZI001_SDV, ZI001_SDV)"
        def twoProperties = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_EqualS(ZI001_SDV, ZI001_SDV)"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned

        when: "2. Data is selected using a filter T_EqualS(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_EqualS(ZI001_SDV, TIMESTAMP('2011-12-26T20:55:27Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV == '2011-12-26T20:55:27Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "3. Data is selected using datetime=TIMESTAMP('2011-12-26T20:55:27Z')"
        def datetime3 = getRequest(restClient, CULTURE_PNT_PATH, [datetime:"2011-12-26T20:55:27Z"])

        then: "Success and returns GeoJSON"
        assertSuccess(datetime3)

        and: "Returns the same number of features"
        datetime3.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<datetime3.responseData.numberReturned; i++) {
            assertFeature(datetime3.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "4. Data is selected using a filter T_EqualS(InTeRvAl(ZI001_SDV,'..'),InTeRvAl(ZI001_SDV,'..'))"
        def intervals = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_EqualS(InTeRvAl(ZI001_SDV,'..'),InTeRvAl(ZI001_SDV,'..'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(intervals)

        and: "Returns all features"
        twoProperties.responseData.numberReturned == allCulturePntFeatures.responseData.numberReturned
    }

    def "Operator T_AFTER"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter T_AFTER(ZI001_SDV, TIMESTAMP('2011-12-31T23:59:59Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_AFTER(ZI001_SDV, TIMESTAMP('2011-12-31T23:59:59Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2011-12-31T23:59:59Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter T_AFTER(TIMESTAMP('2011-12-31T23:59:59Z'), ZI001_SDV)"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_AFTER(TIMESTAMP('2011-12-31T23:59:59Z'), ZI001_SDV)"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2011-12-31T23:59:59Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == literalAndPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "3. Data is selected using a filter T_AFTER(TIMESTAMP('2011-12-31T23:59:59Z'), TIMESTAMP('2011-12-31T23:59:59Z'))"
        def literalAndliteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_AFTER(TIMESTAMP('2011-12-31T23:59:59Z'), TIMESTAMP('2011-12-31T23:59:59Z'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndliteral)

        and: "Returns the same number of features"
        literalAndliteral.responseData.numberReturned == 0

        when: "4. Data is selected using a filter T_AFTER(ZI001_SDV, INTERVAL('2011-01-01T00:00:00Z', '2011-12-31T23:59:59Z'))"
        def propertyAndLiteralInterval = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_AFTER(ZI001_SDV,INTERVAL('2011-01-01T00:00:00Z','2011-12-31T23:59:59Z'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralInterval)

        and: "Returns the same number of features"
        propertyAndLiteralInterval.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralInterval.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralInterval.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "5. Data is selected using a filter CASEI(name) LIKE CASEI('%Germany%') AND T_AFTER(INTERVAL(cowbegin,cowend),INTERVAL('1955-05-05','1990-10-02'))"
        def boundary = getRequest(restClient, BOUNDARY_PATH, getQuery("CASEI(name) LIKE CASEI('%Germany%') AND T_AFTER(INTERVAL(cowbegin,cowend),INTERVAL('1955-05-05','1990-10-02'))"))
        def boundaryCheck = allBoundaries.responseData.features.stream().filter( f -> f.properties.name.toLowerCase().startsWith("germany") && f.properties.cowbegin > '1990-10-02').toList()

        then: "Success and returns GeoJSON"
        assertSuccess(boundary)

        and: "Returns the same number of features"
        boundary.responseData.numberReturned == boundaryCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<boundary.responseData.numberReturned; i++) {
            assertFeature(boundary.responseData.features[i], boundaryCheck.get(i))
        }
    }

    def "Operator T_BEFORE"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter T_BeForE(ZI001_SDV, TIMESTAMP('2012-01-01T00:00:00Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_BeForE(ZI001_SDV, TIMESTAMP('2012-01-01T00:00:00Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV < '2012-01-01T00:00:00Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter T_BeForE(TIMESTAMP('2011-12-31T23:59:59Z'), ZI001_SDV)"
        def literalAndProperty = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_BeForE(TIMESTAMP('2011-12-31T23:59:59Z'), ZI001_SDV)"))
        def literalAndPropertyCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV > '2011-12-31T23:59:59Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndProperty)

        and: "Returns the same number of features"
        literalAndProperty.responseData.numberReturned == literalAndPropertyCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<literalAndProperty.responseData.numberReturned; i++) {
            assertFeature(literalAndProperty.responseData.features[i], literalAndPropertyCheck.get(i))
        }

        when: "3. Data is selected using a filter T_BeForE(TIMESTAMP('2011-12-31T23:59:59Z'), TIMESTAMP('2011-12-31T23:59:59Z'))"
        def literalAndliteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_BeForE(TIMESTAMP('2011-12-31T23:59:59Z'), TIMESTAMP('2011-12-31T23:59:59Z'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndliteral)

        and: "Returns the same number of features"
        literalAndliteral.responseData.numberReturned == 0

        when: "4. Data is selected using a filter T_BeForE(ZI001_SDV, INTERVAL('2011-12-31T23:59:59Z', '..'))"
        def propertyAndLiteralInterval = getRequest(restClient, CULTURE_PNT_PATH, getQuery("T_BeForE(ZI001_SDV, INTERVAL('2011-12-31T23:59:59Z','..'))"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralInterval)

        and: "Returns the same number of features"
        propertyAndLiteralInterval.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteralInterval.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralInterval.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "5. Data is selected using a filter CASEI(name) LIKE CASEI('%Germany%') AND T_BEFORE(INTERVAL(cowbegin,cowend),INTERVAL('1990-10-03','2020-12-31'))"
        def boundary = getRequest(restClient, BOUNDARY_PATH, getQuery("CASEI(name) LIKE CASEI('%Germany%') AND T_BEFORE(INTERVAL(cowbegin,cowend),INTERVAL('1990-10-03','2020-12-31'))"))
        def boundaryCheck = allBoundaries.responseData.features.stream().filter( f -> f.properties.name.toLowerCase().startsWith("germany") && f.properties.cowend < '1990-10-03').toList()

        then: "Success and returns GeoJSON"
        assertSuccess(boundary)

        and: "Returns the same number of features"
        boundary.responseData.numberReturned == boundaryCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<boundary.responseData.numberReturned; i++) {
            assertFeature(boundary.responseData.features[i], boundaryCheck.get(i))
        }
    }

    def "Operator T_DURING"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter t_DuRinG(INTERVAL(ZI001_SDV,ZI001_SDV), INTERVAL('..','2011-12-31T23:59:59Z'))"
        def propertyAndLiteral = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DuRinG(INTERVAL(ZI001_SDV,ZI001_SDV), INTERVAL('..','2011-12-31T23:59:59Z'))"))
        def propertyAndLiteralCheck = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV <= '2011-12-31T23:59:59Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of features"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }


        when: "2. Data is selected using a filter t_DuRinG(INTERVAL(ZI001_SDV,ZI001_SDV), INTERVAL('2012-01-01T00:00:00Z','..'))"
        def propertyAndLiteral2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery("t_DuRinG(INTERVAL(ZI001_SDV,ZI001_SDV), INTERVAL('2012-01-01T00:00:00Z','..'))"))
        def propertyAndLiteral2Check = allCulturePntFeatures.responseData.features.stream().filter( f -> f.properties.ZI001_SDV >= '2012-01-01T00:00:00Z' ).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of features"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteral2Check.size()

        and: "Returns the same feature arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteral2Check.get(i))
        }
    }

    // Array predicates

    def "Operator A_CONTAINS"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter A_ContainS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> f.properties.theme.stream()
                .map(theme -> theme.concept)
                .flatMap(List::stream)
                .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                .distinct()
                .count() == 3).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of records"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i = 0; i < propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }


        when: "2. Data is selected using a filter A_ContainS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"
        def propertyAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"))
        def propertyAndLiteralCheck2 = allAxGebaeudefunktion.responseData.features.stream().filter(f -> f.properties.theme.stream()
                .map(theme -> theme.concept)
                .flatMap(List::stream)
                .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of records"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteralCheck2.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteralCheck2.get(i))
        }

        when: "3. Data is selected using a filter A_ContainS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral)

        and: "Returns all records"
        literalAndLiteral.responseData.numberReturned == allAxGebaeudefunktion.responseData.features.size()

        when: "4. Data is selected using a filter A_ContainS(['DLKM', 'Basis-DLM'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(['DLKM', 'Basis-DLM'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral2)

        and: "Returns no records"
        literalAndLiteral2.responseData.numberReturned == 0

        /* TODO not implemented
        when: "5. Data is selected using a filter theme.concept"
        def twoProperties = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("theme.concept AContainS theme.concept"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all records"
        twoProperties.responseData.numberReturned == allAxGebaeudefunktion.responseData.numberReturned
         */
    }

    @IgnoreIf({ !FilterParameterSpecification.testNestedFilters() })
    def "Operator A_CONTAINS with nested filters"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1a. Data is selected using a nested filter A_ContainS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                    .distinct()
                    .count()==3
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested)

        and: "Returns the same number of records"
        propertyAndLiteralNested.responseData.numberReturned == propertyAndLiteralNestedCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested.responseData.features[i], propertyAndLiteralNestedCheck.get(i))
        }

        when: "1b. Data is selected using a nested filter A_ContainS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNested2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> !theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                    .distinct()
                    .count()==3
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested2)

        and: "Returns the same number of records"
        propertyAndLiteralNested2.responseData.numberReturned == propertyAndLiteralNested2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested2.responseData.features[i], propertyAndLiteralNested2Check.get(i))
        }

        when: "1c. Data is selected using a nested filter A_ContainS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPositionCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            if (themes.size()==0)
                return false
            return themes.get(0)
                    .getAt("concept")
                    .stream()
                    .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                    .distinct()
                    .count()==3
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition.responseData.numberReturned == propertyAndLiteralNestedPositionCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition.responseData.features[i], propertyAndLiteralNestedPositionCheck.get(i))
        }

        when: "1d. Data is selected using a nested filter A_ContainS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_ContainS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPosition2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            def selectedThemes = IntStream.range(0, themes.size())
                    .filter(i -> i>0)
                    .mapToObj(i-> themes.get(i))
                    .collect(Collectors.toList())
            if (selectedThemes.size()==0)
                return false
            return selectedThemes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                    .distinct()
                    .count()==3
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition2)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition2.responseData.numberReturned == propertyAndLiteralNestedPosition2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition2.responseData.features[i], propertyAndLiteralNestedPosition2Check.get(i))
        }
    }

    def "Operator A_EQUALS"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter A_EqualS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def concepts = f.properties.theme.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .distinct()
                    .toList()
            return concepts.size()==3 && concepts.contains('DLKM') && concepts.contains('Basis-DLM') && concepts.contains('DLM50')
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of records"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter A_EqualS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"
        def propertyAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of records"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "3. Data is selected using a filter A_EqualS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral)

        and: "Returns all records"
        literalAndLiteral.responseData.numberReturned == allAxGebaeudefunktion.responseData.features.size()

        when: "4. Data is selected using a filter A_EqualS(['DLKM', 'Basis-DLM'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(['DLKM', 'Basis-DLM'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral2)

        and: "Returns no records"
        literalAndLiteral2.responseData.numberReturned == 0

        /* TODO not implemented
        when: "5. Data is selected using a filter theme.concept"
        def twoProperties = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("theme.concept AEQUALS theme.concept"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all records"
        twoProperties.responseData.numberReturned == allAxGebaeudefunktion.responseData.numberReturned
         */
    }

    @IgnoreIf({ !testNestedFilters() })
    def "Operator A_EQUALS with nested filters"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1a. Data is selected using a nested filter A_EqualS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            def concepts = themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .distinct()
                    .toList()
            return concepts.size()==3 && concepts.contains('DLKM') && concepts.contains('Basis-DLM') && concepts.contains('DLM50')
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested)

        and: "Returns the same number of records"
        propertyAndLiteralNested.responseData.numberReturned == propertyAndLiteralNestedCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested.responseData.features[i], propertyAndLiteralNestedCheck.get(i))
        }

        when: "1b. Data is selected using a nested filter A_EqualS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNested2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> !theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            def concepts = themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .distinct()
                    .toList()
            return concepts.size()==3 && concepts.contains('DLKM') && concepts.contains('Basis-DLM') && concepts.contains('DLM50')
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested2)

        and: "Returns the same number of records"
        propertyAndLiteralNested2.responseData.numberReturned == propertyAndLiteralNested2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested2.responseData.features[i], propertyAndLiteralNested2Check.get(i))
        }

        /* TODO position() does not work
        when: "1c. Data is selected using a nested filter A_EqualS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPositionCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            if (themes.size()==0)
                return false
            def concepts = themes.get(0)
                    .getAt("concept")
                    .stream()
                    .distinct()
                    .toList()
            return concepts.size()==3 && concepts.contains('DLKM') && concepts.contains('Basis-DLM') && concepts.contains('DLM50')
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition.responseData.numberReturned == propertyAndLiteralNestedPositionCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition.responseData.features[i], propertyAndLiteralNestedPositionCheck.get(i))
        }

        when: "1d. Data is selected using a nested filter A_EqualS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_EqualS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPosition2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            def selectedThemes = IntStream.range(0, themes.size())
                    .filter(i -> i>0)
                    .mapToObj(i-> themes.get(i))
                    .toList()
            if (selectedThemes.size()==0)
                return false
            def concepts = selectedThemes.stream()
                    .map(theme -> theme.concept)
                    .stream()
                    .distinct()
                    .toList()
            return concepts.size()==3 && concepts.contains('DLKM') && concepts.contains('Basis-DLM') && concepts.contains('DLM50')
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition2)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition2.responseData.numberReturned == propertyAndLiteralNestedPosition2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition2.responseData.features[i], propertyAndLiteralNestedPosition2Check.get(i))
        }
         */
    }

    def "Operator A_OVERLAPS"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter theme.concept A_OverlapS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> f.properties.theme.stream()
                .map(theme -> theme.concept)
                .flatMap(List::stream)
                .anyMatch(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of records"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "2. Data is selected using a filter A_OverlapS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"
        def propertyAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"))

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of records"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "3. Data is selected using a filter A_OverlapS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral)

        and: "Returns all records"
        literalAndLiteral.responseData.numberReturned == allAxGebaeudefunktion.responseData.features.size()

        when: "4. Data is selected using a filter A_OverlapS(['DLKM', 'Basis-DLM'], ['DLM50'])"
        def literalAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(['DLKM', 'Basis-DLM'], ['DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral2)

        and: "Returns no records"
        literalAndLiteral2.responseData.numberReturned == 0

        /* TODO not implemented
        when: "5. Data is selected using a filter theme.concept"
        def twoProperties = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("theme.concept AOVERLAPS theme.concept"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all records"
        twoProperties.responseData.numberReturned == allAxGebaeudefunktion.responseData.numberReturned
         */
    }

    @IgnoreIf({ !testNestedFilters() })
    def "Operator A_OVERLAPS with nested filters"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1a. Data is selected using a nested filter A_OverlapS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .anyMatch(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested)

        and: "Returns the same number of records"
        propertyAndLiteralNested.responseData.numberReturned == propertyAndLiteralNestedCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested.responseData.features[i], propertyAndLiteralNestedCheck.get(i))
        }

        when: "1b. Data is selected using a nested filter A_OverlapS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNested2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> !theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .anyMatch(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested2)

        and: "Returns the same number of records"
        propertyAndLiteralNested2.responseData.numberReturned == propertyAndLiteralNested2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested2.responseData.features[i], propertyAndLiteralNested2Check.get(i))
        }

        /* TODO position() does not work
        when: "1c. Data is selected using a nested filter A_OverlapS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(theme[position()=1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPositionCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            if (themes.size()==0)
                return false
            return themes.get(0)
                    .getAt("concept")
                    .stream()
                    .anyMatch(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition.responseData.numberReturned == propertyAndLiteralNestedPositionCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition.responseData.features[i], propertyAndLiteralNestedPositionCheck.get(i))
        }

        when: "1d. Data is selected using a nested filter A_OverlapS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("A_OverlapS(theme[position()>1].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPosition2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            def selectedThemes = IntStream.range(0, themes.size())
                    .filter(i -> i>0)
                    .mapToObj(i-> themes.get(i))
                    .toList()
            if (selectedThemes.size()==0)
                return false
            return selectedThemes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .anyMatch(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition2)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition2.responseData.numberReturned == propertyAndLiteralNestedPosition2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition2.responseData.features[i], propertyAndLiteralNestedPosition2Check.get(i))
        }
         */
    }

    def "Operator A_CONTAINEDBY"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1. Data is selected using a filter a_CONtainEDBY(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(theme.concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> f.properties.theme.stream()
                .map(theme -> theme.concept)
                .flatMap(List::stream)
                .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral)

        and: "Returns the same number of records"
        propertyAndLiteral.responseData.numberReturned == propertyAndLiteralCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral.responseData.features[i], propertyAndLiteralCheck.get(i))
        }

        when: "1a. Data is selected using a nested filter a_CONtainEDBY(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(theme[scheme='profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested)

        and: "Returns the same number of records"
        propertyAndLiteralNested.responseData.numberReturned == propertyAndLiteralNestedCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested.responseData.features[i], propertyAndLiteralNestedCheck.get(i))
        }

        when: "2. Data is selected using a filter a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"
        def propertyAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], theme.concept)"))
        def propertyAndLiteralCheck2 = allAxGebaeudefunktion.responseData.features.stream().filter(f -> f.properties.theme.stream()
                .map(theme -> theme.concept)
                .flatMap(List::stream)
                .filter(concept -> concept.equals('DLKM') || concept.equals('Basis-DLM') || concept.equals('DLM50'))
                .distinct()
                .count()==3).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteral2)

        and: "Returns the same number of records"
        propertyAndLiteral2.responseData.numberReturned == propertyAndLiteralCheck2.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteral2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteral2.responseData.features[i], propertyAndLiteralCheck2.get(i))
        }

        when: "3. Data is selected using a filter a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"
        def literalAndLiteral = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM', 'DLM50'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral)

        and: "Returns all records"
        literalAndLiteral.responseData.numberReturned == allAxGebaeudefunktion.responseData.features.size()

        when: "4. Data is selected using a filter a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM'])"
        def literalAndLiteral2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(['DLKM', 'Basis-DLM', 'DLM50'], ['DLKM', 'Basis-DLM'])"))

        then: "Success and returns GeoJSON"
        assertSuccess(literalAndLiteral2)

        and: "Returns no records"
        literalAndLiteral2.responseData.numberReturned == 0

        /* TODO not implemented
        when: "5. Data is selected using a filter theme.concept"
        def twoProperties = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("theme.concept CONTAINEDBY theme.concept"))

        then: "Success and returns GeoJSON"
        assertSuccess(twoProperties)

        and: "Returns all records"
        twoProperties.responseData.numberReturned == allAxGebaeudefunktion.responseData.numberReturned
         */
    }

    @IgnoreIf({ !testNestedFilters() })
    def "Operator A_CONTAINEDBY with nested filters"() {
        given: "Records in the AX_Gebaeudefunktion codelist"

        when: "1b. Data is selected using a nested filter a_CONtainEDBY(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNested2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(theme[scheme<>'profile'].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNested2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = f.properties.theme.stream()
                    .filter(theme -> !theme.scheme.equals('profile'))
                    .toList()
            if (themes.size()==0)
                return false
            return themes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNested2)

        and: "Returns the same number of records"
        propertyAndLiteralNested2.responseData.numberReturned == propertyAndLiteralNested2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNested2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNested2.responseData.features[i], propertyAndLiteralNested2Check.get(i))
        }

        /* TODO position() does not work
        when: "1c. Data is selected using a nested filter a_CONtainEDBY(theme[position() IN (1)].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(theme[position() IN (1)].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPositionCheck = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            if (themes.size()==0)
                return false
            return  themes.get(0)
                    .getAt("concept")
                    .stream()
                    .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition.responseData.numberReturned == propertyAndLiteralNestedPositionCheck.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition.responseData.features[i], propertyAndLiteralNestedPositionCheck.get(i))
        }

        when: "1d. Data is selected using a nested filter a_CONtainEDBY(theme[position() between 2 and 3].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"
        def propertyAndLiteralNestedPosition2 = getRequest(restClient, AX_GEBAEUDEFUNKTION_PATH, getQuery("a_CONtainEDBY(theme[position() between 2 and 3].concept, ['DLKM', 'Basis-DLM', 'DLM50'])"))
        def propertyAndLiteralNestedPosition2Check = allAxGebaeudefunktion.responseData.features.stream().filter(f -> {
            def themes = (List) f.properties.theme
            def selectedThemes = IntStream.range(0, themes.size())
                    .filter(i -> i>0 && i<3)
                    .mapToObj(i-> themes.get(i))
                    .toList()
            if (selectedThemes.size()==0)
                return false
            return selectedThemes.stream()
                    .map(theme -> theme.concept)
                    .flatMap(List::stream)
                    .noneMatch(concept -> !concept.equals('DLKM') && !concept.equals('Basis-DLM') && !concept.equals('DLM50'))
        }).toList()

        then: "Success and returns GeoJSON"
        assertSuccess(propertyAndLiteralNestedPosition2)

        and: "Returns the same number of records"
        propertyAndLiteralNestedPosition2.responseData.numberReturned == propertyAndLiteralNestedPosition2Check.size()

        and: "Returns the same records arrays"
        for (int i=0; i<propertyAndLiteralNestedPosition2.responseData.numberReturned; i++) {
            assertFeature(propertyAndLiteralNestedPosition2.responseData.features[i], propertyAndLiteralNestedPosition2Check.get(i))
        }
         */
    }

    // Logical operators

    def "Logical operators"() {
        given: "CulturePnt features in the Daraa dataset"

        when: "1. Data is selected using a filter F_CODE=F_CODE AnD NoT (F_CODE='AL030' oR (T_AFTER(ZI001_SDV, TIMESTAMP('2011-12-31T23:59:59Z')) aNd ZI037_REL iS nULL)))"
        def logical = getRequest(restClient, CULTURE_PNT_PATH, getQuery( "F_CODE=F_CODE AnD NoT (F_CODE='AL030' oR ((T_AFTER(ZI001_SDV, TIMESTAMP('2011-12-31T23:59:59Z')) aNd ZI037_REL iS nULL)))"))
        def logicalCheck = allCulturePntFeatures.responseData.features.stream()
                .filter(f -> !(f.properties.F_CODE == 'AL030' || (f.properties.ZI001_SDV > '2011-12-31T23:59:59Z' && Objects.isNull(f.properties.ZI037_REL))))
                .toList()

        then: "Success and returns GeoJSON"
        assertSuccess(logical)

        and: "Returns all selected features"
        logical.responseData.numberReturned == logicalCheck.size()
        for (int i = 0; i < logical.responseData.numberReturned; i++) {
            assertFeature(logical.responseData.features[i], logicalCheck.get(i))
        }

        when: "2. Data is selected using a filter F_CODE='AL030' or F_CODE='AL012'"
        def logical2 = getRequest(restClient, CULTURE_PNT_PATH, getQuery( "F_CODE='AL030' or F_CODE='AL012'"))
        def logical2Check = allCulturePntFeatures.responseData.features.stream().filter(f -> f.properties.F_CODE == 'AL030' || f.properties.F_CODE == 'AL012').toList()

        then: "Success and returns GeoJSON"
        assertSuccess(logical2)

        and: "Returns the same number of features"
        logical2.responseData.numberReturned == logical2Check.size()

        and: "Returns the same feature arrays"
        for (int i = 0; i < logical2.responseData.numberReturned; i++) {
            assertFeature(logical2.responseData.features[i], logical2Check.get(i))
        }
    }

    LinkedHashMap<String, String> getQuery(String filter) {
        return json
        ? [filter:cql.write(cql.read(filter, Cql.Format.TEXT), Cql.Format.JSON).replace("\n",""),"filter-lang":"cql2-json",limit:limit]
        : [filter:filter,limit:limit]
    }

    LinkedHashMap<String, String> getQuery4326(String filter) {
        return json
                ? [filter:cql.write(cql.read(filter, Cql.Format.TEXT), Cql.Format.JSON).replace("\n",""),"filter-lang":"cql2-json","filter-crs":epsg4326,limit:limit]
                : [filter:filter,"filter-crs":epsg4326,limit:limit]
    }

    static void assertSuccess(Object response) {
        assert response.status == 200
        assert response.getContentType() == "application/geo+json" || response.getContentType() == "application/json"
    }

    static void assertFeature(Object feature1, Object feature2) {
        assert feature1.id == feature2.id
        assert feature1.type == feature2.type
        assert feature1.properties == feature2.properties
        assert feature1.geometry == feature2.geometry
    }

    static void assertSameResult(Object request1, Object request2) {
        request1.responseData.numberReturned == request2.responseData.numberReturned
        for (int i=0; i<request1.responseData.numberReturned; i++) {
            assertFeature(request1.responseData.features[i], request2.responseData.features[i])
        }
    }

    static Object getRequest(restClient, path, query) {
        return restClient.request(SUT_URL+path, Method.GET,  ContentType.JSON, { req ->
            // uri.path = path
            uri.query = query
            headers.Accept = (path.contains("/items") ? GEO_JSON : JSON) + ", " + PROBLEM_JSON + "; q=0.8"
        })
    }
}