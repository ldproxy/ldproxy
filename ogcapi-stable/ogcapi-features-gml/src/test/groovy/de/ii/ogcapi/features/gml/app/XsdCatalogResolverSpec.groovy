/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import org.w3c.dom.ls.LSInput
import spock.lang.Specification

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import java.nio.charset.StandardCharsets
import java.util.function.Function

class XsdCatalogResolverSpec extends Specification {

    static final String NAS = 'https://repository.gdi-de.org/schemas/adv/nas/7.1/'

    List<String> lookups = []

    def 'a relative include is looked up in the catalog against the including schema'() {
        given:
        def resolver = resolver([(NAS + 'aaa.xsd'): '<schema/>'])

        when:
        LSInput input = resolver.resolveResource(
                XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null, 'aaa.xsd', NAS + 'NAS-Operationen.xsd')

        then:
        lookups == [NAS + 'aaa.xsd']
        input.systemId == NAS + 'aaa.xsd'
        new String(input.byteStream.readAllBytes(), StandardCharsets.UTF_8) == '<schema/>'
    }

    def 'a schema that cannot be loaded from its host fails at once'() {
        given:
        def resolver = resolver([:])

        when:
        LSInput input = resolver.resolveResource(
                XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null, 'x.xsd', 'http://127.0.0.1:1/schemas/a.xsd')

        then:
        lookups == ['http://127.0.0.1:1/schemas/x.xsd']
        input != null
        input.systemId == 'https://127.0.0.1:1/schemas/x.xsd'
        input.byteStream.read() == -1
    }

    def 'a relative systemId without a base is left to the platform'() {
        given:
        def resolver = resolver([:])

        when:
        LSInput input = resolver.resolveResource(
                XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null, 'local.xsd', null)

        then:
        input == null
    }

    def 'systemIds are resolved against the base URI'() {
        expect:
        FeaturesFormatGml.XsdCatalogResolver.absolute(systemId, baseURI) == expected

        where:
        systemId          | baseURI                       || expected
        'aaa.xsd'         | 'https://h/a/b.xsd'           || 'https://h/a/aaa.xsd'
        '../x/y.xsd'      | 'http://h/a/b/c.xsd'          || 'http://h/a/x/y.xsd'
        'http://o/z.xsd'  | 'https://h/a.xsd'             || 'http://o/z.xsd'
        'aaa.xsd'         | null                          || 'aaa.xsd'
        'a b.xsd'         | 'https://h/a.xsd'             || 'a b.xsd'
    }

    private FeaturesFormatGml.XsdCatalogResolver resolver(Map<String, String> catalog) {
        return new FeaturesFormatGml.XsdCatalogResolver({ String url ->
            lookups << url
            if (!catalog.containsKey(url)) {
                return null
            }
            StreamSource source = new StreamSource(
                    new ByteArrayInputStream(catalog[url].getBytes(StandardCharsets.UTF_8)))
            source.setSystemId(url)
            return source
        } as Function<String, StreamSource>)
    }
}
