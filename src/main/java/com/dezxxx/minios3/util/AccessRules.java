package com.dezxxx.minios3.util;

import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;

/**
 * The two questions every ownership check comes down to, written once.
 *
 * <p>They were duplicated word for word in FileService and EventService, which meant the
 * rule "ADMIN and MODERATOR see everything" lived in two places. A fourth role, or a change
 * of mind about a third, would have had to be remembered twice — and the omission would not
 * break anything loudly, it would just quietly show somebody more than they should see.
 *
 * <p>A utility class rather than a bean because neither method needs anything but its
 * arguments. Looking the caller up does need the database, and that lives on
 * {@code UserRepository.findCallerOrThrow} instead.
 */
public final class AccessRules {

    private AccessRules() {
    }

    /** ADMIN and MODERATOR read every row; the specification says so in as many words. */
    public static boolean readsEverything(User caller) {
        return caller.getRole() != Role.USER;
    }

    /** True when the caller may see something owned by {@code ownerUsername}. */
    public static boolean maySee(User caller, String ownerUsername) {
        return readsEverything(caller) || caller.getUsername().equals(ownerUsername);
    }
}
