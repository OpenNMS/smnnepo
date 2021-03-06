package org.opennms.netmgt.sampler.jmx;

import java.net.URL;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.util.KeyValueHolder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.camel.CamelBlueprintTest;
import org.opennms.core.test.http.annotations.JUnitHttpServer;
import org.opennms.netmgt.api.sample.support.SingletonBeanFactory;
import org.opennms.netmgt.api.sample.support.SingletonBeanFactoryImpl;
import org.opennms.netmgt.config.collectd.CollectdConfiguration;
import org.opennms.netmgt.config.collectd.jmx.JmxCollection;
import org.opennms.netmgt.config.collectd.jmx.JmxDatacollectionConfig;
import org.springframework.test.context.ContextConfiguration;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:/META-INF/opennms/emptyContext.xml"})
@JUnitHttpServer(port=9162)
public class JmxConfigRoutesTest extends CamelBlueprintTest {
    private static final String REST_ROOT = "http://localhost:9162";

    // The location of our Blueprint XML file to be used for testing
    @Override
    protected String getBlueprintDescriptor() {
        return "file:src/main/resources/OSGI-INF/blueprint/blueprint.xml";
    }

    @Override
    protected String setConfigAdminInitialConfiguration(Properties props) {
        props.put("jmxDatacollectionConfigUrl", REST_ROOT + "/etc/jmx-datacollection-config.xml");
        return "org.opennms.netmgt.sampler.config.snmp.jmx";
    }

    @Test
    public void canLoadBlueprint() {
        // Verifies that the blueprint can load without throwing any exceptions
    }

    @Test
    @Ignore
    public void testParseJmxXml() throws Exception {
        System.err.printf("Starting testParseJmxXml");
        JmxDatacollectionConfig resultsUsingURL = template.requestBody("direct:parseJmxXml", new URL(REST_ROOT + "/etc/jmx-datacollection-config.xml"), JmxDatacollectionConfig.class);

        System.err.printf("Results Using URL: %s\n", resultsUsingURL);
        assertNotNull(resultsUsingURL);

        JmxCollection jmxCollection = resultsUsingURL.getJmxCollection("jsr160");
        assertNotNull(jmxCollection);

        assertEquals(23, jmxCollection.getMbeanCount());
    }

    @Test
    @Ignore
    public void testLoadJmxDatacollectionConfig() throws Exception {
        System.err.printf("Starting testLoadJmxDatacollectionConfig");

        template.requestBody("direct:loadJmxDatacollectionConfig", null, String.class);

        @SuppressWarnings("unchecked")
        SingletonBeanFactory<JmxDatacollectionConfig> configSvc = bean("jmxConfigFactory", SingletonBeanFactory.class);

        System.err.printf("configSvc: %s\n", configSvc);
        assertNotNull(configSvc);
        assertNotNull(configSvc.getInstance());
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        CollectdConfiguration collectdConfig = new CollectdConfiguration();

        Properties props = new Properties();
        props.put("beanClass", "org.opennms.netmgt.config.collectd.CollectdConfiguration");
        services.put(SingletonBeanFactory.class.getName(), new KeyValueHolder<Object,Dictionary>(new SingletonBeanFactoryImpl<CollectdConfiguration>(collectdConfig), props));
    }

    private <T> T bean(String name,	Class<T> type) {
        return context().getRegistry().lookupByNameAndType(name, type);
    }
}
