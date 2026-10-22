/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver
import spock.lang.Specification

class ProfileRelAsKeyTitleSpec extends Specification {

    static FeatureSchema schema(ImmutableFeatureSchema.Builder reference) {
        return new ImmutableFeatureSchema.Builder()
                .name("verblijfsobject")
                .type(SchemaBase.Type.OBJECT)
                .sourcePath("/verblijfsobject")
                .putPropertyMap("maaktDeelUitVan", reference)
                .build()
    }

    static ImmutableFeatureSchema.Builder reference() {
        return new ImmutableFeatureSchema.Builder()
                .name("maaktDeelUitVan")
                .type(SchemaBase.Type.FEATURE_REF)
                .sourcePath("pand")
    }

    def 'a reference is written with its key and title, without its URI'() {
        given:
        def builder = new ImmutableProfileTransformations.Builder()

        when:
        new ProfileRelAsKeyTitle(Stub(ExtensionRegistry)).addPropertyTransformations(schema(reference().refType("pand")), "application/geo+json", builder)
        def transformation = builder.build().getTransformations().get("maaktDeelUitVan").get(0)

        then:
        transformation.getObjectMapFormat() == [id: FeatureRefResolver.SUB_ID, title: FeatureRefResolver.SUB_TITLE]
        transformation.getObjectRemoveSelect().get() == FeatureRefResolver.ID
    }

    def 'the key is the same as in the profile rel-as-key'() {
        expect:
        ProfileRelAsKeyTitle.key(schema(property).getPropertyMap().get("maaktDeelUitVan")) == key

        where:
        property                                               || key
        reference().refType("pand")                            || FeatureRefResolver.SUB_ID
        reference().refType(FeatureRefResolver.REF_TYPE_DYNAMIC) || ProfileTransformations.KEY_TEMPLATE
        reference().refType("pand").refKeyTemplate("{{id}}")   || ProfileTransformations.KEY_TEMPLATE
    }

    def 'HTML shows a reference as a link'() {
        given:
        def builder = new ImmutableProfileTransformations.Builder()

        when:
        new ProfileRelAsKeyTitle(Stub(ExtensionRegistry)).addPropertyTransformations(schema(reference().refType("pand")), "text/html", builder)

        then:
        builder.build().getTransformations().get("maaktDeelUitVan").get(0).getObjectReduceFormat().get() == ProfileTransformations.HTML_LINK_TEMPLATE
    }

    def 'the profile belongs to the profile set of the other reference profiles'() {
        expect:
        new ProfileRelAsKeyTitle(Stub(ExtensionRegistry)).getProfileSet() == "rel"
    }
}
