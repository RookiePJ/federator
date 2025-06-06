// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin
// Programme.

/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp.federator.access.mappings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class AccessTopicsTest {

    @Test
    void test_equality() {
        AccessTopics original = new AccessTopics("Name", "Anything");
        AccessTopics exactMatch = new AccessTopics("Name", "Anything");
        AccessTopics matchDespiteDifferentGrantedAt = new AccessTopics("Name", "AnythingElse");
        AccessTopics matchDespiteCase = new AccessTopics("nAmE", "AnythingElse");

        assertEquals(original, original);
        assertEquals(original, exactMatch);
        assertEquals(original.hashCode(), exactMatch.hashCode());
        assertEquals(original, matchDespiteDifferentGrantedAt);
        assertEquals(original.hashCode(), matchDespiteDifferentGrantedAt.hashCode());
        assertEquals(original.hashCode(), matchDespiteCase.hashCode());
        assertNotEquals(original, new Object());
    }
}
