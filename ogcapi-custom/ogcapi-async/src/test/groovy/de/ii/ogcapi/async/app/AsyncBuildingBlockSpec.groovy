/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app

import de.ii.ogcapi.common.domain.async.AsyncConfiguration
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import spock.lang.Specification

// The consumers of the async building block (search, transactions, processes) are not on the test
// classpath, they are represented by building block configurations with their async options.
class AsyncBuildingBlockSpec extends Specification {

    def "async is #asyncEnabled and named outputs are #namedOutputs for #consumers"() {
        given:
        OgcApiDataV2 apiData = apiData(configurations)

        expect:
        AsyncBuildingBlock.isAsyncEnabled(apiData) == asyncEnabled
        AsyncBuildingBlock.isNamedOutputsEnabled(apiData) == namedOutputs

        where:
        consumers                               | configurations                                            || asyncEnabled | namedOutputs
        "no async configuration"                | [plain()]                                                 || false        | false
        "search without async"                  | [search(null)]                                            || false        | false
        "search with async=false"               | [search(false)]                                           || false        | false
        "search with async"                     | [search(true)]                                            || true         | false
        "disabled search with async"            | [search(true, false)]                                     || false        | false
        "processes with async"                  | [processes(true)]                                         || true         | true
        "processes with async=false"            | [processes(false)]                                        || false        | false
        "search and processes with async"       | [search(true), processes(true)]                           || true         | true
        "search with async, processes without"  | [search(true), processes(false)]                          || true         | false
    }

    private OgcApiDataV2 apiData(List<ExtensionConfiguration> configurations) {
        Stub(OgcApiDataV2) {
            getMergedExtensions() >> configurations
        }
    }

    private ExtensionConfiguration plain() {
        Stub(ExtensionConfiguration) {
            isEnabled() >> true
        }
    }

    private ExtensionConfiguration search(Boolean async, boolean enabled = true) {
        consumer(async, enabled, false)
    }

    private ExtensionConfiguration processes(Boolean async) {
        consumer(async, true, true)
    }

    private ExtensionConfiguration consumer(Boolean async, boolean enabled, boolean namedOutputs) {
        Stub(ExtensionConfiguration, additionalInterfaces: [AsyncConfiguration]) {
            isEnabled() >> enabled
            getAsync() >> async
            hasNamedOutputs() >> namedOutputs
        } as ExtensionConfiguration
    }
}
