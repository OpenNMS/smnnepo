package org.opennms.netmgt.sampler.routes;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Dictionary;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.KeyValueHolder;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.opennms.core.test.camel.CamelBlueprintTest;
import org.opennms.netmgt.api.sample.SampleSet;
import org.opennms.netmgt.api.sample.SampleSetDispatcher;
import org.opennms.netmgt.api.sample.Timestamp;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

public class XmlStorageContextTest extends CamelBlueprintTest {

	/**
	 * TODO: This isn't working properly because the ActiveMQ logs aren't getting routed
	 * through logback somehow.
	 */
	@BeforeClass
	public static void configureLogging() throws SecurityException, IOException {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		lc.getLogger("org.apache.aries.blueprint").setLevel(Level.INFO);
	}

	// The location of our Blueprint XML file to be used for testing
	@Override
	protected String getBlueprintDescriptor() {
		return "file:blueprint-xml-storage.xml";
	}

	@Override
	@SuppressWarnings("rawtypes")
	protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
		// Don't need any OSGi services yet
	}

	@Test(timeout=60000)
	@Ignore // Only used to sanity check the XML context filename Camel code
	public void test() throws Exception {

		assertTrue(context.hasEndpoint("mock:file:xml") != null);
		MockEndpoint endpoint = getMockEndpoint("mock:file:xml", false);
		// Only one of the SampleSets will make it through because only one has sample values in it
		endpoint.setExpectedMessageCount(1);

		SampleSetDispatcher dispatcher = getOsgiService(SampleSetDispatcher.class);
		assertTrue(dispatcher != null);

		// Unmarshal the SampleSet from a file
		SampleSet set = (SampleSet)JAXBContext.newInstance(SampleSet.class).createUnmarshaller().unmarshal(new File("src/test/resources/sampleSet.xml"));
		dispatcher.save(set);
		set = new SampleSet(new Timestamp(new Date()));
		dispatcher.save(set);

		assertMockEndpointsSatisfied();
	}
}
