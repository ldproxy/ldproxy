/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock GEO_JSON
 * @examplesEn An example of flattening. The non-flattened feature
 *     <p><code>
 * ```json
 * {
 *   "type" : "Feature",
 *   "id" : "1",
 *   "geometry" : {
 *     "type" : "Point",
 *     "coordinates" : [ 7.0, 50.0 ]
 *   },
 *   "properties" : {
 *     "name" : "Beispiel",
 *     "inspireId" : "https://example.org/id/soziales/kindergarten/1",
 *     "serviceType" : {
 *       "title" : "Kinderbetreuung",
 *       "href" : "https://inspire.ec.europa.eu/codelist/ServiceTypeValue/childCareService"
 *     },
 *     "pointOfContact" : {
 *       "address" : {
 *         "thoroughfare" : "Beispielstr.",
 *         "locatorDesignator" : "123",
 *         "postCode" : "99999",
 *         "adminUnit" : "Irgendwo"
 *       },
 *       "telephoneVoice" : "0211 16021740"
 *     },
 *     "occupancy" : [ {
 *       "typeOfOccupant" : "vorschule",
 *       "numberOfOccupants" : 20
 *     }, {
 *       "typeOfOccupant" : "schulkinder",
 *       "numberOfOccupants" : 25
 *     } ]
 *   }
 * }
 * ```
 *     </code>
 *     <p>looks like this flattened with the default separator:
 *     <p><code>
 * ```json
 * {
 *   "type" : "Feature",
 *   "id" : "1",
 *   "geometry" : {
 *     "type" : "Point",
 *     "coordinates" : [ 7.0, 50.0 ]
 *   },
 *   "properties" : {
 *     "name" : "Beispiel",
 *     "inspireId" : "https://example.org/id/soziales/kindergarten/1",
 *     "serviceType.title" : "Kinderbetreuung",
 *     "serviceType.href" : "https://inspire.ec.europa.eu/codelist/ServiceTypeValue/childCareService",
 *     "pointOfContact.address.thoroughfare" : "Otto-Pankok-Str.",
 *     "pointOfContact.address.locatorDesignator" : "29",
 *     "pointOfContact.address.postCode" : "40231",
 *     "pointOfContact.address.adminUnit" : "Düsseldorf",
 *     "pointOfContact.telephoneVoice" : "0211 16021740",
 *     "occupancy.1.typeOfOccupant" : "vorschule",
 *     "occupancy.1.numberOfOccupants" : 20,
 *     "occupancy.2.typeOfOccupant" : "schulkinder",
 *     "occupancy.2.numberOfOccupants" : 25
 *   }
 * }
 * ```
 *     </code>
 *     <p>The relevant options in the configuration file:
 *     <p><code>
 * ```yaml
 * - buildingBlock: GEO_JSON
 *   transformations:
 *     '*':
 *       flatten: '.'
 * ```
 *     </code>
 * @examplesDe Ein Beispiel zur Abflachung. Das nicht abgeflachte Feature
 *     <p><code>
 * ```json
 * {
 *   "type" : "Feature",
 *   "id" : "1",
 *   "geometry" : {
 *     "type" : "Point",
 *     "coordinates" : [ 7.0, 50.0 ]
 *   },
 *   "properties" : {
 *     "name" : "Beispiel",
 *     "inspireId" : "https://example.org/id/soziales/kindergarten/1",
 *     "serviceType" : {
 *       "title" : "Kinderbetreuung",
 *       "href" : "https://inspire.ec.europa.eu/codelist/ServiceTypeValue/childCareService"
 *     },
 *     "pointOfContact" : {
 *       "address" : {
 *         "thoroughfare" : "Beispielstr.",
 *         "locatorDesignator" : "123",
 *         "postCode" : "99999",
 *         "adminUnit" : "Irgendwo"
 *       },
 *       "telephoneVoice" : "0211 16021740"
 *     },
 *     "occupancy" : [ {
 *       "typeOfOccupant" : "vorschule",
 *       "numberOfOccupants" : 20
 *     }, {
 *       "typeOfOccupant" : "schulkinder",
 *       "numberOfOccupants" : 25
 *     } ]
 *   }
 * }
 * ```
 *     </code>
 *     <p>sieht abgeflacht mit dem Standardtrennzeichen wie folgt aus:
 *     <p><code>
 * ```json
 * {
 *   "type" : "Feature",
 *   "id" : "1",
 *   "geometry" : {
 *     "type" : "Point",
 *     "coordinates" : [ 7.0, 50.0 ]
 *   },
 *   "properties" : {
 *     "name" : "Beispiel",
 *     "inspireId" : "https://example.org/id/soziales/kindergarten/1",
 *     "serviceType.title" : "Kinderbetreuung",
 *     "serviceType.href" : "https://inspire.ec.europa.eu/codelist/ServiceTypeValue/childCareService",
 *     "pointOfContact.address.thoroughfare" : "Otto-Pankok-Str.",
 *     "pointOfContact.address.locatorDesignator" : "29",
 *     "pointOfContact.address.postCode" : "40231",
 *     "pointOfContact.address.adminUnit" : "Düsseldorf",
 *     "pointOfContact.telephoneVoice" : "0211 16021740",
 *     "occupancy.1.typeOfOccupant" : "vorschule",
 *     "occupancy.1.numberOfOccupants" : 20,
 *     "occupancy.2.typeOfOccupant" : "schulkinder",
 *     "occupancy.2.numberOfOccupants" : 25
 *   }
 * }
 * ```
 *     </code>
 *     <p>Die entsprechenden Angaben in der Konfigurationsdatei:
 *     <p><code>
 * ```yaml
 * - buildingBlock: GEO_JSON
 *   transformations:
 *     '*':
 *       flatten: '.'
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "GEO_JSON")
@JsonDeserialize(builder = ImmutableGeoJsonConfiguration.Builder.class)
public interface GeoJsonConfiguration
    extends ExtensionConfiguration, PropertyTransformations, ProfilesConfiguration {

  enum NESTED_OBJECTS {
    NEST,
    FLATTEN
  }

  enum MULTIPLICITY {
    ARRAY,
    SUFFIX
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isFlattened() {
    return hasTransformation(
        PropertyTransformations.WILDCARD,
        transformation -> transformation.getFlatten().isPresent());
  }

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the [Features](features.md) building
   *     block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im [Features](features.md)-Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {"rel": "rel-as-link", "geojson": "rfc7946"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  @Override
  default Builder getBuilder() {
    return new ImmutableGeoJsonConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableGeoJsonConfiguration.Builder()
        .from(source)
        .from(this)
        .transformations(
            PropertyTransformations.super
                .mergeInto((PropertyTransformations) source)
                .getTransformations())
        .defaultProfiles(
            ProfilesConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
