package com.hrm.system.enumm;

public enum DocumentStatus {

    ACTIVE,     // Document is valid and accessible
    EXPIRED,    // Document has passed its expiry date
    ARCHIVED,   // Manually archived / no longer in use
    DELETED     // Soft-deleted
}
