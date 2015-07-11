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
import org.openhab.binding.zwave2.internal.protocol.ZWaveController;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmCommandClass.AlarmType;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmCommandClass.ZWaveAlarmValueEvent;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveAlarmConverter class. Converter for communication with the {@link ZWaveAlarmCommandClass}. Implements polling of
 * the alarm status and receiving of alarm events.
 *
 * @author Chris Jackson
 */
public class ZWaveAlarmConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveAlarmConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveAlarmConverter} class.
     *
     * @param controller the {@link ZWaveController} to use for sending messages.
     */
    public ZWaveAlarmConverter() {
        super();

        // State and commmand converters used by this converter.
        // this.addStateConverter(new IntegerDecimalTypeConverter());
        // this.addStateConverter(new IntegerPercentTypeConverter());
        // this.addStateConverter(new IntegerOnOffTypeConverter());
        // this.addStateConverter(new IntegerOpenClosedTypeConverter());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveAlarmCommandClass commandClass = (ZWaveAlarmCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.ALARM, channel.getEndpoint());

        String alarmType = channel.getArguments().get("alarmType");
        logger.debug("NODE {}: Generating poll message for {}, endpoint {}, alarm {}", node.getNodeId(),
                commandClass.getCommandClass().getLabel(), channel.getEndpoint(), alarmType);

        SerialMessage serialMessage;
        if (alarmType != null) {
            serialMessage = node.encapsulate(
                    commandClass.getMessage(AlarmType.getAlarmType(Integer.parseInt(alarmType))), commandClass,
                    channel.getEndpoint());
        } else {
            serialMessage = node.encapsulate(commandClass.getValueMessage(), commandClass, channel.getEndpoint());
        }

        if (serialMessage == null) {
            return null;
        }

        List<SerialMessage> response = new ArrayList<SerialMessage>();
        response.add(serialMessage);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        String alarmType = channel.getArguments().get("alarmType");
        ZWaveAlarmValueEvent alarmEvent = (ZWaveAlarmValueEvent) event;

        // Don't trigger event if this item is bound to another alarm type
        if (alarmType != null && AlarmType.getAlarmType(Integer.parseInt(alarmType)) != alarmEvent.getAlarmType()) {
            return null;
        }

        return alarmEvent.getValue() == 0 ? OnOffType.OFF : OnOffType.ON;
    }
}
