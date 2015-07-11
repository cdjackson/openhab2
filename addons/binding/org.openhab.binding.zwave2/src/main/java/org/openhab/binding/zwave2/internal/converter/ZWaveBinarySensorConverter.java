/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.internal.converter;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave2.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveBinarySensorCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveBinarySensorCommandClass.SensorType;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveBinarySensorCommandClass.ZWaveBinarySensorValueEvent;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveBinarySensorConverter class. Converter for communication with the {@link ZWaveBinarySensorConverter}. Implements
 * polling of the binary sensor status and receiving of binary sensor events.
 *
 * @author Chris Jackson
 * @author Jan-Willem Spuij
 */
public class ZWaveBinarySensorConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveBinarySensorConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveBinarySensorConverter} class.
     *
     */
    public ZWaveBinarySensorConverter() {
        super();

        // State and commmand converters used by this converter.
        // this.addStateConverter(new BinaryDecimalTypeConverter());
        // this.addStateConverter(new BinaryPercentTypeConverter());
        // this.addStateConverter(new IntegerOnOffTypeConverter());
        // this.addStateConverter(new IntegerOpenClosedTypeConverter());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveBinarySensorCommandClass commandClass = (ZWaveBinarySensorCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.SENSOR_BINARY, channel.getEndpoint());

        logger.debug("NODE {}: Generating poll message for {}, endpoint {}", node.getNodeId(),
                commandClass.getCommandClass().getLabel(), channel.getEndpoint());
        SerialMessage serialMessage = node.encapsulate(commandClass.getValueMessage(), commandClass,
                channel.getEndpoint());
        List<SerialMessage> response = new ArrayList<SerialMessage>(1);
        response.add(serialMessage);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        String sensorType = channel.getArguments().get("sensorType");
        ZWaveBinarySensorValueEvent sensorEvent = (ZWaveBinarySensorValueEvent) event;

        // Don't trigger event if this item is bound to another alarm type
        if (sensorType != null && SensorType.valueOf(sensorType) != sensorEvent.getSensorType()) {
            return null;
        }

        return sensorEvent.getValue() == 0 ? OnOffType.OFF : OnOffType.ON;
    }
}
