/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.core.domain.FeatureFormatExtension
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression
import de.ii.ogcapi.features.search.domain.ImmutableStringOrParameter
import de.ii.ogcapi.features.search.domain.StoredQueryExpression
import de.ii.ogcapi.features.search.domain.StoredQueryRepository
import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class QueryParameterFStoredQuerySpec extends Specification {

    def 'html is only a value of f, if a stored query supports HTML'() {
        given:
        StoredQueryRepository repository = Stub()
        repository.getAll(_) >> paging.collect { query(it) }
        QueryParameterFStoredQuery parameter = new QueryParameterFStoredQuery(registry(), Stub(SchemaValidator), repository)

        expect:
        parameter.getSchema(Stub(OgcApiDataV2)).getEnum() == expected

        where:
        paging        || expected
        [false]       || ['json']
        [false, true] || ['html', 'json']
        []            || ['json']
    }

    private static StoredQueryExpression query(boolean supportPaging) {
        return new ImmutableStoredQueryExpression.Builder()
                .id('q' + supportPaging)
                .addCollections(new ImmutableStringOrParameter.Builder().value('ax_test').build())
                .supportPaging(supportPaging)
                .build()
    }

    private ExtensionRegistry registry() {
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(FeatureFormatExtension) >> [format(new MediaType('application', 'geo+json'), 'json'), format(MediaType.TEXT_HTML_TYPE, 'html')]
        registry.getExtensionsForType(_) >> []
        return registry
    }

    private FeatureFormatExtension format(MediaType mediaType, String parameter) {
        ApiMediaType apiMediaType = new ImmutableApiMediaType.Builder()
                .type(mediaType)
                .label(parameter)
                .parameter(parameter)
                .build()
        FeatureFormatExtension format = Stub()
        format.supportsHeterogeneousFeatureCollections() >> true
        format.isEnabledForApi(_) >> true
        format.isInternal() >> false
        format.getMediaType() >> apiMediaType
        return format
    }
}
