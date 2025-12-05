/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.collections.app.QueriesHandlerCollectionsImpl
import de.ii.ogcapi.collections.domain.*
import de.ii.ogcapi.collections.infra.EndpointCollection
import de.ii.ogcapi.collections.infra.EndpointCollections
import de.ii.ogcapi.common.domain.ImmutableCommonConfiguration
import de.ii.ogcapi.features.core.app.CollectionExtensionFeatures
import de.ii.ogcapi.collections.app.CollectionsExtensionBase
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.app.OgcApiEntity
import de.ii.ogcapi.foundation.domain.*
import de.ii.ogcapi.html.domain.ImmutableHtmlConfiguration
import de.ii.ogcapi.json.domain.ImmutableJsonConfiguration
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import spock.lang.Shared
import spock.lang.Specification

import javax.ws.rs.core.MediaType
import java.util.stream.Collectors

class OgcApiCoreSpecCollections extends Specification {

    @Shared
    FeaturesCoreProviders providers = Mock {
        getFeatureSchema(_, _) >> Optional.of(createFeatureSchema())
    }
    @Shared
    ExtensionRegistry registry = createExtensionRegistry(providers)
    @Shared
    OgcApiDataV2 datasetData = createDatasetData()
    @Shared
    OgcApiEntity api = createOgcApiApiEntity(registry, datasetData)
    @Shared
    ApiRequestContext requestContext = createRequestContext(api)
    @Shared
    QueriesHandlerCollectionsImpl ogcApiQueriesHandlerCollections = new QueriesHandlerCollectionsImpl(registry, createI18n())
    @Shared
    EndpointCollections collectionsEndpoint = createCollectionsEndpoint(registry, ogcApiQueriesHandlerCollections)
    @Shared
    EndpointCollection collectionEndpoint = createCollectionEndpoint(registry, ogcApiQueriesHandlerCollections)

    def 'Requirement 13 A: collections response'() {
        given: 'A request to the server at /collections'

        when: 'The response is created'
        def collections = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections

        then: 'the response shall include a link to this document'
        collections.links.any { it.rel == 'self' }

        and: 'a link to the response document in every other media type supported by the server'
        collections.links.any { it.rel == 'alternate' }
    }

    def 'Requirement 14 A: collections response'() {
        given: 'A request to the server at /collections'

        when: 'The response is created'
        def collections = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections

        then: 'each feature shall be provided in the property collections'
        collections.collections.any { it.id == 'featureType1' }
    }

    def 'Requirement 15 A: collections items links'() {
        given: 'A request to the server at /collections'

        when: 'The response is created'
        def collections = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections

        then: 'links property of each feature shall include an item for each supported encoding with a link to the features resource'
        def links = collections.collections.stream().map { x -> x.getLinks() }.collect(Collectors.toList())
        links.any { it.any { it.rel == "items" } }
    }

    def 'Requirement 15 B: collections items links'() {
        given: 'A request to the server at /collections'

        when: 'The response is created'
        def result = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections

        then: 'all links shall include the rel and type properties'
        def links = result.getCollections()
                .stream()
                .map { x -> x.getLinks() }
                .collect(Collectors.toList())
        links.any { it.any { it.rel == "items" && it.type == "application/json" } }
    }

    def 'Requirement 16 A: extent property'() {
        given: 'A request to the server at /collections'

        when: 'The response is created'
        Collections result = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections

        then: 'the extent property shall provide bounding boxes that include all spatial geometries and time intervals that include all temporal geometries in this collection'
        result.collections.any { it.extent.get().getSpatial().get().bbox[0] == ([-180.0, -90.0, 180.0, 90.0] as double[]) }
    }


