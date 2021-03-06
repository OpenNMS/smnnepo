package org.opennms.netmgt.sampler.snmp;

import org.opennms.netmgt.api.sample.SampleSet;
import org.opennms.netmgt.sampler.config.snmp.SnmpCollectionRequest;

public interface SnmpCollector {

	SampleSet collect(SnmpCollectionRequest request) throws Exception;

}