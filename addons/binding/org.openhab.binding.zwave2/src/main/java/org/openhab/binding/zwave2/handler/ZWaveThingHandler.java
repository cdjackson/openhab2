/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zwave2.ZWaveBindingConstants;
import org.openhab.binding.zwave2.internal.converter.ZWaveCommandClassConverter;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveConfigurationParameter;
import org.openhab.binding.zwave2.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveConfigurationCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveConfigurationCommandClass.ZWaveConfigurationParameterEvent;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveWakeUpCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveWakeUpCommandClass.ZWaveWakeUpEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveNodeStatusEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveTransactionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class ZWaveThingHandler extends BaseThingHandler implements ZWaveEventListener {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Sets.newHashSet();

    private Logger logger = LoggerFactory.getLogger(ZWaveThingHandler.class);

    private ZWaveControllerHandler controllerHandler;

    private int nodeId;
    private List<ZWaveThingChannel> thingChannels;

    private ScheduledFuture<?> pollingJob;

    public ZWaveThingHandler(Thing zwaveDevice) {
        super(zwaveDevice);
    }

    @Override
    public void initialize() {
        nodeId = ((BigDecimal) getConfig().get(ZWaveBindingConstants.PARAMETER_NODEID)).intValue();

        // Until we get an update put the Thing into initialisation state
        updateStatus(ThingStatus.INITIALIZING);

        // Create the channels list to simplify processing incoming events
        thingChannels = new ArrayList<ZWaveThingChannel>();
        for (Channel channel : getThing().getChannels()) {
            ZWaveThingChannel zwaveChannel = new ZWaveThingChannel(nodeId, channel);

            thingChannels.add(zwaveChannel);
        }

        logger.debug("NODE {}: Initializing ZWave thing handler.", nodeId);

        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("NODE {}: Polling...", nodeId);
                ZWaveNode node = controllerHandler.getNode(nodeId);
                if (node == null || node.isInitializationComplete() == false) {
                    logger.debug("NODE {}: Polling deferred until initialisation complete", nodeId);
                    return;
                }

                List<SerialMessage> messages = new ArrayList<SerialMessage>();
                for (ZWaveThingChannel channel : thingChannels) {
                    logger.debug("NODE {}: Polling {}", nodeId, channel.getUID());
                    if (channel.converter == null) {
                        logger.debug("NODE {}: Polling aborted as no converter found for {}", nodeId, channel.getUID());
                    } else {
                        messages.addAll(channel.converter.executeRefresh(channel, node));
                    }
                }

                // Send all the messages
                for (SerialMessage message : messages) {
                    controllerHandler.sendData(message);
                }
            }
        };

        pollingJob = scheduler.scheduleAtFixedRate(pollingRunnable, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    protected void bridgeHandlerInitialized(ThingHandler thingHandler, Bridge bridge) {
        controllerHandler = (ZWaveControllerHandler) thingHandler;

        if (nodeId != 0 && controllerHandler != null) {
            // We might not be notified that the controller is online until it's completed a lot of initialisation, so
            // make sure we know the device state.
            ZWaveNode node = controllerHandler.getNode(nodeId);
            if (node != null) {
                switch (node.getNodeState()) {
                    case INITIALIZING:
                        updateStatus(ThingStatus.INITIALIZING);
                        break;
                    case ALIVE:
                        updateStatus(ThingStatus.ONLINE);
                        break;
                    case DEAD:
                    case FAILED:
                        updateStatus(ThingStatus.OFFLINE);
                        break;
                }
            }

            // Add the listener for ZWave events.
            // This ensures we get called whenever there's an event we might be interested in
            controllerHandler.addEventListener(this);
        }
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposed. Unregistering listener.");
        if (nodeId != 0) {
            if (controllerHandler != null) {
                controllerHandler.removeEventListener(this);
            }
            nodeId = 0;
        }

        pollingJob.cancel(true);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        ZWaveNode node = controllerHandler.getNode(nodeId);

        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            String[] cfg = configurationParameter.getKey().split("_");
            if ("config".equals(cfg[0])) {
                if (cfg.length < 3) {
                    logger.warn("NODE{}: Configuration invalid {}", nodeId, configurationParameter.getKey());
                    continue;
                }

                ZWaveConfigurationCommandClass configurationCommandClass = (ZWaveConfigurationCommandClass) node
                        .getCommandClass(CommandClass.CONFIGURATION);
                if (configurationCommandClass == null) {
                    logger.error("NODE {}: Error getting configurationCommandClass", nodeId);
                    continue;
                }

                // Get the size
                int size = Integer.parseInt(cfg[2]);
                if (size == 0 || size > 4) {
                    logger.error("NODE {}: Size error ({}) from {}", nodeId, size, configurationParameter.getKey());
                    continue;
                }

                // Convert to integer
                Integer value;
                if (configurationParameter.getValue() instanceof BigDecimal) {
                    value = ((BigDecimal) configurationParameter.getValue()).intValue();
                } else if (configurationParameter.getValue() instanceof String) {
                    value = Integer.parseInt((String) configurationParameter.getValue());
                } else {
                    logger.error("NODE {}: Error converting config value from {}", nodeId,
                            configurationParameter.getValue().getClass());
                    continue;
                }

                Integer parameterIndex = Integer.valueOf(cfg[1]);
                ZWaveConfigurationParameter cfgParameter = configurationCommandClass.getParameter(parameterIndex);
                if (cfgParameter == null) {
                    cfgParameter = new ZWaveConfigurationParameter(parameterIndex, value, size);
                } else {
                    cfgParameter.setValue(value);
                }

                // Set the parameter and request a read-back
                controllerHandler.sendData(configurationCommandClass.setConfigMessage(cfgParameter));
                controllerHandler.sendData(configurationCommandClass.getConfigMessage(parameterIndex));

                configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
            } else if ("group".equals(cfg[0])) {
                if (cfg.length < 2) {
                    logger.warn("NODE{}: Association invalid {}", nodeId, configurationParameter.getKey());
                    continue;
                }

                Object xxx = configurationParameter.getValue();

                configuration.put(configurationParameter.getKey(), xxx);
            } else if ("wakeup".equals(cfg[0])) {
            } else {
                logger.warn("NODE{}: Configuration invalid {}", nodeId, configurationParameter.getKey());
            }
        }

        // Persist changes
        updateConfiguration(configuration);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (controllerHandler == null) {
            logger.warn("Controller handler not found. Cannot handle command without ZWave controller.");
            return;
        }

        // Find the channel
        ZWaveThingChannel cmdChannel = null;
        for (ZWaveThingChannel channel : thingChannels) {
            if (channel.getUID().equals(channelUID)) {
                cmdChannel = channel;
                break;
            }
        }

        if (cmdChannel == null) {
            logger.warn("NODE {}: Command for unknown channel {}", nodeId, channelUID);
            return;
        }

        ZWaveNode node = controllerHandler.getNode(cmdChannel.getNode());
        if (node == null) {
            logger.warn("NODE {}: Node is not found for {}", cmdChannel.getNode(), channelUID);
            return;
        }

        if (cmdChannel.converter == null) {
            logger.warn("NODE {}: No converter set for {}", cmdChannel.getNode(), channelUID);
            return;
        }
        List<SerialMessage> messages = cmdChannel.converter.receiveCommand(cmdChannel, node, command);
        if (messages == null) {
            logger.warn("NODE {}: No messages returned from converter", cmdChannel.getNode());
            return;
        }

        // Send all the messages
        for (SerialMessage message : messages) {
            controllerHandler.sendData(message);
        }
    }

    @Override
    public void ZWaveIncomingEvent(ZWaveEvent incomingEvent) {
        // Check if this event is for this device
        if (incomingEvent.getNodeId() != nodeId) {
            return;
        }

        // Handle command class value events.
        if (incomingEvent instanceof ZWaveCommandClassValueEvent) {
            // Cast to a command class event
            ZWaveCommandClassValueEvent event = (ZWaveCommandClassValueEvent) incomingEvent;

            String commandClass = event.getCommandClass().getLabel();

            logger.debug(
                    "NODE {}: Got a value event from Z-Wave network, endpoint = {}, command class = {}, value = {}",
                    event.getNodeId(), event.getEndpoint(), commandClass, event.getValue());

            // If this is a configuration parameter update, process it before the channels
            if (event instanceof ZWaveConfigurationParameterEvent) {
                ZWaveConfigurationParameter parameter = ((ZWaveConfigurationParameterEvent) event).getParameter();
                if (parameter != null) {
                    logger.debug("NODE {}: Update {} to {}", nodeId,
                            "config_" + parameter.getIndex() + "_" + parameter.getSize(), parameter.getValue());
                    Configuration configuration = editConfiguration();
                    configuration.put("config_" + parameter.getIndex() + "_" + parameter.getSize(),
                            parameter.getValue());
                    updateConfiguration(configuration);
                }
            }

            // Process the channels to see if we're interested
            for (ZWaveThingChannel channel : thingChannels) {
                if (channel.getEndpoint() != event.getEndpoint()) {
                    continue;
                }

                // Is this command class associated with this channel?
                if (channel.getCommandClass().contains(commandClass) == false) {
                    continue;
                }

                if (channel.converter == null) {
                    logger.warn("NODE {}: No converter set for {}", nodeId, channel.getUID());
                    return;
                }

                logger.debug("NODE {}: Processing event as channel {}", nodeId, channel.getUID());
                updateState(channel.getUID(), channel.converter.handleEvent(channel, event));
            }

            return;
        }

        // Handle transaction complete events.
        if (incomingEvent instanceof ZWaveTransactionCompletedEvent) {

            return;
        }

        // Handle wakeup notification events.
        if (incomingEvent instanceof ZWaveWakeUpEvent) {
            if (((ZWaveWakeUpEvent) incomingEvent)
                    .getEvent() != ZWaveWakeUpCommandClass.WAKE_UP_INTERVAL_CAPABILITIES_REPORT
                    && ((ZWaveWakeUpEvent) incomingEvent)
                            .getEvent() != ZWaveWakeUpCommandClass.WAKE_UP_INTERVAL_REPORT) {
                return;
            }

            ZWaveNode node = controllerHandler.getNode(((ZWaveWakeUpEvent) incomingEvent).getNodeId());
            if (node == null) {
                return;
            }

            ZWaveWakeUpCommandClass commandClass = (ZWaveWakeUpCommandClass) node.getCommandClass(CommandClass.WAKE_UP);
            Configuration configuration = editConfiguration();
            configuration.put("wakeup_interval", commandClass.getInterval());
            configuration.put("wakeup_node", commandClass.getTargetNodeId());
            updateConfiguration(configuration);
            return;
        }

        // Handle node state change events.
        if (incomingEvent instanceof ZWaveNodeStatusEvent) {
            // Cast to a command class event
            ZWaveNodeStatusEvent event = (ZWaveNodeStatusEvent) incomingEvent;

            switch (event.getState()) {
                case INITIALIZING:
                    updateStatus(ThingStatus.INITIALIZING);
                    break;
                case ALIVE:
                    updateStatus(ThingStatus.ONLINE);
                    break;
                case DEAD:
                case FAILED:
                    updateStatus(ThingStatus.OFFLINE);
                    break;
            }

            return;
        }
    }

    public class ZWaveThingChannel {
        ChannelUID uid;
        int node;
        int endpoint;
        LinkedHashSet<String> commandClass;
        ZWaveCommandClassConverter converter;
        ItemType itemType;
        Map<String, String> arguments;

        ZWaveThingChannel(int node, Channel channel) {
            uid = channel.getUID();

            arguments = new HashMap<String, String>();

            // Process the channel properties
            Map<String, String> properties = channel.getProperties();

            // Set the node
            this.node = node;

            // Get the command class('s)
            if (properties.containsKey(ZWaveBindingConstants.CHANNEL_CFG_COMMANDCLASS)) {
                // See if we specify an endpoint
                String[] array = properties.get(ZWaveBindingConstants.CHANNEL_CFG_COMMANDCLASS).split(":");
                if (array.length == 2) {
                    endpoint = Integer.parseInt(array[0]);
                    array[0] = array[1];
                } else {
                    endpoint = 0;
                }

                // Now extract the command classes
                array = array[0].split(",");
                commandClass = new LinkedHashSet<String>(Arrays.asList(array));
            } else {
                logger.warn("NODE {}: No command classes defined in {}", getThing().getUID().toString());
                commandClass = new LinkedHashSet<String>(0);
            }

            for (String key : properties.keySet()) {
                String[] array = key.split(":");
                if (array.length != 2) {
                    continue;
                }
                if (!ZWaveBindingConstants.CHANNEL_CFG_COMMANDCLASS.equals(array[0])) {
                    continue;
                }

                arguments.put(array[1], properties.get(key));
            }

            try {
                itemType = ItemType.valueOf(channel.getAcceptedItemType());
            } catch (IllegalArgumentException e) {
                logger.warn("NODE {}: Invalid item type defined ({}). Assuming Number", nodeId,
                        channel.getAcceptedItemType());
                itemType = ItemType.Number;
            }

            // Get the converter
            this.converter = ZWaveCommandClassConverter
                    .getConverter(ZWaveCommandClass.CommandClass.getCommandClass(commandClass.iterator().next()));
            if (this.converter == null) {
                logger.warn("NODE {}: No converter for {}", nodeId, channel.getUID());
            }
        }

        public ChannelUID getUID() {
            return uid;
        }

        public Set<String> getCommandClass() {
            return commandClass;
        }

        public int getNode() {
            return node;
        }

        public int getEndpoint() {
            return endpoint;
        }

        public ItemType getItemType() {
            return itemType;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }
    }

    public enum ItemType {
        Color,
        Contact,
        DateTime,
        Dimmer,
        Image,
        Location,
        Number,
        Player,
        Rollershutter,
        String,
        Switch;
    }

}
