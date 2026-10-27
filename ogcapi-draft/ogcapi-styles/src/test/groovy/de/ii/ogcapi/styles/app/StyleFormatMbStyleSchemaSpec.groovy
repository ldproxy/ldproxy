/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.infra.json.ClassSchemaCacheImpl
import de.ii.ogcapi.styles.domain.MbStyleStylesheet
import de.ii.ogcapi.styles.domain.Tiles3dStylesheet
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import spock.lang.Specification

class StyleFormatMbStyleSchemaSpec extends Specification {

    def 'a layer property value may be any JSON value'() {
        given:
        ClassSchemaCacheImpl classSchemaCache = new ClassSchemaCacheImpl()
        new StyleFormatMbStyle(Stub(ExtensionRegistry), classSchemaCache)

        when:
        Schema<?> expression = classSchemaCache.getReferencedSchemas(MbStyleStylesheet).get("MbStyleExpression")

        then:
        expression != null
        expression.getType() == null
        expression.getProperties() == null
        expression.getNullable()
    }

    def 'the sprite is a URL or an array of sprite objects'() {
        given:
        ClassSchemaCacheImpl classSchemaCache = new ClassSchemaCacheImpl()
        new StyleFormatMbStyle(Stub(ExtensionRegistry), classSchemaCache)

        when:
        ComposedSchema sprites = classSchemaCache.getReferencedSchemas(MbStyleStylesheet).get("MbStyleSprites") as ComposedSchema

        then:
        sprites.getOneOf()*.getType() == ["string", "array"]
    }

    def 'metadata values may be any JSON value'() {
        given:
        ClassSchemaCacheImpl classSchemaCache = new ClassSchemaCacheImpl()
        new StyleFormatMbStyle(Stub(ExtensionRegistry), classSchemaCache)

        when:
        Schema<?> layer = classSchemaCache.getReferencedSchemas(MbStyleStylesheet).get("MbStyleLayer")
        Schema<?> metadataValues = layer.getProperties().get("metadata").getAdditionalProperties() as Schema<?>

        then:
        metadataValues.getType() == null
    }

    def 'show and color of a 3D Tiles style have their alternatives'() {
        given:
        ClassSchemaCacheImpl classSchemaCache = new ClassSchemaCacheImpl()
        StyleFormat3dTiles format = new StyleFormat3dTiles(classSchemaCache)

        when:
        Schema<?> style = classSchemaCache.getSchema(Tiles3dStylesheet)
        ComposedSchema show = style.getProperties().get("show") as ComposedSchema
        ComposedSchema color = style.getProperties().get("color") as ComposedSchema

        then:
        show.getOneOf().size() == 3
        show.getOneOf()*.getType().containsAll(["string", "boolean"])
        color.getOneOf().size() == 2
        classSchemaCache.getReferencedSchemas(Tiles3dStylesheet).containsKey("Tiles3dConditions")
    }
}
