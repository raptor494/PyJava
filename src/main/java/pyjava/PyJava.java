package pyjava;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.json.simple.parser.JSONParser;

import pyjava.parser.PyJavaLexer;
import pyjava.parser.PyJavaParser;
import pyjava.tree.LazyAppendable.AppendFunction;
import pyjava.tree.Transpiler;

public class PyJava {
    private static final Pattern ESCAPE_CHARS_REGEX = Pattern.compile("\\[|\\]|\\\\");
    private static final Pattern SPECIAL_CHARS_REGEX = Pattern.compile("[*?]");

    public static void main(String[] args) throws Exception {
        final var fs = FileSystems.getDefault();

        Path configFile = null;
        Path outputDir = null;
        var optionsBuilder = PyJavaOptions.builder();
        var inputs = new ArrayList<Path>();
        var include = new ArrayList<PathMatcher>();
        var exclude = new ArrayList<PathMatcher>();
        
        parseArgs: {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
            matchArg:
                switch (arg) {
                    case "--config", "-c" -> {
                        if (configFile != null) {
                            error("Error: duplicate argument --config");
                            return;
                        }
                        i++;
                        if (i == args.length) {
                            error("Error: missing path after " + arg);
                            return;
                        }
                        configFile = fs.getPath(args[i]);
                    }
                    case "--output", "-o" -> {
                        if (outputDir != null) {
                            error("Error: duplicate argument --output");
                            return;
                        }
                        i++;
                        if (i == args.length) {
                            error("Error: missing path after " + arg);
                            return;
                        }
                        outputDir = fs.getPath(args[i]);
                    }
                    case "--help", "-help", "-h", "--?", "-?", "/?" -> {
                        printHelp();
                        return;
                    }
                    case "--" -> {
                        break parseArgs;
                    }
                    default -> {
                        for (var option : new String[] {"--config=", "-c"}) {
                            if (arg.startsWith(option)) {
                                if (configFile != null) {
                                    error("Error: duplicate argument --config");
                                    return;
                                }
                                configFile = fs.getPath(arg.substring(option.length()));
                                break matchArg;
                            }
                        }
                        for (var option : new String[] {"--output=", "-o"}) {
                            if (arg.startsWith(option)) {
                                if (outputDir != null) {
                                    error("Error: duplicate argument --output");
                                    return;
                                }
                                outputDir = fs.getPath(arg.substring(option.length()));
                                break matchArg;
                            }
                        }
                        if (arg.startsWith("-")) {
                            error("Error: unknown option "+arg);
                            return;
                        }
                        if (SPECIAL_CHARS_REGEX.matcher(arg).find()) {
                            include.add(fs.getPathMatcher(getGlob(arg)));
                        } else {
                            var path = fs.getPath(arg).normalize();
                            if (!Files.exists(path)) {
                                error("Error: the system cannot find the path specified: "+arg);
                                return;
                            }
                            boolean duplicate = false;
                            for (var oldInput : inputs) {
                                if (Files.isSameFile(oldInput, path)) {
                                    duplicate = true;
                                }
                            }
                            if (!duplicate) {
                                inputs.add(path);
                            }
                        }
                    }
                }
            }
        } /* parseArgs */

