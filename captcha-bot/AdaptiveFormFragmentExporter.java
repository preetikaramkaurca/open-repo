package com.usfoods.aem.base.core.models.adaptiveform.intake;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ContainerExporter;
import com.adobe.cq.export.json.ExporterConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Via;
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.via.ForcedResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Exports an Adaptive Form fragment. A fragment that has not been given a path yet has no
 * fragment container, and the core model throws when the form model is serialized.
 */
@Model(
    adaptables = SlingHttpServletRequest.class,
    adapters = ComponentExporter.class,
    resourceType = {
        AdaptiveFormFragmentExporter.RESOURCE_TYPE,
        AdaptiveFormFragmentExporter.PROXY_RESOURCE_TYPE
    },
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME, extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class AdaptiveFormFragmentExporter implements ComponentExporter {

    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveFormFragmentExporter.class);

    public static final String RESOURCE_TYPE =
            "usf-aem-cloud-base/wcm/components/adaptiveform/fragment/v1/fragment";

    public static final String PROXY_RESOURCE_TYPE =
            "usf-aem-cloud-base/components/adaptiveform/fragment";

    static final String PN_FRAGMENT_PATH = "fragmentPath";

    static final String CORE_RESOURCE_TYPE = "core/fd/components/form/fragment/v1/fragment";

    /**
     * Force the core fragment model. {@code @Via ResourceSuperType} stops on this class, because the
     * model is registered for both the proxy and the versioned component, so the delegate was another
     * {@code AdaptiveFormFragmentExporter} and {@code :items} was exported empty.
     */
    @Self
    @Via(type = ForcedResourceType.class, value = CORE_RESOURCE_TYPE)
    @JsonUnwrapped
    @JsonIgnoreProperties({":items", ":itemsOrder", "fragmentChildren"})
    private ComponentExporter delegate;

    @ValueMapValue(name = PN_FRAGMENT_PATH, injectionStrategy = InjectionStrategy.OPTIONAL)
    private String fragmentPath;

    @SlingObject
    private ResourceResolver resourceResolver;

    @Override
    @JsonIgnore
    public String getExportedType() {
        ComponentExporter core = unwrapDelegate();
        if (core != null && core != this) {
            return core.getExportedType();
        }
        return RESOURCE_TYPE;
    }

    /**
     * The model is registered for both the proxy and the versioned fragment component.
     * {@code @Via ResourceSuperType} therefore injects another {@code AdaptiveFormFragmentExporter}
     * instead of the core fragment. Walk past those wrappers to the core container.
     */
    private ComponentExporter unwrapDelegate() {
        ComponentExporter current = delegate;
        while (current instanceof AdaptiveFormFragmentExporter) {
            ComponentExporter next = ((AdaptiveFormFragmentExporter) current).delegate;
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    @JsonProperty(":items")
    public Map<String, ? extends ComponentExporter> getItems() {
        if (!hasFragmentContainer()) {
            return Collections.emptyMap();
        }
        ComponentExporter core = unwrapDelegate();
        if (!(core instanceof ContainerExporter)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, ? extends ComponentExporter> items = ((ContainerExporter) core).getExportedItems();
            if (items == null) {
                return Collections.emptyMap();
            }
            return items;
        } catch (NullPointerException ex) {
            LOG.warn("Fragment {} could not export items because its container is missing", fragmentPath);
            return Collections.emptyMap();
        }
    }

    @JsonProperty(":itemsOrder")
    public String[] getItemsOrder() {
        return getItems().keySet().toArray(new String[0]);
    }

    @JsonProperty("fragmentChildren")
    public Object getFragmentChildren() {
        if (!hasFragmentContainer()) {
            return Collections.emptyList();
        }
        ComponentExporter core = unwrapDelegate();
        if (core == null) {
            return Collections.emptyList();
        }
        try {
            return core.getClass().getMethod("getFragmentChildren").invoke(core);
        } catch (ReflectiveOperationException | NullPointerException ex) {
            return Collections.emptyList();
        }
    }

    private boolean hasFragmentContainer() {
        if (StringUtils.isBlank(fragmentPath) || resourceResolver == null) {
            return false;
        }
        return resourceResolver.getResource(fragmentPath) != null;
    }
}
