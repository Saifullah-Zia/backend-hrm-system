package com.hrm.system.enumm;

public enum ResignationStatus {
    PENDING,        // Submitted, awaiting HR/manager review
    APPROVED,       // Approved by HR
    REJECTED,       // Rejected (e.g. withdrawal accepted)
    WITHDRAWN,      // Employee withdrew the resignation
    COMPLETED       // Last working day passed, fully offboarded
}