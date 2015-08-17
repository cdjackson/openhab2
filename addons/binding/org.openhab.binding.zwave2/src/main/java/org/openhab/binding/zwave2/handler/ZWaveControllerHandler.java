/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.handler;

import static org.openhab.binding.zwave2.ZWaveBindingConstants.*;

import java.util.Collection;
import java.util.Hashtable;

import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.zwave2.discovery.ZWaveDiscoveryService;
import org.openhab.binding.zwave2.internal.ZWaveConfigProvider;
import org.openhab.binding.zwave2.internal.ZWaveNetworkMonitor;
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

    @Override
    public void ZWaveIncomingEvent(ZWaveEvent event) {
        // TODO Auto-generated method stub

    }

    protected void incomingMessage(SerialMessage serialMessage) {
        if (controller == null) {
            return;
        }
        controller.incomingPacket(serialMessage);
    }

    public abstract void sendPacket(SerialMessage serialMessage);

    public void deviceDiscovered(int nodeId) {
        if (discoveryService == null) {
            return;
        }
        discoveryService.deviceDiscovered(nodeId);
    }

    public void deviceAdded(ZWaveNode node) {
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

    public int getOwnNodeId() {
        return controller.getOwnNodeId();
    }

    public ZWaveNode getNode(int node) {
        if (controller == null) {
            return null;
        }

        return controller.getNode(node);
    }

    public Collection<ZWaveNode> getNodes() {
        return controller.getNodes();
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
