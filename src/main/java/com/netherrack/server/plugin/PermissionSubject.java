package com.netherrack.server.plugin;

import com.netherrack.api.permission.Permissible;
import com.netherrack.api.permission.Permission;
import com.netherrack.api.permission.PermissionAttachment;
import com.netherrack.api.permission.PermissionDefault;
import com.netherrack.api.permission.PermissionState;
import com.netherrack.api.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Resolves permissions for one player or the console.
 * <p>
 * Rules are checked from most to least specific: an exact node, then the longest matching
 * {@code parent.*} wildcard, then the registered default, then operator status. Later attachments
 * win ties, so a permission manager can replace an older rule by adding a new attachment. Nothing
 * is cached, which keeps an attachment change visible immediately on any thread.
 */
final class PermissionSubject implements Permissible {

    private final BooleanSupplier operator;
    private final PermissionManagerImpl manager;
    private final List<PermissionAttachment> attachments = new CopyOnWriteArrayList<>();

    PermissionSubject(BooleanSupplier operator, PermissionManagerImpl manager) {
        this.operator = operator;
        this.manager = manager;
    }

    @Override
    public PermissionState getPermissionValue(String node) {
        if (node == null || node.isBlank()) {
            return PermissionState.UNDEFINED;
        }
        String key = node.toLowerCase(Locale.ROOT);

        PermissionState explicit = explicit(key);
        if (explicit != PermissionState.UNDEFINED) {
            return explicit;
        }
        PermissionState registered = registeredDefault(key);
        if (registered != PermissionState.UNDEFINED) {
            return registered;
        }
        return operator.getAsBoolean() ? PermissionState.ALLOW : PermissionState.UNDEFINED;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        PermissionAttachment attachment = new PermissionAttachment(this, plugin);
        attachments.add(attachment);
        return attachment;
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        attachments.remove(attachment);
    }

    @Override
    public void recalculatePermissions() {
        // Checks are live, so there is no cached result to invalidate.
    }

    private PermissionState explicit(String key) {
        PermissionState best = PermissionState.UNDEFINED;
        int bestScore = -1;
        // Backwards, so a later attachment wins when two rules are equally specific.
        for (int i = attachments.size() - 1; i >= 0; i--) {
            for (Map.Entry<String, PermissionState> rule : attachments.get(i).getPermissions().entrySet()) {
                int score = score(rule.getKey(), key);
                if (score > bestScore) {
                    bestScore = score;
                    best = rule.getValue();
                }
            }
        }
        return best;
    }

    private PermissionState registeredDefault(String key) {
        PermissionState best = PermissionState.UNDEFINED;
        int bestScore = -1;
        for (Permission permission : manager.snapshot()) {
            int score = score(permission.getKey(), key);
            if (score <= bestScore) {
                continue;
            }
            bestScore = score;
            best = switch (permission.getDefault()) {
                case ALLOW -> PermissionState.ALLOW;
                case DENY -> PermissionState.DENY;
                case OPERATOR -> operator.getAsBoolean() ? PermissionState.ALLOW : PermissionState.DENY;
                case NOT_OPERATOR -> operator.getAsBoolean() ? PermissionState.DENY : PermissionState.ALLOW;
                case UNDEFINED -> PermissionState.UNDEFINED;
            };
        }
        return best;
    }

    /** Exact matches rank above wildcards; a longer prefix is more specific than a shorter one. */
    private static int score(String rule, String node) {
        if (rule.equals(node)) {
            return Integer.MAX_VALUE;
        }
        if (rule.equals("*")) {
            return 1;
        }
        if (rule.endsWith(".*")) {
            String prefix = rule.substring(0, rule.length() - 2);
            if (node.equals(prefix) || node.startsWith(prefix + ".")) {
                return prefix.length() + 1;
            }
        }
        return -1;
    }
}
