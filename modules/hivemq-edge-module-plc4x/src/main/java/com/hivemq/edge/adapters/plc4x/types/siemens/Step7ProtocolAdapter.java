/*
 * Copyright 2023-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.edge.adapters.plc4x.types.siemens;

import com.codahale.metrics.MetricRegistry;
import com.hivemq.edge.adapters.plc4x.impl.AbstractPlc4xAdapter;
import com.hivemq.edge.modules.api.adapters.ProtocolAdapterInformation;

/**
 * @author HiveMQ Adapter Generator
 */
public class Step7ProtocolAdapter extends AbstractPlc4xAdapter<Step7AdapterConfig> {

    public Step7ProtocolAdapter(
            final ProtocolAdapterInformation adapterInformation,
            final Step7AdapterConfig adapterConfig,
            final MetricRegistry metricRegistry) {
        super(adapterInformation, adapterConfig, metricRegistry);
    }

    @Override
    protected String getProtocolHandler() {
        return "s7";
    }

    @Override
    protected AbstractPlc4xAdapter.ReadType getReadType() {
        return ReadType.Read;
    }
}
