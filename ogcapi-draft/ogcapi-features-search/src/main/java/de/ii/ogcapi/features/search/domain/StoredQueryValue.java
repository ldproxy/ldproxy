/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.docs.DocFile;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import org.immutables.value.Value;

/**
 * @langEn # Stored Queries
 *     <p>Stored queries are queries that the building block [Features -
 *     Search](../services/building-blocks/features_-_search.md) publishes and executes. Their query
 *     expression follows the draft [OGC API - Features - Part 10:
 *     Query](https://docs.ogc.org/DRAFTS/26-008.html#clause-query-expression). See the building
 *     block for the supported options and limitations.
 * @langDe # Stored Queries
 *     <p>Stored Queries sind Abfragen, die der Baustein [Features -
 *     Search](../services/building-blocks/features_-_search.md) veröffentlicht und ausführt. Ihr
 *     Abfrageausdruck folgt dem Entwurf [OGC API - Features - Part 10:
 *     Query](https://docs.ogc.org/DRAFTS/26-008.html#clause-query-expression). Unterstützte
 *     Optionen und Einschränkungen beschreibt der Baustein.
 * @langEn ## Storage
 *     <p>Stored queries reside in the [Store](../application/20-configuration/10-store-new.md) as
 *     values with type `queries` and relative path `{apiId}/{queryId}.json`. If the option
 *     `managerEnabled` of the building block is enabled, they can also be managed via the API using
 *     PUT and DELETE.
 * @langDe ## Speicherung
 *     <p>Stored Queries liegen im [Store](../application/20-configuration/10-store-new.md) als
 *     Values mit Typ `queries` und dem relativen Pfad `{apiId}/{queryId}.json`. Ist die Option
 *     `managerEnabled` des Bausteins aktiviert, können sie auch über die API mit PUT und DELETE
 *     verwaltet werden.
 */
@DocFile(path = "auxiliaries", name = "stored-queries.md")
@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "queries", defaultFormat = FORMAT.JSON)
@JsonDeserialize(builder = ImmutableStoredQueryValue.Builder.class)
public interface StoredQueryValue extends StoredQueryBase, StoredValue {

  abstract class Builder implements ValueBuilder<StoredQueryValue> {}
}
