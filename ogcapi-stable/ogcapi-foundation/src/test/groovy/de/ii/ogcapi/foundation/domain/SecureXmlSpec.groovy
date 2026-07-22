/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import javax.xml.stream.XMLStreamException
import javax.xml.transform.sax.SAXSource
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import spock.lang.Specification

class SecureXmlSpec extends Specification {

    private static void parseWithSource(String xml) {
        SAXSource source = SecureXml.source(new InputSource(new StringReader(xml)))
        source.getXMLReader().setContentHandler(new DefaultHandler())
        source.getXMLReader().parse(source.getInputSource())
    }

    def "source() rejects a DOCTYPE declaration (blocks external-entity XXE)"() {
        given:
        String xml =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n" +
                "<foo>&xxe;</foo>"

        when:
        parseWithSource(xml)

        then:
        SAXParseException e = thrown()
        e.message.toLowerCase().contains("doctype")
    }

    def "source() rejects a billion-laughs entity-expansion document"() {
        given:
        String xml =
                "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE lolz [ <!ENTITY lol \"lol\"> <!ENTITY lol2 \"&lol;&lol;\"> ]>\n" +
                "<lolz>&lol2;</lolz>"

        when:
        parseWithSource(xml)

        then:
        thrown(SAXParseException)
    }

    def "source() parses a well-formed document without a DOCTYPE"() {
        when:
        parseWithSource("<foo><bar>baz</bar></foo>")

        then:
        noExceptionThrown()
    }

    def "inputFactory() has DTD support disabled"() {
        expect:
        SecureXml.inputFactory().getProperty("javax.xml.stream.supportDTD") == Boolean.FALSE
    }

    def "inputFactory() rejects a document with a DOCTYPE"() {
        given:
        def reader =
                SecureXml.inputFactory()
                        .createXMLEventReader(
                                new StringReader(
                                        "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]><foo>&xxe;</foo>"))

        when:
        while (reader.hasNext()) {
            reader.nextEvent()
        }

        then:
        thrown(XMLStreamException)
    }
}
