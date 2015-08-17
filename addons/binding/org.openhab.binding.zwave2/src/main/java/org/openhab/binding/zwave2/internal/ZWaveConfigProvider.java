package org.openhab.binding.zwave2.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter;
import org.eclipse.smarthome.config.core.ConfigDescriptionParameterGroup;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.eclipse.smarthome.core.thing.type.ThingTypeRegistry;
import org.openhab.binding.zwave2.ZWaveBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZWaveConfigProvider implements ConfigDescriptionProvider {
    private final Logger logger = LoggerFactory.getLogger(ZWaveConfigProvider.class);

    private static ThingTypeRegistry thingTypeRegistry;

    private static Set<ThingTypeUID> zwaveThingTypeUIDList = new HashSet<ThingTypeUID>();
    private static List<ZWaveProduct> productIndex = new ArrayList<ZWaveProduct>();

    protected void setThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZWaveConfigProvider.thingTypeRegistry = thingTypeRegistry;
    }

    protected void unsetThingTypeRegistry(ThingTypeRegistry thingTypeRegistry) {
        ZWaveConfigProvider.thingTypeRegistry = null;
    }

    @Override
    public Collection<ConfigDescription> getConfigDescriptions(Locale locale) {
        // TODO Auto-generated method stub
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
}
