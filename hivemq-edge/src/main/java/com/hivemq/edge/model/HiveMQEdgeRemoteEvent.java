package com.hivemq.edge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hivemq.edge.utils.HiveMQEdgeEnvironmentUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Events to help track usage patterns to help define UX & platform usability
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HiveMQEdgeRemoteEvent {

    public enum EVENT_TYPE {
        EDGE_STARTED,
        EDGE_PING,
        ADAPTER_STARTED,
        ADAPTER_ERROR,
        BRIDGE_STARTED,
        BRIDGE_ERROR
    }

    public final Map<String, String> userData = new HashMap<>();
    public final Date created = new Date();
    public final EVENT_TYPE eventType;
    public String installationToken;
    public String edgeVersion;
    public String sessionToken;

    public HiveMQEdgeRemoteEvent(final EVENT_TYPE eventType) {
        this.eventType = eventType;
        this.installationToken = HiveMQEdgeEnvironmentUtils.generateInstallationToken();
        this.sessionToken = HiveMQEdgeEnvironmentUtils.getSessionToken();
    }

    public Date getCreated() {
        return created;
    }

    public EVENT_TYPE getEventType() {
        return eventType;
    }

    public String getEdgeVersion() {
        return edgeVersion;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setEdgeVersion(final String edgeVersion) {
        this.edgeVersion = edgeVersion;
    }

    public Map<String, String> getUserData() {
        return userData;
    }

    public void addUserData(final String propertyName, final String propertyValue) {
        this.userData.put(propertyName, propertyValue);
    }

    public void addAll(Map<String, String> map) {
        this.userData.putAll(map);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HiveMQEdgeEvent{");
        sb.append("userData=").append(userData);
        sb.append(", created=").append(created);
        sb.append(", eventType=").append(eventType);
        sb.append(", installationToken='").append(installationToken).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
