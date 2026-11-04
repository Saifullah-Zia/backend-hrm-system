package com.hrm.system.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Builds page requests with a bounded size, so a client cannot ask for an unlimited page. */
public final class PageLimits {

    public static final int MAX_PAGE_SIZE = 1000;

    private PageLimits() {}

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }
}
