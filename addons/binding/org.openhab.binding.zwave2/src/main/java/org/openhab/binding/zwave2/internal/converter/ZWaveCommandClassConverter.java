/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.internal.converter;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave2.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;;

/**
 * ZWaveCommandClassConverter class. Base class for all converters that convert between Z-Wave command classes and
 * openHAB channels.
 *
 * @author Chris Jackson
 */
public abstract class ZWaveCommandClassConverter {
    private static Map<CommandClass, Class<? extends ZWaveCommandClassConverter>> messageMap = null;

    /**
     * Constructor. Creates a new instance of the {@link ZWaveCommandClassConverter} class.
     *
     */
    public ZWaveCommandClassConverter() {
        super();
    }

    /**
     * Execute refresh method. This method is called every time a binding item is refreshed and the corresponding node
     * should be sent a message.
     *
     * @param node the {@link ZWaveNode} that is bound to the item.
     * @param endpointId the endpoint id to send the message.
     */
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        return new ArrayList<SerialMessage>();
    }

    /**
     * Handles an incoming {@link ZWaveCommandClassValueEvent}. Implement this message in derived classes to convert the
     * value and post an update on the openHAB bus.
     *
     * @param event the received {@link ZWaveCommandClassValueEvent}.
     * @return
     */
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        return null;
    }

    /**
     * Receives a command from openHAB and translates it to an operation on the Z-Wave network.
     *
     * @param command the received command
     * @param node the {@link ZWaveNode} to send the command to
     * @param commandClass the {@link ZWaveCommandClass} to send the command to.
     * @param endpointId the endpoint ID to send the command to.
     */
    public List<SerialMessage> receiveCommand(ZWaveThingChannel channel, ZWaveNode node, Command command) {
        return new ArrayList<SerialMessage>();
    }

    public int getRefreshInterval() {
        return 0;
    }

    public static ZWaveCommandClassConverter getConverter(CommandClass commandClass) {
        if (messageMap == null) {
            messageMap = new HashMap<CommandClass, Class<? extends ZWaveCommandClassConverter>>();

            messageMap.put(CommandClass.BASIC, ZWaveBasicConverter.class);
            messageMap.put(CommandClass.BATTERY, ZWaveBatteryConverter.class);
            messageMap.put(CommandClass.SWITCH_BINARY, ZWaveBinarySwitchConverter.class);
            messageMap.put(CommandClass.SWITCH_MULTILEVEL, ZWaveMultiLevelSwitchConverter.class);
        }

        Constructor<? extends ZWaveCommandClassConverter> constructor;
        try {
            if (messageMap.get(commandClass) == null) {
                // logger.warn("CommandClass converter {} is not implemented!", commandClass.getLabel());
                return null;
            }
            constructor = messageMap.get(commandClass).getConstructor();
            return constructor.newInstance();
        } catch (

        Exception e)

        {
            // logger.error("Command processor error");
        }

        return null;
    }
}
