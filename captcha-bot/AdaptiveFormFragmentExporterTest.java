package com.usfoods.aem.base.core.models.adaptiveform.intake;

import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.export.json.ContainerExporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usfoods.aem.base.core.testutils.AEMContextHelper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class AdaptiveFormFragmentExporterTest {

    private static final String FRAGMENT_PATH =
            "/content/forms/af/what-is-dropdown-issue/jcr:content/guideContainer/tabsontop/panel/fragment";

    private final AemContext context = AEMContextHelper.newAemContext();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        context.addModelsForClasses(AdaptiveFormFragmentExporter.class);
    }

    @Test
    void exportsEmptyItemsWhenFragmentPathIsMissing() throws Exception {
        Resource fragment = context.create().resource(
                FRAGMENT_PATH,
                "sling:resourceType", AdaptiveFormFragmentExporter.PROXY_RESOURCE_TYPE,
                "name", "fragment1791492546109");
        context.currentResource(fragment);
        AEMContextHelper.setBindingsToRequest(context, fragment, "/content/forms/af/what-is-dropdown-issue");

        ModelFactory modelFactory = context.getService(ModelFactory.class);
        AdaptiveFormFragmentExporter exporter = modelFactory.createModel(
                context.request(), AdaptiveFormFragmentExporter.class);

        assertNotNull(exporter);
        setDelegate(exporter, throwingContainer());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(exporter));
        assertTrue(json.get(":items").isObject());
        assertEquals(0, json.get(":items").size());
        assertTrue(json.get(":itemsOrder").isArray());
        assertEquals(0, json.get(":itemsOrder").size());
        assertTrue(json.get("fragmentChildren").isArray());
        assertEquals(0, json.get("fragmentChildren").size());
    }

    @Test
    void exportsDelegateItemsWhenFragmentPathResolves() throws Exception {
        context.create().resource("/content/dam/formsanddocuments/sample-fragment");
        Resource fragment = context.create().resource(
                FRAGMENT_PATH + "-selected",
                "sling:resourceType", AdaptiveFormFragmentExporter.RESOURCE_TYPE,
                "fragmentPath", "/content/dam/formsanddocuments/sample-fragment");
        context.currentResource(fragment);
        AEMContextHelper.setBindingsToRequest(context, fragment, "/content/forms/af/what-is-dropdown-issue");

        ModelFactory modelFactory = context.getService(ModelFactory.class);
        AdaptiveFormFragmentExporter exporter = modelFactory.createModel(
                context.request(), AdaptiveFormFragmentExporter.class);
        setField(exporter, "fragmentPath", "/content/dam/formsanddocuments/sample-fragment");
        setField(exporter, "resourceResolver", context.resourceResolver());
        setDelegate(exporter, new ContainerExporter() {
            @Override
            public java.util.Map<String, ? extends ComponentExporter> getExportedItems() {
                return Collections.singletonMap("field", new ComponentExporter() {
                    @Override
                    public String getExportedType() {
                        return "core/fd/components/form/textinput/v1/textinput";
                    }
                });
            }

            @Override
            public String[] getExportedItemsOrder() {
                return new String[] {"field"};
            }

            @Override
            public String getExportedType() {
                return "core/fd/components/form/fragment/v1/fragment";
            }
        });

        JsonNode json = mapper.readTree(mapper.writeValueAsString(exporter));
        assertTrue(json.get(":items").has("field"));
        assertEquals("field", json.get(":itemsOrder").get(0).asText());
    }

    @Test
    void unwrapsSelfDelegateToCoreFragmentItems() throws Exception {
        context.create().resource("/content/dam/formsanddocuments/sample-fragment");
        Resource fragment = context.create().resource(
                FRAGMENT_PATH + "-wrapped",
                "sling:resourceType", AdaptiveFormFragmentExporter.PROXY_RESOURCE_TYPE,
                "fragmentPath", "/content/dam/formsanddocuments/sample-fragment");
        context.currentResource(fragment);
        AEMContextHelper.setBindingsToRequest(context, fragment, "/content/forms/af/what-is-dropdown-issue");

        ModelFactory modelFactory = context.getService(ModelFactory.class);
        AdaptiveFormFragmentExporter exporter = modelFactory.createModel(
                context.request(), AdaptiveFormFragmentExporter.class);
        AdaptiveFormFragmentExporter wrapped = modelFactory.createModel(
                context.request(), AdaptiveFormFragmentExporter.class);
        setField(exporter, "fragmentPath", "/content/dam/formsanddocuments/sample-fragment");
        setField(exporter, "resourceResolver", context.resourceResolver());
        setDelegate(wrapped, new ContainerExporter() {
            @Override
            public java.util.Map<String, ? extends ComponentExporter> getExportedItems() {
                return Collections.singletonMap("dropdown", new ComponentExporter() {
                    @Override
                    public String getExportedType() {
                        return "core/fd/components/form/dropdown/v1/dropdown";
                    }
                });
            }

            @Override
            public String[] getExportedItemsOrder() {
                return new String[] {"dropdown"};
            }

            @Override
            public String getExportedType() {
                return "core/fd/components/form/fragment/v1/fragment";
            }
        });
        setDelegate(exporter, wrapped);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(exporter));
        assertTrue(json.get(":items").has("dropdown"));
        assertEquals("dropdown", json.get(":itemsOrder").get(0).asText());
        assertEquals("core/fd/components/form/fragment/v1/fragment", exporter.getExportedType());
    }

    private static ContainerExporter throwingContainer() {
        return new ContainerExporter() {
            @Override
            public java.util.Map<String, ? extends ComponentExporter> getExportedItems() {
                throw new NullPointerException("fragmentContainer");
            }

            @Override
            public String[] getExportedItemsOrder() {
                throw new NullPointerException("fragmentContainer");
            }

            @Override
            public String getExportedType() {
                return "core/fd/components/form/fragment/v1/fragment";
            }
        };
    }

    private static void setDelegate(AdaptiveFormFragmentExporter exporter, ComponentExporter delegate) throws Exception {
        setField(exporter, "delegate", delegate);
    }

    private static void setField(AdaptiveFormFragmentExporter exporter, String name, Object value) throws Exception {
        Field field = AdaptiveFormFragmentExporter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(exporter, value);
    }
}
