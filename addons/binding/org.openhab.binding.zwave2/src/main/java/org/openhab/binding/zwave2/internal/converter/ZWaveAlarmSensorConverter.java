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
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmSensorCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmSensorCommandClass.AlarmType;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveAlarmSensorCommandClass.ZWaveAlarmSensorValueEvent;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWaveAlarmSensorConverter class. Converter for communication with the {@link ZWaveAlarmSensorCommandClass}.
 * Implements polling of the alarm sensor status and receiving of alarm sensor events.
 *
 * @author Chris Jackson
 * @author Jan-Willem Spuij - OH1 implementation
 */
public class ZWaveAlarmSensorConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveAlarmSensorConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveAlarmSensorConverter} class.
     *
     */
    public ZWaveAlarmSensorConverter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveAlarmSensorCommandClass commandClass = (ZWaveAlarmSensorCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.SENSOR_ALARM, channel.getEndpoint());
        if (commandClass == null) {
            return null;
        }

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
        ZWaveAlarmSensorValueEvent alarmEvent = (ZWaveAlarmSensorValueEvent) event;

        // Don't trigger event if this item is bound to another alarm type
        if (alarmType != null && AlarmType.getAlarmType(Integer.parseInt(alarmType)) != alarmEvent.getAlarmType()) {
            return null;
        }

        return alarmEvent.getValue() == 0 ? OnOffType.OFF : OnOffType.ON;
    }
}
