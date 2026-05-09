package com.mdt.foundation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionRequest {
    private final String operation;
    private final Map<String, String> parameters;

    public ActionRequest(String operation, Map<String, String> parameters) {
        this.operation = operation == null ? "" : operation.trim();
        this.parameters = parameters == null
            ? Collections.<String, String>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, String>(parameters));
    }

    public static ActionRequest of(String operation, Map<String, String> parameters) {
        return new ActionRequest(operation, parameters);
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }
}
