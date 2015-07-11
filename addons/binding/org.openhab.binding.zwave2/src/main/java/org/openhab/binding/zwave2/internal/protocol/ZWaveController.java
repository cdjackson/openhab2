/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave2.internal.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openhab.binding.zwave2.handler.ZWaveControllerHandler;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage.SerialMessageClass;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage.SerialMessagePriority;
import org.openhab.binding.zwave2.internal.protocol.SerialMessage.SerialMessageType;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveCommandClassDynamicState;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveMultiInstanceCommandClass;
import org.openhab.binding.zwave2.internal.protocol.commandclass.ZWaveWakeUpCommandClass;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveInclusionEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveNetworkEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveNodeStatusEvent;
import org.openhab.binding.zwave2.internal.protocol.event.ZWaveTransactionCompletedEvent;
import org.openhab.binding.zwave2.internal.protocol.initialization.ZWaveNodeSerializer;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.AddNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.AssignReturnRouteMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.AssignSucReturnRouteMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.ControllerSetDefaultMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.DeleteReturnRouteMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.EnableSucMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.GetControllerCapabilitiesMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.GetRoutingInfoMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.GetSucNodeIdMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.GetVersionMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.IdentifyNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.IsFailedNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.MemoryGetIdMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.RemoveFailedNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.RemoveNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.RequestNodeInfoMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.RequestNodeNeighborUpdateMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SendDataMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SerialApiGetCapabilitiesMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SerialApiGetInitDataMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SerialApiSetTimeoutsMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SerialApiSoftResetMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.SetSucNodeMessageClass;
import org.openhab.binding.zwave2.internal.protocol.serialmessage.ZWaveCommandProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZWave controller class. Implements communication with the ZWave controller
 * stick using serial messages.
 *
 * @author Chris Jackson
 * @author Victor Belov
 * @author Brian Crosby
 */
