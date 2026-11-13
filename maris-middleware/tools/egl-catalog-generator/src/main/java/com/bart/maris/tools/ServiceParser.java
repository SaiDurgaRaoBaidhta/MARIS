package com.bart.maris.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a MARIS_RUI_Services *Service.egl wrapper. Reads only. */
final class ServiceParser {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);
    private static final Pattern SERVICE = Pattern.compile("\\bservice\\s+(\\w+)");
    private static final Pattern FUNCTION = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern CALL = Pattern.compile("\\bcall\\s+\"(\\w+)\"");

    record Parsed(String pkg, String service, List<String> explicitImports, List<String> wildcardImports,
                  List<Function> functions) {}

    record Function(String name, String program, List<String[]> params) {}   // params: {type, name}

    static Parsed parse(String raw) {
        String src = EglText.stripComments(raw);
        String pkg = RecordParser.packageOf(src);
        List<String> explicit = new ArrayList<>(), wildcard = new ArrayList<>();
        Matcher im = IMPORT.matcher(src);
        while (im.find()) (im.group(2) != null ? wildcard : explicit).add(im.group(1));
        Matcher sm = SERVICE.matcher(src);
        String service = sm.find() ? sm.group(1) : null;
        List<Function> functions = new ArrayList<>();
        Matcher fm = FUNCTION.matcher(src);
        List<int[]> spans = new ArrayList<>();
        while (fm.find()) spans.add(new int[] {fm.start(), fm.end()});
        fm.reset();
        int idx = 0;
        while (fm.find()) {
            int bodyEnd = idx + 1 < spans.size() ? spans.get(idx + 1)[0] : src.length();
            Matcher cm = CALL.matcher(src.substring(fm.end(), bodyEnd));
            List<String[]> params = new ArrayList<>();
            for (String p : fm.group(2).split(",")) {
                String[] tn = p.trim().split("\\s+");
                if (tn.length == 2) params.add(tn);
            }
            functions.add(new Function(fm.group(1), cm.find() ? cm.group(1) : null, params));
            idx++;
        }
        return new Parsed(pkg, service, explicit, wildcard, functions);
    }

    /** Resolves a parameter type to a qualified record name using imports, then the own package, then wildcards. */
    static String resolve(String type, Parsed p, Map<String, RecordDef> index, Map<String, List<String>> byName) {
        for (String imp : p.explicitImports()) {
            if (imp.endsWith("." + type) && index.containsKey(imp)) return imp;
        }
        if (index.containsKey(p.pkg() + "." + type)) return p.pkg() + "." + type;
        for (String w : p.wildcardImports()) {
            if (index.containsKey(w + "." + type)) return w + "." + type;
        }
        List<String> any = byName.get(type);
        return any != null && any.size() == 1 ? any.get(0) : null;
    }
}
