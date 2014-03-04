package org.opennms.netmgt.sampler.snmp;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.opennms.netmgt.api.sample.support.SingletonBeanFactory;
import org.opennms.netmgt.config.collectd.CollectdConfiguration;
import org.opennms.netmgt.config.collectd.Package;
import org.opennms.netmgt.config.collectd.Service;
import org.opennms.netmgt.sampler.config.snmp.SnmpMetricRepository;
import org.opennms.netmgt.sampler.snmp.ServiceAgent.ServiceAgentList;

public class SamplerRoutingTest extends CamelTestSupport {
	
	public static final int AGENT_COUNT = 1;
	
	private static URL url(String path) throws MalformedURLException {
		return new URL("file:src/test/resources/" + path);
	}

	/**
	 * This class will convert all incoming objects to URLs.
	 */
	public static class UrlNormalizer {

		public URL toURL(String s) throws MalformedURLException {
			return new URL(s);
		}

		public URL toURL(URL u) {
			return u;
		}
	}


	@Override
	protected JndiRegistry createRegistry() throws Exception {
		JndiRegistry registry = super.createRegistry();
		
		SnmpMetricRepository snmpMetricRepository = new SnmpMetricRepository(
			url("datacollection-config.xml"), 
			url("datacollection/mib2.xml"), 
			url("datacollection/netsnmp.xml"),
			url("datacollection/dell.xml")
		);
		
		registry.bind("collectdConfiguration", new SingletonBeanFactory<CollectdConfiguration>());
		registry.bind("snmpConfiguration", new SingletonBeanFactory<SnmpConfiguration>());;
		registry.bind("snmpMetricRepository", snmpMetricRepository);
		registry.bind("urlNormalizer", new UrlNormalizer());
		registry.bind("packageServiceSplitter", new PackageServiceSplitter());
		
		return registry;
	}
	
	public static class PackageServiceSplitter  {
		public List<Package> packageWithOneService(Package pkg) {
			List<Package> retval = new ArrayList<Package>();
			for (Service svc : pkg.getServices()) {
				Package newPackage = new Package(pkg);
				newPackage.setServices(Collections.singletonList(svc));
				retval.add(newPackage);
			}
			return retval;
		}
	}

