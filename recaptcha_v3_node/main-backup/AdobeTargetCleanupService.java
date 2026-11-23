package com.usfoods.aem.base.core.services;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.usfoods.aem.base.core.util.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Removes Adobe Target metadata nodes whose offer id is no longer returned by Target.
 */
public interface AdobeTargetCleanupService {

    String DISABLED_MESSAGE = "This feature is disabled contact Admin team";
    String INCOMPLETE_CONFIGURATION_PREFIX = "Adobe Target configuration is incomplete: ";
    String EXPERIENCE_FRAGMENTS_ROOT = "/content/experience-fragments";

    static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static boolean isAllowedPath(String path) {
        if (StringUtils.isBlank(path) || hasIllegalCharacter(path)) {
            return false;
        }
        if (path.contains("..") || path.contains("\\") || path.contains("//")) {
            return false;
        }
        if (path.contains("?") || path.contains("#") || path.contains("%")) {
            return false;
        }
        return EXPERIENCE_FRAGMENTS_ROOT.equals(path) || path.startsWith(EXPERIENCE_FRAGMENTS_ROOT + "/");
    }

    private static boolean hasIllegalCharacter(String path) {
        for (int index = 0; index < path.length(); index++) {
            if (path.charAt(index) < ' ') {
                return true;
            }
        }
        return false;
    }

    boolean isEnabled();

    /**
     * Names of credentials that are blank. Empty when the Target calls can run.
     */
    String missingConfiguration();

    AdobeTargetCleanupResult cleanup(ResourceResolver resolver, String path);

    /**
     * Summary of one Cleanup Adobe Target Ids run.
     */
    final class AdobeTargetCleanupResult {

        private final String message;
        private final String path;
        private final int offerCount;
        private final int examined;
        private final int kept;
        private final int deleted;
        private final int skipped;
        private final List<String> deletedPaths;
        private final boolean truncated;

        public AdobeTargetCleanupResult(String message, String path, int offerCount, int examined, int kept,
                                         int deleted, int skipped, List<String> deletedPaths, boolean truncated) {
            this.message = message;
            this.path = path;
            this.offerCount = offerCount;
            this.examined = examined;
            this.kept = kept;
            this.deleted = deleted;
            this.skipped = skipped;
            this.deletedPaths = Collections.unmodifiableList(deletedPaths);
            this.truncated = truncated;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }

        public int getOfferCount() {
            return offerCount;
        }

        public int getExamined() {
            return examined;
        }

        public int getKept() {
            return kept;
        }

        public int getDeleted() {
            return deleted;
        }

        public int getSkipped() {
            return skipped;
        }

        public List<String> getDeletedPaths() {
            return deletedPaths;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public String toJson() {
            ObjectNode payload = JsonUtil.getJsonMapper().createObjectNode();
            payload.put("success", true);
            payload.put("message", message);
            payload.put("path", path);
            payload.put("offerCount", offerCount);
            payload.put("examined", examined);
            payload.put("kept", kept);
            payload.put("deleted", deleted);
            payload.put("skipped", skipped);
            payload.put("truncated", truncated);
            ArrayNode paths = payload.putArray("deletedPaths");
            for (String deletedPath : deletedPaths) {
                paths.add(deletedPath);
            }
            try {
                return JsonUtil.getJsonMapper().writeValueAsString(payload);
            } catch (IOException ex) {
                throw new AdobeTargetCleanupException("Unable to write the cleanup result.", ex);
            }
        }
    }

    /**
     * Failure while loading Adobe Target offers or deleting stale metadata.
     */
    class AdobeTargetCleanupException extends RuntimeException {

        public AdobeTargetCleanupException(String message) {
            super(message);
        }

        public AdobeTargetCleanupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
