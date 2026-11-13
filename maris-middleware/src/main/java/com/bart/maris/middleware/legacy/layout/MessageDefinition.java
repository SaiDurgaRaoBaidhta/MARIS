package com.bart.maris.middleware.legacy.layout;

/** One entry of the XXM0IT1 message table: type I/W/E and text with ~1..~3 insert markers. */
public record MessageDefinition(String type, String text) {}
