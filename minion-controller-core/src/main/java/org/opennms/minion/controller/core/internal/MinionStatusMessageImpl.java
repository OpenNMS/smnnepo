package org.opennms.minion.controller.core.internal;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.opennms.minion.controller.api.DateAdapter;
import org.opennms.minion.controller.api.MapAdapter;
import org.opennms.minion.controller.api.MinionStatusMessage;

@XmlRootElement(name="minion-status")
@XmlAccessorType(XmlAccessType.NONE)
public class MinionStatusMessageImpl implements MinionStatusMessage {
    @XmlAttribute(name="id")
    private String m_id;

    @XmlElement(name="location")
    private String m_location;

    @XmlElement(name="status")
    private String m_status;

    @XmlElement(name="date")
    @XmlJavaTypeAdapter(DateAdapter.class)
    private Date m_date;

    @XmlElement(name="properties")
    @XmlJavaTypeAdapter(MapAdapter.class)
    private Map<String,String> m_properties;

    @Override
    public String getId() {
        return m_id;
    }

    public void setId(final String id) {
        m_id = id;
    }

    @Override
    public String getLocation() {
        return m_location;
    }

    public void setLocation(final String location) {
        m_location = location;
    }

    @Override
    public String getStatus() {
        return m_status;
    }

    public void setStatus(final String status) {
        m_status = status;
    }

    @Override
    public Date getDate() {
        return m_date;
    }

    public void setDate(final Date date) {
        m_date = date;
    }

    @Override
    public Map<String,String> getProperties() {
        if (m_properties != null) {
            return Collections.unmodifiableMap(m_properties);
        } else {
            return Collections.emptyMap();
        }
    }

    public void addProperty(final String key, final String value) {
        assertPropertiesCreated();
        m_properties.put(key, value);
    }

    private void assertPropertiesCreated() {
        if (m_properties == null) {
            m_properties = new LinkedHashMap<String,String>();
        }
    }

    @Override
    public String toString() {
        return "MinionStatusMessageImpl [id=" + m_id + ", location=" + m_location + ", status=" + m_status + ", date=" + m_date + ", properties=" + m_properties + "]";
    }
}