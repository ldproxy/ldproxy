/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app

import de.ii.ogcapi.tiles.domain.ImmutableOwsOnlineResource
import de.ii.ogcapi.tiles.domain.ImmutableWmtsContents
import de.ii.ogcapi.tiles.domain.ImmutableWmtsLayer
import de.ii.ogcapi.tiles.domain.ImmutableWmtsResourceURL
import de.ii.ogcapi.tiles.domain.ImmutableWmtsServiceMetadata
import de.ii.ogcapi.tiles.domain.ImmutableWmtsStyle
import de.ii.ogcapi.tiles.domain.ImmutableWmtsTileMatrix
import de.ii.ogcapi.tiles.domain.ImmutableWmtsTileMatrixSet
import de.ii.ogcapi.tiles.domain.ImmutableWmtsTileMatrixSetLink
import de.ii.ogcapi.tiles.domain.ImmutableWmtsWGS84BoundingBox
import de.ii.ogcapi.tiles.domain.WmtsServiceMetadata
import de.ii.xtraplatform.tiles.domain.ImmutableTileMatrixSetLimits
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import spock.lang.Specification

class WmtsCapabilitiesXmlSpec extends Specification {

    static WmtsServiceMetadata capabilities() {
        def layer = new ImmutableWmtsLayer.Builder()
                .title("Layer")
                .wGS84BoundingBox(new ImmutableWmtsWGS84BoundingBox.Builder()
                        .lowerCornerValues([-180.0, -85.0])
                        .upperCornerValues([180.0, 85.0])
                        .build())
                .identifier("layer")
                .addStyle(new ImmutableWmtsStyle.Builder().identifier("default").build())
                .addFormats("image/png")
                .addTileMatrixSetLink(new ImmutableWmtsTileMatrixSetLink.Builder()
                        .tileMatrixSet("WebMercatorQuad")
                        .addTileMatrixSetLimits(new ImmutableTileMatrixSetLimits.Builder()
                                .tileMatrix("0")
                                .minTileRow(0)
                                .maxTileRow(0)
                                .minTileCol(0)
                                .maxTileCol(0)
                                .build())
                        .build())
                .addResourceURL(new ImmutableWmtsResourceURL.Builder()
                        .format("image/png")
                        .resourceType("tile")
                        .template("http://localhost/{TileMatrix}/{TileRow}/{TileCol}")
                        .build())
                .build()

        def tileMatrixSet = new ImmutableWmtsTileMatrixSet.Builder()
                .title("WebMercatorQuad")
                .identifier("WebMercatorQuad")
                .supportedCRS("EPSG:3857")
                .wellKnownScaleSet("http://www.opengis.net/def/wkss/OGC/1.0/GoogleMapsCompatible")
                .addTileMatrix(new ImmutableWmtsTileMatrix.Builder()
                        .identifier("0")
                        .scaleDenominator(559082264.028717)
                        .topLeftCornerValues([-20037508.3427892, 20037508.3427892] as BigDecimal[])
                        .tileWidth(256)
                        .tileHeight(256)
                        .matrixWidth(1)
                        .matrixHeight(1)
                        .build())
                .build()

        return new ImmutableWmtsServiceMetadata.Builder()
                .contents(new ImmutableWmtsContents.Builder()
                        .addLayers(layer)
                        .addTileMatrixSets(tileMatrixSet)
                        .build())
                .serviceMetadataURL(new ImmutableOwsOnlineResource.Builder()
                        .href("http://localhost/WMTSCapabilities.xml")
                        .build())
                .build()
    }

    static List<Element> childElements(Element element) {
        def nodes = element.getChildNodes()
        return (0..<nodes.getLength())
                .collect { nodes.item(it) }
                .findAll { it.getNodeType() == Node.ELEMENT_NODE }
                .collect { (Element) it }
    }

    static List<String> children(Element element) {
        return childElements(element).collect { it.getLocalName() }
    }

    static Element first(Element root, String localName) {
        return (Element) root.getElementsByTagNameNS("*", localName).item(0)
    }

    def "Capabilities elements follow the sequence of the WMTS schema"() {

        when:
        String xml = new WmtsCapabilitiesXml().getEntity(capabilities(), null, null)
        def factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        Element root = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement()
        Element contents = first(root, "Contents")
        Element tileMatrixSet = childElements(contents).find { it.getLocalName() == "TileMatrixSet" }

        then:
        children(first(root, "WGS84BoundingBox")) == ["LowerCorner", "UpperCorner"]
        children(contents) == ["Layer", "TileMatrixSet"]
        children(first(root, "TileMatrixSetLink")) == ["TileMatrixSet", "TileMatrixSetLimits"]
        children(first(root, "Layer")) == ["Title", "WGS84BoundingBox", "Identifier", "Style",
                                           "Format", "TileMatrixSetLink", "ResourceURL"]
        children(tileMatrixSet) == ["Title", "Identifier", "SupportedCRS", "WellKnownScaleSet",
                                    "TileMatrix"]
        children(root) == ["Contents", "ServiceMetadataURL"]
    }
}
