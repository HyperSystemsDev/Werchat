package com.werchat.integration.papi;

public class WerchatExpansionConfig {
    private String activeChannel = "active";

    public WerchatExpansionConfig() {
    }

    public WerchatExpansionConfig(String activeChannel) {
        this.activeChannel = activeChannel;
    }

    public String activeChannel() {
        if (activeChannel == null || activeChannel.isBlank()) {
            return "active";
        }
        return activeChannel;
    }
}
