package com.bart.maris.middleware.engine.screen;

import java.util.List;

/** ExecutablePart { callFunction = FN }: "execute" runs the statement group, then its queued field events, then "hasExecuted". */
public final class ExecPart extends Part {
    final String function;
    private final ScreenInstance screen;

    ExecPart(String name, String function, ScreenInstance screen) {
        super(name, screen.session);
        this.function = function;
        this.screen = screen;
    }

    @Override
    public Object get(String attr) {
        return null;
    }

    @Override
    public void set(String attr, Object value) {
    }

    @Override
    public Object perform(String action, List<Object> args, boolean invokedFromEvent) {
        if (action.equals("execute") || action.equals("vaExecute") || action.equals("executeWithArguments_")) {
            Object r = screen.execute(function);
            signal("hasExecuted");
            return r;
        }
        return null;
    }
}
