/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.internal.converter;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave2.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveSceneConverter class. Converters between binding items and the Z-Wave API for scene controllers.
 *
 * @author Chris Jackson
 */
public class ZWaveSceneConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveSceneConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveConverterBase} class.
     *
     */
    public ZWaveSceneConverter() {
        super();

        // State converters used by this converter.
        // this.addStateConverter(new BinaryDecimalTypeConverter());
        // this.addStateConverter(new IntegerOnOffTypeConverter());
        // this.addStateConverter(new IntegerOpenClosedTypeConverter());
    }

    @Override
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        if (channel.getArguments().get("scene") == null) {
            return null;
        }

        int scene = Integer.parseInt(channel.getArguments().get("scene"));
        if (scene != (Integer) event.getValue()) {
            return null;
        }
        Integer state = Integer.parseInt(channel.getArguments().get("state"));

        return new DecimalType(state);
    }
}
