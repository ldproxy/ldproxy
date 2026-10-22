/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.SchemaInfo
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.entities.domain.maptobuilder.BuildableMap
import spock.lang.Specification

class QueryParameterExcludePropertiesSpec extends Specification {

    static final List<String> NAMES = ["id", "naam", "buurtcode", "grondoppervlakte"]

    QueryParameterExcludeProperties parameter() {
        def schemaInfo = Stub(SchemaInfo) {
            getPropertyNames(_, "c", true, false) >> NAMES
        }
        def providers = Stub(FeaturesCoreProviders) {
            getFeatureSchema(_, _) >> Optional.empty()
        }
        return new QueryParameterExcludeProperties(schemaInfo, Stub(SchemaValidator), providers)
    }

    OgcApiDataV2 apiData(List<String> defaultExcluded) {
        def configuration = new ImmutableProjectionsConfiguration.Builder()
                .enabled(true)
                .defaultExcludedProperties(defaultExcluded)
                .build()
        def collection = Stub(FeatureTypeConfigurationOgcApi)
        return Stub(OgcApiDataV2) {
            getCollections() >> Stub(BuildableMap) { get("c") >> collection }
            getExtension(ProjectionsConfiguration, "c") >> Optional.of(configuration)
        }
    }

    def 'without configured defaults, the auxiliary properties are the default'() {
        expect:
        QueryParameterExcludeProperties.defaultExcludedProperties(NAMES, configured, ["buurtcode", "unknown"]) == expected

        where:
        configured        || expected
        []                || ["buurtcode"]
        ["naam", "other"] || ["naam"]
    }

    def 'the schema of the parameter has the default and accepts an empty list'() {
        when:
        def schema = parameter().getSchema(apiData(["buurtcode", "grondoppervlakte"]), "c")

        then:
        schema.getDefault() == ["buurtcode", "grondoppervlakte"]
        schema.getMinItems() == null
    }

    def 'the default applies if the request selects no properties'() {
        expect:
        QueryParameterExcludeProperties.excludedProperties(requested, propertiesRequested, { -> ["buurtcode"] }) == expected

        where:
        requested               | propertiesRequested || expected
        Optional.empty()        | false               || Optional.of(["buurtcode"])
        Optional.of([])         | false               || Optional.of([])
        Optional.of(["naam"])   | false               || Optional.of(["naam"])
        Optional.empty()        | true                || Optional.empty()
    }

    def 'the lists of the API and of a collection are merged'() {
        given:
        def api = new ImmutableProjectionsConfiguration.Builder().defaultExcludedProperties(["a", "b"]).build()
        def collection = new ImmutableProjectionsConfiguration.Builder().defaultExcludedProperties(["b", "c"]).build()

        expect:
        ((ProjectionsConfiguration) collection.mergeInto(api)).getDefaultExcludedProperties() == ["a", "b", "c"]
    }
}
