import java.util.*;

// Symbol represents a variable, function, or type in the program
class Symbol {
    String name;
    String type;
    String kind; // "variable", "function", "parameter", "typedef"
    int scopeLevel;
    boolean isPointer;
    int pointerLevel; // 0 = not pointer, 1 = *, 2 = **, etc.
    ASTNode declaration;
    
    // For functions
    List<String> paramTypes;
    String returnType;
    
    // For tracking initialization
    boolean isInitialized;
    
    public Symbol(String name, String type, String kind, int scopeLevel) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.scopeLevel = scopeLevel;
        this.isPointer = type.contains("*");
        this.pointerLevel = countPointers(type);
        this.isInitialized = false;
        this.paramTypes = new ArrayList<>();
    }
    
    private int countPointers(String type) {
        int count = 0;
        for (char c : type.toCharArray()) {
            if (c == '*') count++;
        }
        return count;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-15s %-20s %-12s scope=%d", name, type, kind, scopeLevel));
        if (isPointer) {
            sb.append(String.format(" ptr_level=%d", pointerLevel));
        }
        if (kind.equals("function")) {
            sb.append(" params=(");
            sb.append(String.join(", ", paramTypes));
            sb.append(")");
        }
        return sb.toString();
    }
}

// Scope represents a lexical scope in the program
class Scope {
    int level;
    Map<String, Symbol> symbols;
    Scope parent;
    
    public Scope(int level, Scope parent) {
        this.level = level;
        this.parent = parent;
        this.symbols = new HashMap<>();
    }
    
    public void addSymbol(Symbol symbol) {
        symbols.put(symbol.name, symbol);
    }
    
    public Symbol lookup(String name) {
        if (symbols.containsKey(name)) {
            return symbols.get(name);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return null;
    }
    
    public boolean hasSymbol(String name) {
        return symbols.containsKey(name);
    }
}

// Symbol Table manages all scopes and symbols
class SymbolTable {
    private Scope currentScope;
    private Scope globalScope;
    private int currentLevel;
    private List<Symbol> allSymbols;
    
    public SymbolTable() {
        currentLevel = 0;
        globalScope = new Scope(0, null);
        currentScope = globalScope;
        allSymbols = new ArrayList<>();
    }
    
    public void enterScope() {
        currentLevel++;
        currentScope = new Scope(currentLevel, currentScope);
    }
    
    public void exitScope() {
        if (currentScope.parent != null) {
            currentScope = currentScope.parent;
            currentLevel--;
        }
    }
    
    public void addSymbol(String name, String type, String kind) {
        Symbol symbol = new Symbol(name, type, kind, currentLevel);
        currentScope.addSymbol(symbol);
        allSymbols.add(symbol);
    }
    
    public void addSymbol(Symbol symbol) {
        currentScope.addSymbol(symbol);
        allSymbols.add(symbol);
    }
    
    public Symbol lookup(String name) {
        return currentScope.lookup(name);
    }
    
    public Symbol lookupInCurrentScope(String name) {
        return currentScope.hasSymbol(name) ? currentScope.symbols.get(name) : null;
    }
    
    /**
     * Look up a symbol specifically by kind="typedef", walking the scope chain
     * but skipping non-typedef symbols with the same name. This avoids the
     * situation where a local variable named "list" shadows the typedef "list".
     */
    public Symbol lookupTypedef(String name) {
        Scope scope = currentScope;
        while (scope != null) {
            if (scope.symbols.containsKey(name) && scope.symbols.get(name).kind.equals("typedef")) {
                return scope.symbols.get(name);
            }
            scope = scope.parent;
        }
        return null;
    }
    
    public boolean isDeclared(String name) {
        return lookup(name) != null;
    }
    
    public int getCurrentLevel() {
        return currentLevel;
    }
    
    public List<Symbol> getAllSymbols() {
        return allSymbols;
    }
    
