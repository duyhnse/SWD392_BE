package swd392.group6.AIVES.model;

import lombok.Getter;

/** System roles. Ids must match the seed rows of the {@code roles} table in init-scripts. */
@Getter
public enum Role {
    ADMIN((short) 1),
    LECTURER((short) 2),
    STUDENT((short) 3);

    private final Short id;

    Role(Short id) {
        this.id = id;
    }

    /** Spring Security authority name, usable as hasRole('ADMIN'). */
    public String authority() {
        return "ROLE_" + name();
    }

    public static Role fromId(Short id) {
        for (Role role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role id: " + id);
    }
}
