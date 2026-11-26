/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.models.media.StringSchema
import javax.annotation.Nullable
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

    def 'the getters of the interfaces of a class are properties'() {
        when:
        ObjectSchema schema = new ObjectSchema(StatusInfoResponse, classSchemaCache)

        then:
        schema.getProperties().keySet().containsAll(['id', 'status', 'links'])
        schema.getRequired().contains('id')
    }

    def 'interface methods that are not getters or are ignored are not properties'() {
        when:
        ObjectSchema schema = new ObjectSchema(StatusInfoResponse, classSchemaCache)

        then:
        !schema.getProperties().containsKey('asText')
        !schema.getProperties().containsKey('internal')
    }

    def 'the methods of an interface that are not getters are not properties'() {
        when:
        ObjectSchema schema = new ObjectSchema(StatusInfo, classSchemaCache)

        then:
        schema.getProperties().keySet() == ['status', 'id'] as Set
    }

    def 'a property declared by the class takes precedence over the interface'() {
        when:
        ObjectSchema schema = new ObjectSchema(StatusInfoResponse, classSchemaCache)

        then:
        !schema.getRequired().contains('status')
    }

    def 'a getter that the class renames is a property only with the new name'() {
        when:
        ObjectSchema schema = new ObjectSchema(clazz, classSchemaCache)

        then:
        schema.getProperties().containsKey('identifier')
        !schema.getProperties().containsKey('id')
        !schema.getRequired().contains('id')

        where:
        clazz << [RenamedInClass, RenamedInSubclass]
    }

    def 'a getter that the class ignores is not a property'() {
        when:
        ObjectSchema schema = new ObjectSchema(clazz, classSchemaCache)

        then:
        schema.getProperties().containsKey('id')
        !schema.getProperties().containsKey('internal')
        !schema.getRequired().contains('internal')

        where:
        clazz << [IgnoredInClass, IgnoredInSubclass]
    }

    def 'the name of an overridden getter is the name declared in the interface'() {
        when:
        ObjectSchema schema = new ObjectSchema(RenamedInInterface, classSchemaCache)

        then:
        schema.getProperties().containsKey('identifier')
        !schema.getProperties().containsKey('id')
    }

    def 'an overridden getter that the interface ignores is not a property'() {
        when:
        ObjectSchema schema = new ObjectSchema(IgnoredInInterface, classSchemaCache)

        then:
        schema.getProperties().containsKey('id')
        !schema.getProperties().containsKey('internal')
    }

    def 'an overridden getter that the interface declares as nullable is not required'() {
        when:
        ObjectSchema schema = new ObjectSchema(NullableInInterface, classSchemaCache)

        then:
        schema.getProperties().containsKey('status')
        !schema.getRequired().contains('status')
    }

    def 'an explicit property name is used as is'() {
        when:
        ObjectSchema schema = new ObjectSchema(ExplicitNames, classSchemaCache)

        then:
        schema.getProperties().keySet().containsAll(['isbn', 'URL', 'getter'])
        !schema.getProperties().keySet().any { it in ['bn', 'uRL', 'ter'] }
    }

    def 'only the prefix of a getter is removed from the method name'() {
        when:
        ObjectSchema schema = new ObjectSchema(WithIssues, classSchemaCache)

        then:
        schema.getProperties().keySet().containsAll(['issues', 'valid'])
    }

    static interface Identified {
        String getId()
    }

    static interface StatusInfo extends Identified {
        String getStatus()

        String asText()

        @JsonIgnore
        String getInternal()
    }

    static abstract class StatusInfoResponse implements StatusInfo {
        @Nullable
        String getStatus() {
            return null
        }

        @JsonProperty(value = 'links', required = true)
        List<String> getLinks() {
            return []
        }
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

    static interface WithId {
        String getId()
    }

    static interface WithInternal extends WithId {
        String getInternal()
    }

    static abstract class RenamedInClass implements WithId {
        @JsonProperty('identifier')
        String getId() {
            return 'id'
        }
    }

    static abstract class IgnoredInClass implements WithInternal {
        String getId() {
            return 'id'
        }

        @JsonIgnore
        String getInternal() {
            return 'internal'
        }
    }

    static class Base {
        String getId() {
            return 'id'
        }

        String getInternal() {
            return 'internal'
        }
    }

    static class RenamedInSubclass extends Base {
        @JsonProperty('identifier')
        String getId() {
            return 'id'
        }
    }

    static class IgnoredInSubclass extends Base {
        @JsonIgnore
        String getInternal() {
            return 'internal'
        }
    }

    static interface WithRenamedId {
        @JsonProperty('identifier')
        String getId()
    }

    static abstract class RenamedInInterface implements WithRenamedId {
        String getId() {
            return 'id'
        }
    }

    static interface WithIgnoredInternal extends WithId {
        @JsonIgnore
        String getInternal()
    }

    static abstract class IgnoredInInterface implements WithIgnoredInternal {
        String getId() {
            return 'id'
        }

        String getInternal() {
            return 'internal'
        }
    }

    static interface WithNullableStatus {
        @Nullable
        String getStatus()
    }

    static abstract class NullableInInterface implements WithNullableStatus {
        String getStatus() {
            return null
        }
    }

    static class ExplicitNames {
        @JsonProperty('isbn')
        String getIsbn() {
            return ''
        }

        @JsonProperty('URL')
        String getUrl() {
            return ''
        }

        @JsonProperty('getter')
        String getter() {
            return ''
        }
    }

    static class WithIssues {
        List<String> issues() {
            return []
        }

        boolean isValid() {
            return true
        }
    }
}
