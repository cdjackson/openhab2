/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.discovery;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.openhab.binding.zwave2.ZWaveBindingConstants;
import org.openhab.binding.zwave2.handler.ZWaveControllerHandler;
import org.openhab.binding.zwave2.internal.config.ZWaveConfigProvider;
import org.openhab.binding.zwave2.internal.config.ZWaveProduct;
import org.openhab.binding.zwave2.internal.protocol.ZWaveNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZWaveDiscoveryService} tracks ZWave devices which are associated to a controller.
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZWaveDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(ZWaveDiscoveryService.class);

    private final static int SEARCH_TIME = 30;

    private ZWaveControllerHandler controllerHandler;

    public ZWaveDiscoveryService(ZWaveControllerHandler coordinatorHandler) {
        super(SEARCH_TIME);
        this.controllerHandler = coordinatorHandler;
    }

    public void activate() {
        logger.debug("Activating ZWave discovery service for {}", controllerHandler.getThing().getUID());

        // Listen for device events
        // coordinatorHandler.addDeviceListener(this);

        // startScan();
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating ZWave discovery service for {}", controllerHandler.getThing().getUID());

        // Remove the listener
        // coordinatorHandler.removeDeviceListener(this);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return ZWaveConfigProvider.getSupportedThingTypes();
    }

    @Override
    public void startScan() {
        logger.debug("Starting ZWave inclusion scan for {}", controllerHandler.getThing().getUID());

        // Add all existing devices
        for (ZWaveNode node : controllerHandler.getNodes()) {
            if (node.getManufacturer() == Integer.MAX_VALUE) {
                deviceDiscovered(node.getNodeId());
            } else {
                deviceAdded(node);
            }
        }

        // Start the search for new devices
        controllerHandler.startDeviceDiscovery();
    }

    private ThingUID getThingUID(ZWaveNode node) {
        ThingUID bridgeUID = controllerHandler.getThing().getUID();

        ThingTypeUID thingTypeUID = null;
        for (ZWaveProduct product : ZWaveConfigProvider.getProductIndex()) {
            if (product.match(node) == true) {
                thingTypeUID = product.getThingTypeUID();
                break;
            }
        }

        if (thingTypeUID == null) {
            logger.warn("NODE {}: Could note be resolved to a thingType! {}:{}:{}::{}",
                    String.format("%04X", node.getManufacturer()), String.format("%04X", node.getDeviceType()),
                    String.format("%04X", node.getDeviceId()), node.getVersion());
            return null;
        }

        // Our ThingType UID is based on the device type
        // ThingTypeUID thingTypeUID = new ThingTypeUID(ZWaveBindingConstants.BINDING_ID, thingID);

        if (getSupportedThingTypes().contains(thingTypeUID)) {
            String thingId = "node" + node.getNodeId();
            return new ThingUID(thingTypeUID, bridgeUID, thingId);
        } else {
            return null;
        }
    }

    public void deviceDiscovered(int nodeId) {
        logger.debug("NODE {}: Device discovered", nodeId);

        ThingUID bridgeUID = controllerHandler.getThing().getUID();

        ThingUID thingUID = new ThingUID(new ThingTypeUID(ZWaveBindingConstants.UNKNOWN_THING), bridgeUID,
                String.format("node%d", nodeId));

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                .withLabel(String.format("Node %d", nodeId)).build();

        thingDiscovered(discoveryResult);
    }

    public ThingUID deviceAdded(ZWaveNode node) {
        logger.debug("NODE {}: Device discovery completed", node.getNodeId());

        ThingUID bridgeUID = controllerHandler.getThing().getUID();

        ZWaveProduct foundProduct = null;
        for (ZWaveProduct product : ZWaveConfigProvider.getProductIndex()) {
            if (product.match(node) == true) {
                foundProduct = product;
                break;
            }
        }

        // Remove the temporary thing
        String thingId = "node" + node.getNodeId();
        ThingUID thingUID = new ThingUID(new ThingTypeUID(ZWaveBindingConstants.UNKNOWN_THING), bridgeUID, thingId);
        thingRemoved(thingUID);

        // If we didn't find the product, then add the unknown thing back in, but with more information
        if (foundProduct == null) {
            logger.warn("NODE {}: Device could not be resolved to a thingType! {}:{}:{}::{}", node.getNodeId(),
                    String.format("%04X", node.getManufacturer()), String.format("%04X", node.getDeviceType()),
                    String.format("%04X", node.getDeviceId()), node.getApplicationVersion());

            thingUID = new ThingUID(new ThingTypeUID(ZWaveBindingConstants.UNKNOWN_THING), bridgeUID,
                    String.format("node%d", node.getNodeId()));

            String label = String.format("Node %d (%04X:%04X:%04X:%s)", node.getNodeId(), node.getManufacturer(),
                    node.getDeviceType(), node.getDeviceId(), node.getApplicationVersion());

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                    .withLabel(label).build();

            thingDiscovered(discoveryResult);

            return null;
        }

        // And create the new thing
        thingUID = new ThingUID(foundProduct.getThingTypeUID(), bridgeUID, thingId);
        ThingType thingType = ZWaveConfigProvider.getThingType(foundProduct.getThingTypeUID());
        String label = String.format("Node %d: %s", node.getNodeId(), thingType.getLabel());

        Map<String, Object> properties = new HashMap<>(1);
        properties.put(ZWaveBindingConstants.PARAMETER_NODEID, BigDecimal.valueOf(node.getNodeId()));
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withBridge(bridgeUID).withLabel(label).build();

        thingDiscovered(discoveryResult);

        return thingUID;
    }

    public void deviceRemoved(ZWaveNode node) {
        ThingUID thingUID = getThingUID(node);

        if (thingUID != null) {
            thingRemoved(thingUID);
        }
    }

    @Override
    protected void startBackgroundDiscovery() {
    }
}