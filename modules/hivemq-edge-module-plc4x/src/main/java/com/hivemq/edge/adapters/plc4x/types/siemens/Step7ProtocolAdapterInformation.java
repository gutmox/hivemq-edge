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

import com.hivemq.edge.modules.api.adapters.ProtocolAdapterInformation;
import com.hivemq.edge.modules.config.CustomConfig;
import com.hivemq.edge.modules.adapters.impl.AbstractProtocolAdapterInformation;
import com.hivemq.extension.sdk.api.annotations.NotNull;

/**
 * @author HiveMQ Adapter Generator
 */
public class Step7ProtocolAdapterInformation
    extends AbstractProtocolAdapterInformation {

    public static final ProtocolAdapterInformation INSTANCE = new Step7ProtocolAdapterInformation();

    protected Step7ProtocolAdapterInformation() {
    }

    @Override
    public @NotNull String getProtocolName() {
        return "Plc4x";
    }

    @Override
    public @NotNull String getProtocolId() {
        return "plc4x";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Plc4x to MQTT Protocol Adapter";
    }

    @Override
    public @NotNull String getDescription() {
        return "Connects HiveMQ Edge to existing Plc4x devices, bringing data from the PLC into MQTT.";
    }

    @Override
    public @NotNull Class<? extends CustomConfig> getConfigClass() {
        return Step7AdapterConfig.class;
    }
}
