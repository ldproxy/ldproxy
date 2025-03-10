/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.domain

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.foundation.domain.AbstractExtensionConfigurationSpec
import de.ii.ogcapi.foundation.domain.ImmutableLink
import de.ii.ogcapi.foundation.domain.MergeBase
import de.ii.ogcapi.foundation.domain.MergeCollection
import de.ii.ogcapi.foundation.domain.MergeMinimal
import de.ii.ogcapi.foundation.domain.MergeSimple

@SuppressWarnings('ClashingTraitMethods')
class CollectionsConfigurationSpec extends AbstractExtensionConfigurationSpec implements MergeBase<CollectionsConfiguration>, MergeMinimal<CollectionsConfiguration>, MergeSimple<CollectionsConfiguration>, MergeCollection<CollectionsConfiguration> {
    @Override
    CollectionsConfiguration getFull() {
        return new ImmutableCollectionsConfiguration.Builder()
                .enabled(false)
                .addAdditionalLinks(new ImmutableLink.Builder()
                        .title("foo")
                        .href("bar")
                        .rel("baz")
                        .build())
                .build()
    }

    @Override
    CollectionsConfiguration getMinimal() {
        return new ImmutableCollectionsConfiguration.Builder()
                .build()
    }

    @Override
    CollectionsConfiguration getMinimalFullMerged() {
        return getFull()
    }

    @Override
    CollectionsConfiguration getSimple() {
        return new ImmutableCollectionsConfiguration.Builder()
                .enabled(true)
                .build()
    }

    @Override
    CollectionsConfiguration getSimpleFullMerged() {
        return new ImmutableCollectionsConfiguration.Builder()
                .from(getFull())
                .enabled(true)
                .build()
    }

    @Override
    CollectionsConfiguration getCollection() {
        return new ImmutableCollectionsConfiguration.Builder()
                .addAdditionalLinks(new ImmutableLink.Builder()
                        .title("bar")
                        .href("foo")
                        .rel("baz")
                        .build())
                .build()
    }

    @Override
    CollectionsConfiguration getCollectionFullMerged() {
        return new ImmutableCollectionsConfiguration.Builder()
                .from(getFull())
                .additionalLinks(ImmutableList.of(
                        new ImmutableLink.Builder()
                                .title("foo")
                                .href("bar")
                                .rel("baz")
                                .build(),
                        new ImmutableLink.Builder()
                                .title("bar")
                                .href("foo")
                                .rel("baz")
                                .build()
                ))
                .build()
    }
}
