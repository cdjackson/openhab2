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

import org.openhab.binding.zwave2.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveController;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveThermostatFanStateCommandClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveThermostatFanStateConverter class. Converter for communication with the
 * {@link ZWaveThermostatFanStateCommandClass}. Implements polling of the fan state and receiving of fan state events.
 *
 * @author Chris Jackson
 * @author Dan Cunningham
 */
public class ZWaveThermostatFanStateConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveThermostatFanStateConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveThermostatFanStateConverter} class.
     *
     * @param controller the {@link ZWaveController} to use for sending messages.
     */
    public ZWaveThermostatFanStateConverter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveThermostatFanStateCommandClass commandClass = (ZWaveThermostatFanStateCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.THERMOSTAT_FAN_MODE, channel.getEndpoint());
        if (commandClass == null) {
            return null;
        }

        logger.debug("NODE {}: Generating poll message for {}, endpoint {}", node.getNodeId(),
                commandClass.getCommandClass().getLabel(), channel.getEndpoint());
        SerialMessage serialMessage = node.encapsulate(commandClass.getValueMessage(), commandClass,
                channel.getEndpoint());
        List<SerialMessage> response = new ArrayList<SerialMessage>(1);
        response.add(serialMessage);
        return response;
    }

}
