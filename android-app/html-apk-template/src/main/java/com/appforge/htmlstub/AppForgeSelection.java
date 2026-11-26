package com.appforge.htmlstub;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** One system picker at a time. Retire the callback on navigation, drain the old result before reentry. */
final class AppForgeSelection<T> {
    interface Callback<T> { void complete(List<T> values); }
    private Callback<T> callback;
    private boolean outstanding;

    boolean begin(Callback<T> next) {
        if (outstanding) return false;
        outstanding = true;
        callback = next;
        return true;
    }
    boolean outstanding() { return outstanding; }
    void restoreOutstanding(boolean value) { outstanding = value; }
    void cancel() {
        Callback<T> previous = callback;
        callback = null;
        if (previous != null) previous.complete(null);
    }
    void finish(List<T> values) {
        Callback<T> previous = callback;
        callback = null;
        outstanding = false;
        if (previous != null) previous.complete(values == null || values.isEmpty() || values.size() > 32 || values.stream().anyMatch(value -> value == null)
            ? null : new ArrayList<>(new LinkedHashSet<>(values)));
    }
    static boolean safeContentUri(String value) {
        try {
            URI uri = new URI(value);
            return value.length() <= 8192 && "content".equals(uri.getScheme()) &&
                uri.getRawAuthority() != null && uri.getRawAuthority().matches("[A-Za-z0-9_.-]+") &&
                uri.getRawPath() != null && uri.getRawPath().startsWith("/") && uri.getFragment() == null;
        } catch (Exception error) { return false; }
    }
    static String[] mimeTypes(String[] input) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (input != null) for (String raw : input) {
            if (raw == null) continue;
            for (String part : raw.split(",")) {
                String value = part.trim().toLowerCase(java.util.Locale.ROOT);
                if (value.matches("(?:[a-z0-9!#$&^_.+-]+|\\*)/(?:[a-z0-9!#$&^_.+-]+|\\*)")) types.add(value);
                if (types.size() >= 32) return types.toArray(new String[0]);
            }
        }
        return types.toArray(new String[0]);
    }
}
