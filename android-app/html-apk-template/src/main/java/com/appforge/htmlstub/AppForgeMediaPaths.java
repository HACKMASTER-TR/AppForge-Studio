package com.appforge.htmlstub;

/** Deliberately local-only: never accepts a URL, provider URI, traversal or arbitrary private file. */
final class AppForgeMediaPaths {
    static String asset(String path) {
        if(path==null || path.length()<1 || path.length()>512 || path.startsWith("/") ||
            path.codePoints().anyMatch(c->Character.isISOControl(c)) || path.matches(".*[:\\\\?#%].*"))
            throw new IllegalArgumentException("Packaged relative media path required");
        for(String part:path.split("/",-1))if(part.isEmpty() || ".".equals(part) || "..".equals(part))
            throw new IllegalArgumentException("Invalid media path");
        String lower=path.toLowerCase(java.util.Locale.ROOT);
        if(!lower.matches(".*\\.(mp3|m4a|aac|ogg|wav|mp4|webm)"))throw new IllegalArgumentException("Unsupported media extension");
        return "site/"+path;
    }
    static String title(String value) {
        if(value==null || value.length()>100 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid media title");
        return value.isEmpty()?"AppForge Audio":value;
    }
}
