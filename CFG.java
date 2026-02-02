import java.util.*;

// Control Flow Graph Node
class CFGNode {
    private static int nextId = 0;
    int id;
    String label;
    List<CFGNode> successors;
    List<CFGNode> predecessors;
    ASTNode astNode;
    
    public CFGNode(String label) {
        this.id = nextId++;
        this.label = label;
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
    }
    
    public CFGNode(String label, ASTNode astNode) {
        this(label);
        this.astNode = astNode;
    }
    
    public void addSuccessor(CFGNode node) {
        if (!successors.contains(node)) {
            successors.add(node);
            node.predecessors.add(this);
        }
    }
    
    @Override
    public String toString() {
        return "Node" + id + ": " + label;
    }
    
    public static void resetIdCounter() {
        nextId = 0;
    }
}

// Control Flow Graph for a function
class ControlFlowGraph {
    String functionName;
    CFGNode entry;
    CFGNode exit;
    List<CFGNode> allNodes;
    
    public ControlFlowGraph(String functionName) {
        this.functionName = functionName;
        this.allNodes = new ArrayList<>();
        this.entry = new CFGNode("ENTRY");
        this.exit = new CFGNode("EXIT");
        allNodes.add(entry);
        allNodes.add(exit);
    }
    
    public void addNode(CFGNode node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
        }
    }
    
    public int calculateCyclomaticComplexity() {
        // M = E - N + 2 (for strongly connected graph)
        // Or M = number of decision points + 1
        int edges = 0;
        int nodes = allNodes.size();
        
        for (CFGNode node : allNodes) {
            edges += node.successors.size();
        }
        
        // For a single connected component
        int complexity = edges - nodes + 2;
        return complexity;
    }
    
    public int calculateDecisionPoints() {
        int decisions = 0;
        for (CFGNode node : allNodes) {
            // Decision points are nodes with more than one successor
            if (node.successors.size() > 1) {
                decisions++;
            }
        }
        return decisions + 1; // +1 for the entry point
    }
    
    public void printCFG() {
        System.out.println("\n=== Control Flow Graph for: " + functionName + " ===");
        System.out.println("Nodes: " + allNodes.size() + ", Entry: Node" + entry.id + ", Exit: Node" + exit.id);
        
        for (CFGNode node : allNodes) {
            System.out.print("  " + node);
            if (!node.successors.isEmpty()) {
                System.out.print(" -> [");
                for (int i = 0; i < node.successors.size(); i++) {
                    System.out.print("Node" + node.successors.get(i).id);
                    if (i < node.successors.size() - 1) System.out.print(", ");
                }
                System.out.print("]");
            }
            System.out.println();
        }
        
        int complexity = calculateCyclomaticComplexity();
        int decisions = calculateDecisionPoints();
        System.out.println("Cyclomatic Complexity: " + complexity + " (via edges-nodes+2)");
        System.out.println("Cyclomatic Complexity: " + decisions + " (via decision points)");
    }
    
    public String toDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CFG_").append(functionName.replace(" ", "_")).append(" {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  node [shape=box];\n");
        
        for (CFGNode node : allNodes) {
            String shape = "box";
            if (node == entry) shape = "ellipse";
            else if (node == exit) shape = "ellipse";
            else if (node.successors.size() > 1) shape = "diamond";
            
            sb.append("  Node").append(node.id).append(" [label=\"").append(node.label.replace("\"", "\\\"")).append("\", shape=").append(shape).append("];\n");
        }
        
        for (CFGNode node : allNodes) {
            for (CFGNode succ : node.successors) {
                sb.append("  Node").append(node.id).append(" -> Node").append(succ.id).append(";\n");
            }
        }
        
        sb.append("}\n");
        return sb.toString();
    }
}

// CFG Builder - constructs CFG from AST
class CFGBuilder {
    
