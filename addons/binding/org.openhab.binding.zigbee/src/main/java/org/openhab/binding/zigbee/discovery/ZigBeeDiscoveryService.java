/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.ZigBeeApiConstants;
import org.bubblecloud.zigbee.network.ZigBeeNode;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.zigbee.ZigBeeBindingConstants;
import org.openhab.binding.zigbee.handler.ZigBeeCoordinatorHandler;
import org.openhab.binding.zigbee.internal.ZigBeeConfigProvider;
import org.openhab.binding.zigbee.internal.ZigBeeProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZigBeeDiscoveryService} tracks ZigBee devices which are associated
 * to coordinator.
 *
 * @author Chris Jackson - Initial contribution
 *
 */
public class ZigBeeDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(ZigBeeDiscoveryService.class);

    private final static int SEARCH_TIME = 60;

    private ZigBeeCoordinatorHandler coordinatorHandler;
    List<ZigBeeThingType> zigbeeThingTypeList = new ArrayList<ZigBeeThingType>();

    public ZigBeeDiscoveryService(ZigBeeCoordinatorHandler coordinatorHandler) {
        super(SEARCH_TIME);
        this.coordinatorHandler = coordinatorHandler;
        logger.debug("Creating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        // The following code adds devices to a list.
        // The device list resolves Thing names from supported clusters
        // The discovery code will search through this list to find the
        // best matching cluster list and use the name for this Thing.
        // A 'best match' is defined as the maximum number of matching clusters.
        // zigbeeThingTypeList.add(new ZigBeeThingType("OnOffLight", new int[] { ZigBeeApiConstants.CLUSTER_ID_ON_OFF
        // }));
        zigbeeThingTypeList.add(new ZigBeeThingType("zigbee:generic_levelcontrol",
                new int[] { ZigBeeApiConstants.CLUSTER_ID_ON_OFF, ZigBeeApiConstants.CLUSTER_ID_LEVEL_CONTROL }));
        // zigbeeThingTypeList
        // .add(new ZigBeeThingType("ColorDimmableLight", new int[] { ZigBeeApiConstants.CLUSTER_ID_ON_OFF,
        // ZigBeeApiConstants.CLUSTER_ID_COLOR_CONTROL, ZigBeeApiConstants.CLUSTER_ID_LEVEL_CONTROL }));
    }

    public void activate() {
        logger.debug("Activating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        // Listen for device events
        // coordinatorHandler.addDeviceListener(this);

        // startScan();
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating ZigBee discovery service for {}", coordinatorHandler.getThing().getUID());

        // Remove the listener
        // coordinatorHandler.removeDeviceListener(this);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return ZigBeeBindingConstants.SUPPORTED_DEVICE_TYPES_UIDS;
    }

    @Override
    public void startScan() {
        logger.debug("Starting ZigBee scan for {}", coordinatorHandler.getThing().getUID());

        // Start the search for new devices
        coordinatorHandler.startDeviceDiscovery();
    }

    public void addThing(ZigBeeNode node, List<Device> devices, String manufacturer, String model) {
        if (manufacturer == null || model == null) {
            return;
        }
        String manufacturerSplitter[] = manufacturer.split(" ");
        String modelSplitter[] = model.split(" ");
        ThingTypeUID thingTypeUID = null;

        // First we try and find a specific thing for this exact device.
        // This allow us to customise a device if needed.
        String manufacturerSanatized = manufacturerSplitter[0].replaceAll("[^\\x20-\\x7F]", "");
        String modelSanatized = modelSplitter[0].replaceAll("[^\\x20-\\x7F]", "");
        logger.debug("New ZigBee device {}, {}", manufacturer, model);

        // Try and find this product in the database
        for (ZigBeeProduct product : ZigBeeConfigProvider.getProductIndex()) {
            if (product.match(manufacturerSanatized, modelSanatized) == true) {
                thingTypeUID = product.getThingTypeUID();
                logger.info("Found ZigBee device {} in database ('{}' :: '{}')", thingTypeUID, manufacturerSplitter[0],
                        modelSplitter[0]);
                break;
            }
        }

        // Did we find it in the product database?
        // If not, we try and create a generic thing based on the supported clusters
        if (thingTypeUID == null) {
            logger.debug("No ThingUID found in database");
            for (Device device : devices) {
                int max = 0;
                ZigBeeThingType bestThing = null;
                for (ZigBeeThingType thing : zigbeeThingTypeList) {
                    int s = thing.getScore(device);
                    if (s > max) {
                        max = s;
                        bestThing = thing;
                    }
                }
                if (bestThing == null) {
                    logger.debug("No ThingUID found for device {}, type {} ({})", device.getDescription(),
                            device.getDeviceType(), device.getDeviceTypeId());
                } else {
                    thingTypeUID = bestThing.getUID();
                    logger.info("Found ZigBee device {} using clusters ('{}' :: '{}')", thingTypeUID,
                            manufacturerSplitter[0], modelSplitter[0]);
                }
            }
        }

        if (thingTypeUID == null) {
            logger.info("Unknown ZigBee device '{}' :: '{}'", manufacturerSplitter[0], modelSplitter[0]);

            return;
        }

        ThingUID bridgeUID = coordinatorHandler.getThing().getUID();
        String thingId = node.getIeeeAddress().toLowerCase().replaceAll("[^a-z0-9_/]", "");
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, thingId);

        String label = null;
        if (manufacturer != null && model != null) {
            label = manufacturer.toString().trim() + " " + model.toString().trim();
        } else {
            label = "Unknown ZigBee Device " + node.getIeeeAddress();
        }

        logger.info("Creating ZigBee device {} with bridge {}", thingTypeUID, bridgeUID);

        Map<String, Object> properties = new HashMap<>(1);
        properties.put(ZigBeeBindingConstants.PARAMETER_MACADDRESS, node.getIeeeAddress());
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withBridge(bridgeUID).withLabel(label).build();

        thingDiscovered(discoveryResult);
    }

    @Override
    protected void startBackgroundDiscovery() {
    }

    private class ZigBeeThingType {
        private ThingTypeUID uid;
        private List<Integer> clusters;

        private ZigBeeThingType(String uid, int clusters[]) {
            this.uid = new ThingTypeUID(uid);
            this.clusters = new ArrayList<Integer>();
            for (int i = 0; i < clusters.length; i++) {
                this.clusters.add(clusters[i]);
            }
        }

        /**
         * Return a count of how many many clusters this thing type
         * supports in the device
         *
         * @param device
         * @return
         */
        public int getScore(Device device) {
            int score = 0;

            for (int c : device.getInputClusters()) {
                if (clusters.contains(c)) {
                    score++;
                }
            }
            return score;
        }

        public ThingTypeUID getUID() {
            return uid;
        }
    }

}
