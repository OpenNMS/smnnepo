package org.opennms.minion.api;

import java.util.Date;

public interface MinionStatusMessage extends MinionMessage {
    public String getId();
    public String getLocation();
    public String getStatus();
    public Date getDate();
}
