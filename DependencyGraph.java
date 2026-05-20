import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * DependencyGraph - builds a file-level include dependency graph for C source files.
 *
 * Shows the source file(s) and every library they depend on:
 *   - Local headers  (#include "foo.h")   — resolved recursively
 *   - System headers (#include <stdio.h>) — shown but not traversed
 *
 * Produces a Graphviz DOT file (and PNG if Graphviz is installed), styled
 * analogously to the CFG visualizations.
 *
 * Node colours:
 *   Blue (#4A90D9)  — source (.c) file
 *   Green (#7EC8A4) — local header found on disk
 *   Orange (#F0A070)— local header NOT found (still referenced)
 *   Grey  (#DDDDDD) — system header  (<…>)
 */
public class DependencyGraph {

    // ------------------------------------------------------------------ types

    enum NodeType { SOURCE, LOCAL_HEADER, SYSTEM_HEADER }

    static class DepNode {
        final String name;       // short display name (basename)
        final String fullPath;   // absolute path, or null for unresolved/system
        final NodeType type;
        // Direct includes preserved in insertion order for deterministic output
        final Set<String> includes = new LinkedHashSet<>();

        DepNode(String name, String fullPath, NodeType type) {
            this.name     = name;
            this.fullPath = fullPath;
            this.type     = type;
        }
    }

    // ------------------------------------------------------------------ state

    /** All nodes, keyed by short name (for deduplication). */
    private final Map<String, DepNode> nodes = new LinkedHashMap<>();
    /** Source-file root nodes, in the order they were added. */
    private final List<String> rootFiles = new ArrayList<>();
    /** Directories searched (in order) when resolving local headers. */
    private final List<String> searchDirs = new ArrayList<>();
    /** Guards against infinite recursion on circular includes. */
    private final Set<String> visitedPaths = new HashSet<>();

    private static final Pattern LOCAL_INC  = Pattern.compile("#include\\s+\"([^\"]+)\"");
    private static final Pattern SYSTEM_INC = Pattern.compile("#include\\s+<([^>]+)>");

    // --------------------------------------------------------- public API

    /** Register an extra directory to search when resolving local headers. */
    public void addSearchDirectory(String dir) {
        if (dir != null && !dir.isEmpty() && !searchDirs.contains(dir))
            searchDirs.add(dir);
    }

    /**
     * Add a C source file as a root node and recursively traverse its includes.
     * May be called multiple times (for folder-wide analysis).
     */
    public void addSourceFile(String filePath) {
        File f = new File(filePath).getAbsoluteFile();
        String name = f.getName();
        String dir  = f.getParent();

        // Put the file's own directory first so its local headers are found first
        if (!searchDirs.contains(dir)) searchDirs.add(0, dir);

        if (!nodes.containsKey(name)) {
            DepNode node = new DepNode(name, f.getAbsolutePath(), NodeType.SOURCE);
            nodes.put(name, node);
            parseIncludes(f.getAbsolutePath(), node);
        }
        if (!rootFiles.contains(name)) rootFiles.add(name);
    }

    // --------------------------------------------------------- analysis

    private void parseIncludes(String filePath, DepNode parent) {
        if (!visitedPaths.add(filePath)) return;   // already visited

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Local headers
                Matcher m = LOCAL_INC.matcher(trimmed);
                if (m.find()) {
                    String header    = m.group(1);
                    String shortName = new File(header).getName();
                    parent.includes.add(shortName);

                    if (!nodes.containsKey(shortName)) {
                        String resolved = findFile(header, new File(filePath).getParent());
                        DepNode child   = new DepNode(shortName, resolved, NodeType.LOCAL_HEADER);
                        nodes.put(shortName, child);
                        if (resolved != null) parseIncludes(resolved, child);
                    }
                    continue;
                }

                // System headers
                m = SYSTEM_INC.matcher(trimmed);
                if (m.find()) {
                    String header = m.group(1);
                    parent.includes.add(header);
                    if (!nodes.containsKey(header))
                        nodes.put(header, new DepNode(header, null, NodeType.SYSTEM_HEADER));
                }
            }
        } catch (IOException e) {
            // Silently skip unreadable / missing files
        }
    }

    /**
     * Try to locate a header file by searching:
     *   1. The directory of the file that issued the #include (localBase)
     *   2. Each registered search directory (and their include/ / src/ subdirs)
     */
    private String findFile(String headerName, String localBase) {
        // 1. Relative to the including file
        if (localBase != null) {
            File candidate = new File(localBase, headerName);
            if (candidate.exists()) return candidate.getAbsolutePath();
        }
        // 2. Registered search directories
        for (String dir : searchDirs) {
            for (String sub : new String[]{".", "include", "src"}) {
                File base      = sub.equals(".") ? new File(dir) : new File(dir, sub);
                File candidate = new File(base, headerName);
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    // --------------------------------------------------------- DOT generation

    /**
     * Build and return a Graphviz DOT string for the collected dependency graph.
     */
    public String toDot() {
        boolean multiRoot = rootFiles.size() > 1;
        String graphId = multiRoot
            ? "DependencyGraph"
            : sanitizeId(rootFiles.get(0));

        StringBuilder sb = new StringBuilder();
        sb.append("digraph dep_").append(graphId).append(" {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  splines=ortho;\n");
        sb.append("  node [fontname=\"Helvetica\", fontsize=11, margin=\"0.2,0.1\"];\n");
        sb.append("  edge [color=\"#666666\"];\n\n");

        // ---- legend cluster ----
        sb.append("  subgraph cluster_legend {\n");
        sb.append("    label=\"Legend\"; style=dashed; fontsize=10;\n");
        sb.append("    L1 [label=\"Source File\",   style=filled, fillcolor=\"#4A90D9\", fontcolor=white,   shape=box];\n");
        sb.append("    L2 [label=\"Local Header\",  style=filled, fillcolor=\"#7EC8A4\", fontcolor=black,   shape=box];\n");
        sb.append("    L3 [label=\"Not Found\",     style=filled, fillcolor=\"#F0A070\", fontcolor=black,   shape=box];\n");
        sb.append("    L4 [label=\"System Header\", style=filled, fillcolor=\"#DDDDDD\", fontcolor=\"#444444\", shape=box];\n");
        sb.append("    L1 -> L2 -> L3 -> L4 [style=invis];\n");
        sb.append("  }\n\n");

        // ---- system headers cluster (right side) ----
        boolean hasSystem = nodes.values().stream().anyMatch(n -> n.type == NodeType.SYSTEM_HEADER);
        if (hasSystem) {
            sb.append("  subgraph cluster_system {\n");
            sb.append("    label=\"System / Standard Library\"; style=filled; fillcolor=\"#F5F5F5\"; fontsize=10;\n");
            for (DepNode node : nodes.values()) {
                if (node.type != NodeType.SYSTEM_HEADER) continue;
                String id = sanitizeId(node.name);
                sb.append("    ").append(id)
                  .append(" [label=\"<").append(escape(node.name)).append(">\"")
                  .append(", style=filled, fillcolor=\"#DDDDDD\"")
                  .append(", fontcolor=\"#444444\", shape=box];\n");
            }
            sb.append("  }\n\n");
        }

        // ---- source + local header nodes ----
        for (DepNode node : nodes.values()) {
            if (node.type == NodeType.SYSTEM_HEADER) continue;
            String id = sanitizeId(node.name);
            String label, fillColor, fontColor;
            switch (node.type) {
                case SOURCE:
                    label     = escape(node.name);
                    fillColor = "#4A90D9";
                    fontColor = "white";
                    break;
                case LOCAL_HEADER:
                default:
                    label     = escape(node.name) + (node.fullPath == null ? "\\n(not found)" : "");
                    fillColor = node.fullPath != null ? "#7EC8A4" : "#F0A070";
                    fontColor = "black";
                    break;
            }
            sb.append("  ").append(id)
              .append(" [label=\"").append(label).append("\"")
              .append(", style=filled")
              .append(", fillcolor=\"").append(fillColor).append("\"")
              .append(", fontcolor=\"").append(fontColor).append("\"")
              .append(", shape=box];\n");
        }

        sb.append("\n");

        // ---- edges ----
        for (DepNode node : nodes.values()) {
            for (String dep : node.includes) {
                if (!nodes.containsKey(dep)) continue;
                NodeType depType = nodes.get(dep).type;
                String edgeAttrs = depType == NodeType.SYSTEM_HEADER
                    ? " [style=dashed, color=\"#AAAAAA\"]" : "";
                sb.append("  ").append(sanitizeId(node.name))
                  .append(" -> ").append(sanitizeId(dep))
                  .append(edgeAttrs).append(";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    // --------------------------------------------------------- text summary

    /**
     * Print a human-readable dependency summary to stdout.
     */
    public void printSummary() {
        int localCount  = (int) nodes.values().stream().filter(n -> n.type == NodeType.LOCAL_HEADER).count();
        int systemCount = (int) nodes.values().stream().filter(n -> n.type == NodeType.SYSTEM_HEADER).count();
        int notFound    = (int) nodes.values().stream().filter(n -> n.type == NodeType.LOCAL_HEADER && n.fullPath == null).count();

        System.out.println(String.format("  Source files   : %d", rootFiles.size()));
        System.out.println(String.format("  Local headers  : %d%s", localCount,
            notFound > 0 ? "  (" + notFound + " not found)" : ""));
        System.out.println(String.format("  System headers : %d", systemCount));
        System.out.println();

        // Per-file include listing
        System.out.println(String.format("  %-42s  %s", "File", "Direct includes"));
        System.out.println("  " + "-".repeat(80));
        for (DepNode node : nodes.values()) {
            if (node.includes.isEmpty()) continue;
            String tag = node.type == NodeType.SOURCE ? "[src]"
                       : node.type == NodeType.LOCAL_HEADER ? "[hdr]" : "[sys]";
            System.out.println(String.format("  %-6s %-36s  -> %s",
                tag, node.name, String.join(", ", node.includes)));
        }
    }

    // --------------------------------------------------------- static helpers

    /**
     * Generate the DOT file, try to render it to PNG, and return the DOT filename.
     * @param baseName   Base name for output files (without extension / path).
     */
    public static String generateFiles(DependencyGraph dg, String baseName) {
        String dotFile = baseName + ".dot";
        String pngFile = baseName + ".png";

        try (PrintWriter writer = new PrintWriter(dotFile)) {
            writer.print(dg.toDot());
            System.out.println("  Generated: " + dotFile);
        } catch (IOException e) {
            System.err.println("  Error writing " + dotFile + ": " + e.getMessage());
            return dotFile;
        }

        // Render to PNG via Graphviz
        try {
            ProcessBuilder pb = new ProcessBuilder("dot", "-Tpng", dotFile, "-o", pngFile);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("  Generated: " + pngFile);
            } else {
                System.err.println("  Warning: Graphviz exited with code " + exitCode +
                    " — PNG not generated for " + dotFile);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("  Warning: Could not generate PNG (is Graphviz installed?): " + e.getMessage());
        }

        return dotFile;
    }

    // --------------------------------------------------------- utilities

    private static String sanitizeId(String name) {
        return "n_" + name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
