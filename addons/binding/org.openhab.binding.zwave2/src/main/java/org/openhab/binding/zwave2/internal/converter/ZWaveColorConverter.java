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
import java.util.Collection;
import java.util.List;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave2.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveColorCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveColorCommandClass.ZWaveColorValueEvent;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveColorConverter class. Converter for communication with the {@link ZWaveColorCommandClass}. Implements
 * polling of the status and receiving of events.
 *
 * @author Chris Jackson
 */
public class ZWaveColorConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveColorConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveColorConverter} class.
     *
     */
    public ZWaveColorConverter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveColorCommandClass commandClass = (ZWaveColorCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.COLOR, channel.getEndpoint());
        if (commandClass == null) {
            return null;
        }

        logger.debug("NODE {}: Generating poll message for {}, endpoint {}", node.getNodeId(),
                commandClass.getCommandClass().getLabel(), channel.getEndpoint());

        // Add a poll to update the color
        List<SerialMessage> messages = new ArrayList<SerialMessage>();
        Collection<SerialMessage> rawMessages = commandClass.getColor();
        for (SerialMessage msg : rawMessages) {
            messages.add(node.encapsulate(msg, commandClass, channel.getEndpoint()));
        }
        return messages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        ZWaveColorValueEvent colorEvent = null;
        if (!(event instanceof ZWaveColorValueEvent)) {
            return null;
        }

        colorEvent = (ZWaveColorValueEvent) event;

        colorEvent.getColorType();
        State state;
        switch (channel.getDataType()) {
            case HSBType:
                state = null;
                break;
            default:
                state = null;
                logger.warn("No conversion in {} to {}", this.getClass().getSimpleName(), channel.getDataType());
                break;
        }

        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> receiveCommand(ZWaveThingChannel channel, ZWaveNode node, Command command) {
        ZWaveColorCommandClass commandClass = (ZWaveColorCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.COLOR, channel.getEndpoint());

        // Command must be color - convert to zwave format
        HSBType color = (HSBType) command;

        logger.debug("NODE {}: Converted command '{}' to value {} {} {} for channel = {}, endpoint = {}.",
                node.getNodeId(), command.toString(), color.getRed().intValue(), color.getGreen().intValue(),
                color.getBlue().intValue(), channel.getUID(), channel.getEndpoint());

        // Since we get an HSB, there is brightness information. However, we only deal with the color class here
        // so we need to scale the color and let the brightness be handled by the multi_level command class
        // TODO: Does this need to be configurable - some devices might need to handle brightness here?
        double scaling = 100 / color.getBrightness().doubleValue() * 256 / 100;

        List<SerialMessage> messages = new ArrayList<SerialMessage>();

        // Queue the command
        Collection<SerialMessage> rawMessages = commandClass.setColor((int) (color.getRed().doubleValue() * scaling),
                (int) (color.getGreen().doubleValue() * scaling), (int) (color.getBlue().doubleValue() * scaling), 0,
                0);
        for (SerialMessage msg : rawMessages) {
            messages.add(node.encapsulate(msg, commandClass, channel.getEndpoint()));
        }

        // Add a poll to update the color
        rawMessages = commandClass.getColor();
        for (SerialMessage msg : rawMessages) {
            messages.add(node.encapsulate(msg, commandClass, channel.getEndpoint()));
        }
        return messages;
    }
}
