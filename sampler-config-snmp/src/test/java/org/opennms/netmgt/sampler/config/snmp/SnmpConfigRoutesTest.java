package org.opennms.netmgt.sampler.config.snmp;

import java.io.IOException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.MockLogger;
import org.opennms.core.test.MockLoggerFactory;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.camel.CamelBlueprintTest;
import org.opennms.core.test.http.annotations.JUnitHttpServer;
import org.opennms.netmgt.api.sample.support.SingletonBeanFactory;
import org.opennms.netmgt.config.snmp.SnmpConfig;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:/META-INF/opennms/emptyContext.xml"})
@JUnitHttpServer(port=9162)
public class SnmpConfigRoutesTest extends CamelBlueprintTest {
    private static final String REST_ROOT = "http://localhost:9162";

    @BeforeClass
    public static void configureLogging() throws SecurityException, IOException {
        final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            LoggerContext lc = (LoggerContext) loggerFactory;
            lc.getLogger("org.apache.aries.blueprint").setLevel(Level.INFO);
        } else if (loggerFactory instanceof MockLoggerFactory) {
            final Properties props = new Properties();
            props.put(MockLogger.LOG_KEY_PREFIX + "org.apache.aries.blueprint", "INFO");
            MockLogAppender.setupLogging(true, props);
        }

    }

    // The location of our Blueprint XML file to be used for testing
    @Override
    protected String getBlueprintDescriptor() {
        return "file:src/main/resources/OSGI-INF/blueprint/blueprint-sampler-config-snmp.xml";
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties props = new Properties();
        props.put("snmpConfigUrl", REST_ROOT + "/etc/snmp-config.xml");
        props.put("datacollectionFileUrl", REST_ROOT + "/etc/datacollection-config.xml");
        props.put("datacollectionGroupUrls", REST_ROOT + "/etc/datacollection/mib2.xml," + REST_ROOT + "/etc/datacollection/netsnmp.xml," + REST_ROOT + "/etc/datacollection/dell.xml");
        return props;
    }

    /**
     * We have to use {@link #useOverridePropertiesWithPropertiesComponent()} and
     * {@link #useOverridePropertiesWithConfigAdmin(Dictionary)} because there are
     * beans outside of the Camel context that use CM properties.
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        props.put("snmpConfigUrl", REST_ROOT + "/etc/snmp-config.xml");
        props.put("datacollectionFileUrl", REST_ROOT + "/etc/datacollection-config.xml");
        props.put("datacollectionGroupUrls", REST_ROOT + "/etc/datacollection/mib2.xml," + REST_ROOT + "/etc/datacollection/netsnmp.xml," + REST_ROOT + "/etc/datacollection/dell.xml");
        return "org.opennms.netmgt.sampler.config.snmp";
    }

    @Test
    public void testParseSnmpXml() throws Exception {
        System.err.printf("Starting testParseSnmpXML");
        SnmpConfig resultsUsingURL = template.requestBody("direct:parseSnmpXml", new URL(REST_ROOT + "/etc/snmp-config.xml"), SnmpConfig.class);

        System.err.printf("Results Using URL: %s\n", resultsUsingURL);
        assertNotNull(resultsUsingURL);
        assertEquals("public", resultsUsingURL.getReadCommunity());
        assertEquals(44, resultsUsingURL.getDefinitions().size());

        SnmpConfig resultsUsingString = template.requestBody("direct:parseSnmpXml", REST_ROOT + "/etc/snmp-config.xml", SnmpConfig.class);

        System.err.printf("Results Using String: %s\n", resultsUsingString);
        assertNotNull(resultsUsingString);
        assertEquals("public", resultsUsingString.getReadCommunity());
        assertEquals(44, resultsUsingString.getDefinitions().size());
    }

    @Test
    public void testLoadSnmpConfig() throws Exception {
        System.err.printf("Starting testLoadSnmpConfig");

        template.requestBody("direct:loadSnmpConfig", null, String.class);

        @SuppressWarnings("unchecked")
        SingletonBeanFactory<SnmpConfig> configSvc = bean("snmpConfigFactory", SingletonBeanFactory.class);

        System.err.printf("configSvc: %s\n", configSvc);
        assertNotNull(configSvc);
        assertNotNull(configSvc.getInstance());

    }

    /**
     * Test loading the {@link SnmpMetricRepository}.
     */
    @Test
    public void testLoadDataCollectionConfig() throws Exception {
        System.err.printf("Starting testLoadDataCollectionConfig");
        template.requestBody("direct:loadDataCollectionConfig", null, String.class);

        SnmpMetricRepository metricRepo = bean("snmpMetricRepository", SnmpMetricRepository.class);

        System.err.printf("metricRepo: %s\n", metricRepo);
        assertNotNull(metricRepo);
        assertNotNull(metricRepo.getMetric("ifInOctets"));

    }

    private <T> T bean(String name,	Class<T> type) {
        return context().getRegistry().lookupByNameAndType(name, type);
    }
}