	@Override
	public boolean isUseAdviceWith() {
		return true;
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				
				onException(IOException.class)
					.handled(true)
					//.transform().constant(null)
					.stop()
				;
				
				JAXBContext context = JAXBContext.newInstance(CollectdConfiguration.class, SnmpConfiguration.class);

				JaxbDataFormat jaxb = new JaxbDataFormat(context);
				JacksonDataFormat json = new JacksonDataFormat(ServiceAgentList.class);
				//JacksonDataFormat json = new JacksonDataFormat();
				
				// Call this to retrieve a URL in string form or URL form into the JAXB objects they represent
				from("direct:parseXML")
					.beanRef("urlNormalizer")
					.unmarshal(jaxb)
				;
				
				// Call this to retrieve a URL in string form or URL form into the JSON objects they represent
				from("direct:parseJSON")
					.beanRef("urlNormalizer")
					.unmarshal(json)
				;

				// Direct route to fetch the config
				from("direct:collectdConfig")
					.beanRef("collectdConfiguration", "getInstance")
				;

				// Direct route to fetch the config
				from("direct:snmpConfig")
					.beanRef("snmpConfiguration", "getInstance")
				;

				from("seda:start")
					// Load all of the configs
					.multicast()
						.parallelProcessing().to(
							"direct:loadCollectdConfiguration",
							"direct:loadDataCollectionConfig",
							"direct:loadSnmpConfig"
						)
					.end()
					.log("==== Configurations Loaded ====")
					// Launch the scheduler
					.to("direct:schedulerStart")
					.to("mock:result")
				;

				// TODO: Create a reload timer that will check for changes to the config
				from("direct:loadCollectdConfiguration")
					.transform(constant(url("collectd-configuration.xml")))
					.to("direct:parseXML")
					.beanRef("collectdConfiguration", "setInstance")
				;
				
				// TODO: Create a reload timer that will check for changes to the config
				from("direct:loadDataCollectionConfig")
					.log("Refreshing snmpMetricRepository")
					.beanRef("snmpMetricRepository", "refresh")
				;
				
				// TODO: Create a reload timer that will check for changes to the config
				from("direct:loadSnmpConfig")
					.transform(constant(url("snmp-config.xml")))
					.to("direct:parseXML")
					.beanRef("snmpConfiguration", "setInstance")
				;
				
				from("direct:schedulerStart")
					.to("direct:loadCollectionPackages")
				;
				
				// Get all of the collection packages that are associated with the current package
				from("direct:loadCollectionPackages")
					// Replace the current message with the CollectdConfiguration
					.enrich("direct:collectdConfig")
					// Split the CollectdConfiguration into a list of the packages that it contains
					.log("Parsing CollectdConfiguration with ${body.packages.size} package(s)")
					.transform().simple("${body.packages}")
					.split().body()
					// Split the package into a package-per-service
					.log("Parsing package ${body.name} with ${body.services.size} service(s)")
					.split().method("packageServiceSplitter", "packageWithOneService")
					// Route different service types to different routes
					.choice()
						.when(simple("${body.services[0].name} == 'SNMP'"))
							.to("seda:loadSnmpAgents")
						.when(property("${body.services[0].name == 'JMX'}"))
							.to("seda:loadJmxAgents")
						/*
						.otherwise()
							.throwException(new UnsupportedOperationException("Cannot process service ${body}"))
						*/
				;

				from("seda:loadSnmpAgents")
					.log("Running seda:loadSnmpAgents")
					.to("seda:loadPackageAgents")
				;

				from("seda:loadJmxAgents")
					.log("Running seda:loadJmxAgents")
					.to("seda:loadPackageAgents")
				;

				from("seda:loadPackageAgents")
					.enrich("direct:getServiceAgents", new AggregationStrategy() {
						
						@Override
						public Exchange aggregate(Exchange pkgServiceExchange, Exchange svcAgentsExchange) {
							Package pkgService = pkgServiceExchange.getIn().getBody(Package.class);
							List<ServiceAgent> svcAgents = svcAgentsExchange.getIn().getBody(ServiceAgentList.class);
							
							PackageAgentList pkgAgents = new PackageAgentList(pkgService, svcAgents);
							
							pkgServiceExchange.getIn().setBody(pkgAgents);
							
							return pkgServiceExchange;
						}
					})
					.log("Package: ${body.package}, Agents: ${body.agents}")
					.to("seda:scheduleAgents")
				;

				from("direct:getServiceAgents")
					.log("Parsing URL: file:src/test/resources/agents/${body.name}/${body.services[0].name}.json")
					.transform().simple("file:src/test/resources/agents/${body.name}/${body.services[0].name}.json")
					.to("direct:parseJSON")
				;
				
				from("seda:scheduleAgents")
					.log("TODO: IMPLEMENT seda:scheduleAgents")
				;
			}
		};
	}
	
	@Test
	public void testParseXML() throws Exception {
		context.start();

		SnmpConfiguration resultsUsingURL = template.requestBody("direct:parseXML", url("snmp-config.xml"), SnmpConfiguration.class);

		//System.err.printf("Results: %s\n", resultsUsingURL);
		assertNotNull(resultsUsingURL);
		
		SnmpConfiguration resultsUsingString = template.requestBody("direct:parseXML", url("snmp-config.xml").toString(), SnmpConfiguration.class);

		//System.err.printf("Results: %s\n", resultsUsingString);
		assertNotNull(resultsUsingString);
	}

	@Test
	public void testParseJSON() throws Exception {
		context.start();

		List<ServiceAgent> resultsUsingURL = template.requestBody("direct:parseJSON", url("agents/example1/SNMP.json"), ServiceAgentList.class);

		//System.err.printf("Results: %s\n", resultsUsingURL);
		assertNotNull(resultsUsingURL);
		assertEquals(3, resultsUsingURL.size());
		
		List<ServiceAgent> resultsUsingString = template.requestBody("direct:parseJSON", url("agents/example1/SNMP.json").toString(), ServiceAgentList.class);

		//System.err.printf("Results: %s\n", resultsUsingString);
		assertNotNull(resultsUsingString);
		assertEquals(3, resultsUsingString.size());
	}

	@Test
	public void testLoadCollectdConfiguration() throws Exception {
		context.start();
		
		template.requestBody("direct:loadCollectdConfiguration", null, String.class);
		
		SingletonBeanFactory<CollectdConfiguration> configSvc = bean("collectdConfiguration", SingletonBeanFactory.class);
		
		assertNotNull(configSvc);
		assertNotNull(configSvc.getInstance());
	}

	@Test
	public void testLoadSnmpConfig() throws Exception {
		context.start();
		
		template.requestBody("direct:loadSnmpConfig", null, String.class);
		
		SingletonBeanFactory<SnmpConfiguration> configSvc = bean("snmpConfiguration", SingletonBeanFactory.class);
		
		assertNotNull(configSvc);
		assertNotNull(configSvc.getInstance());

	}
	@Test
	public void testLoadDataCollectionConfig() throws Exception {
		context.start();
		
		template.requestBody("direct:loadDataCollectionConfig", null, String.class);
		
		SnmpMetricRepository metricRepo = bean("snmpMetricRepository", SnmpMetricRepository.class);
		
		assertNotNull(metricRepo);
		assertNotNull(metricRepo.getMetric("ifInOctets"));
		
	}
	@Test
	public void testLoadServiceAgents() throws Exception {
		// Add mock endpoints to the route context
		context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				mockEndpoints();
			}
		});
		context.start();

		// We should get 1 call to the scheduler endpoint
		MockEndpoint endpoint = getMockEndpoint("mock:seda:scheduleAgents");
		endpoint.setExpectedMessageCount(1);

		Service svc = new Service();
		svc.setInterval(1000L);
		svc.setName("SNMP");
		svc.setStatus("on");
		svc.setUserDefined("false");

		Package pkg = new Package();
		pkg.setName("example1");
		pkg.setServices(Collections.singletonList(svc));
		
		template.requestBody("seda:loadSnmpAgents", pkg);
		
		assertMockEndpointsSatisfied();

		// Make sure that we got one exchange to the scheduler
		assertEquals(1, endpoint.getReceivedCounter());
		// That contains 3 SNMP agent instances
		for (Exchange exchange : endpoint.getReceivedExchanges()) {
			PackageAgentList agents = exchange.getIn().getBody(PackageAgentList.class);
			assertNotNull(agents);
			assertEquals(3, agents.getAgents().size());
		}
	}
	
	@Test
	public void testLoadPackageServiceList() throws Exception {
		// Add mock endpoints to the route context
		context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				mockEndpoints();
			}
		});
		context.start();

		MockEndpoint result = getMockEndpoint("mock:seda:scheduleAgents");
		result.expectedMessageCount(1);
		
		// Load the CollectdConfiguration
		template.sendBody("direct:loadCollectdConfiguration", null);
		
		// Fetch the loaded config
		CollectdConfiguration collectConfig = template.requestBody("direct:collectdConfig", null, CollectdConfiguration.class);
		// Pass it to the method that parses the config into a package/service object
		template.sendBody("direct:loadCollectionPackages", collectConfig);
		
		assertMockEndpointsSatisfied();
		
		assertEquals(1, result.getReceivedCounter());
		for (Exchange exchange : result.getReceivedExchanges()) {
			assertEquals(3, exchange.getIn().getBody(PackageAgentList.class).getAgents().size());
		}
	}
	


	@Test(timeout=10000)
	public void testStartup() throws Exception {
		context.start();
		
		MockEndpoint result = getMockEndpoint("mock:result");
		result.expectedMessageCount(1);
		
		MockEndpoint scheduled = getMockEndpoint("mock:scheduled");
		scheduled.expectedMessageCount(2);
		
		//template.asyncRequestBody("seda:start", url("datacollection-config.xml"));
		//template.asyncRequestBody("seda:start", null);
		template.sendBody("seda:start", null);
		result.await();

		SingletonBeanFactory<CollectdConfiguration> collectdConfig = bean("collectdConfiguration", SingletonBeanFactory.class);
		SingletonBeanFactory<SnmpConfiguration> snmpConfig = bean("snmpConfiguration", SingletonBeanFactory.class);
		
		System.err.println("Waiting");

		//scheduled.await();
		
		System.err.println("Finished waiting");

		assertNotNull(collectdConfig);
		assertNotNull(collectdConfig.getInstance());

		assertNotNull(snmpConfig);
		assertNotNull(snmpConfig.getInstance());
	}

	private <T> T bean(String name,	Class<T> type) {
		return context().getRegistry().lookupByNameAndType(name, type);
	}
}