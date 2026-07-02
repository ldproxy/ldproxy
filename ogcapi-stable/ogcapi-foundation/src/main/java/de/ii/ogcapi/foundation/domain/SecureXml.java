/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Central factory for XML processors hardened against XXE (external-entity injection) and
 * entity-expansion attacks. Every place that parses or validates attacker-supplied XML should
 * obtain its parser/validator here so the hardening cannot drift between call sites.
 *
 * <p>This lives in ogcapi-foundation because all current callers are ldproxy building blocks. If a
 * non-ldproxy class (e.g. in xtraplatform or xtraplatform-spatial) needs the same capability, move
 * this class down into the xtraplatform layer so it can be shared across repositories.
 */
public final class SecureXml {

  private SecureXml() {}

  /**
   * A StAX {@link XMLInputFactory} with DTD support and external-entity resolution disabled. Use
   * for event/stream parsing of untrusted XML.
   */
  public static XMLInputFactory inputFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    return factory;
  }

  /**
   * A W3C XML Schema {@link SchemaFactory} with secure processing enabled. External schema
   * resolution is left enabled because schemas are loaded from trusted sources (bundled resources
   * or admin-configured locations) and may import/include other schemas; secure processing would
   * otherwise deny that. External DTD access stays denied (schemas do not use DTDs). Attacker-
   * supplied instance documents are still parsed with the hardened reader from {@link
   * #source(InputSource)}. Callers may set a resource resolver.
   */
  public static SchemaFactory schemaFactory() {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    trySetFeature(factory);
    trySetFactoryProperty(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");
    return factory;
  }

  /**
   * A {@link SAXSource} backed by a reader that rejects DOCTYPE declarations and does not resolve
   * external entities, for validating an untrusted instance document. A fresh reader is created per
   * call so the settings apply regardless of {@link Validator#reset()} (which clears
   * validator-level configuration).
   */
  public static SAXSource source(InputSource input) {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      XMLReader reader = factory.newSAXParser().getXMLReader();
      return new SAXSource(reader, input);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IllegalStateException("Could not create a secure XML parser for validation.", e);
    }
  }

  /**
   * Defense-in-depth for instance validation: forbid the validator from resolving any external DTD
   * or external schema referenced by the instance document, and enable secure processing. The
   * primary defense is {@link #source(InputSource)}; unsupported properties/features are ignored.
   * Call after every {@link Validator#reset()} because reset clears this configuration.
   */
  public static void harden(Validator validator) {
    try {
      validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (SAXException e) {
      // not supported by this implementation
    }
    trySetProperty(validator, XMLConstants.ACCESS_EXTERNAL_DTD);
    trySetProperty(validator, XMLConstants.ACCESS_EXTERNAL_SCHEMA);
  }

  private static void trySetFeature(SchemaFactory factory) {
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (SAXException e) {
      // not supported by this implementation
    }
  }

  private static void trySetFactoryProperty(SchemaFactory factory, String property, String value) {
    try {
      factory.setProperty(property, value);
    } catch (SAXException e) {
      // not supported by this implementation
    }
  }

  private static void trySetProperty(Validator validator, String property) {
    try {
      validator.setProperty(property, "");
    } catch (SAXException e) {
      // not supported by this implementation
    }
  }
}