        if (outputDir != null) {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            } else if (!Files.isDirectory(outputDir)) {
                error("Error: not a directory: "+outputDir);
                return;
            }
        } else {
            outputDir = fs.getPath("./");
        }

        if (configFile == null) {
            configFile = fs.getPath("pyjavaconfig.json");
            if (!Files.exists(configFile)) {
                configFile = null;
            } else if (!Files.isRegularFile(configFile)) {
                error("Error: not a file: "+configFile);
                return;
            }
        } else {
            if (!Files.exists(configFile)) {
                error("Error: the system cannot find the path specified: "+configFile);
                return;
            }
            if (!Files.isRegularFile(configFile)) {
                error("Error: not a file: "+configFile);
                return;
            }
        }
        if (configFile != null) {
            var parser = new JSONParser();
            Object parsedJSON;
            try (var reader = Files.newBufferedReader(configFile)) {
                parsedJSON = parser.parse(reader);
            }
            if (parsedJSON instanceof Map<?,?> map) {
                @SuppressWarnings("unchecked")
                var jsonObj = (Map<String, Object>)map;
                if (jsonObj.containsKey("requireSemicolons")) {
                    optionsBuilder.requireSemicolons(getBoolean(jsonObj, "requireSemicolons"));
                }
                if (jsonObj.containsKey("allowColonSimpleBlocks")) {
                    optionsBuilder.allowColonSimpleBlocks(getBoolean(jsonObj, "allowColonSimpleBlocks"));
                }
                if (jsonObj.containsKey("allowNoColonSimpleBlocks")) {
                    optionsBuilder.allowNoColonSimpleBlocks(getBoolean(jsonObj, "allowNoColonSimpleBlocks"));
                }
                if (jsonObj.containsKey("forceParensInStatements")) {
                    optionsBuilder.forceParensInStatements(getBoolean(jsonObj, "forceParensInStatements"));
                }
                if (jsonObj.containsKey("files")) {
                    var files = getObject(jsonObj, "files");
                    if (files.containsKey("include")) {
                        for (var glob : getStringArray(files, "include")) {
                            include.add(fs.getPathMatcher(getGlob(glob)));
                        }
                    }
                    if (files.containsKey("exclude")) {
                        for (var glob : getStringArray(files, "exclude")) {
                            exclude.add(fs.getPathMatcher(getGlob(glob)));
                        }
                    }
                }
            } else {
                error("Error: invalid config file: expected top-level JSON to be an object");
                return;
            }
        } else if (inputs.isEmpty() && include.isEmpty() && exclude.isEmpty()) {
            printHelp();
            return;
        }

        if (inputs.isEmpty()) {
            inputs.add(fs.getPath("./"));
        }
        if (include.isEmpty()) {
            include.add(fs.getPathMatcher("glob:**.pyj"));
        }

        final var options = optionsBuilder.build();

        class Visitor extends SimpleFileVisitor<Path> {
            private final Path outputDir;
            private final Path parentDir;

            public Visitor(Path outputDir, Path parentDir) {
                this.outputDir = outputDir;
                this.parentDir = parentDir.toAbsolutePath();
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                for (var excludeMatcher : exclude) {
                    if (excludeMatcher.matches(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                for (var includeMatcher : include) {
                    if (includeMatcher.matches(file)) {
                        for (var excludeMatcher : exclude) {
                            if (excludeMatcher.matches(file)) {
                                return FileVisitResult.CONTINUE;
                            }
                        }
                        String name = file.getFileName().toString();
                        int i = name.indexOf('.');
                        if (i == -1) {
                            name += ".py";
                        } else {
                            name = name.substring(0, i) + ".py";
                        }
                        Path outputFile = outputDir.resolve(parentDir.relativize(file.toAbsolutePath()).resolveSibling(name));
                        processFile(file, outputFile, options);
                        return FileVisitResult.CONTINUE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        };
        final var noParentDirVisitor = new Visitor(outputDir, fs.getPath(""));

        for (var input : inputs) {
            if (Files.isDirectory(input)) {
                Files.walkFileTree(input, new Visitor(outputDir, input.normalize()));
            } else {
                noParentDirVisitor.visitFile(input, null);
            }
        }
    }

    private static void processFile(Path file, Path output, PyJavaOptions options) {
        var transpiler = new Transpiler();
        try {
            var source = CharStreams.fromPath(file);
            var lexer = new PyJavaLexer(source);
            var tokens = new CommonTokenStream(lexer);
            var parser = new PyJavaParser(tokens, options);
            var result = parser.file();
            result.accept(transpiler);
        } catch (Exception e) {
            System.err.println("Failed to process file "+file+':');
            e.printStackTrace(System.err);
            return;
        }
        try {
            Files.createDirectories(output.getParent());
            try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                transpiler.appendTo(AppendFunction.wrap(writer));
            }
        } catch (Exception e) {
            System.err.println("Failed to write to file "+output+':');
            e.printStackTrace(System.err);
            return;
        }
    }

    private static void error(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private static void printHelp() {
        System.out.print("""
        java -jar PyJava.jar [OPTIONS AND INPUTS...] [--] INPUTS...

        OPTIONS:
          --config FILE, -c FILE    The config file to use. Default is "pyjavaconfig.json".
          --output DIR, -o DIR      Output directory to use. Folder structure is kept intact. Default is ".".
          --                        Everything after this will be treated as an input.

        INPUTS  A list of files/glob patterns to run over. Default is "**.pyj".
        """);
        System.exit(0);
    }

    private static String getGlob(String arg) {
        return ESCAPE_CHARS_REGEX.matcher(arg).replaceAll("\\\\$0");
    }

    private static boolean getBoolean(Map<String,Object> jsonObj, String key) {
        var obj = jsonObj.get(key);
        if (obj instanceof Boolean b) {
            return b;
        }
        error("Error: invalid config file: expected key "+key+" to be a boolean");
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> getObject(Map<String,Object> jsonObj, String key) {
        var obj = jsonObj.get(key);
        if (obj instanceof Map<?,?>) {
            return (Map<String,Object>)obj;
        }
        error("Error: invalid config file: expected key "+key+" to be an object");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringArray(Map<String,Object> jsonObj, String key) {
        var obj = jsonObj.get(key);
        if (obj instanceof List<?> arr) {
            if (arr.stream().allMatch(String.class::isInstance)) {
                return (List<String>)arr;
            }
        }
        error("Error: invalid config file: expected key "+key+" to be a string array");
        return null;
    }
}