    def 'Requirement 18 B: feature collection response'() {
        given: 'A request to the server at /collections'

        when: 'the response is created'
        def allCollections = collectionsEndpoint.getCollections(Optional.empty(), api, requestContext).entity as Collections
        def singleCollection = collectionEndpoint.getCollection(Optional.empty(), api, requestContext, "featureType1").entity as OgcApiCollection

        then: 'the values for id, title, description and extent shall be identical to the values in the /collections response'
        allCollections.getCollections().get(0).id == singleCollection.id
        allCollections.getCollections().get(0).title == singleCollection.title
        allCollections.getCollections().get(0).description == singleCollection.description
        allCollections.getCollections().get(0).extent.get().getSpatial().get().bbox == singleCollection.getExtent().get().getSpatial().get().bbox
        allCollections.getCollections().get(0).extent.get().getSpatial().get().crs == singleCollection.extent.get().getSpatial().get().crs
        allCollections.getCollections().get(0).extent.get().getTemporal().get().interval == singleCollection.extent.get().getTemporal().get().interval
        allCollections.getCollections().get(0).extent.get().getTemporal().get().trs == singleCollection.extent.get().getTemporal().get().trs
    }


    static def createExtensionRegistry(FeaturesCoreProviders providers) {
        new ExtensionRegistry() {

            @Override
            List<ApiExtension> getExtensions() {
                return ImmutableList.of()

            }

            @Override
            <T extends ApiExtension> List<T> getExtensionsForType(Class<T> extensionType) {
                ExtensionRegistry self = this;
                if (extensionType == CollectionsFormatExtension.class) {
                    return ImmutableList.of((T) new CollectionsFormatExtension() {
                        @Override
                        ApiMediaType getMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build()
                        }

                        @Override
                        ApiMediaTypeContent getContent() {
                            return new ImmutableApiMediaTypeContent.Builder()
                                    .build()
                        }

                        @Override
                        Object getEntity(Collections collections, OgcApi api, ApiRequestContext requestContext) {
                            return collections
                        }
                    }, (T) new CollectionsFormatExtension() {
                        @Override
                        ApiMediaType getMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.TEXT_HTML_TYPE)
                                    .build()
                        }

                        @Override
                        ApiMediaTypeContent getContent() {
                            return new ImmutableApiMediaTypeContent.Builder()
                                    .build()
                        }

                        @Override
                        Object getEntity(Collections collections, OgcApi api, ApiRequestContext requestContext) {
                            return collections
                        }
                    })
                } else if (extensionType == CollectionFormatExtension.class) {
                    return ImmutableList.of((T) new CollectionFormatExtension() {
                        @Override
                        ApiMediaType getMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build()
                        }

                        @Override
                        ApiMediaTypeContent getContent() {
                            return new ImmutableApiMediaTypeContent.Builder()
                                    .build()
                        }

                        @Override
                        Object getEntity(OgcApiCollection ogcApiCollection, OgcApi api, ApiRequestContext requestContext) {
                            return ogcApiCollection
                        }
                    }, (T) new CollectionFormatExtension() {
                        @Override
                        ApiMediaType getMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.TEXT_HTML_TYPE)
                                    .build()
                        }

                        @Override
                        ApiMediaTypeContent getContent() {
                            return new ImmutableApiMediaTypeContent.Builder()
                                    .build()
                        }

