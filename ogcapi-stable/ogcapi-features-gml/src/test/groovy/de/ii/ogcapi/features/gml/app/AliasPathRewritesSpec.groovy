/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app


import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase.Type
import spock.lang.Specification

/**
 * Locks the {@code GmlConfiguration} → {@code FeatureTransformationContextGml} key remapping
 * that {@link FeaturesFormatGml#getFeatureEncoder} applies when {@code useAlias: true}.
 *
 * <p>The runtime encoder ({@code GmlWriterProperties}) looks up properties in
 * {@code codelistProperties}, {@code xmlAttributes}, and {@code valueWrap} by
 * {@code schema.getFullPathAsString()}. The schema reaching the encoder has been
 * alias-renamed by {@link de.ii.xtraplatform.features.domain.FeatureSchemaAliases} (it injects a
 * RENAME transformer per aliased property), so the lookup key is the alias-form path. The
 * configuration, however, is written in technical names (matching the {@code SchemaConstraints.codelist}
 * declarations on the same schema and the decoder-side wiring). Without this remap an
 * {@code anl: AA_Anlassart} entry silently fails to match the alias-rewritten path
 * {@code anlass}, the property falls through to a plain text element, and the operator gets
 * {@code <anlass>010704</anlass>} instead of {@code <anlass xlink:href=".../010704"/>}.
 */
class AliasPathRewritesSpec extends Specification {

    def 'buildAliasPathRewrites: top-level alias produces a one-segment rewrite'() {
        given:
        def schema = new ImmutableFeatureSchema.Builder()
                .name('ax_flurstueck')
                .type(Type.OBJECT)
                .putProperties2('anl', new ImmutableFeatureSchema.Builder()
                        .type(Type.STRING)
                        .alias('anlass'))
                .build()

        expect:
        FeaturesFormatGml.buildAliasPathRewrites(schema) == ['anl': 'anlass']
    }

    def 'buildAliasPathRewrites: aliased ancestor rewrites every descendant path'() {
        given:
        def schema = new ImmutableFeatureSchema.Builder()
                .name('ax_flurstueck')
                .type(Type.OBJECT)
                .putProperties2('mat', new ImmutableFeatureSchema.Builder()
                        .type(Type.OBJECT_ARRAY)
                        .alias('material')
                        .putProperties2('som', new ImmutableFeatureSchema.Builder()
                                .type(Type.OBJECT)
                                .alias('sonstigesModell')
                                .putProperties2('codeListValue', new ImmutableFeatureSchema.Builder()
                                        .type(Type.STRING))))
                .build()

        expect: 'both the aliased nodes and every descendant get an entry, joined with the alias path'
        FeaturesFormatGml.buildAliasPathRewrites(schema) == [
                'mat'                    : 'material',
                'mat.som'                : 'material.sonstigesModell',
                'mat.som.codeListValue'  : 'material.sonstigesModell.codeListValue'
        ]
    }

    def 'buildAliasPathRewrites: properties without an alias contribute no entry (identity is implicit)'() {
        given:
        def schema = new ImmutableFeatureSchema.Builder()
                .name('feat')
                .type(Type.OBJECT)
                .putProperties2('plain', new ImmutableFeatureSchema.Builder()
                        .type(Type.STRING))
                .putProperties2('aliased', new ImmutableFeatureSchema.Builder()
                        .type(Type.STRING)
                        .alias('renamed'))
                .build()

        expect:
        FeaturesFormatGml.buildAliasPathRewrites(schema) == ['aliased': 'renamed']
    }

    def 'remapKeys: empty rewrites returns the same map instance (no allocation in the common path)'() {
        given:
        def map = ['anl': 'AA_Anlassart']

        expect:
        FeaturesFormatGml.remapKeys(map, [:]).is(map)
    }

    def 'remapKeys: keys present in the rewrite table are translated; others pass through'() {
        given:
        def rewrites = ['anl': 'anlass', 'mat.som': 'material.sonstigesModell']
        def map = ['anl': 'AA_Anlassart', 'mat.som': 'AA_WeitereModellart', 'untouched': 'X']

        expect:
        FeaturesFormatGml.remapKeys(map, rewrites) == [
                'anlass'                   : 'AA_Anlassart',
                'material.sonstigesModell' : 'AA_WeitereModellart',
                'untouched'                : 'X'
        ]
    }

    def 'remapList: same identity-pass and translate-or-pass-through behaviour as remapKeys, for xmlAttributes'() {
        given:
        def rewrites = ['mat.som.codeListValue': 'material.sonstigesModell.codeListValue']

        expect:
        FeaturesFormatGml.remapList(
                ['mat.som.codeListValue', 'foo.bar'], rewrites
        ) == ['material.sonstigesModell.codeListValue', 'foo.bar']

        and:
        def list = ['foo']
        FeaturesFormatGml.remapList(list, [:]).is(list)
    }
}