public class ZWaveController {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveController.class);

    private static final int ZWAVE_RESPONSE_TIMEOUT = 5000;
    private static final int INITIAL_TX_QUEUE_SIZE = 128;
    private static final int INITIAL_RX_QUEUE_SIZE = 8;
    private static final long WATCHDOG_TIMER_PERIOD = 10000;

    private static final int TRANSMIT_OPTION_ACK = 0x01;
    private static final int TRANSMIT_OPTION_AUTO_ROUTE = 0x04;
    private static final int TRANSMIT_OPTION_EXPLORE = 0x20;

    private final ConcurrentHashMap<Integer, ZWaveNode> zwaveNodes = new ConcurrentHashMap<Integer, ZWaveNode>();
    private final ArrayList<ZWaveEventListener> zwaveEventListeners = new ArrayList<ZWaveEventListener>();
    private final PriorityBlockingQueue<SerialMessage> sendQueue = new PriorityBlockingQueue<SerialMessage>(
            INITIAL_TX_QUEUE_SIZE, new SerialMessage.SerialMessageComparator(this));
    private final PriorityBlockingQueue<SerialMessage> recvQueue = new PriorityBlockingQueue<SerialMessage>(
            INITIAL_RX_QUEUE_SIZE, new SerialMessage.SerialMessageComparator(this));
    private ZWaveSendThread sendThread;
    // private ZWaveReceiveThread receiveThread;
    private ZWaveInputThread inputThread;

    private final Semaphore sendAllowed = new Semaphore(1);
    private final Semaphore transactionCompleted = new Semaphore(1);
    private volatile SerialMessage lastSentMessage = null;
    private long lastMessageStartTime = 0;
    private long longestResponseTime = 0;
    private int zWaveResponseTimeout = ZWAVE_RESPONSE_TIMEOUT;
    // private Timer watchdog;

    private String zWaveVersion = "Unknown";
    private String serialAPIVersion = "Unknown";
    private int homeId = 0;
    private int ownNodeId = 0;
    private int manufactureId = 0;
    private int deviceType = 0;
    private int deviceId = 0;
    private int ZWaveLibraryType = 0;
    private int sentDataPointer = 1;
    private boolean setSUC = false;
    private ZWaveDeviceType controllerType = ZWaveDeviceType.UNKNOWN;
    private int sucID = 0;
    private boolean softReset = false;
    private boolean masterController = false;

    private AtomicInteger timeOutCount = new AtomicInteger(0);

    private ZWaveControllerHandler ioHandler;

    // Constructors
    public ZWaveController(ZWaveControllerHandler handler) {
        this(handler, new HashMap<String, String>());
    }

    /**
     * Constructor. Creates a new instance of the ZWave controller class.
     *
     * @param serialPortName
     *            the serial port name to use for communication with the ZWave
     *            controller stick.
     * @throws SerialInterfaceException
     *             when a connection error occurs.
     */
    public ZWaveController(ZWaveControllerHandler handler, Map<String, String> config) {
        final boolean masterController = "true".equals(config.get("masterController"));
        final boolean isSUC = "true".equals(config.get("isSUC"));
        final Integer timeout = config.containsKey("timeout") ? Integer.parseInt(config.get("timeout")) : 0;
        final boolean reset = "true".equals(config.get("softReset"));

        logger.info("Starting ZWave controller");
        this.masterController = masterController;
        this.setSUC = isSUC;
        this.softReset = reset;

        if (timeout != null && timeout >= 1500 && timeout <= 10000) {
            zWaveResponseTimeout = timeout;
        }
        logger.info("ZWave timeout is set to {}ms. Soft reset is {}.", zWaveResponseTimeout, reset);
        // this.watchdog = new Timer(true);
        // this.watchdog.schedule(new WatchDogTimerTask(serialPortName), WATCHDOG_TIMER_PERIOD, WATCHDOG_TIMER_PERIOD);

        ioHandler = handler;

        // We have a delay in running the initialisation sequence to allow any frames queued in the controller to be
        // received before sending the init sequence. This avoids protocol errors (CAN errors).
        Timer initTimer = new Timer();
        initTimer.schedule(new InitializeDelayTask(), 3000);

        this.sendThread = new ZWaveSendThread();
        this.sendThread.start();
        this.inputThread = new ZWaveInputThread();
        this.inputThread.start();
    }

    private class InitializeDelayTask extends TimerTask {
        private final Logger logger = LoggerFactory.getLogger(InitializeDelayTask.class);

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            logger.debug("Initialising network");
            initialize();
        }
    }

    // Incoming message handlers

    /**
     * Handles incoming Serial Messages. Serial messages can either be messages
     * that are a response to our own requests, or the stick asking us information.
     *
     * @param incomingMessage
     *            the incoming message to process.
     */
    private void handleIncomingMessage(SerialMessage incomingMessage) {
        logger.debug(incomingMessage.toString());

        switch (incomingMessage.getMessageType()) {
            case Request:
                handleIncomingRequestMessage(incomingMessage);
                break;
            case Response:
                handleIncomingResponseMessage(incomingMessage);
                break;
            default:
                logger.warn("Unsupported incomingMessageType: {}", incomingMessage.getMessageType());
        }
    }

    /**
     * Handles an incoming request message. An incoming request message is a
     * message initiated by a node or the controller.
     *
     * @param incomingMessage
     *            the incoming message to process.
     */
    private void handleIncomingRequestMessage(SerialMessage incomingMessage) {
        logger.trace("Incoming Message type = REQUEST");

        ZWaveCommandProcessor processor = ZWaveCommandProcessor.getMessageDispatcher(incomingMessage.getMessageClass());
        if (processor == null) {
            logger.warn(String.format("TODO: Implement processing of Request Message = %s (0x%02X)",
                    incomingMessage.getMessageClass().getLabel(), incomingMessage.getMessageClass().getKey()));

            return;
        }

        boolean result = processor.handleRequest(this, lastSentMessage, incomingMessage);
        if (processor.isTransactionComplete()) {
            notifyEventListeners(new ZWaveTransactionCompletedEvent(this.lastSentMessage, result));
            transactionCompleted.release();
            logger.trace("Released. Transaction completed permit count -> {}", transactionCompleted.availablePermits());
        }
    }

    /**
     * Handles a failed SendData request. This can either be because of the
     * stick actively reporting it or because of a time-out of the transaction
     * in the send thread.
     *
     * @param originalMessage
     *            the original message that was sent
     */
    private void handleFailedSendDataRequest(SerialMessage originalMessage) {
        new SendDataMessageClass().handleFailedSendDataRequest(this, originalMessage);
    }

    /**
     * Handles an incoming response message. An incoming response message is a
     * response, based one of our own requests.
     *
     * @param incomingMessage
     *            the response message to process.
     */
    private void handleIncomingResponseMessage(SerialMessage incomingMessage) {
        logger.trace("Incoming Message type = RESPONSE");

        ZWaveCommandProcessor processor = ZWaveCommandProcessor.getMessageDispatcher(incomingMessage.getMessageClass());
        if (processor == null) {
            logger.warn(String.format("TODO: Implement processing of Response Message = %s (0x%02X)",
                    incomingMessage.getMessageClass().getLabel(), incomingMessage.getMessageClass().getKey()));

            return;
        }

        boolean result = processor.handleResponse(this, lastSentMessage, incomingMessage);
        if (processor.isTransactionComplete()) {
            notifyEventListeners(new ZWaveTransactionCompletedEvent(this.lastSentMessage, result));
            transactionCompleted.release();
            logger.trace("Released. Transaction completed permit count -> {}", transactionCompleted.availablePermits());
        }

        switch (incomingMessage.getMessageClass()) {
            case GetVersion:
                this.zWaveVersion = ((GetVersionMessageClass) processor).getVersion();
                this.ZWaveLibraryType = ((GetVersionMessageClass) processor).getLibraryType();
                break;
            case MemoryGetId:
                this.ownNodeId = ((MemoryGetIdMessageClass) processor).getNodeId();
                this.homeId = ((MemoryGetIdMessageClass) processor).getHomeId();
                break;
            case SerialApiGetInitData:
                // this.isConnected = true;
                for (Integer nodeId : ((SerialApiGetInitDataMessageClass) processor).getNodes()) {
                    addNode(nodeId);
                }
                break;
            case GetSucNodeId:
                // Remember the SUC ID
                this.sucID = ((GetSucNodeIdMessageClass) processor).getSucNodeId();

                // If we want to be the SUC, enable it here
                if (this.setSUC == true && this.sucID == 0) {
                    // We want to be SUC
                    this.enqueue(new EnableSucMessageClass().doRequest(EnableSucMessageClass.SUCType.SERVER));
                    this.enqueue(new SetSucNodeMessageClass().doRequest(this.ownNodeId,
                            SetSucNodeMessageClass.SUCType.SERVER));
                } else if (this.setSUC == false && this.sucID == this.ownNodeId) {
                    // We don't want to be SUC, but we currently are!
                    // Disable SERVER functionality, and set the node to 0
                    this.enqueue(new EnableSucMessageClass().doRequest(EnableSucMessageClass.SUCType.NONE));
                    this.enqueue(new SetSucNodeMessageClass().doRequest(this.ownNodeId,
                            SetSucNodeMessageClass.SUCType.NONE));
                }
                this.enqueue(new GetControllerCapabilitiesMessageClass().doRequest());
                break;
            case SerialApiGetCapabilities:
                this.serialAPIVersion = ((SerialApiGetCapabilitiesMessageClass) processor).getSerialAPIVersion();
                this.manufactureId = ((SerialApiGetCapabilitiesMessageClass) processor).getManufactureId();
                this.deviceId = ((SerialApiGetCapabilitiesMessageClass) processor).getDeviceId();
                this.deviceType = ((SerialApiGetCapabilitiesMessageClass) processor).getDeviceType();

                this.enqueue(new SerialApiGetInitDataMessageClass().doRequest());
                break;
            case GetControllerCapabilities:
                this.controllerType = ((GetControllerCapabilitiesMessageClass) processor).getDeviceType();
                break;
            default:
                break;
        }
    }

    // Controller methods

    /**
     * Removes the node, and restarts the initialisation sequence
     *
     * @param nodeId
     */
    public void reinitialiseNode(int nodeId) {
        this.zwaveNodes.remove(nodeId);
        addNode(nodeId);
    }

    /**
     * Advise the discovery service that we have identified a new node.
     * <p>
     * This must be done after some of the initialisation is complete since we need to know the manufacturer, type, id,
     * and version of the device in order to correctly identify the thingType.
     *
     * @param node
     *            the node to add
     */
    public void nodeDiscoveryStarted(ZWaveNode node) {
        // Advise the discovery service
        ioHandler.deviceAdded(node);
    }

    /**
     * Advise the discovery service that we have identified a new node.
     * <p>
     * This must be done after some of the initialisation is complete since we need to know the manufacturer, type, id,
     * and version of the device in order to correctly identify the thingType.
     *
     * @param node
     *            the node to add
     */
    public void nodeDiscoveryComplete(ZWaveNode node) {
        // Advise the discovery service
        ioHandler.deviceAdded(node);
    }

    /**
     * Add a node to the controller
     *
     * @param nodeId
     *            the node number to add
     */
    private void addNode(int nodeId) {
        ioHandler.deviceDiscovered(nodeId);
        new ZWaveInitNodeThread(this, nodeId).start();
    }

    private class ZWaveInitNodeThread extends Thread {
        int nodeId;
        ZWaveController controller;

        ZWaveInitNodeThread(ZWaveController controller, int nodeId) {
            this.nodeId = nodeId;
            this.controller = controller;
        }

        @Override
        public void run() {
            logger.debug("NODE {}: Init node thread start", nodeId);

            // Check if the node exists
            if (zwaveNodes.get(nodeId) != null) {
                logger.warn("NODE {}: Attempting to add node that already exists", nodeId);
                return;
            }

            ZWaveNode node = null;
            try {
                ZWaveNodeSerializer nodeSerializer = new ZWaveNodeSerializer();
                node = nodeSerializer.DeserializeNode(nodeId);
            } catch (Exception e) {
                logger.error("NODE {}: Restore from config: Error deserialising XML file. {}", nodeId, e.toString());
                node = null;
            }

            // Did the node deserialise ok?
            if (node != null) {
                // Sanity check the data from the file
                if (node.getManufacturer() == Integer.MAX_VALUE || node.getHomeId() != controller.homeId
                        || node.getNodeId() != nodeId) {
                    logger.warn("NODE {}: Restore from config: Error. Data invalid, ignoring config.", nodeId);
                    node = null;
                } else {
                    // The restore was ok, but we have some work to set up the
                    // links that aren't
                    // made as the deserialiser doesn't call the constructor
                    logger.debug("NODE {}: Restore from config: Ok.", nodeId);
                    node.setRestoredFromConfigfile(controller);

                    // Set the controller and node references for all command
                    // classes
                    for (ZWaveCommandClass commandClass : node.getCommandClasses()) {
                        commandClass.setController(controller);
                        commandClass.setNode(node);

                        // Handle event handlers
                        if (commandClass instanceof ZWaveEventListener) {
                            controller.addEventListener((ZWaveEventListener) commandClass);
                        }

                        // If this is the multi-instance class, add all command
                        // classes for the endpoints
                        if (commandClass instanceof ZWaveMultiInstanceCommandClass) {
                            for (ZWaveEndpoint endPoint : ((ZWaveMultiInstanceCommandClass) commandClass)
                                    .getEndpoints()) {
                                for (ZWaveCommandClass endpointCommandClass : endPoint.getCommandClasses()) {
                                    endpointCommandClass.setController(controller);
                                    endpointCommandClass.setNode(node);
                                    endpointCommandClass.setEndpoint(endPoint);

                                    // Handle event handlers
                                    if (endpointCommandClass instanceof ZWaveEventListener) {
                                        controller.addEventListener((ZWaveEventListener) endpointCommandClass);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create a new node if it wasn't deserialised ok
            if (node == null) {
                node = new ZWaveNode(controller.homeId, nodeId, controller);
            }

            if (nodeId == controller.ownNodeId) {
                // This is the controller node.
                // We already know the device type, id, manufacturer so set it
                // here
                // It won't be set later as we probably won't request the
                // manufacturer specific data
                node.setDeviceId(controller.getDeviceId());
                node.setDeviceType(controller.getDeviceType());
                node.setManufacturer(controller.getManufactureId());
            }

            // Place nodes in the local ZWave Controller
            controller.zwaveNodes.putIfAbsent(nodeId, node);
            node.initialiseNode();

            logger.debug("NODE {}: Init node thread finished", nodeId);
        }
    }

    /**
     * Enqueues a message for sending on the send queue.
     *
     * @param serialMessage
     *            the serial message to enqueue.
     */
    public void enqueue(SerialMessage serialMessage) {
        // Sanity check!
        if (serialMessage == null) {
            return;
        }

        // First try and get the node
        // If we're sending to a node, then this obviously isn't to the
        // controller, and we should
        // queue anything to a battery node (ie a node supporting the WAKEUP
        // class)!
        ZWaveNode node = this.getNode(serialMessage.getMessageNode());
        if (node != null) {
            // Keep track of the number of packets sent to this device
            node.incrementSendCount();

            // If the device isn't listening, queue the message if it supports
            // the wakeup class
            if (!node.isListening() && !node.isFrequentlyListening()) {
                ZWaveWakeUpCommandClass wakeUpCommandClass = (ZWaveWakeUpCommandClass) node
                        .getCommandClass(CommandClass.WAKE_UP);

                // If it's a battery operated device, check if it's awake or
                // place in wake-up queue.
                if (wakeUpCommandClass != null && !wakeUpCommandClass.processOutgoingWakeupMessage(serialMessage)) {
                    return;
                }
            }
        }

        // Add the message to the queue
        this.sendQueue.add(serialMessage);
        logger.debug("Enqueueing message. Queue length = {}", this.sendQueue.size());
    }

    /**
     * Returns the size of the send queue.
     */
    public int getSendQueueLength() {
        return this.sendQueue.size();
    }

    /**
     * Notify our own event listeners of a ZWave event.
     *
     * @param event
     *            the event to send.
     */
    public void notifyEventListeners(ZWaveEvent event) {
        logger.debug("Notifying event listeners: {}", event.getClass().getSimpleName());
        ArrayList<ZWaveEventListener> copy = new ArrayList<ZWaveEventListener>(this.zwaveEventListeners);
        for (ZWaveEventListener listener : copy) {
            listener.ZWaveIncomingEvent(event);
        }

        // We also need to handle the inclusion internally within the controller
        if (event instanceof ZWaveInclusionEvent) {
            ZWaveInclusionEvent incEvent = (ZWaveInclusionEvent) event;
            switch (incEvent.getEvent()) {
                case IncludeDone:
                    logger.debug("NODE {}: Including node.", incEvent.getNodeId());
                    // First make sure this isn't an existing node
                    if (getNode(incEvent.getNodeId()) != null) {
                        logger.debug("NODE {}: Newly included node already exists - not initialising.",
                                incEvent.getNodeId());
                        break;
                    }

                    // Initialise the new node
                    addNode(incEvent.getNodeId());
                    break;
                case ExcludeDone:
                    logger.debug("NODE {}: Excluding node.", incEvent.getNodeId());
                    // Remove the node from the controller
                    if (getNode(incEvent.getNodeId()) == null) {
                        logger.debug("NODE {}: Excluding node that doesn't exist.", incEvent.getNodeId());
                        break;
                    }
                    this.zwaveNodes.remove(incEvent.getNodeId());

                    // Remove the XML file
                    ZWaveNodeSerializer nodeSerializer = new ZWaveNodeSerializer();
                    nodeSerializer.DeleteNode(event.getNodeId());
                    break;
                default:
                    break;
            }
        } else if (event instanceof ZWaveNetworkEvent) {
            ZWaveNetworkEvent networkEvent = (ZWaveNetworkEvent) event;
            switch (networkEvent.getEvent()) {
                case DeleteNode:
                    if (getNode(networkEvent.getNodeId()) == null) {
                        logger.debug("NODE {}: Deleting a node that doesn't exist.", networkEvent.getNodeId());
                        break;
                    }
                    this.zwaveNodes.remove(networkEvent.getNodeId());

                    // Remove the XML file
                    ZWaveNodeSerializer nodeSerializer = new ZWaveNodeSerializer();
                    nodeSerializer.DeleteNode(event.getNodeId());
                    break;
                default:
                    break;
            }
        } else if (event instanceof ZWaveNodeStatusEvent) {
            ZWaveNodeStatusEvent statusEvent = (ZWaveNodeStatusEvent) event;
            logger.debug("NODE {}: Node Status event - Node is {}", statusEvent.getNodeId(), statusEvent.getState());

            // Get the node
            ZWaveNode node = getNode(event.getNodeId());
            if (node == null) {
                logger.error("NODE {}: Node is unknown!", statusEvent.getNodeId());
                return;
            }

            // Handle node state changes
            switch (statusEvent.getState()) {
                case DEAD:
                    break;
                case FAILED:
                    break;
                case ALIVE:
                    break;
            }
        }
    }

    /**
     * Initializes communication with the ZWave controller stick.
     */
    public void initialize() {
        this.enqueue(new GetVersionMessageClass().doRequest());
        this.enqueue(new MemoryGetIdMessageClass().doRequest());
        this.enqueue(new SerialApiGetCapabilitiesMessageClass().doRequest());
        this.enqueue(new SerialApiSetTimeoutsMessageClass().doRequest(150, 15));
        this.enqueue(new GetSucNodeIdMessageClass().doRequest());
    }

    /**
     * Send Identify Node message to the controller.
     *
     * @param nodeId
     *            the nodeId of the node to identify
     */
    public void identifyNode(int nodeId) {
        this.enqueue(new IdentifyNodeMessageClass().doRequest(nodeId));
    }

    /**
     * Send Request Node info message to the controller.
     *
     * @param nodeId
     *            the nodeId of the node to identify
     */
    public void requestNodeInfo(int nodeId) {
        this.enqueue(new RequestNodeInfoMessageClass().doRequest(nodeId));
    }

    /**
     * Polls a node for any dynamic information
     *
     * @param node
     */
    public void pollNode(ZWaveNode node) {
        for (ZWaveCommandClass zwaveCommandClass : node.getCommandClasses()) {
            logger.trace("NODE {}: Inspecting command class {}", node.getNodeId(),
                    zwaveCommandClass.getCommandClass().getLabel());
            if (zwaveCommandClass instanceof ZWaveCommandClassDynamicState) {
                logger.debug("NODE {}: Found dynamic state command class {}", node.getNodeId(),
                        zwaveCommandClass.getCommandClass().getLabel());
                ZWaveCommandClassDynamicState zdds = (ZWaveCommandClassDynamicState) zwaveCommandClass;
                int instances = zwaveCommandClass.getInstances();
                if (instances == 1) {
                    Collection<SerialMessage> dynamicQueries = zdds.getDynamicValues(true);
                    for (SerialMessage serialMessage : dynamicQueries) {
                        sendData(serialMessage);
                    }
                } else {
                    for (int i = 1; i <= instances; i++) {
                        Collection<SerialMessage> dynamicQueries = zdds.getDynamicValues(true);
                        for (SerialMessage serialMessage : dynamicQueries) {
                            sendData(node.encapsulate(serialMessage, zwaveCommandClass, i));
                        }
                    }
                }
            } else if (zwaveCommandClass instanceof ZWaveMultiInstanceCommandClass) {
                ZWaveMultiInstanceCommandClass multiInstanceCommandClass = (ZWaveMultiInstanceCommandClass) zwaveCommandClass;
                for (ZWaveEndpoint endpoint : multiInstanceCommandClass.getEndpoints()) {
                    for (ZWaveCommandClass endpointCommandClass : endpoint.getCommandClasses()) {
                        logger.trace("NODE {}: Inspecting command class {} for endpoint {}", node.getNodeId(),
                                endpointCommandClass.getCommandClass().getLabel(), endpoint.getEndpointId());
                        if (endpointCommandClass instanceof ZWaveCommandClassDynamicState) {
                            logger.debug("NODE {}: Found dynamic state command class {}", node.getNodeId(),
                                    endpointCommandClass.getCommandClass().getLabel());
                            ZWaveCommandClassDynamicState zdds2 = (ZWaveCommandClassDynamicState) endpointCommandClass;
                            Collection<SerialMessage> dynamicQueries = zdds2.getDynamicValues(true);
                            for (SerialMessage serialMessage : dynamicQueries) {
                                sendData(node.encapsulate(serialMessage, endpointCommandClass,
                                        endpoint.getEndpointId()));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Request the node routing information.
     *
     * @param nodeId
     *            The address of the node to update
     */
    public void requestNodeRoutingInfo(int nodeId) {
        this.enqueue(new GetRoutingInfoMessageClass().doRequest(nodeId));
    }

    /**
     * Request the node neighbor list to be updated for the specified node. Once
     * this is complete, the requestNodeRoutingInfo will be called automatically
     * to update the data in the binding.
     *
     * @param nodeId
     *            The address of the node to update
     */
    public void requestNodeNeighborUpdate(int nodeId) {
        this.enqueue(new RequestNodeNeighborUpdateMessageClass().doRequest(nodeId));
    }

    /**
     * Puts the controller into inclusion mode to add new nodes
     */
    public void requestAddNodesStart() {
        this.enqueue(new AddNodeMessageClass().doRequestStart(true));
    }

    /**
     * Terminates the inclusion mode
     */
    public void requestAddNodesStop() {
        this.enqueue(new AddNodeMessageClass().doRequestStop());
    }

    /**
     * Puts the controller into exclusion mode to remove new nodes
     */
    public void requestRemoveNodesStart() {
        this.enqueue(new RemoveNodeMessageClass().doRequestStart(true));
    }

    /**
     * Terminates the exclusion mode
     */
    public void requestRemoveNodesStop() {
        this.enqueue(new RemoveNodeMessageClass().doRequestStop());
    }

    /**
     * Sends a request to perform a soft reset on the controller. This will just
     * reset the controller - probably similar to a power cycle. It doesn't
     * reinitialise the network, or change the network configuration.
     * <p>
     * <b>NOTE</b>: On some (most!) sticks, this doesn't return a response. Therefore, the number of retries is set to
     * 1. <br>
     * <b>NOTE</b>: On some (most!) ZWave-Plus sticks, this can cause the stick to hang.
     */
    public void requestSoftReset() {
        SerialMessage msg = new SerialApiSoftResetMessageClass().doRequest();
        msg.attempts = 1;
        this.enqueue(msg);
    }

    /**
     * Sends a request to perform a hard reset on the controller.
     * This will reset the controller to its default, resetting the network completely
     */
    public void requestHardReset() {
        // Clear the queues
        // If we're resetting, there's no point in queuing messages!
        sendQueue.clear();
        recvQueue.clear();

        SerialMessage msg = new ControllerSetDefaultMessageClass().doRequest();
        msg.attempts = 1;
        this.enqueue(msg);

        // Clear all the nodes and we'll reinitialise
        this.zwaveNodes.clear();
        this.enqueue(new SerialApiGetInitDataMessageClass().doRequest());
    }

    /**
     * Request if the node is currently marked as failed by the controller.
     *
     * @param nodeId
     *            The address of the node to check
     */
    public void requestIsFailedNode(int nodeId) {
        this.enqueue(new IsFailedNodeMessageClass().doRequest(nodeId));
    }

    /**
     * Removes a failed node from the network. Note that this won't remove nodes
     * that have not failed.
     *
     * @param nodeId
     *            The address of the node to remove
     */
    public void requestRemoveFailedNode(int nodeId) {
        this.enqueue(new RemoveFailedNodeMessageClass().doRequest(nodeId));
    }

    /**
     * Delete all return nodes from the specified node. This should be performed
     * before updating the routes
     *
     * @param nodeId
     */
    public void requestDeleteAllReturnRoutes(int nodeId) {
        this.enqueue(new DeleteReturnRouteMessageClass().doRequest(nodeId));
    }

    /**
     * Request the controller to set the return route between two nodes
     *
     * @param nodeId
     *            Source node
     * @param destinationId
     *            Destination node
     */
    public void requestAssignReturnRoute(int nodeId, int destinationId) {
        this.enqueue(new AssignReturnRouteMessageClass().doRequest(nodeId, destinationId, getCallbackId()));
    }

    /**
     * Request the controller to set the return route from a node to the
     * controller
     *
     * @param nodeId
     *            Source node
     */
    public void requestAssignSucReturnRoute(int nodeId) {
        this.enqueue(new AssignSucReturnRouteMessageClass().doRequest(nodeId, getCallbackId()));
    }

    /**
     * Returns the next callback ID
     *
     * @return callback ID
     */
    public int getCallbackId() {
        if (++sentDataPointer > 0xFF) {
            sentDataPointer = 1;
        }
        logger.trace("Callback ID = {}", sentDataPointer);

        return sentDataPointer;
    }

    /**
     * Transmits the SerialMessage to a single ZWave Node. Sets the transmission options as well.
     *
     * @param serialMessage
     *            the Serial message to send.
     */
    public void sendData(SerialMessage serialMessage) {
        if (serialMessage == null) {
            logger.error("Null message for sendData");
            return;
        }
        if (serialMessage.getMessageClass() != SerialMessageClass.SendData) {
            logger.error(String.format("Invalid message class %s (0x%02X) for sendData",
                    serialMessage.getMessageClass().getLabel(), serialMessage.getMessageClass().getKey()));
            return;
        }
        if (serialMessage.getMessageType() != SerialMessageType.Request) {
            logger.error("Only request messages can be sent");
            return;
        }

        // We need to wait on the ACK from the controller before completing the transaction.
        // This is required in case the Application Message is received from the SendData ACK
        serialMessage.setAckRequired();

        serialMessage.setTransmitOptions(TRANSMIT_OPTION_ACK | TRANSMIT_OPTION_AUTO_ROUTE | TRANSMIT_OPTION_EXPLORE);
        serialMessage.setCallbackId(getCallbackId());
        this.enqueue(serialMessage);
    }

    /**
     * Add a listener for ZWave events to this controller.
     *
     * @param eventListener
     *            the event listener to add.
     */
    public void addEventListener(ZWaveEventListener eventListener) {
        synchronized (this.zwaveEventListeners) {
            this.zwaveEventListeners.add(eventListener);
        }
    }

    /**
     * Remove a listener for ZWave events to this controller.
     *
     * @param eventListener
     *            the event listener to remove.
     */
    public void removeEventListener(ZWaveEventListener eventListener) {
        synchronized (this.zwaveEventListeners) {
            this.zwaveEventListeners.remove(eventListener);
        }
    }

    /**
     * Gets the API Version of the controller.
     *
     * @return the serialAPIVersion
     */
    public String getSerialAPIVersion() {
        return serialAPIVersion;
    }

    /**
     * Gets the zWave Version of the controller.
     *
     * @return the zWaveVersion
     */
    public String getZWaveVersion() {
        return zWaveVersion;
    }

    /**
     * Gets the Manufacturer ID of the controller.
     *
     * @return the manufactureId
     */
    public int getManufactureId() {
        return manufactureId;
    }

    /**
     * Gets the device type of the controller;
     *
     * @return the deviceType
     */
    public int getDeviceType() {
        return deviceType;
    }

    /**
     * Gets the device ID of the controller.
     *
     * @return the deviceId
     */
    public int getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the node ID of the controller.
     *
     * @return the deviceId
     */
    public int getOwnNodeId() {
        return ownNodeId;
    }

    /**
     * Gets the device type of the controller.
     *
     * @return the device type
     */
    public ZWaveDeviceType getControllerType() {
        return controllerType;
    }

    /**
     * Gets the networks SUC controller ID.
     *
     * @return the device id of the SUC, or 0 if none exists
     */
    public int getSucId() {
        return sucID;
    }

    /**
     * Returns true if the binding is the master controller in the network. The
     * master controller simply means that we get notifications.
     *
     * @return true if this is the master
     */
    public boolean isMasterController() {
        return masterController;
    }

    /**
     * Gets the node object using it's node ID as key. Returns null if the node
     * is not found
     *
     * @param nodeId
     *            the Node ID of the node to get.
     * @return node object
     */
    public ZWaveNode getNode(int nodeId) {
        return this.zwaveNodes.get(nodeId);
    }

    /**
     * Gets the node list
     *
     * @return
     */
    public Collection<ZWaveNode> getNodes() {
        return this.zwaveNodes.values();
    }

    /**
     * Indicates a working connection to the ZWave controller stick and
     * initialization complete
     *
     * @return isConnected;
     */
    // public boolean isConnected() {
    // return isConnected; // && initializationComplete;
    // }

    /**
     * Returns the number of Time-Outs while sending.
     *
     * @return the timeoutCount
     */
    public int getTimeOutCount() {
        return timeOutCount.get();
    }

    // Nested classes and enumerations

    /**
     * Input thread. This processes incoming messages - it decouples the receive thread, which responds to messages from
     * the controller, and the actual processing of messages to ensure we respond to the controller in a timely manner
     *
     * @author Chris Jackson
     */
    private class ZWaveInputThread extends Thread {
        /**
         * Run method. Runs the actual receiving process.
         */
        @Override
        public void run() {
            logger.debug("Starting ZWave thread: Input");

            SerialMessage recvMessage;
            while (!interrupted()) {
                try {
                    if (recvQueue.size() == 0) {
                        sendAllowed.release();
                    }
                    recvMessage = recvQueue.take();
                    logger.debug("Receive queue TAKE: Length={}", recvQueue.size());
                    logger.debug("Process Message = {}", SerialMessage.bb2hex(recvMessage.getMessageBuffer()));

                    handleIncomingMessage(recvMessage);
                    sendAllowed.tryAcquire();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Exception during ZWave thread: Input.", e);
                }
            }

            logger.debug("Stopped ZWave thread: Input");
        }
    }

    /**
     * ZWave controller Send Thread. Takes care of sending all messages. It uses a semaphore to synchronize
     * communication with the receiving thread.
     *
     * @author Jan-Willem Spuij
     * @author Chris Jackson
     */
    private class ZWaveSendThread extends Thread {

        private final Logger logger = LoggerFactory.getLogger(ZWaveSendThread.class);

        /**
         * Run method. Runs the actual sending process.
         */
        @Override
        public void run() {
            logger.debug("Starting ZWave thread: Send");
            try {
                while (!interrupted()) {
                    // To avoid sending lots of frames when we still have input frames to process, we wait here until
                    // we've processed all receive frames
                    if (!sendAllowed.tryAcquire(1, zWaveResponseTimeout, TimeUnit.MILLISECONDS)) {
                        logger.warn("Receive queue TIMEOUT:", recvQueue.size());
                        continue;
                    }
                    sendAllowed.release();

                    // Take the next message from the send queue
                    try {
                        lastSentMessage = sendQueue.take();
                        logger.debug("Took message from queue for sending. Queue length = {}", sendQueue.size());
                    } catch (InterruptedException e1) {
                        break;
                    }

                    // Check we got a message
                    if (lastSentMessage == null) {
                        continue;
                    }

                    // Get the node for this message
                    ZWaveNode node = getNode(lastSentMessage.getMessageNode());

                    // If it's a battery device, it needs to be awake, or we queue the frame until it is.
                    if (node != null && !node.isListening() && !node.isFrequentlyListening()) {
                        ZWaveWakeUpCommandClass wakeUpCommandClass = (ZWaveWakeUpCommandClass) node
                                .getCommandClass(CommandClass.WAKE_UP);

                        // If it's a battery operated device, check if it's awake or place in wake-up queue.
                        if (wakeUpCommandClass != null
                                && !wakeUpCommandClass.processOutgoingWakeupMessage(lastSentMessage)) {
                            continue;
                        }
                    }

                    // A transaction consists of (up to) 4 parts -:
                    // 1) We send a REQUEST to the controller.
                    // 2) The controller sends a RESPONSE almost immediately. This RESPONSE typically tells us that the
                    // message was, or wasn't, added to the sticks queue.
                    // 3) The controller sends a REQUEST once it's received the response from the device. We need to be
                    // aware that there is no synchronization of messages between steps 2 and 3 so we can get other
                    // messages received at step 3 that are not related to our original request.
                    // 4) We ultimately receive the requested message from the device if we're requesting such a
                    // message.
                    //
                    // A transaction is generally completed at the completion of step 4.
                    // However, for some messages, there may not be a further REQUEST so the transaction is terminated
                    // at step 2. This is handled by the serial message class processor by setting transactionCompleted.
                    //
                    // It seems that some of these steps may occur out of order.
                    // For example, the requested message at step 4 may be received before the REQUEST at step 3. This
                    // can (I guess) occur if the message to the device is received by the device, but the ACK back to
                    // the controller is lost. The device then sends the requested data, and then finally the ACK is
                    // received. We cover this by setting an 'AckPending' flag in the sent message.
                    // This needs to be cleared before the transaction is completed.

                    // Clear the semaphore used to acknowledge the completed transaction.
                    transactionCompleted.drainPermits();

                    // Send the REQUEST message TO the controller
                    ioHandler.sendPacket(lastSentMessage);
                    /*
                     * byte[] buffer = lastSentMessage.getMessageBuffer();
                     * logger.debug("NODE {}: Sending REQUEST Message = {}", lastSentMessage.getMessageNode(),
                     * SerialMessage.bb2hex(buffer));
                     * lastMessageStartTime = System.currentTimeMillis();
                     * try {
                     * synchronized (serialPort.getOutputStream()) {
                     * serialPort.getOutputStream().write(buffer);
                     * serialPort.getOutputStream().flush();
                     * logger.trace("Message SENT");
                     * }
                     * } catch (IOException e) {
                     * logger.error("Got I/O exception {} during sending. exiting thread.", e.getLocalizedMessage());
                     * break;
                     * }
                     */

                    // Now wait for the RESPONSE, or REQUEST message FROM the controller
                    // This will terminate when the transactionCompleted flag gets set
                    // So, this might complete on a RESPONSE if there's an error (or no further REQUEST expected) or it
                    // might complete on a subsequent REQUEST.
                    try {
                        if (!transactionCompleted.tryAcquire(1, zWaveResponseTimeout, TimeUnit.MILLISECONDS)) {
                            timeOutCount.incrementAndGet();
                            // If this is a SendData message, then we need to abort
                            // This should only be sent if we didn't get the initial ACK!!!
                            // So we need to check the ACK flag and only abort if it's not set
                            if (lastSentMessage.getMessageClass() == SerialMessageClass.SendData
                                    && lastSentMessage.isAckPending()) {
                                SerialMessage serialMessage = new SerialMessage(SerialMessageClass.SendDataAbort,
                                        SerialMessageType.Request, SerialMessageClass.SendData,
                                        SerialMessagePriority.Immediate);
                                logger.debug("NODE {}: Sending ABORT Message = {}", lastSentMessage.getMessageNode(),
                                        SerialMessage.bb2hex(serialMessage.getMessageBuffer()));

                                ioHandler.sendPacket(serialMessage);
                            }

                            // Check if we've exceeded the number of retries.
                            // Requeue if we're ok, otherwise discard the message
                            if (--lastSentMessage.attempts >= 0) {
                                logger.error("NODE {}: Timeout while sending message. Requeueing - {} attempts left!",
                                        lastSentMessage.getMessageNode(), lastSentMessage.attempts);
                                if (lastSentMessage.getMessageClass() == SerialMessageClass.SendData) {
                                    handleFailedSendDataRequest(lastSentMessage);
                                } else {
                                    enqueue(lastSentMessage);
                                }
                            } else {
                                logger.warn("NODE {}: Too many retries. Discarding message: {}",
                                        lastSentMessage.getMessageNode(), lastSentMessage.toString());
                            }
                            continue;
                        }
                        long responseTime = System.currentTimeMillis() - lastMessageStartTime;
                        if (responseTime > longestResponseTime) {
                            longestResponseTime = responseTime;
                        }
                        logger.debug("NODE {}: Response processed after {}ms/{}ms.", lastSentMessage.getMessageNode(),
                                responseTime, longestResponseTime);
                        logger.trace("Acquired. Transaction completed permit count -> {}",
                                transactionCompleted.availablePermits());
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Exception during ZWave thread: Send", e);
            }
            logger.debug("Stopped ZWave thread: Send");
        }
    }

    /**
     * WatchDogTimerTask class. Acts as a watch dog and checks the serial
     * threads to see whether they are still running.
     *
     * @author Jan-Willem Spuij
     */
    // private class WatchDogTimerTask extends TimerTask {
    //
    // private final Logger logger = LoggerFactory.getLogger(WatchDogTimerTask.class);
    // private final String serialPortName;
    //
    // /**
    // * Creates a new instance of the WatchDogTimerTask class.
    // *
    // * @param serialPortName
    // * the serial port name to reconnect to in case the serial
    // * threads have died.
    // */
    // public WatchDogTimerTask(String serialPortName) {
    // this.serialPortName = serialPortName;
    // }
    //
    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // public void run() {
    // logger.trace("Watchdog: Checking Serial threads");
    // if (
    // // (receiveThread != null && !receiveThread.isAlive()) ||
    // (sendThread != null && !sendThread.isAlive()) || (inputThread != null && !inputThread.isAlive())) {
    // logger.warn("Threads not alive, respawning");
    // disconnect();
    // try {
    // connect(serialPortName);
    // } catch (SerialInterfaceException e) {
    // logger.error("Unable to restart Serial threads: {}", e.getLocalizedMessage());
    // }
    // }
    // }
    // }

    public void incomingPacket(SerialMessage packet) {
        // Add the packet to the receive queue
        recvQueue.add(packet);
    }
}
