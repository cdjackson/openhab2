/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zigbee.handler;

import static org.openhab.binding.zigbee.ZigBeeBindingConstants.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bubblecloud.zigbee.ZigBeeApi;
import org.bubblecloud.zigbee.api.Device;
import org.bubblecloud.zigbee.api.DeviceListener;
import org.bubblecloud.zigbee.api.cluster.Cluster;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.Attribute;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ReportListener;
import org.bubblecloud.zigbee.api.cluster.impl.api.core.ZigBeeClusterException;
import org.bubblecloud.zigbee.api.cluster.impl.attribute.AttributeDescriptor;
import org.bubblecloud.zigbee.network.NodeListener;
import org.bubblecloud.zigbee.network.ZigBeeEndpoint;
import org.bubblecloud.zigbee.network.ZigBeeNode;
import org.bubblecloud.zigbee.network.ZigBeeNodeDescriptor;
import org.bubblecloud.zigbee.network.ZigBeeNodePowerDescriptor;
import org.bubblecloud.zigbee.network.impl.ZigBeeEndpointImpl;
import org.bubblecloud.zigbee.network.impl.ZigBeeNetworkManagerException;
import org.bubblecloud.zigbee.network.impl.ZigBeeNodeImpl;
import org.bubblecloud.zigbee.network.model.DiscoveryMode;
import org.bubblecloud.zigbee.network.port.ZigBeePort;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.zigbee.discovery.ZigBeeDiscoveryService;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;

//public String serializeNetworkState() {
//public void deserializeNetworkState(final String networkState) {

