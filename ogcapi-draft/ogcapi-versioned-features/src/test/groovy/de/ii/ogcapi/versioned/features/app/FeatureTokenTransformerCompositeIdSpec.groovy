/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.xtraplatform.features.domain.FeatureEventHandler
import de.ii.xtraplatform.features.domain.FeatureQuery
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.FeatureTokenReader
import de.ii.xtraplatform.features.domain.FeatureTokenType
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableSchemaMapping
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.SchemaMapping
import spock.lang.Specification

class FeatureTokenTransformerCompositeIdSpec extends Specification {

    static final String PATTERN = '^(?<id>DE[A-Za-z0-9]{14})(?<start>\\d{8}T\\d{6}Z)$'

    FeatureTokenReader tokenReader
    FeatureEventHandler.ModifiableContext context
    List<Object> tokens

    def setup() {
        FeatureSchema versioned = new ImmutableFeatureSchema.Builder()
                .name('versioned')
                .type(SchemaBase.Type.OBJECT)
                .sourcePath('/versioned')
                .putProperties2('id', new ImmutableFeatureSchema.Builder()
                        .type(SchemaBase.Type.STRING)
                        .role(SchemaBase.Role.ID)
                        .sourcePath('id'))
                .putProperties2('beg', new ImmutableFeatureSchema.Builder()
                        .type(SchemaBase.Type.DATETIME)
                        .role(SchemaBase.Role.PRIMARY_INTERVAL_START)
                        .sourcePath('beg'))
                .build()
        SchemaMapping mapping = new ImmutableSchemaMapping.Builder()
                .targetSchema(versioned)
                .sourcePathTransformer((path, isValue) -> path)
                .build()
        FeatureTokenTransformerCompositeId mapper = new FeatureTokenTransformerCompositeId(PATTERN, null)
        FeatureQuery query = ImmutableFeatureQuery.builder().type('test').build()
        context = mapper.createContext()
                .setQuery(query)
                .setMappings([test: mapping])
                .setType('test')
                .setIsUseTargetPaths(true)
        tokenReader = new FeatureTokenReader(mapper, context)
        tokens = []
        mapper.init(token -> tokens.add(token))
    }

    def feed(String beg) {
        [FeatureTokenType.INPUT, true,
         FeatureTokenType.FEATURE,
         FeatureTokenType.VALUE, ['id'], 'DEHE862010016eK3', SchemaBase.Type.STRING,
         FeatureTokenType.VALUE, ['beg'], beg, SchemaBase.Type.DATETIME,
         FeatureTokenType.FEATURE_END,
         FeatureTokenType.INPUT_END].forEach(token -> tokenReader.onToken(token))
    }

    def 'the id is rewritten and the canonical id and version start are stashed'() {
        when:
        feed('2017-06-13T08:03:16Z')

        then:
        tokens.contains('DEHE862010016eK320170613T080316Z')
        context.canonicalFeatureId() == 'DEHE862010016eK3'
        context.featureVersionStart() == '2017-06-13T08:03:16Z'
    }

    def 'a SQL-form interval start is normalized to an ISO instant'() {
        when: 'the raw provider value uses a space separator and no zone designator'
        feed('2017-06-13 08:03:16')

        then:
        tokens.contains('DEHE862010016eK320170613T080316Z')
        context.featureVersionStart() == '2017-06-13T08:03:16Z'
    }
}
