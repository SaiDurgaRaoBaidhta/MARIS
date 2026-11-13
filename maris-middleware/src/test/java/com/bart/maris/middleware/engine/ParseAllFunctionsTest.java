package com.bart.maris.middleware.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.egl.Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every statement group of every old screen parses. */
class ParseAllFunctionsTest {
    @Test
    void parsesEveryFunctionFile() throws Exception {
        List<String> errors = new ArrayList<>();
        int functions = 0;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/egl-screens/functions"))) {
            for (Path f : files.sorted().toList()) {
                try {
                    List<Ast.Function> fns = Parser.parseFile(Files.readString(f, StandardCharsets.ISO_8859_1), f.getFileName().toString());
                    functions += fns.size();
                } catch (RuntimeException e) {
                    errors.add(e.getMessage());
                }
            }
        }
        System.out.println("functions parsed: " + functions);
        assertThat(errors).isEmpty();
        assertThat(functions).isGreaterThan(2300);
    }
}