/**
 * The {@link ZigBeeCoordinatorHandler} is responsible for handling commands,
 * which are sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public abstract class ZigBeeCoordinatorHandler extends BaseBridgeHandler implements NodeListener {
    protected int panId;
    protected int channelId;

    protected ZigBeeApi zigbeeApi = null;
    private ScheduledFuture<?> restartJob = null;
    private ZigBeePort networkInterface;

    private ZigBeeDiscoveryService discoveryService;
    private ServiceRegistration<?> serviceReg;

    private String folderName = "userdata/zigbee";

    private Logger logger = LoggerFactory.getLogger(ZigBeeCoordinatorHandler.class);

    public ZigBeeCoordinatorHandler(Bridge coordinator) {
        super(coordinator);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing ZigBee network [{}].", this.thing.getUID());

        try {
            panId = ((BigDecimal) getConfig().get(PARAMETER_PANID)).intValue();
            channelId = ((BigDecimal) getConfig().get(PARAMETER_CHANNEL)).intValue();
        } catch (ClassCastException e) {

        }
        final String USERDATA_DIR_PROG_ARGUMENT = "smarthome.userdata";
        final String eshUserDataFolder = System.getProperty(USERDATA_DIR_PROG_ARGUMENT);
        if (eshUserDataFolder != null) {
            folderName = eshUserDataFolder + "/zigbee";
        }

        final File folder = new File(folderName);

        // create path for serialization.
        if (!folder.exists()) {
            logger.debug("Creating directory {}", folderName);
            folder.mkdirs();
        }
    }

    @Override
    public void dispose() {
        // Remove the discovery service
        discoveryService.deactivate();
        serviceReg.unregister();

        // If we have scheduled tasks, stop them
        if (restartJob != null) {
            restartJob.cancel(true);
        }

        // Shut down the ZigBee library
        if (zigbeeApi != null) {
            zigbeeApi.shutdown();
        }
        logger.debug("ZigBee network [{}] closed.", this.thing.getUID());
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
        logger.debug("Updating coordinator [{}]", this.thing.getUID());
    }

    @Override
    protected void updateStatus(ThingStatus status, ThingStatusDetail detail, String desc) {
        super.updateStatus(status, detail, desc);
        for (Thing child : getThing().getThings()) {
            child.setStatusInfo(new ThingStatusInfo(status, detail, desc));
        }
    }

    /**
     * Common initialisation point for all ZigBee coordinators.
     * Called by bridges after they have initialised their interfaces.
     *
     * @param networkInterface a ZigBeePort interface instance
     */
    protected void startZigBee(ZigBeePort networkInterface) {
        this.networkInterface = networkInterface;

        // Start the network. This is a scheduled task to ensure we give the coordinator
        // some time to initialise itself!
        startZigBeeNetwork();
    }

    /**
     * Initialise the ZigBee network
     */
    private void initialiseZigBee() {
        logger.debug("Initialising coordinator");

        final EnumSet<DiscoveryMode> discoveryModes = DiscoveryMode.ALL;

        zigbeeApi = new ZigBeeApi(networkInterface, panId, channelId, false, discoveryModes);
        zigbeeApi.initializeHardware();

        boolean reset = false;
        int channel = zigbeeApi.getZigBeeNetworkManager().getCurrentChannel();
        int pan = zigbeeApi.getZigBeeNetworkManager().getCurrentPanId();
        if (channel != channelId || pan != panId) {
            logger.info("ZigBee current pan={}, channel={}. Network will be reset.", pan, channel);
            reset = true;
        }

        if (!zigbeeApi.initializeNetwork(reset)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                    "Unable to start ZigBee network");

            // Shut down the ZigBee library
            zigbeeApi.shutdown();
            zigbeeApi = null;

            restartZigBeeNetwork();

            return;
        }

        logger.debug("ZigBee network [{}] started", this.thing.getUID());

        final List<ZigBeeEndpoint> endpoints = new ArrayList<ZigBeeEndpoint>();
        for (final ZigBeeNode node : zigbeeApi.getNodes()) {
            for (final ZigBeeEndpoint endpoint : zigbeeApi.getNodeEndpoints(node)) {
                endpoints.add(endpoint);
            }
        }
        waitForNetwork();
    }

    /**
     * If the network initialisation fails, then periodically reschedule a restart
     */
    private void startZigBeeNetwork() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("ZigBee network starting");
                restartJob = null;
                initialiseZigBee();
            }
        };

        logger.debug("Scheduling ZigBee start");
        restartJob = scheduler.schedule(runnable, 1, TimeUnit.SECONDS);
    }

    /**
     * If the network initialisation fails, then periodically reschedule a restart
     */
    private void restartZigBeeNetwork() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("ZigBee network restarting");
                restartJob = null;
                initialiseZigBee();
            }
        };

        logger.debug("Scheduleing ZigBee restart");
        restartJob = scheduler.schedule(runnable, 15, TimeUnit.SECONDS);
    }

    /**
     * Wait for the network initialisation to complete.
     */
    protected void waitForNetwork() {
        // Start the discovery service
        discoveryService = new ZigBeeDiscoveryService(this);
        discoveryService.activate();

        // And register it as an OSGi service
        serviceReg = bundleContext.registerService(DiscoveryService.class.getName(), discoveryService,
                new Hashtable<String, Object>());

        logger.debug("Browsing ZigBee network [{}]...", this.thing.getUID());
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (!zigbeeApi.isInitialBrowsingComplete()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                browsingComplete();
            }
        };

        // Kick off the discovery
        thread.start();
    }

    /**
     * Called after initial browsing is complete. At this point we're good to go...
     */
    protected void browsingComplete() {
        updateStatus(ThingStatus.ONLINE);

        logger.debug("ZigBee network [{}] READY. Found {} nodes.", this.thing.getUID(), zigbeeApi.getNodes().size());

        final List<ZigBeeNode> nodes = zigbeeApi.getNodes();
        for (ZigBeeNode node : nodes) {
            addNewNode(node);
        }

        // Add a listener for any new devices
        zigbeeApi.addNodeListener(this);

        // Notify all our things...
        logger.debug("Bridge connection open. Updating thing status to ONLINE.");
        // now also re-initialize all light handlers
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler != null) {
                handler.initialize();
            }
        }
    }

    private Device getDeviceByIndexOrEndpointId(ZigBeeApi zigbeeApi, String deviceIdentifier) {
        Device device;
        device = zigbeeApi.getDevice(deviceIdentifier);
        if (device == null) {
            logger.debug("Error finding ZigBee device with address {}", deviceIdentifier);
        }
        return device;
    }

    public Object attributeRead(String zigbeeAddress, int clusterId, int attributeIndex) {
        final Device device = getDeviceByIndexOrEndpointId(zigbeeApi, zigbeeAddress);
        if (device == null) {
            return null;
        }

        return readAttribute(device, clusterId, attributeIndex);
    }

    public Object readAttribute(Device device, int clusterId, int attributeIndex) {
        final Cluster cluster = device.getCluster(clusterId);
        if (cluster == null) {
            logger.debug("Cluster not found.");
            return null;
        }

        final Attribute attribute = cluster.getAttributes()[attributeIndex];
        if (attribute == null) {
            logger.debug("Attribute not found.");
            return null;
        }

        try {
            return attribute.getValue();
        } catch (ZigBeeClusterException e) {
            e.printStackTrace();
            return null;
        }
    }

    public <T extends Cluster> Attribute openAttribute(String zigbeeAddress, Class<T> clusterId,
            AttributeDescriptor attributeId, ReportListener listener) {
        final Device device = getDeviceByIndexOrEndpointId(zigbeeApi, zigbeeAddress);
        if (device == null) {
            return null;
        }
        Cluster cluster = device.getCluster(clusterId);
        if (cluster == null) {
            return null;
        }
        Attribute attribute = cluster.getAttribute(attributeId.getId());
        if (attribute == null) {
            return null;
        }

        if (listener != null) {
            attribute.getReporter().addReportListener(listener, false);
        }
        return attribute;
    }

    public void closeAttribute(Attribute attribute, ReportListener listener) {
        if (attribute != null && listener != null) {
            attribute.getReporter().removeReportListener(listener, false);
        }
    }

    public <T extends Cluster> T openCluster(String zigbeeAddress, Class<T> clusterId) {
        final Device device = getDeviceByIndexOrEndpointId(zigbeeApi, zigbeeAddress);
        if (device == null) {
            return null;
        }
        return device.getCluster(clusterId);
    }

    public void closeCluster(Cluster cluster) {

    }

    /**
     * Returns a list of all known devices
     *
     * @return list of devices
     */
    public List<Device> getDeviceList() {
        return zigbeeApi.getDevices();
    }

    public void startDeviceDiscovery() {
        final List<ZigBeeNode> nodes = zigbeeApi.getNodes();
        for (ZigBeeNode node : nodes) {
            addNewNode(node);
        }

        // Allow devices to join for 60 seconds
        zigbeeApi.permitJoin(60);

        // ZigBeeDiscoveryManager discoveryManager = zigbeeApi.getZigBeeDiscoveryManager();
        // discoveryManager.
    }

    /**
     * Adds a device listener to receive updates on device status
     *
     * @param listener
     */
    public void addDeviceListener(DeviceListener listener) {
        zigbeeApi.addDeviceListener(listener);
    }

    /**
     * Removes a device listener to receive updates on device status
     *
     * @param listener
     */
    public void removeDeviceListener(DeviceListener listener) {
        zigbeeApi.removeDeviceListener(listener);
    }

    /**
     * Adds a new device to the network.
     * This starts a thread to read information about the device so we can
     * create the thingType, and the label for the user.
     *
     * @param device
     */
    private void addNewNode(ZigBeeNode node) {
        DiscoveryThread discover = new DiscoveryThread();
        discover.run(node);
    }

    private class DiscoveryThread extends Thread {
        public void run(ZigBeeNode node) {
            logger.debug("Node Discovery: {}", node.getIeeeAddress());

            // Get the list of endpoints found on this node
            List<ZigBeeEndpoint> endpoints = zigbeeApi.getNodeEndpoints(node);

            // Is it valid?
            if (endpoints == null || endpoints.size() == 0) {
                logger.warn("Node has no endpoints: {}", node.getIeeeAddress());
                return;
            }

            // Create a list of devices for the discovery service to work with
            List<Device> devices = new ArrayList<Device>();
            for (ZigBeeEndpoint endpoint : endpoints) {
                Device device = zigbeeApi.getDevice(endpoint.getEndpointId());
                if (device != null) {
                    devices.add(device);
                    logger.debug("Node {} is {}", device.getIeeeAddress(), device.getDeviceType());
                }
            }

            // Make sure we found some devices!
            if (devices.size() == 0) {
                logger.warn("Node has no devices: {}", node.getIeeeAddress());
                return;
            }

            // Use the first device to get the device information required to
            // define a thingType and description
            String manufacturer = (String) readAttribute(devices.get(0), 0, 4);
            String model = (String) readAttribute(devices.get(0), 0, 5);

            // Signal to the handlers that they are known...
            // ZigBeeEventListener listener = eventListeners.get(device.getEndpointId());
            // if (listener != null) {
            // if (listener.openDevice()) {
            // listener.onEndpointStateChange();
            // }
            // }

            discoveryService.addThing(node, devices, manufacturer, model);
        }
    }

    private XStream createXStream() {
        XStream stream = new XStream(new StaxDriver());

        stream.alias("Endpoint", ZigBeeEndpointImpl.class);
        stream.alias("Node", ZigBeeNodeImpl.class);
        stream.alias("NodeDescriptor", ZigBeeNodeDescriptor.class);
        stream.alias("PowerDescriptor", ZigBeeNodePowerDescriptor.class);
        stream.setClassLoader(ZigBeeEndpointImpl.class.getClassLoader());
        // stream.addImplicitCollection(ZigBeeNodeDescriptor.class, "macCapabilities");

        return stream;
    }

    private void serializeNode(ZigBeeNode node) {
        // Create a copy of the node for serialization
        ZigBeeNodeImpl node2 = new ZigBeeNodeImpl();
        node2.setIeeeAddress(node.getIeeeAddress());
        node2.setNetworkAddress(node.getNetworkAddress());
        node2.setNodeDescriptor(node.getNodeDescriptor());
        node2.setPowerDescriptor(node.getPowerDescriptor());

        final List<ZigBeeEndpoint> endpoints = new ArrayList<ZigBeeEndpoint>();
        for (final ZigBeeEndpoint endpoint : zigbeeApi.getNodeEndpoints(node)) {
            ZigBeeEndpointImpl endpoint2 = new ZigBeeEndpointImpl();
            endpoint2.setNode(node2);
            endpoint2.setDeviceTypeId(endpoint.getDeviceTypeId());
            endpoint2.setProfileId(endpoint.getProfileId());
            endpoint2.setDeviceVersion(endpoint.getDeviceVersion());
            endpoint2.setEndPointAddress(endpoint.getEndPointAddress());
            endpoint2.setInputClusters(endpoint.getInputClusters());
            endpoint2.setOutputClusters(endpoint.getOutputClusters());
            endpoint2.setEndpointId(endpoint.getEndpointId());

            endpoints.add(endpoint2);
        }

        final XStream stream = createXStream();

        File file = new File(this.folderName, node.getIeeeAddress() + ".xml");
        BufferedWriter writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            stream.marshal(endpoints, new PrettyPrintWriter(writer));
            writer.flush();
        } catch (IOException e) {
            logger.error("{}: Error serializing network to file: {}", this.thing.getUID(), e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public void deserializeNode(String address) {
        File file = new File(this.folderName, address + ".xml");
        BufferedReader reader = null;

        logger.debug("{}: Serializing from file {}", address, file.getPath());

        if (!file.exists()) {
            logger.debug("{}: Error serializing from file: file does not exist.", address);
            return;
        }

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            final XStream stream = createXStream();
            Object xxx = stream.fromXML(reader);
            final List<ZigBeeEndpoint> endpoints = (List<ZigBeeEndpoint>) xxx;

            for (final ZigBeeEndpoint endpoint : endpoints) {
                // Check if the node is existing
                ZigBeeNodeImpl existingNode = zigbeeApi.getZigBeeNetwork().getNode(endpoint.getNode().getIeeeAddress());
                if (existingNode == null) {
                    zigbeeApi.getZigBeeNetwork().addNode((ZigBeeNodeImpl) endpoint.getNode());
                } else {
                    ((ZigBeeEndpointImpl) endpoint).setNode(existingNode);
                }

                // Check if the endpoint is existing

                ((ZigBeeEndpointImpl) endpoint).setNetworkManager(zigbeeApi.getZigBeeNetworkManager());
                zigbeeApi.getZigBeeNetwork().addEndpoint(endpoint);
            }

        } catch (IOException e) {
            logger.error("{}: Error serializing from file: {}", address, e.getMessage());
        } catch (ConversionException e) {
            logger.error("{}: Error serializing from file: {}", address, e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return;
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        logger.debug("Node is added to network: {}", node.getIeeeAddress());
    }

    @Override
    public void nodeDiscovered(ZigBeeNode node) {
        logger.debug("Node discovery complete: {}", node.getIeeeAddress());
        addNewNode(node);

        serializeNode(node);
    }

    @Override
    public void nodeUpdated(ZigBeeNodeImpl node) {
        logger.debug("Node updated: {}", node.getIeeeAddress());
        serializeNode(node);
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        logger.debug("Node removed: {}", node.getIeeeAddress());
        // TODO Remove the XML ??
        // serializeNetwork();
    }

    public Device getDevice(String endPointId) {
        return zigbeeApi.getDevice(endPointId);
    }

    public void bindAttribute(String address, int cluster, int attribute) {
        Device server = getDeviceByIndexOrEndpointId(zigbeeApi, address);

        try {
            server.bindToLocal(cluster);
        } catch (final ZigBeeNetworkManagerException e) {
            e.printStackTrace();
        }
    }
}
