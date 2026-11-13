package com.bart.maris.middleware.support;

import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import org.springframework.core.io.DefaultResourceLoader;

/** The real generated catalog, loaded once for unit tests. */
public final class TestCatalog {
    private static LegacyCatalog catalog;

    private TestCatalog() {}

    public static synchronized LegacyCatalog get() {
        if (catalog == null) catalog = LegacyCatalog.load(new DefaultResourceLoader(), "classpath:legacy-catalog/");
        return catalog;
    }
}