    public static List<ControlFlowGraph> buildCFGsFromProgram(ProgramNode program) {
        List<ControlFlowGraph> cfgs = new ArrayList<>();
        CFGNode.resetIdCounter();
        
        for (ASTNode node : program.declarations) {
            if (node instanceof FuncDeclNode) {
                FuncDeclNode func = (FuncDeclNode) node;
                ControlFlowGraph cfg = buildCFG(func);
                cfgs.add(cfg);
            }
        }
        
        return cfgs;
    }
    
    private static ControlFlowGraph buildCFG(FuncDeclNode func) {
        ControlFlowGraph cfg = new ControlFlowGraph(func.name);
        
        if (func.body != null) {
            CFGNode lastNode = buildCFGFromNode(func.body, cfg, cfg.entry);
            if (lastNode != null) {
                lastNode.addSuccessor(cfg.exit);
            }
        } else {
            cfg.entry.addSuccessor(cfg.exit);
        }
        
        return cfg;
    }
    
    // Returns the last node of the constructed CFG fragment
    private static CFGNode buildCFGFromNode(ASTNode node, ControlFlowGraph cfg, CFGNode previous) {
        if (node == null) return previous;
        
        if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            CFGNode current = previous;
            
            for (ASTNode stmt : block.statements) {
                current = buildCFGFromNode(stmt, cfg, current);
            }
            
            return current;
        }
        else if (node instanceof IfStmtNode) {
            IfStmtNode ifStmt = (IfStmtNode) node;
            
            // Create condition node
            CFGNode condNode = new CFGNode("if (" + ifStmt.condition.toString(0) + ")", ifStmt);
            cfg.addNode(condNode);
            previous.addSuccessor(condNode);
            
            // Create merge node (where both branches converge)
            CFGNode mergeNode = new CFGNode("merge", null);
            cfg.addNode(mergeNode);
            
            // Then branch
            CFGNode thenLast = buildCFGFromNode(ifStmt.thenBranch, cfg, condNode);
            if (thenLast != null) {
                thenLast.addSuccessor(mergeNode);
            }
            
            // Else branch (if exists)
            if (ifStmt.elseBranch != null) {
                CFGNode elseLast = buildCFGFromNode(ifStmt.elseBranch, cfg, condNode);
                if (elseLast != null) {
                    elseLast.addSuccessor(mergeNode);
                }
            } else {
                // No else branch, condition goes directly to merge
                condNode.addSuccessor(mergeNode);
            }
            
            return mergeNode;
        }
        else if (node instanceof WhileStmtNode) {
            WhileStmtNode whileStmt = (WhileStmtNode) node;
            
            // Create condition node
            CFGNode condNode = new CFGNode("while (" + whileStmt.condition.toString(0) + ")", whileStmt);
            cfg.addNode(condNode);
            previous.addSuccessor(condNode);
            
            // Create after-loop node
            CFGNode afterLoop = new CFGNode("after-loop", null);
            cfg.addNode(afterLoop);
            
            // Body
            CFGNode bodyLast = buildCFGFromNode(whileStmt.body, cfg, condNode);
            if (bodyLast != null) {
                bodyLast.addSuccessor(condNode); // Loop back
            }
            
            // Exit condition
            condNode.addSuccessor(afterLoop);
            
            return afterLoop;
        }
        else if (node instanceof ReturnStmtNode) {
            ReturnStmtNode retStmt = (ReturnStmtNode) node;
            CFGNode retNode = new CFGNode("return " + (retStmt.expr != null ? retStmt.expr.toString(0) : ""), retStmt);
            cfg.addNode(retNode);
            previous.addSuccessor(retNode);
            retNode.addSuccessor(cfg.exit);
            return null; // No continuation after return
        }
        else {
            // Regular statement (assignment, expression, etc.)
            String label = node.toString(0).trim();
            if (label.length() > 50) {
                label = label.substring(0, 47) + "...";
            }
            CFGNode stmtNode = new CFGNode(label, node);
            cfg.addNode(stmtNode);
            previous.addSuccessor(stmtNode);
            return stmtNode;
        }
    }
}