    public void print() {
        System.out.println("\n=== Symbol Table ===");
        System.out.println("Name            Type                 Kind         Info");
        System.out.println("----------------------------------------------------------------");
        for (Symbol symbol : allSymbols) {
            System.out.println(symbol);
        }
    }
}

// Alias Table Entry - tracks pointer aliases
class AliasEntry {
    String pointerName;
    Set<String> pointsTo; // Set of variable names this pointer might point to
    boolean isDangling;
    boolean isVoid;
    boolean isFreed;           // pointer was explicitly free()'d
    boolean isHeapAllocated;   // pointer owns heap memory (malloc/calloc/...)
    int scopeLevel;
    
    public AliasEntry(String pointerName, int scopeLevel) {
        this.pointerName = pointerName;
        this.pointsTo = new HashSet<>();
        this.isDangling = false;
        this.isVoid = false;
        this.isFreed = false;
        this.isHeapAllocated = false;
        this.scopeLevel = scopeLevel;
    }
    
    public void addAlias(String varName) {
        pointsTo.add(varName);
    }
    
    public void removeAlias(String varName) {
        pointsTo.remove(varName);
        if (pointsTo.isEmpty()) {
            isDangling = true;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-15s -> [", pointerName));
        if (isHeapAllocated && pointsTo.isEmpty()) {
            sb.append("heap");
        } else {
            sb.append(String.join(", ", pointsTo));
        }
        sb.append("]");
        if (isFreed)      sb.append(" FREED!");
        else if (isDangling) sb.append(" DANGLING!");
        if (isVoid)       sb.append(" VOID_PTR");
        if (isHeapAllocated && !isFreed) sb.append(" heap-owned");
        return sb.toString();
    }
}

// Alias Table - array of lists approach for pointer analysis
class AliasTable {
    private Map<String, AliasEntry> aliases;
    private List<String> danglingPointers;
    private List<String> voidPointerCalls;
    private List<String> useAfterFree;   // use-after-free violations
    private List<String> doubleFree;     // double-free violations
    private List<String> memoryLeaks;    // leaked heap allocations
    
    public AliasTable() {
        this.aliases = new HashMap<>();
        this.danglingPointers = new ArrayList<>();
        this.voidPointerCalls = new ArrayList<>();
        this.useAfterFree = new ArrayList<>();
        this.doubleFree = new ArrayList<>();
        this.memoryLeaks = new ArrayList<>();
    }
    
    public void addPointer(String pointerName, int scopeLevel) {
        if (!aliases.containsKey(pointerName)) {
            aliases.put(pointerName, new AliasEntry(pointerName, scopeLevel));
        }
    }
    
    public void addAlias(String pointerName, String targetName) {
        if (!aliases.containsKey(pointerName)) {
            aliases.put(pointerName, new AliasEntry(pointerName, 0));
        }
        aliases.get(pointerName).addAlias(targetName);
        // Reassigning clears freed/dangling state — pointer now points somewhere new
        aliases.get(pointerName).isDangling = false;
        aliases.get(pointerName).isFreed = false;
    }
    
    public void removeAlias(String pointerName, String targetName) {
        if (aliases.containsKey(pointerName)) {
            aliases.get(pointerName).removeAlias(targetName);
            if (aliases.get(pointerName).isDangling) {
                danglingPointers.add(pointerName);
            }
        }
    }
    
    /** Mark pointer as owning heap memory (result of malloc/calloc/etc.) */
    public void markHeapAllocated(String pointerName, int scopeLevel) {
        if (!aliases.containsKey(pointerName)) {
            aliases.put(pointerName, new AliasEntry(pointerName, scopeLevel));
        }
        AliasEntry entry = aliases.get(pointerName);
        entry.isHeapAllocated = true;
        entry.isFreed = false;
        entry.isDangling = false;
        entry.pointsTo.clear(); // now points to heap, not a named var
    }
    
    /** Called when free(ptr) — marks pointer as freed/dangling. */
    public boolean markFreed(String pointerName) {
        if (!aliases.containsKey(pointerName)) {
            aliases.put(pointerName, new AliasEntry(pointerName, 0));
        }
        AliasEntry entry = aliases.get(pointerName);
        if (entry.isFreed) {
            doubleFree.add(pointerName);
            return false; // double-free detected
        }
        entry.isFreed = true;
        entry.isDangling = true;
        entry.isHeapAllocated = false; // no longer owns heap memory
        entry.pointsTo.clear();
        return true;
    }
    
    /** Returns true if pointer was already freed. */
    public boolean isFreed(String pointerName) {
        return aliases.containsKey(pointerName) && aliases.get(pointerName).isFreed;
    }
    
    /** Returns true if pointer owns heap memory (malloc'd but not freed). */
    public boolean isHeapAllocated(String pointerName) {
        return aliases.containsKey(pointerName) && aliases.get(pointerName).isHeapAllocated;
    }
    
    /** Returns a snapshot of all heap-owning (unfreed) pointer names. */
    public Set<String> getHeapAllocated() {
        Set<String> result = new HashSet<>();
        for (AliasEntry entry : aliases.values()) {
            if (entry.isHeapAllocated) result.add(entry.pointerName);
        }
        return result;
    }
    
    /** Record a memory leak for display. */
    public void recordLeak(String description) {
        memoryLeaks.add(description);
    }
    
    /** Record a use-after-free for display. */
    public void recordUseAfterFree(String description) {
        useAfterFree.add(description);
    }
    
    public void markVoidPointer(String pointerName) {
        if (aliases.containsKey(pointerName)) {
            aliases.get(pointerName).isVoid = true;
        }
    }
    
    public void checkDereference(String pointerName, int line) {
        if (aliases.containsKey(pointerName)) {
            AliasEntry entry = aliases.get(pointerName);
            if (entry.isFreed) {
                String msg = "'" + pointerName + "'";
                useAfterFree.add(msg);
            } else if (entry.isDangling) {
                danglingPointers.add(pointerName + " at line " + line);
            }
            if (entry.isVoid) {
                voidPointerCalls.add(pointerName + " at line " + line);
            }
        }
    }
    
    public Set<String> getPointsToSet(String pointerName) {
        if (aliases.containsKey(pointerName)) {
            return aliases.get(pointerName).pointsTo;
        }
        return new HashSet<>();
    }
    
    public List<String> getDanglingPointers() { return danglingPointers; }
    public List<String> getVoidPointerCalls() { return voidPointerCalls; }
    public List<String> getUseAfterFree()     { return useAfterFree; }
    public List<String> getDoubleFree()       { return doubleFree; }
    public List<String> getMemoryLeaks()      { return memoryLeaks; }
    
    public void print() {
        System.out.println("\n=== Alias Table ===");
        if (aliases.isEmpty()) {
            System.out.println("No pointers found.");
            return;
        }
        
        System.out.println("Pointer         Points-to Set");
        System.out.println("----------------------------------------");
        for (AliasEntry entry : aliases.values()) {
            System.out.println(entry);
        }
        
        if (!danglingPointers.isEmpty()) {
            System.out.println("\n⚠ DANGLING POINTERS DETECTED:");
            for (String ptr : danglingPointers) System.out.println("  - " + ptr);
        }
        if (!useAfterFree.isEmpty()) {
            System.out.println("\n⚠ USE-AFTER-FREE DETECTED:");
            for (String s : useAfterFree) System.out.println("  - " + s);
        }
        if (!doubleFree.isEmpty()) {
            System.out.println("\n⚠ DOUBLE-FREE DETECTED:");
            for (String s : doubleFree) System.out.println("  - " + s);
        }
        if (!memoryLeaks.isEmpty()) {
            System.out.println("\n⚠ MEMORY LEAKS DETECTED:");
            for (String s : memoryLeaks) System.out.println("  - " + s);
        }
        if (!voidPointerCalls.isEmpty()) {
            System.out.println("\n⚠ VOID POINTER DEREFERENCES:");
            for (String call : voidPointerCalls) System.out.println("  - " + call);
        }
    }
}
