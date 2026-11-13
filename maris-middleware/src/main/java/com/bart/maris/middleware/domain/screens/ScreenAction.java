package com.bart.maris.middleware.domain.screens;

import com.bart.maris.middleware.security.MarisUser;
import java.util.Map;

/**
 * One push button of one MARIS screen, translated from the old screen's EGL logic
 * (MARIS_RUI/EGLSource/&lt;module&gt;/RUIFunctions.egl and &lt;SCREEN&gt;Connections.egl).
 * Each implementation is a Spring bean; {@link ScreenActionRegistry} finds it by screen and control name.
 */
public interface ScreenAction {
    /** Screen code, e.g. {@code VHIE01I}. */
    String screen();

    /** The button's control name in the screen layout, e.g. {@code Push_Button3}. */
    String control();

    /**
     * @param fields the screen's fields by name (data-w), as the UI sends them
     */
    ActionResult run(Map<String, String> fields, MarisUser user);
}
