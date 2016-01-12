/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.handler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave.ZWaveBindingConstants;
import org.openhab.binding.zwave.internal.converter.ZWaveCommandClassConverter;
import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.ZWaveAssociation;
import org.openhab.binding.zwave.internal.protocol.ZWaveAssociationGroup;
import org.openhab.binding.zwave.internal.protocol.ZWaveConfigurationParameter;
import org.openhab.binding.zwave.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveAssociationCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveConfigurationCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveConfigurationCommandClass.ZWaveConfigurationParameterEvent;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveWakeUpCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveWakeUpCommandClass.ZWaveWakeUpEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveAssociationEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveInitializationCompletedEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveNodeStatusEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveTransactionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class ZWaveThingHandler extends BaseThingHandler implements ZWaveEventListener {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Sets.newHashSet();

    private Logger logger = LoggerFactory.getLogger(ZWaveThingHandler.class);

    private ZWaveControllerHandler controllerHandler;

    private int nodeId;
    private List<ZWaveThingChannel> thingChannelsCmd;
    private List<ZWaveThingChannel> thingChannelsState;
    private List<ZWaveThingChannel> thingChannelsPoll;

    private ScheduledFuture<?> pollingJob;

    public ZWaveThingHandler(Thing zwaveDevice) {
        super(zwaveDevice);
    }

    @Override
    public void initialize() {
        final String nodeParm = this.getThing().getProperties().get(ZWaveBindingConstants.PARAMETER_NODEID);
        if (nodeParm == null) {
            logger.debug("NodeID is not set in {}", this.getThing().getUID());
            return;
        }
        try {
            nodeId = Integer.parseInt(nodeParm);
        } catch (final NumberFormatException ex) {
            logger.debug("NodeID ({}) cannot be parsed in {}", nodeParm, this.getThing().getUID());
            return;
        }

        // Until we get an update put the Thing into initialisation state
        updateStatus(ThingStatus.INITIALIZING);

        // Create the channels list to simplify processing incoming events
        thingChannelsCmd = new ArrayList<ZWaveThingChannel>();
        thingChannelsPoll = new ArrayList<ZWaveThingChannel>();
        thingChannelsState = new ArrayList<ZWaveThingChannel>();
        for (Channel channel : getThing().getChannels()) {
            // Process the channel properties
            Map<String, String> properties = channel.getProperties();

            for (String key : properties.keySet()) {
                String[] bindingType = key.split(":");
                if (bindingType.length != 3) {
                    continue;
                }
                if (!ZWaveBindingConstants.CHANNEL_CFG_BINDING.equals(bindingType[0])) {
                    continue;
                }

                String[] bindingProperties = properties.get(key).split(";");

                // TODO: Check length???

                // Get the command classes - comma separated
                String[] cmdClasses = bindingProperties[0].split(",");

                // Convert the arguments to a map
                // - comma separated list of arguments "arg1=val1, arg2=val2"
                Map<String, String> argumentMap = new HashMap<String, String>();
                if (bindingProperties.length == 2) {
                    String[] arguments = bindingProperties[1].split(",");
                    for (String arg : arguments) {
                        String[] prop = arg.split("=");
                        argumentMap.put(prop[0], prop[1]);
                    }
                }

                // Add all the command classes...
                boolean first = true;
                for (String cc : cmdClasses) {
                    String[] ccSplit = cc.split(":");
                    int endpoint = 0;

                    if (ccSplit.length == 2) {
                        endpoint = Integer.parseInt(ccSplit[1]);
                    }

                    // Get the data type
                    DataType dataType = DataType.DecimalType;
                    try {
                        dataType = DataType.valueOf(bindingType[2]);
                    } catch (IllegalArgumentException e) {
                        logger.warn("NODE {}: Invalid item type defined ({}). Assuming DecimalType", nodeId, dataType);
                    }

                    ZWaveThingChannel chan = new ZWaveThingChannel(channel.getUID(), dataType, ccSplit[0], endpoint,
                            argumentMap);

                    // First time round, and this is a command - then add the command
                    if (first && ("*".equals(bindingType[1]) || "Command".equals(bindingType[1]))) {
                        thingChannelsCmd.add(chan);
                    }

                    // Add the state and polling handlers
                    if ("*".equals(bindingType[1]) || "State".equals(bindingType[1])) {
                        thingChannelsState.add(chan);

                        if (first == true) {
                            thingChannelsState.add(chan);
                        }
                    }

                    first = false;
                }
            }
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
                for (ZWaveThingChannel channel : thingChannelsPoll) {
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
            } else if ("group".equals(cfg[0])) {
                if (cfg.length < 2) {
                    logger.warn("NODE{}: Association invalid {}", nodeId, configurationParameter.getKey());
                    continue;
                }

                Integer groupIndex = Integer.valueOf(cfg[1]);

                // Get the association command class
                ZWaveAssociationCommandClass associationCommandClass = (ZWaveAssociationCommandClass) node
                        .getCommandClass(CommandClass.ASSOCIATION);
                        // ZWaveAssociationCommandClass associationCommandClassMulti = (ZWaveAssociationCommandClass)
                        // node
                        // .getCommandClass(CommandClass.ASSOCIATION);

                // Get the configuration information.
                // This should be an array of nodes, and/or nodes and endpoints
                ArrayList<String> paramValues = new ArrayList<String>();
                Object parameter = configurationParameter.getValue();
                if (parameter instanceof List) {
                    paramValues.addAll((List) configurationParameter.getValue());
                } else if (parameter instanceof String) {
                    paramValues.add((String) parameter);
                }

                ZWaveAssociationGroup currentMembers = associationCommandClass.getGroupMembers(groupIndex);
                ZWaveAssociationGroup newMembers = new ZWaveAssociationGroup(groupIndex);

                if (!paramValues.contains("empty")) {
                    // Loop over all the parameters
                    for (String paramValue : paramValues) {
                        String[] groupCfg = paramValue.split("_");

                        // Make sure this is a correctly formatted option
                        if (!"node".equals(groupCfg[0])) {
                            continue;
                        }

                        // Get the node Id and endpoint Id
                        int associationNodeId = Integer.parseInt(groupCfg[1]);
                        int associationEndpointId = Integer.parseInt(groupCfg[2]);

                        newMembers.addAssociation(associationNodeId, associationEndpointId);
                    }
                }

                // Loop through the current members and remove anything that's not in the new members list
                for (ZWaveAssociation member : currentMembers.getAssociations()) {
                    // Is the current association still in the newMembers list?
                    if (newMembers.isAssociated(member.getNode(), member.getEndpoint()) == false) {
                        // No - so it needs to be removed
                        controllerHandler.sendData(
                                associationCommandClass.removeAssociationMessage(groupIndex, member.getNode()));
                    }
                }

                // Now loop through the new members and add anything not in the current members list
                for (ZWaveAssociation member : newMembers.getAssociations()) {
                    // Is the new association still in the currentMembers list?
                    if (currentMembers.isAssociated(member.getNode(), member.getEndpoint()) == false) {
                        // No - so it needs to be added
                        controllerHandler
                                .sendData(associationCommandClass.setAssociationMessage(groupIndex, member.getNode()));
                    }
                }

                // Request an update to the association group
                controllerHandler.sendData(associationCommandClass.getAssociationMessage(groupIndex));
            } else if ("wakeup".equals(cfg[0])) {
            } else {
                logger.warn("NODE{}: Configuration invalid {}", nodeId, configurationParameter.getKey());
            }

            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
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

        DataType dataType;
        try {
            dataType = DataType.valueOf(command.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            logger.warn("NODE {}: Command received with no implementation ({}).", nodeId,
                    command.getClass().getSimpleName());
            return;
        }

        // Find the channel
        ZWaveThingChannel cmdChannel = null;
        for (ZWaveThingChannel channel : thingChannelsCmd) {
            if (channel.getUID().equals(channelUID) && channel.getDataType() == dataType) {
                cmdChannel = channel;
                break;
            }
        }

        if (cmdChannel == null) {
            logger.warn("NODE {}: Command for unknown channel {}", nodeId, channelUID);
            return;
        }

        ZWaveNode node = controllerHandler.getNode(nodeId);
        if (node == null) {
            logger.warn("NODE {}: Node is not found for {}", nodeId, channelUID);
            return;
        }

        if (cmdChannel.converter == null) {
            logger.warn("NODE {}: No converter set for {}", nodeId, channelUID);
            return;
        }

        List<SerialMessage> messages = cmdChannel.converter.receiveCommand(cmdChannel, node, command);
        if (messages == null) {
            logger.warn("NODE {}: No messages returned from converter", nodeId);
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
                    logger.debug("NODE {}: Update CONFIGURATION {} to {}", nodeId,
                            "config_" + parameter.getIndex() + "_" + parameter.getSize(), parameter.getValue());
                    Configuration configuration = editConfiguration();
                    configuration.put("config_" + parameter.getIndex() + "_" + parameter.getSize(),
                            parameter.getValue());
                    updateConfiguration(configuration);
                }

                return;
            }

            // If this is an association event, update the configuration
            if (incomingEvent instanceof ZWaveAssociationEvent) {
                int groupId = ((ZWaveAssociationEvent) event).getGroupId();
                List<ZWaveAssociation> groupMembers = ((ZWaveAssociationEvent) event).getGroupMembers();
                if (groupMembers != null) {
                    logger.debug("NODE {}: Update ASSOCIATION {}", nodeId, "group_" + groupId);
                    Configuration configuration = editConfiguration();

                    List<String> group = new ArrayList<String>();

                    // Build the configuration value
                    for (ZWaveAssociation groupMember : groupMembers) {
                        group.add("node_" + groupMember.getNode() + "_" + groupMember.getEndpoint());
                    }

                    if (group.isEmpty()) {
                        configuration.put("group_" + groupId, "empty");
                    } else {
                        configuration.put("group_" + groupId, group);
                    }
                    updateConfiguration(configuration);
                }

                return;
            }

            // Process the channels to see if we're interested
            for (ZWaveThingChannel channel : thingChannelsState) {
                if (channel.getEndpoint() != event.getEndpoint()) {
                    continue;
                }

                // Is this command class associated with this channel?
                if (!channel.getCommandClass().equals(commandClass)) {
                    continue;
                }

                if (channel.converter == null) {
                    logger.warn("NODE {}: No converter set for {}", nodeId, channel.getUID());
                    return;
                }

                logger.debug("NODE {}: Processing event as channel {} {}", nodeId, channel.getUID(), channel.dataType);
                State state = channel.converter.handleEvent(channel, event);
                if (state != null) {
                    updateState(channel.getUID(), state);
                }
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

        if (incomingEvent instanceof ZWaveInitializationCompletedEvent) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    public class ZWaveThingChannel {
        ChannelUID uid;
        int endpoint;
        String commandClass;
        ZWaveCommandClassConverter converter;
        DataType dataType;
        Map<String, String> arguments;

        ZWaveThingChannel(ChannelUID uid, DataType dataType, String commandClass, int endpoint,
                Map<String, String> arguments) {
            this.uid = uid;
            this.arguments = arguments;
            this.commandClass = commandClass;
            this.endpoint = endpoint;
            this.dataType = dataType;

            // Get the converter
            this.converter = ZWaveCommandClassConverter
                    .getConverter(ZWaveCommandClass.CommandClass.getCommandClass(commandClass));
            if (this.converter == null) {
                logger.warn("NODE {}: No converter for {}, class {}", nodeId, uid, commandClass);
            }
        }

        public ChannelUID getUID() {
            return uid;
        }

        public String getCommandClass() {
            return commandClass;
        }

        public int getEndpoint() {
            return endpoint;
        }

        public DataType getDataType() {
            return dataType;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }
    }

    public enum DataType {
        DecimalType,
        HSBType,
        IncreaseDecreaseType,
        OnOffType,
        OpenClosedType,
        PercentType,
        StopMoveType;
    }
}
