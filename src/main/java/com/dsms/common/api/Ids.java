package com.dsms.common.api;

public final class Ids {
    private Ids() {}

    public static String toJson(Long id) {
        return id == null ? null : Long.toString(id);
    }

    public static Long parse(String id) {
        if (id == null || id.isBlank()) {
            throw ExceptionsHelper.badId();
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            throw ExceptionsHelper.badId();
        }
    }

    private static final class ExceptionsHelper {
        private static com.dsms.common.exception.ApiException badId() {
            return com.dsms.common.exception.Exceptions.badRequest("INVALID_ID", "Invalid identifier");
        }
    }
}
