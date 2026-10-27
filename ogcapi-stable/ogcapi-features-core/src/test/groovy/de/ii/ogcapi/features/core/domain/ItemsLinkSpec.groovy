/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import spock.lang.Specification

class ItemsLinkSpec extends Specification {

    def 'the link to a feature of a listed collection starts at the items of the collection'() {
        when:
        def link = EndpointFeaturesDefinition.linkFromItems('item', 'buildings')

        then:
        link.getOperationId() == 'buildings.getItems'
        link.getParameters() == ['featureId': '$response.body#/features/0/id']
        link.getDescription().get().contains('first feature')
    }

    def 'the link to a feature of a collection that is not listed takes the collection from the request'() {
        when:
        def link = EndpointFeaturesDefinition.linkFromItems('item', '{collectionId}')

        then: 'the id of the items operation of a collection that is not listed'
        link.getOperationId() == 'collection.getItems'
        link.getParameters() == [
                'featureId'   : '$response.body#/features/0/id',
                'collectionId': '$request.path.collectionId']
    }
}
