package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface GovernanceCommandHandler {
    String supportedRequestType();
    void execute(JsonNode payload);
}
