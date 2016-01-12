package org.openhab.binding.zwave.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterGroup;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.config.core.ConfigDescriptionRegistry;
import org.eclipse.smarthome.config.core.ConfigOptionProvider;
import org.eclipse.smarthome.config.core.ParameterOption;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zwave.ZWaveBindingConstants;
import org.openhab.binding.zwave.handler.ZWaveControllerHandler;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class ZWaveConfigProvider implements ConfigDescriptionProvider, ConfigOptionProvider {
    private final Logger logger = LoggerFactory.getLogger(ZWaveConfigProvider.class);

    private static ThingRegistry thingRegistry;
    private static ThingTypeRegistry thingTypeRegistry;
    private static ConfigDescriptionRegistry configDescriptionRegistry;

    private static Set<ThingTypeUID> zwaveThingTypeUIDList = new HashSet<ThingTypeUID>();
    private static List<ZWaveProduct> productIndex = new ArrayList<ZWaveProduct>();

    // The following is a list of classes that are controllable.
    // This is used to filter endpoints so that when we display a list of nodes/endpoints
    // for configuring associations, we only list endpoints that are useful
    private static final Set<ZWaveCommandClass.CommandClass> controllableClasses = ImmutableSet.of(CommandClass.BASIC,
            CommandClass.SWITCH_BINARY, CommandClass.SWITCH_MULTILEVEL, CommandClass.SWITCH_TOGGLE_BINARY,
            CommandClass.SWITCH_TOGGLE_MULTILEVEL, CommandClass.CHIMNEY_FAN, CommandClass.THERMOSTAT_HEATING,
            CommandClass.THERMOSTAT_MODE, CommandClass.THERMOSTAT_OPERATING_STATE, CommandClass.THERMOSTAT_SETPOINT,
            CommandClass.THERMOSTAT_FAN_MODE, CommandClass.THERMOSTAT_FAN_STATE, CommandClass.FIBARO_FGRM_222);

    protected void setThingRegistry(ThingRegistry thingRegistry) {
        ZWaveConfigProvider.thingRegistry = thingRegistry;
    }

    protected void unsetThingRegistry(ThingRegistry thingRegistry) {
        ZWaveConfigProvider.thingRegistry = null;
    }

    protected void setThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZWaveConfigProvider.thingTypeRegistry = thingTypeRegistry;
    }

    protected void unsetThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZWaveConfigProvider.thingTypeRegistry = null;
    }

    protected void setConfigDescriptionRegistry(ConfigDescriptionRegistry configDescriptionRegistry) {
        ZWaveConfigProvider.configDescriptionRegistry = configDescriptionRegistry;
    }

    protected void unsetConfigDescriptionRegistry(ConfigDescriptionRegistry configDescriptionRegistry) {
        ZWaveConfigProvider.configDescriptionRegistry = null;
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(Locale locale) {
        logger.debug("getConfigDescriptions called");
        return null;
    }

    @Override
    public ConfigDescription getConfigDescription(URI uri, Locale locale) {
        // If this is a template, then ignore to avoid recursive loop

        if (uri == null) {
            return null;
        }

        List<ConfigDescriptionParameterGroup> groups = new ArrayList<ConfigDescriptionParameterGroup>();
        List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();
        /*
         * groups.add(new ConfigDescriptionParameterGroup("status", "compass", false, "Status", null));
         *
         * parameters.add(ConfigDescriptionParameterBuilder.create("deviceManufacturer", Type.TEXT)
         * .withLabel("Device Manufacturer").withGroupName("status").withAdvanced(true).withReadOnly(true)
         * .build());
         *
         * parameters.add(ConfigDescriptionParameterBuilder.create("deviceType", Type.TEXT).withLabel("Device Type")
         * .withGroupName("status").withAdvanced(true).withReadOnly(true).build());
         *
         * parameters.add(ConfigDescriptionParameterBuilder.create("deviceId", Type.TEXT).withLabel("Device ID")
         * .withGroupName("status").withAdvanced(true).withReadOnly(true).build());
         *
         * parameters.add(ConfigDescriptionParameterBuilder.create("deviceVersion", Type.TEXT).withLabel(
         * "Device Version")
         * .withGroupName("status").withAdvanced(true).withReadOnly(true).build());
         *
         * parameters.add(ConfigDescriptionParameterBuilder.create("deviceVersion", Type.TEXT).withLabel(
         * "Device Version")
         * .withGroupName("status").withAdvanced(true).withReadOnly(true).build());
         */
        return new ConfigDescription(uri, parameters, groups);
    }

    private static String initialiseZWaveThings() {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return null;
        }

        // Get all the thing types
        Collection<ThingType> thingTypes = thingTypeRegistry.getThingTypes();
        for (ThingType thingType : thingTypes) {
            // Is this for our binding?
            if (ZWaveBindingConstants.BINDING_ID.equals(thingType.getBindingId()) == false) {
                continue;
            }

            // Create a list of all things supported by this binding
            zwaveThingTypeUIDList.add(thingType.getUID());

            // Get the properties
            Map<String, String> thingProperties = thingType.getProperties();

            if (thingProperties.get("manufacturerRef") == null) {
                continue;
            }

            String[] references = thingProperties.get("manufacturerRef").split(",");
            for (String ref : references) {
                String[] values = ref.split(":");
                Integer type;
                Integer id = null;
                if (values.length != 2) {
                    continue;
                }

                type = Integer.parseInt(values[0], 16);
                if (!values[1].trim().equals("*")) {
                    id = Integer.parseInt(values[1], 16);
                }
                String versionMin = thingProperties.get("versionMin");
                String versionMax = thingProperties.get("versionMax");
                productIndex.add(new ZWaveProduct(thingType.getUID(),
                        Integer.parseInt(thingProperties.get("manufacturerId"), 16), type, id, versionMin, versionMax));
            }

        }
        return "";
    }

    public static List<ZWaveProduct> getProductIndex() {
        if (productIndex.size() == 0) {
            initialiseZWaveThings();
        }
        return productIndex;
    }

    public static Set<ThingTypeUID> getSupportedThingTypes() {
        if (zwaveThingTypeUIDList.size() == 0) {
            initialiseZWaveThings();
        }
        return zwaveThingTypeUIDList;
    }

    public static ThingType getThingType(ThingTypeUID thingTypeUID) {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return null;
        }

        return thingTypeRegistry.getThingType(thingTypeUID);
    }

    public static ThingType getThingType(ZWaveNode node) {
        // Check that we know about the registry
        if (thingTypeRegistry == null) {
            return null;
        }

        for (ZWaveProduct product : ZWaveConfigProvider.getProductIndex()) {
            if (product.match(node) == true) {
                return thingTypeRegistry.getThingType(product.thingTypeUID);
            }
        }
        return null;
    }

    public static ConfigDescription getThingTypeConfig(ThingType type) {
        // Check that we know about the registry
        if (configDescriptionRegistry == null) {
            return null;
        }

        return configDescriptionRegistry.getConfigDescription(type.getConfigDescriptionURI());
    }

    public static Thing getThing(ThingUID thingUID) {
        // Check that we know about the registry
        if (thingRegistry == null) {
            return null;
        }

        return thingRegistry.get(thingUID);
    }

    /**
     * Check if this node supports a controllable command class
     *
     * @param node the {@link ZWaveNode)
     * @return true if a controllable class is supported
     */
    private boolean supportsControllableClass(ZWaveNode node) {
        for (CommandClass commandClass : controllableClasses) {
            if (node.supportsCommandClass(commandClass) == true) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Collection<ParameterOption> getParameterOptions(URI uri, String param, Locale locale) {
        // We need to update the options of all requests for association groups...
        if (!"thing".equals(uri.getScheme())) {
            return null;
        }

        ThingUID thingUID = new ThingUID(uri.getSchemeSpecificPart());
        ThingType thingType = thingTypeRegistry.getThingType(thingUID.getThingTypeUID());
        if (thingType == null) {
            return null;
        }

        // Is this a zwave thing?
        if (!thingUID.getBindingId().equals(ZWaveBindingConstants.BINDING_ID)) {
            return null;
        }

        // And is it an association group?
        if (!param.startsWith("group_")) {
            return null;
        }

        // And make sure this is a node because we want to get the id off the end...
        if (!thingUID.getId().startsWith("node")) {
            return null;
        }
        int nodeId = Integer.parseInt(thingUID.getId().substring(4));

        Thing thing = getThing(thingUID);
        ThingUID bridgeUID = thing.getBridgeUID();

        // Get the controller for this thing
        Thing bridge = getThing(bridgeUID);
        if (bridge == null) {
            return null;
        }

        // Get its handler
        ZWaveControllerHandler handler = (ZWaveControllerHandler) bridge.getHandler();

        boolean supportsMultiInstanceAssociation = false;
        ZWaveNode myNode = handler.getNode(nodeId);
        if (myNode.getCommandClass(CommandClass.MULTI_INSTANCE_ASSOCIATION) != null) {
            supportsMultiInstanceAssociation = true;
        }

        List<ParameterOption> options = new ArrayList<ParameterOption>();

        // Add the controller (ie openHAB) to the top of the list
        options.add(new ParameterOption("node_" + handler.getOwnNodeId() + "_0", "openHAB Controller"));

        // And iterate over all its nodes
        Collection<ZWaveNode> nodes = handler.getNodes();
        for (ZWaveNode node : nodes) {
            // Don't add its own id or the controller
            if (node.getNodeId() == nodeId || node.getNodeId() == handler.getOwnNodeId()) {
                continue;
            }

            // Get this nodes thing so we can find the name
            // TODO: Add this when thing names are supported!
            // Thing thingNode = getThing(thingUID);

            // Add the node for the standard association class if it supports a controllable class
            if (supportsControllableClass(node)) {
                // TODO: Use the node name
                options.add(new ParameterOption("node_" + node.getNodeId() + "_0", "Node " + node.getNodeId()));
            }

            // If the device supports multi_instance_association class, then add all controllable endpoints as well...
            // If this node also supports multi_instance class
            if (supportsMultiInstanceAssociation == true && node.getCommandClass(CommandClass.MULTI_INSTANCE) != null) {
                // Loop through all the endpoints for this device and add any that are controllable

                // for(node.get)
                // options.add(new ParameterOption("node" + node.getNodeId() + "." + endpointId, "Node " +
                // node.getNodeId()));
            }
        }

        return Collections.unmodifiableList(options);
    }
}
