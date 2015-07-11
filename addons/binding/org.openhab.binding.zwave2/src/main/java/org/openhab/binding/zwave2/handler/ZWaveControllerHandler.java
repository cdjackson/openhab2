/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.handler;

import static org.openhab.binding.zwave2.ZWaveBindingConstants.*;

import java.util.Hashtable;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zwave2.discovery.ZWaveDiscoveryService;
import org.openhab.binding.zwave2.internal.ZWaveNetworkMonitor;
import org.openhab.binding.zwave2.internal.config.ZWaveConfigProvider;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage;
import org.openhab.binding.zwave2.internal.protocol.ZWaveController;
import org.openhab.binding.zwave2.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZWaveControllerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public abstract class ZWaveControllerHandler extends BaseThingHandler implements ZWaveEventListener {

    private Logger logger = LoggerFactory.getLogger(ZWaveControllerHandler.class);

    private ZWaveDiscoveryService discoveryService;

    private volatile ZWaveController controller;

    // Network monitoring class
    ZWaveNetworkMonitor networkMonitor;

    // private Iterator<ZWavePollItem> pollingIterator = null;
    // private List<ZWavePollItem> pollingList = new ArrayList<ZWavePollItem>();

    private Boolean isMaster;
    private Boolean isSUC;

    public ZWaveControllerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZWave Controller.");

        isMaster = (Boolean) getConfig().get(PARAMETER_MASTER);
        isSUC = (Boolean) getConfig().get(PARAMETER_SUC);

        super.initialize();
    }

    /**
     * Common initialisation point for all ZWave controllers.
     * Called by bridges after they have initialised their interfaces.
     *
     * @param networkInterface a ZWave controller instance
     */
    protected void initializeNetwork(String port) {
        logger.debug("Initialising ZWave controller");

        // TODO: Handle soft reset better!
        controller = new ZWaveController(this);
        controller.addEventListener(this);

        // The network monitor service needs to know the controller...
        this.networkMonitor = new ZWaveNetworkMonitor(controller);
        // if(healtime != null) {
        // this.networkMonitor.setHealTime(healtime);
        // }
        // if(aliveCheckPeriod != null) {
        // this.networkMonitor.setPollPeriod(aliveCheckPeriod);
        // }
        // if(softReset != false) {
        // this.networkMonitor.resetOnError(softReset);
        // }

        // The config service needs to know the controller and the network monitor...
        // this.zConfigurationService = new ZWaveConfiguration(this.zController, this.networkMonitor);
        // zController.addEventListener(this.zConfigurationService);

        // Start the discovery service
        discoveryService = new ZWaveDiscoveryService(this);
        discoveryService.activate();

        // And register it as an OSGi service
        bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                new Hashtable<String, Object>());
    }

    @Override
    public void dispose() {
        // Remove the discovery service
        discoveryService.deactivate();

        // if (this.converterHandler != null) {
        // this.converterHandler = null;
        // }

        ZWaveController controller = this.controller;
        if (controller != null) {
            this.controller = null;
            controller.removeEventListener(this);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // if(channelUID.getId().equals(CHANNEL_1)) {
        // TODO: handle command
        // }
    }

    public void startDeviceDiscovery() {
        // TODO Auto-generated method stub

    }

    /**
     * This method rebuilds the polling table. The polling table is a list of items that have
     * polling enabled (ie a refresh interval is set). This list is then checked periodically
     * and any item that has passed its polling interval will be polled.
     */
    private void rebuildPollingTable() {
        // Rebuild the polling table
        // pollingList.clear();

        // if (converterHandler == null) {
        // logger.debug("ConverterHandler not initialised. Polling disabled.");

        // return;
        // }
        /*
         * // Loop all bound items for this provider
         * for (String name : eachProvider.getItemNames()) {
         * // Find the node and check if it's completed initialisation.
         * ZWaveBindingConfig cfg = eachProvider.getZwaveBindingConfig(name);
         * ZWaveNode node = controller.getNode(cfg.getNodeId());
         * if(node == null) {
         * logger.debug("NODE {}: Polling list: can't get node for item {}", cfg.getNodeId(), name);
         * continue;
         * }
         * if(node.getNodeInitializationStage() != ZWaveNodeInitStage.DONE) {
         * logger.debug("NODE {}: Polling list: item {} is not completed initialisation", cfg.getNodeId(), name);
         * continue;
         * }
         *
         * logger.trace("Polling list: Checking {} == {}", name, converterHandler.getRefreshInterval(eachProvider,
         * name));
         *
         * // If this binding is configured to poll - add it to the list
         * if (converterHandler.getRefreshInterval(eachProvider, name) > 0) {
         * ZWavePollItem item = new ZWavePollItem();
         * item.item = name;
         * item.provider = eachProvider;
         * pollingList.add(item);
         * logger.trace("Polling list added {}", name);
         * }
         * }
         *
         * pollingIterator = null;
         */
    }

    @Override
    public void ZWaveIncomingEvent(ZWaveEvent event) {
        // TODO Auto-generated method stub

    }

    private class ZWavePollItem {
        // ZWaveBindingProvider provider;
        String item;
    }

    protected void incomingMessage(SerialMessage serialMessage) {
        if (controller == null) {
            return;
        }
        controller.incomingPacket(serialMessage);
    }

    public abstract void sendPacket(SerialMessage serialMessage);

    public void deviceDiscovered(int nodeId) {
        // Don't add the controller as a thing
        if (controller.getOwnNodeId() == nodeId) {
            return;
        }

        if (discoveryService == null) {
            return;
        }
        discoveryService.deviceDiscovered(nodeId);
    }

    public void deviceAdded(ZWaveNode node) {
        // Don't add the controller as a thing
        if (controller.getOwnNodeId() == node.getNodeId()) {
            return;
        }

        if (discoveryService == null) {
            return;
        }
        ThingUID newThing = discoveryService.deviceAdded(node);
        if (newThing == null) {
            return;
        }

        ThingType thingType = ZWaveConfigProvider.getThingType(newThing.getThingTypeUID());

        // thingType.getProperties()
    }

    public ZWaveNode getNode(int node) {
        if (controller == null) {
            return null;
        }
        return controller.getNode(node);
    }

    public void sendData(SerialMessage message) {
        controller.sendData(message);

    }

    public void addEventListener(ZWaveThingHandler zWaveThingHandler) {
        controller.addEventListener(zWaveThingHandler);
    }

    public void removeEventListener(ZWaveThingHandler zWaveThingHandler) {
        controller.removeEventListener(zWaveThingHandler);
    }
}
