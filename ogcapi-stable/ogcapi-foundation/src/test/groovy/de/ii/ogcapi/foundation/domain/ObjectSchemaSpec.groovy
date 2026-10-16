/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class ObjectSchemaSpec extends Specification {

    ClassSchemaCache classSchemaCache = Stub(ClassSchemaCache)

    def setup() {
        classSchemaCache.getSchema(String, _) >> new StringSchema()
    }

    def 'a required list stays required with the default inclusion'() {
        expect:
        new ObjectSchema(WithDefaultInclusion, classSchemaCache).getRequired().contains('links')
    }

    def 'a required list is not required, if empty lists are omitted'() {
        expect:
        !(new ObjectSchema(clazz, classSchemaCache).getRequired() ?: []).contains('links')

        where:
        clazz << [WithNonEmptyClass, WithNonEmptyInterface]
    }

    def 'a required list stays required, if only null values are omitted'() {
        expect:
        new ObjectSchema(WithNonNullClass, classSchemaCache).getRequired().contains('links')
    }

    def 'a string property gets the declared format'() {
        when:
        ObjectSchema schema = new ObjectSchema(WithFormat, classSchemaCache)

        then:
        schema.getProperties().get('uri').getFormat() == 'uri'
        schema.getProperties().get('optionalUri').getFormat() == 'uri'
        schema.getProperties().get('plain').getFormat() == null
    }

    def 'methods without a value are not properties'() {
        when:
        ObjectSchema schema = new ObjectSchema(WithCheck, classSchemaCache)

        then:
        schema.getProperties().containsKey('name')
        !schema.getProperties().containsKey('check')
        !schema.getRequired().contains('check')
    }

    static class WithCheck {
        String getName() {
            return 'name'
        }

        void check() {
        }
    }

    static class WithFormat {
        @SchemaFormat('uri')
        String getUri() {
            return 'http://example.com'
        }

        @SchemaFormat('uri')
        Optional<String> getOptionalUri() {
            return Optional.empty()
        }

        String getPlain() {
            return ''
        }
    }

    static abstract class Page {
        @JsonProperty(value = 'links', required = true)
        List<String> getLinks() {
            return []
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    static interface OmitsEmpty {}

    static class WithDefaultInclusion extends Page {}

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    static class WithNonEmptyClass extends Page {}

    static class WithNonEmptyInterface extends Page implements OmitsEmpty {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class WithNonNullClass extends Page {}
}
