/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument
import de.ii.xtraplatform.xtralink.domain.Jobs

class ArazzoFixtures {

    static ArazzoDocument document(List<Map> workflows, Map extra = [:]) {
        Map document = [
                arazzo            : "1.1.0",
                info              : [title: "Test", version: "1.0.0"],
                sourceDescriptions: [[name: "bag", url: "/bag/api", type: "openapi"]],
                workflows         : workflows,
        ] + extra
        return Jobs.DEFAULT_MAPPER.convertValue(document, ArazzoDocument)
    }

    static Map workflow(String id, List<Map> steps, Map extra = [:]) {
        return [workflowId: id, steps: steps] + extra
    }
}
