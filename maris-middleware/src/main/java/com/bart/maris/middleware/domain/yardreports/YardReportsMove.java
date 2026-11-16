package com.bart.maris.middleware.domain.yardreports;

/** The outcome of moving a pseudo train's cars to another yard (old screen VHIC03I): moved, or not (no room), with the old message. */
public record YardReportsMove(boolean moved, String message) {}
