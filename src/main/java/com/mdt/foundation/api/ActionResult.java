package com.mdt.foundation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionResult {
    private final boolean success;
    private final String operation;
    private final String message;
    private final Map<String, Object> data;

    private ActionResult(boolean success, String operation, String message, Map<String, Object> data) {
        this.success = success;
        this.operation = operation;
        this.message = message;
        this.data = data == null
            ? Collections.<String, Object>emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data));
    }

    public static ActionResult success(String operation, String message, Map<String, Object> data) {
        return new ActionResult(true, operation, message, data);
    }

    public static ActionResult failure(String operation, String message) {
        return new ActionResult(false, operation, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOperation() {
        return operation;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