                        @Override
                        Object getEntity(OgcApiCollection ogcApiCollection, OgcApi api, ApiRequestContext requestContext) {
                            return ogcApiCollection
                        }
                    })
                }
                if (extensionType == CollectionsExtension.class) {
                    return ImmutableList.of((T) new CollectionsExtensionBase(self))
                }

                if (extensionType == CollectionExtension.class) {
                    CollectionExtensionFeatures collectionExtension = new CollectionExtensionFeatures(self, createI18n(), providers)
                    return ImmutableList.of((T) collectionExtension)
                }

                if (extensionType == FeatureFormatExtension.class) {
                    return ImmutableList.of((T) new FeatureFormatExtension(self, null) {

                        @Override
                        ApiMediaType getMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build()
                        }

                        @Override
                        ApiMediaTypeContent getContent() {
                            return new ImmutableApiMediaTypeContent.Builder()
                                    .build()
                        }

                        @Override
                        boolean isEnabledForApi(OgcApiDataV2 apiData) {
                            return true
                        }

                        @Override
                        ApiMediaType getCollectionMediaType() {
                            return new ImmutableApiMediaType.Builder()
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build()
                        }
                    })
                }
                return ImmutableList.of()
            }
        }
    }

    static def createRequestContext(OgcApiEntity api, String uri = 'http://example.com/collections') {
        new ImmutableStaticRequestContext.Builder()
                .webContext(new AppContextTest())
                .mediaType(new ImmutableApiMediaType.Builder()
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build())
                .alternateMediaTypes(ImmutableList.of(new ImmutableApiMediaType.Builder()
                        .type(MediaType.TEXT_HTML_TYPE)
                        .build()))
                .api(api)
                .requestUri(new URI(uri))
                .queryParameterSet(QueryParameterSet.of())
                .language(Locale.GERMAN)
                .build()
    }

    static def createDatasetData() {
        return new ImmutableOgcApiDataV2.Builder()
                .id('test')
                .serviceType('OGC_API')
                .putCollections('featureType1', new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id('featureType1')
                        .label('FeatureType 1')
                        .description('foo bar')
                        .extent(new ImmutableCollectionExtent.Builder()
                                .spatial(BoundingBox.of(-180.0, -90.0, 180.0, 90.0, OgcCrs.CRS84))
                                .temporal(new ImmutableTemporalExtent.Builder().build())
                                .build())
                        .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                                .enabled(true)
                                .build())
                        .enabled(true)
                        .build())
                .addExtensions(new ImmutableCommonConfiguration.Builder()
                        .enabled(true)
                        .build())
                .addExtensions(new ImmutableCollectionsConfiguration.Builder()
                        .enabled(true)
                        .build())
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                        .enabled(true)
                        .build())
                .addExtensions(new ImmutableJsonConfiguration.Builder()
                        .enabled(true)
                        .build())
                .addExtensions(new ImmutableHtmlConfiguration.Builder()
                        .enabled(true)
                        .build())
                .build()
    }

    static def createFeatureSchema() {
        return new ImmutableFeatureSchema.Builder()
                .name("featureType1")
                .type(SchemaBase.Type.OBJECT)
                .sourcePath("/table1")
                .putProperties2("geo",
                        new ImmutableFeatureSchema.Builder()
                                .type(SchemaBase.Type.GEOMETRY)
                                .role(SchemaBase.Role.PRIMARY_GEOMETRY))
                .putProperties2("date",
                        new ImmutableFeatureSchema.Builder()
                                .type(SchemaBase.Type.DATE)
                                .role(SchemaBase.Role.PRIMARY_INSTANT))
                .build()
    }

    static def createOgcApiApiEntity(ExtensionRegistry registry, OgcApiDataV2 datasetData) {
        def entity = new OgcApiEntity(null, registry, () -> "", new AppContextTest(), null, new CacheTest(), datasetData)
        entity.updateSpatialExtent("featureType1", BoundingBox.of(-180, -90, 180, 90, OgcCrs.CRS84))
        entity.updateTemporalExtent("featureType1", TemporalExtent.of(null, null))
        entity.updateItemCount("featureType1", 0)
        return entity
    }

    static def createCollectionsEndpoint(ExtensionRegistry registry, QueriesHandlerCollectionsImpl ogcApiQueriesHandlerCollections) {
        return new EndpointCollections(registry, ogcApiQueriesHandlerCollections)
    }

    static def createCollectionEndpoint(ExtensionRegistry registry, QueriesHandlerCollectionsImpl ogcApiQueriesHandlerCollections) {
        return new EndpointCollection(registry, ogcApiQueriesHandlerCollections)
    }

    static def createI18n() {
        new I18n() {
            @Override
            Set<Locale> getLanguages() {
                return Set.of()
            }

            @Override
            String get(String key) {
                return ""
            }

            @Override
            String get(String key, Optional<Locale> language) {
                return ""
            }

            @Override
            Set<String> getKeys() {
                return Set.of()
            }

            @Override
            Set<String> getKeysWithPrefix(String prefix) {
                return Set.of()
            }
        }
    }

}
