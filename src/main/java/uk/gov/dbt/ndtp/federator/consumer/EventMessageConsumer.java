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

package uk.gov.dbt.ndtp.federator.consumer;

import java.time.Duration;
import uk.gov.dbt.ndtp.secure.agent.sources.Event;
import uk.gov.dbt.ndtp.secure.agent.sources.EventSource;

public class EventMessageConsumer<Key, Value> implements MessageConsumer<Event<Key, Value>> {

    private final EventSource<Key, Value> source;

    private final Duration pollDuration;

    public EventMessageConsumer(EventSource<Key, Value> eventSource, Duration duration) {
        source = eventSource;
        pollDuration = duration;
    }

    @Override
    public boolean stillAvailable() {
        return !source.isClosed();
    }

    @Override
    public Event<Key, Value> getNextMessage() {
        return source.poll(pollDuration);
    }

    @Override
    public void close() {
        source.close();
    }
}
