package com.fintrack.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fintrack.mail")
public class MailProperties {

    private String enabled = "";
    private String fromAddress = "noreply@fintrack.local";
    private String fromName = "FinTrack Pro";
    private String baseUrl = "http://localhost";

    public boolean isEnabled() {
        return enabled != null && !enabled.isBlank();
    }

    public String getEnabled() { return enabled; }
    public void setEnabled(String enabled) { this.enabled = enabled; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
