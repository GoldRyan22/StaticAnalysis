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
    int scopeLevel;
    
    public AliasEntry(String pointerName, int scopeLevel) {
        this.pointerName = pointerName;
        this.pointsTo = new HashSet<>();
        this.isDangling = false;
        this.isVoid = false;
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
        sb.append(String.join(", ", pointsTo));
        sb.append("]");
        if (isDangling) {
            sb.append(" DANGLING!");
        }
        if (isVoid) {
            sb.append(" VOID_PTR");
        }
        return sb.toString();
    }
}

// Alias Table - array of lists approach for pointer analysis
class AliasTable {
    private Map<String, AliasEntry> aliases;
    private List<String> danglingPointers;
    private List<String> voidPointerCalls;
    
    public AliasTable() {
        this.aliases = new HashMap<>();
        this.danglingPointers = new ArrayList<>();
        this.voidPointerCalls = new ArrayList<>();
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
    }
    
    public void removeAlias(String pointerName, String targetName) {
        if (aliases.containsKey(pointerName)) {
            aliases.get(pointerName).removeAlias(targetName);
            if (aliases.get(pointerName).isDangling) {
                danglingPointers.add(pointerName);
            }
        }
    }
    
    public void markVoidPointer(String pointerName) {
        if (aliases.containsKey(pointerName)) {
            aliases.get(pointerName).isVoid = true;
        }
    }
    
    public void checkDereference(String pointerName, int line) {
        if (aliases.containsKey(pointerName)) {
            AliasEntry entry = aliases.get(pointerName);
            if (entry.isDangling) {
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
    
    public List<String> getDanglingPointers() {
        return danglingPointers;
    }
    
    public List<String> getVoidPointerCalls() {
        return voidPointerCalls;
    }
    
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
            for (String ptr : danglingPointers) {
                System.out.println("  - " + ptr);
            }
        }
        
        if (!voidPointerCalls.isEmpty()) {
            System.out.println("\n⚠ VOID POINTER DEREFERENCES:");
            for (String call : voidPointerCalls) {
                System.out.println("  - " + call);
            }
        }
    }
}
