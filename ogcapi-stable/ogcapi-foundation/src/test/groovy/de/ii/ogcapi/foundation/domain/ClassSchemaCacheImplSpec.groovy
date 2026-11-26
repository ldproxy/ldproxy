/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import de.ii.ogcapi.foundation.infra.json.ClassSchemaCacheImpl
import io.swagger.v3.oas.models.media.Schema
import spock.lang.Shared
import spock.lang.Specification

class ClassSchemaCacheImplSpec extends Specification {

    @Shared ClassSchemaCacheImpl schemaGenerator

    def setupSpec() {
        schemaGenerator = new ClassSchemaCacheImpl()
    }

    def 'Test Link schema'() {
        when:
        Schema schema = schemaGenerator.getSchema(Link.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getRequired()  == ["href", "rel"]
        schema.getProperties().get("title").getType() == "string"
        schema.getProperties().get("href").getType() == "string"
        schema.getProperties().get("href").getFormat() == "uri-reference"
        schema.getProperties().get("rel").getType() == "string"
        schema.getProperties().get("type").getType() == "string"
    }

    def 'Test ExternalDocumentation schema'() {
        when:
        Schema schema = schemaGenerator.getSchema(ExternalDocumentation.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getRequired()  == ["url"]
        schema.getProperties().get("url").getType() == "string"
        schema.getProperties().get("description").getType() == "string"
    }

    def 'the values of an enum are its JSON values'() {
        when:
        Schema schema = schemaGenerator.getSchema(Mode.class)

        then:
        schema.getEnum() == ['sync-execute', 'async-execute']
    }

    def 'the values of an enum are the names of its JSON properties'() {
        when:
        Schema schema = schemaGenerator.getSchema(Kind.class)

        then:
        schema.getEnum() == ['object', 'ARRAY']
    }

    def 'an object serialized as any value of a JSON value method has the schema of any value'() {
        when:
        Schema schema = schemaGenerator.getSchema(AnyValue.class)

        then:
        schema.getType() == null
        schema.getProperties() == null
        schema.getRequired() == null
    }

    enum Mode {
        SYNC_EXECUTE,
        ASYNC_EXECUTE

        @JsonValue
        String toValue() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-')
        }
    }

    enum Kind {
        @JsonProperty("object")
        OBJECT,
        ARRAY
    }

    static abstract class AnyValue {
        @JsonValue
        abstract Object getValue()
    }
}
