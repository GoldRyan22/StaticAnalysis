#!/usr/bin/env python3
"""
Static Analysis Tool - Modern GUI Interface
IntelliJ-inspired dark theme using CustomTkinter
"""

import os
import sys
import json
import subprocess
import threading
import glob
import re
import shutil
import tempfile
from pathlib import Path
from datetime import datetime

import customtkinter as ctk
from tkinter import filedialog, messagebox
from PIL import Image, ImageTk

# Set appearance mode and color theme
ctk.set_appearance_mode("dark")  # Modes: "System", "Dark", "Light"
ctk.set_default_color_theme("blue")  # Themes: "blue", "green", "dark-blue"

# IntelliJ-inspired color palette
COLORS = {
    "bg_dark": "#2B2D30",
    "bg_medium": "#3C3F41",
    "bg_light": "#45494A",
    "accent": "#4A88C7",
    "accent_hover": "#5B99D8",
    "text": "#BABABA",
    "text_bright": "#FFFFFF",
    "success": "#499C54",
    "warning": "#BE9117",
    "error": "#C75450",
    "border": "#323232",
    "selection": "#214283",
}


# ─── C source function extraction helpers ────────────────────────────────────

def _extract_c_functions(filepath):
    """Return a list of (name, body) tuples extracted from a C source file.

    Uses a simple regex + brace-depth scanner approach.  Not a full parser but
    reliable enough for typical hand-written C.
    """
    try:
        with open(filepath, 'r', errors='replace') as fh:
            content = fh.read()
    except Exception:
        return []

    # Strip block comments (preserve newlines so line numbers stay meaningful)
    content = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group().count('\n'), content, flags=re.DOTALL)
    # Strip line comments
    content = re.sub(r'//[^\n]*', '', content)

    KEYWORDS = frozenset({
        'if', 'while', 'for', 'switch', 'do', 'else', 'return',
        'typedef', 'struct', 'enum', 'union', 'static', 'extern',
        'sizeof', 'void', 'int', 'char', 'long', 'short', 'unsigned',
        'signed', 'double', 'float', 'const', 'volatile', 'inline',
    })

    # Pattern: <return-type> <name>(<params>) [attrs] {
    # We require the line to start at column 0 (or after whitespace) and the
    # signature NOT to end with a semicolon (which would be a prototype).
    func_re = re.compile(
        r'(?:^|\n)'               # line boundary
        r'(?!#)'                  # not a preprocessor directive
        r'(?:[\w\s\*]+?)'         # return type (greedy-minimal)
        r'\b(\w+)\s*'             # function name  ← group 1
        r'\([^;{]*?\)\s*'         # parameter list (no ; = not proto, no { yet)
        r'(?:__\w+\s*)*'          # optional compiler attrs like __attribute__
        r'\{',                    # opening brace of the body
        re.MULTILINE,
    )

    functions = []
    for m in func_re.finditer(content):
        name = m.group(1)
        if name in KEYWORDS:
            continue

        # Walk forward from the opening '{' matching braces
        brace_pos = m.end() - 1   # index of '{'
        depth = 0
        end = brace_pos
        for idx in range(brace_pos, len(content)):
            ch = content[idx]
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    end = idx + 1
                    break

        if depth == 0:
            body = content[m.start():end].lstrip('\n')
            functions.append((name, body))

    return functions


def _write_function_files(functions, output_dir):
    """Write ``function_names.txt`` and batched ``function_implementations_batch_N.txt``.

    Each batch file is kept under ~32 000 characters (≈8 000 tokens at ~4 chars/token).
    Returns the number of batch files written.
    """
    MAX_CHARS = 32_000  # ~8 k tokens

    if not functions:
        return 0

    # 1. Names file
    names_path = os.path.join(output_dir, 'function_names.txt')
    with open(names_path, 'w') as fh:
        for name, _ in functions:
            fh.write(name + '\n')

    # 2. Batched implementation files
    batch_num = 1
    parts = []
    size = 0

    def _flush(parts, batch_num):
        path = os.path.join(output_dir, f'function_implementations_batch_{batch_num}.txt')
        with open(path, 'w') as fh:
            fh.writelines(parts)

    for name, body in functions:
        entry = f"=== {name} ===\n{body}\n\n"
        if size + len(entry) > MAX_CHARS and parts:
            _flush(parts, batch_num)
            batch_num += 1
            parts = []
            size = 0
        parts.append(entry)
        size += len(entry)

    if parts:
        _flush(parts, batch_num)

    return batch_num


# ─────────────────────────────────────────────────────────────────────────────

class ModernScrollableFrame(ctk.CTkScrollableFrame):
    """Custom scrollable frame with IntelliJ styling"""
    def __init__(self, master, **kwargs):
        super().__init__(master, **kwargs)
        self.configure(fg_color=COLORS["bg_dark"])


class FileTreeItem(ctk.CTkFrame):
    """A clickable file item in the file tree"""
    def __init__(self, master, filename, filepath, on_click, **kwargs):
        super().__init__(master, **kwargs)
        self.filepath = filepath
        self.on_click = on_click
        self.selected = False
        
        self.configure(fg_color="transparent", height=28)
        self.pack_propagate(False)
        
        # File icon (using emoji as simple icon)
        icon = "📄" if filepath.endswith('.c') else "📁"
        self.icon_label = ctk.CTkLabel(self, text=icon, width=24, 
                                        font=ctk.CTkFont(size=12))
        self.icon_label.pack(side="left", padx=(5, 2))
        
        # Filename
        self.name_label = ctk.CTkLabel(self, text=filename, 
                                        font=ctk.CTkFont(size=12),
                                        text_color=COLORS["text"],
                                        anchor="w")
        self.name_label.pack(side="left", fill="x", expand=True, padx=2)
        
        # Bind click events
        for widget in [self, self.icon_label, self.name_label]:
            widget.bind("<Button-1>", self._on_click)
            widget.bind("<Enter>", self._on_enter)
            widget.bind("<Leave>", self._on_leave)
    
    def _on_click(self, event):
        self.on_click(self)
    
    def _on_enter(self, event):
        if not self.selected:
            self.configure(fg_color=COLORS["bg_light"])
    
    def _on_leave(self, event):
        if not self.selected:
            self.configure(fg_color="transparent")
    
    def set_selected(self, selected):
        self.selected = selected
        if selected:
            self.configure(fg_color=COLORS["selection"])
            self.name_label.configure(text_color=COLORS["text_bright"])
        else:
            self.configure(fg_color="transparent")
            self.name_label.configure(text_color=COLORS["text"])


class CFGListItem(ctk.CTkFrame):
    """A clickable function item in the CFG list (vertical layout)"""
    def __init__(self, master, image_path, function_name, on_click, **kwargs):
        super().__init__(master, **kwargs)
        self.image_path = image_path
        self.function_name = function_name
        self.on_click = on_click
        self.selected = False
        
        self.configure(fg_color="transparent", height=36, corner_radius=6)
        self.pack_propagate(False)
        
        # Function icon
        self.icon_label = ctk.CTkLabel(self, text="🔀", width=24,
                                        font=ctk.CTkFont(size=12))
        self.icon_label.pack(side="left", padx=(8, 4))
        
        # Function name
        self.name_label = ctk.CTkLabel(self, text=f"{function_name}()",
                                        font=ctk.CTkFont(size=11),
                                        text_color=COLORS["text"],
                                        anchor="w")
        self.name_label.pack(side="left", fill="x", expand=True, padx=4)
        
        # Bind click events
        for widget in [self, self.icon_label, self.name_label]:
            widget.bind("<Button-1>", self._on_click)
            widget.bind("<Enter>", self._on_enter)
            widget.bind("<Leave>", self._on_leave)
    
    def _on_click(self, event):
        self.on_click(self)
    
    def _on_enter(self, event):
        if not self.selected:
            self.configure(fg_color=COLORS["bg_light"])
    
    def _on_leave(self, event):
        if not self.selected:
            self.configure(fg_color="transparent")
    
    def set_selected(self, selected):
        self.selected = selected
        if selected:
            self.configure(fg_color=COLORS["selection"])
            self.name_label.configure(text_color=COLORS["text_bright"])
        else:
            self.configure(fg_color="transparent")
            self.name_label.configure(text_color=COLORS["text"])


class DepListItem(ctk.CTkFrame):
    """A clickable dependency graph item in the dep list (vertical layout)"""
    def __init__(self, master, image_path, dep_name, on_click, **kwargs):
        super().__init__(master, **kwargs)
        self.image_path = image_path
        self.dep_name = dep_name
        self.on_click = on_click
        self.selected = False

        self.configure(fg_color="transparent", height=36, corner_radius=6)
        self.pack_propagate(False)

        self.icon_label = ctk.CTkLabel(self, text="🔗", width=24,
                                        font=ctk.CTkFont(size=12))
        self.icon_label.pack(side="left", padx=(8, 4))

        self.name_label = ctk.CTkLabel(self, text=dep_name,
                                        font=ctk.CTkFont(size=11),
                                        text_color=COLORS["text"],
                                        anchor="w")
        self.name_label.pack(side="left", fill="x", expand=True, padx=4)

        for widget in [self, self.icon_label, self.name_label]:
            widget.bind("<Button-1>", self._on_click)
            widget.bind("<Enter>", self._on_enter)
            widget.bind("<Leave>", self._on_leave)

    def _on_click(self, event):
        self.on_click(self)

    def _on_enter(self, event):
        if not self.selected:
            self.configure(fg_color=COLORS["bg_light"])

    def _on_leave(self, event):
        if not self.selected:
            self.configure(fg_color="transparent")

    def set_selected(self, selected):
        self.selected = selected
        if selected:
            self.configure(fg_color=COLORS["selection"])
            self.name_label.configure(text_color=COLORS["text_bright"])
        else:
            self.configure(fg_color="transparent")
            self.name_label.configure(text_color=COLORS["text"])


class AnalysisPanel(ctk.CTkFrame):
    """Left panel for file selection and analysis"""
    def __init__(self, master, app, **kwargs):
        super().__init__(master, **kwargs)
        self.app = app
        self.configure(fg_color=COLORS["bg_dark"])
        
        self.selected_file = None
        self.selected_folder = None
        self.output_dir = None
        self.file_items = []
        
        self.setup_ui()
    
    def setup_ui(self):
        # Header
        header = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=40)
        header.pack(fill="x", padx=2, pady=2)
        header.pack_propagate(False)
        
        ctk.CTkLabel(header, text="📂 Project Explorer",
                    font=ctk.CTkFont(size=13, weight="bold"),
                    text_color=COLORS["text_bright"]).pack(side="left", padx=10, pady=8)
        
        # Toolbar
        toolbar = ctk.CTkFrame(self, fg_color="transparent", height=36)
        toolbar.pack(fill="x", padx=5, pady=5)
        
        ctk.CTkButton(toolbar, text="📁 Open Folder", width=100, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["bg_light"],
                     command=self.browse_folder).pack(side="left", padx=2)
        
        ctk.CTkButton(toolbar, text="📄 Open File", width=90, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["bg_light"],
                     command=self.browse_file).pack(side="left", padx=2)
        
        # File tree
        self.file_tree_label = ctk.CTkLabel(self, text="No folder selected",
                                            font=ctk.CTkFont(size=11),
                                            text_color=COLORS["text"])
        self.file_tree_label.pack(fill="x", padx=10, pady=5)
        
        self.file_tree = ModernScrollableFrame(self, height=200)
        self.file_tree.pack(fill="both", expand=True, padx=5, pady=5)
        
        # Selected file display
        selected_frame = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], 
                                      corner_radius=8)
        selected_frame.pack(fill="x", padx=5, pady=5)
        
        ctk.CTkLabel(selected_frame, text="Selected File:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(anchor="w", padx=10, pady=(5, 0))
        
        self.selected_label = ctk.CTkLabel(selected_frame, text="None",
                                           font=ctk.CTkFont(size=12, weight="bold"),
                                           text_color=COLORS["accent"],
                                           wraplength=280)
        self.selected_label.pack(anchor="w", padx=10, pady=(0, 5))
        
        # Output directory
        output_frame = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"],
                                    corner_radius=8)
        output_frame.pack(fill="x", padx=5, pady=5)
        
        output_header = ctk.CTkFrame(output_frame, fg_color="transparent")
        output_header.pack(fill="x", padx=10, pady=5)
        
        ctk.CTkLabel(output_header, text="Output Directory:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left")
        
        ctk.CTkButton(output_header, text="Change", width=60, height=24,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["bg_light"],
                     hover_color=COLORS["accent"],
                     command=self.browse_output).pack(side="right")
        
        self.output_label = ctk.CTkLabel(output_frame, text="./analysis_results",
                                         font=ctk.CTkFont(size=11),
                                         text_color=COLORS["text"],
                                         wraplength=280)
        self.output_label.pack(anchor="w", padx=10, pady=(0, 5))
        
        # Analyze buttons
        self.analyze_btn = ctk.CTkButton(self, text="▶  Analyze Selected",
                                         font=ctk.CTkFont(size=13, weight="bold"),
                                         fg_color=COLORS["success"],
                                         hover_color="#5AAC64",
                                         height=38,
                                         command=self.start_analysis)
        self.analyze_btn.pack(fill="x", padx=5, pady=(8, 2))

        self.analyze_all_btn = ctk.CTkButton(self, text="▶▶  Analyze All Files in Folder",
                                              font=ctk.CTkFont(size=13, weight="bold"),
                                              fg_color=COLORS["accent"],
                                              hover_color=COLORS["accent_hover"],
                                              height=38,
                                              command=self.start_folder_analysis)
        self.analyze_all_btn.pack(fill="x", padx=5, pady=(2, 8))

        # Progress bar
        self.progress = ctk.CTkProgressBar(self, mode="indeterminate",
                                           progress_color=COLORS["accent"])
        self.progress.pack(fill="x", padx=5, pady=(0, 5))
        self.progress.set(0)
    
    def browse_folder(self):
        folder = filedialog.askdirectory(title="Select Project Folder")
        if folder:
            self.load_folder(folder)
    
    def browse_file(self):
        filepath = filedialog.askopenfilename(
            title="Select C File",
            filetypes=[("C Files", "*.c"), ("All Files", "*.*")]
        )
        if filepath:
            self.select_file(filepath)
    
    def browse_output(self):
        directory = filedialog.askdirectory(title="Select Output Directory")
        if directory:
            self.output_dir = directory
            self.output_label.configure(text=directory)
    
    def load_folder(self, folder):
        self.selected_folder = folder
        # Clear existing items
        for item in self.file_items:
            item.destroy()
        self.file_items.clear()
        
        self.file_tree_label.configure(text=os.path.basename(folder))
        
        # Find C files
        c_files = sorted(Path(folder).glob("*.c"))
        
        if not c_files:
            ctk.CTkLabel(self.file_tree, text="No .c files found",
                        font=ctk.CTkFont(size=11),
                        text_color=COLORS["text"]).pack(pady=20)
            return
        
        for f in c_files:
            item = FileTreeItem(self.file_tree, f.name, str(f),
                               self.on_file_click)
            item.pack(fill="x", pady=1)
            self.file_items.append(item)
    
    def on_file_click(self, item):
        # Deselect all
        for fi in self.file_items:
            fi.set_selected(False)
        # Select clicked
        item.set_selected(True)
        self.select_file(item.filepath)
    
    def select_file(self, filepath):
        self.selected_file = filepath
        self.selected_label.configure(text=os.path.basename(filepath))
        self.app.log_message(f"Selected: {filepath}")
    
    def _disable_buttons(self):
        self.analyze_btn.configure(state="disabled", text="Analyzing...")
        self.analyze_all_btn.configure(state="disabled", text="Analyzing...")
        self.progress.start()

    def start_analysis(self):
        if not self.selected_file:
            messagebox.showwarning("No File", "Please select a C file first")
            return
        self._disable_buttons()
        thread = threading.Thread(target=self.run_analysis, daemon=True)
        thread.start()

    def start_folder_analysis(self):
        if not self.selected_folder:
            messagebox.showwarning("No Folder", "Please open a folder first")
            return
        self._disable_buttons()
        thread = threading.Thread(target=self.run_folder_analysis, daemon=True)
        thread.start()
    
    def run_analysis(self):
        try:
            # Setup output directory
            if not self.output_dir:
                self.output_dir = os.path.join(
                    os.path.dirname(self.selected_file),
                    "analysis_results"
                )
            
            os.makedirs(self.output_dir, exist_ok=True)
            
            self.app.log_message("\n"+ "━"* 50)
            self.app.log_message("Starting Analysis...")
            self.app.log_message(f"Input: {self.selected_file}")
            self.app.log_message(f"Output: {self.output_dir}")
            self.app.log_message("━" * 50)
            
            script_dir = os.path.dirname(os.path.abspath(__file__))
            
            # Step 1: Preprocess
            self.app.log_message("\n[1/3] Preprocessing...")
            preprocess_script = os.path.join(script_dir, "preprocess.sh")
            
            base_name = os.path.basename(self.selected_file)
            file_dir = os.path.dirname(self.selected_file)
            preprocessed_file = os.path.join(
                file_dir, base_name.replace('.c', '_preprocessed.c')
            )
            
            if os.path.exists(preprocess_script):
                result = subprocess.run(
                    ['bash', preprocess_script, self.selected_file],
                    capture_output=True, text=True, cwd=script_dir
                )
                if result.returncode == 0:
                    self.app.log_message("Preprocessing complete")
                else:
                    self.app.log_message("Preprocessing skipped")
                    preprocessed_file = self.selected_file
            else:
                preprocessed_file = self.selected_file
            
            # Step 2: Compile Java
            self.app.log_message("\n[2/3] Checking Java compilation...")
            main_class = os.path.join(script_dir, "Main.class")
            main_java = os.path.join(script_dir, "Main.java")
            
            if not os.path.exists(main_class) or \
               os.path.getmtime(main_java) > os.path.getmtime(main_class):
                self.app.log_message("Compiling Java files...")
                java_files = glob.glob(os.path.join(script_dir, '*.java'))
                result = subprocess.run(
                    ['javac'] + java_files,
                    capture_output=True, text=True,
                    cwd=script_dir
                )
                if result.returncode == 0:
                    self.app.log_message("Compilation complete")
                else:
                    raise Exception(f"Compilation failed: {result.stderr}")
            else:
                self.app.log_message("Already compiled")
            
            # Step 3: Run analysis
            self.app.log_message("\n[3/3] Running static analysis...")
            
            original_dir = os.getcwd()
            try:
                os.chdir(script_dir)
                
                result = subprocess.run(
                    ['java', 'Main', preprocessed_file, '--all'],
                    capture_output=True, text=True, timeout=60
                )
                
                if result.stdout or result.stderr:
                    # Save report (stdout + any stderr errors)
                    report_file = os.path.join(self.output_dir, 'analysis_report.txt')
                    with open(report_file, 'w') as f:
                        f.write(result.stdout or "")
                        if result.stderr and result.stderr.strip():
                            f.write("\n--- ERRORS / WARNINGS ---\n")
                            f.write(result.stderr.strip())
                            f.write("\n")
                    self.app.log_message("Analysis report saved")
                
                # Move output files
                moved = 0
                for pattern in ['cfg_*.dot', 'cfg_*.png', 'dep_*.dot', 'dep_*.png']:
                    for file in glob.glob(pattern):
                        dest = os.path.join(self.output_dir, os.path.basename(file))
                        os.rename(file, dest)
                        moved += 1

                self.app.log_message(f"Moved {moved} files")
                
            finally:
                os.chdir(original_dir)

            # Extract function data → write txt files for AI analysis
            self.app.log_message("\n[+] Extracting function data...")
            src = self.selected_file
            functions = _extract_c_functions(src)
            if functions:
                batches = _write_function_files(functions, self.output_dir)
                self.app.log_message(
                    f"      ✓ {len(functions)} function(s) → "
                    f"function_names.txt + {batches} implementation batch(es)"
                )
            else:
                self.app.log_message("No functions extracted from source")

            # Load results
            self.app.log_message("\n"+ "━"* 50)
            self.app.log_message("ANALYSIS COMPLETE")
            self.app.log_message("━" * 50)
            
            # Update results panel
            self.app.after(100, lambda: self.app.results_panel.load_results(self.output_dir))
            
        except Exception as e:
            self.app.log_message(f"\nError: {str(e)}")
            import traceback
            self.app.log_message(traceback.format_exc())
        finally:
            self.app.after(0, self.finish_analysis)
    
    def finish_analysis(self):
        self.analyze_btn.configure(state="normal", text="▶  Analyze Selected")
        self.analyze_all_btn.configure(state="normal", text="▶▶  Analyze All Files in Folder")
        self.progress.stop()
        self.progress.set(0)

    def run_folder_analysis(self):
        """Analyze every .c file in the selected folder."""
        try:
            c_files = sorted(Path(self.selected_folder).glob("*.c"))
            if not c_files:
                self.app.log_message("No .c files found in the selected folder.")
                return

            if not self.output_dir:
                self.output_dir = os.path.join(self.selected_folder, "analysis_results")
            os.makedirs(self.output_dir, exist_ok=True)

            self.app.log_message("\n"+ "━"* 50)
            self.app.log_message(f"Folder Analysis — {len(c_files)} file(s) found")
            self.app.log_message(f"Folder : {self.selected_folder}")
            self.app.log_message(f"Output : {self.output_dir}")
            self.app.log_message("━" * 50)

            script_dir = os.path.dirname(os.path.abspath(__file__))

            # Ensure Java is compiled once
            self.app.log_message("\nChecking Java compilation...")
            main_class = os.path.join(script_dir, "Main.class")
            main_java  = os.path.join(script_dir, "Main.java")
            if not os.path.exists(main_class) or \
               os.path.getmtime(main_java) > os.path.getmtime(main_class):
                java_files = glob.glob(os.path.join(script_dir, '*.java'))
                result = subprocess.run(
                    ['javac'] + java_files,
                    capture_output=True, text=True, cwd=script_dir
                )
                if result.returncode != 0:
                    raise Exception(f"Compilation failed: {result.stderr}")
                self.app.log_message("Compiled")
            else:
                self.app.log_message("Already compiled")

            NUM_THREADS = 8
            if len(c_files) >= 8:
                # ── Parallel path ──────────────────────────────────────────
                self.app.log_message(f"\nParallel analysis — {NUM_THREADS} threads")
                indexed = list(enumerate(c_files, 1))
                base = len(indexed) // NUM_THREADS
                chunks = [indexed[i * base:(i + 1) * base] for i in range(NUM_THREADS - 1)]
                chunks.append(indexed[(NUM_THREADS - 1) * base:])  # last thread gets the remainder

                lock = threading.Lock()
                par_results = []  # list of (idx, section, errors, warnings)

                threads = [
                    threading.Thread(
                        target=self._analyze_chunk,
                        args=(chunk, len(c_files), script_dir, lock, par_results),
                        daemon=True
                    )
                    for chunk in chunks if chunk
                ]
                for t in threads:
                    t.start()
                for t in threads:
                    t.join()

                par_results.sort(key=lambda r: r[0])
                combined_sections = [r[1] for r in par_results]
                errors_total   = sum(r[2] for r in par_results)
                warnings_total = sum(r[3] for r in par_results)
            else:
                # ── Sequential path ────────────────────────────────────────
                combined_sections = []
                errors_total = warnings_total = 0
                original_dir = os.getcwd()

                for idx, c_file in enumerate(c_files, 1):
                    self.app.log_message(f"\n[{idx}/{len(c_files)}] {c_file.name}")
                    try:
                        os.chdir(script_dir)
                        result = subprocess.run(
                            ['java', 'Main', str(c_file), '--all'],
                            capture_output=True, text=True, timeout=60
                        )

                        stderr_block = ""
                        if result.stderr and result.stderr.strip():
                            stderr_block = "\n--- ERRORS / WARNINGS ---\n" + result.stderr.strip() + "\n"

                        section = (
                            f"{'═' * 60}\n"
                            f"  FILE: {c_file.name}\n"
                            f"{'═' * 60}\n"
                            + (result.stdout or "(no stdout output)\n")
                            + stderr_block
                            + "\n"
                        )
                        combined_sections.append(section)

                        m = re.search(r'(\d+) error.*?(\d+) warning', result.stdout or '')
                        if m:
                            errors_total   += int(m.group(1))
                            warnings_total += int(m.group(2))

                        stem = c_file.stem
                        moved = 0
                        for pattern in ['cfg_*.dot', 'cfg_*.png', 'dep_*.dot', 'dep_*.png']:
                            for f in glob.glob(pattern):
                                basename = os.path.basename(f)
                                dest = os.path.join(self.output_dir, f"{stem}__{basename}")
                                if os.path.exists(dest):
                                    name, ext = os.path.splitext(f"{stem}__{basename}")
                                    counter = 2
                                    while os.path.exists(dest):
                                        dest = os.path.join(self.output_dir, f"{name}_{counter}{ext}")
                                        counter += 1
                                os.rename(f, dest)
                                moved += 1

                        self.app.log_message(f"Done ({moved} CFG + dep file(s) saved)")

                    except subprocess.TimeoutExpired:
                        combined_sections.append(f"FILE: {c_file.name}\n❌ Timed out\n\n")
                        self.app.log_message(f"Timed out")
                    except Exception as e:
                        combined_sections.append(f"FILE: {c_file.name}\n❌ Error: {e}\n\n")
                        self.app.log_message(f"Error: {e}")
                    finally:
                        os.chdir(original_dir)

            # Write combined report
            header = (
                f"FOLDER ANALYSIS REPORT\n"
                f"Generated : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
                f"Folder    : {self.selected_folder}\n"
                f"Files     : {len(c_files)}\n"
                f"Total errors: {errors_total}   warnings: {warnings_total}\n"
                + "━" * 60 + "\n\n"
            )
            report_file = os.path.join(self.output_dir, 'analysis_report.txt')
            with open(report_file, 'w') as fh:
                fh.write(header)
                fh.writelines(combined_sections)

            # Extract functions from every C file → combined txt files for AI analysis
            self.app.log_message("\n[+] Extracting function data from all files...")
            all_functions = []
            for c_file in c_files:
                all_functions.extend(_extract_c_functions(str(c_file)))
            if all_functions:
                batches = _write_function_files(all_functions, self.output_dir)
                self.app.log_message(
                    f"      ✓ {len(all_functions)} total function(s) → "
                    f"function_names.txt + {batches} implementation batch(es)"
                )
            else:
                self.app.log_message("No functions extracted from folder sources")

            self.app.log_message("\n"+ "━"* 50)
            self.app.log_message(f"FOLDER ANALYSIS COMPLETE")
            self.app.log_message(f"{len(c_files)} file(s) · {errors_total} error(s) · {warnings_total} warning(s)")
            self.app.log_message("━" * 50)

            self.app.after(100, lambda: self.app.results_panel.load_results(self.output_dir))

        except Exception as e:
            self.app.log_message(f"\nError: {str(e)}")
            import traceback
            self.app.log_message(traceback.format_exc())
        finally:
            self.app.after(0, self.finish_analysis)

    def _analyze_one_file(self, c_file, idx, total, script_dir, lock, results):
        """Analyze a single .c file in an isolated temp dir (thread-safe)."""
        try:
            with tempfile.TemporaryDirectory() as tmpdir:
                result = subprocess.run(
                    ['java', '-cp', script_dir, 'Main', str(c_file.resolve()), '--all'],
                    capture_output=True, text=True, timeout=60, cwd=tmpdir
                )

                stderr_block = ""
                if result.stderr and result.stderr.strip():
                    stderr_block = "\n--- ERRORS / WARNINGS ---\n" + result.stderr.strip() + "\n"

                section = (
                    f"{'═' * 60}\n"
                    f"  FILE: {c_file.name}\n"
                    f"{'═' * 60}\n"
                    + (result.stdout or "(no stdout output)\n")
                    + stderr_block
                    + "\n"
                )

                errors = warnings = 0
                m = re.search(r'(\d+) error.*?(\d+) warning', result.stdout or '')
                if m:
                    errors   = int(m.group(1))
                    warnings = int(m.group(2))

                # Move CFG + dep files to shared output_dir under a lock to avoid races
                stem = c_file.stem
                moved = 0
                with lock:
                    for pattern in ['cfg_*.dot', 'cfg_*.png', 'dep_*.dot', 'dep_*.png']:
                        for f in glob.glob(os.path.join(tmpdir, pattern)):
                            basename = os.path.basename(f)
                            dest = os.path.join(self.output_dir, f"{stem}__{basename}")
                            if os.path.exists(dest):
                                name, ext = os.path.splitext(f"{stem}__{basename}")
                                counter = 2
                                while os.path.exists(dest):
                                    dest = os.path.join(self.output_dir, f"{name}_{counter}{ext}")
                                    counter += 1
                            shutil.move(f, dest)
                            moved += 1

                with lock:
                    results.append((idx, section, errors, warnings))
                self.app.log_message(f"[{idx}/{total}] {c_file.name} ({moved} CFG + dep file(s))")

        except subprocess.TimeoutExpired:
            with lock:
                results.append((idx, f"FILE: {c_file.name}\n❌ Timed out\n\n", 0, 0))
            self.app.log_message(f"[{idx}/{total}] {c_file.name} — Timed out")
        except Exception as e:
            with lock:
                results.append((idx, f"FILE: {c_file.name}\n❌ Error: {e}\n\n", 0, 0))
            self.app.log_message(f"[{idx}/{total}] {c_file.name} — {e}")

    def _analyze_chunk(self, chunk, total, script_dir, lock, results):
        """Process a list of (idx, c_file) pairs sequentially (runs in a worker thread)."""
        for idx, c_file in chunk:
            self._analyze_one_file(c_file, idx, total, script_dir, lock, results)


_API_URL_DEFAULTS = {
    "api/openai":          ("https://api.openai.com/v1/chat/completions", "gpt-4o"),
    "api/anthropic":       ("https://api.anthropic.com/v1/messages",      "claude-3-5-sonnet-20241022"),
    "api/custom-endpoint": ("",                                            ""),
}


class ResultsPanel(ctk.CTkFrame):
    """Right panel for viewing results"""
    def __init__(self, master, app, **kwargs):
        super().__init__(master, **kwargs)
        self.app = app
        self.configure(fg_color=COLORS["bg_dark"])
        
        self.results_dir = None
        self.cfg_thumbnails = []
        self.current_image = None
        self.photo = None
        self.zoom_level = 1.0
        self._file_sections = {}
        self._all_report_text = ""
        self._all_cfg_data = []  # list of (png_path, func_name, source_stem)
        self._cfg_filter_job = None  # debounce timer id
        self._complexity_map = {}   # func_name -> int complexity
        self._cfg_top10_active = False
        self.dep_thumbnails = []
        self.dep_current_image = None
        self.dep_photo = None
        self.dep_zoom_level = 1.0
        self._all_dep_data = []  # list of (png_path, dep_name)
        self._dep_filter_job = None
        self._ollama_proc = None          # active 'ollama run' Popen handle
        self._ollama_model_loaded = None  # name of model currently loaded

        self.setup_ui()
    
    def setup_ui(self):
        # Header
        header = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=40)
        header.pack(fill="x", padx=2, pady=2)
        header.pack_propagate(False)
        
        ctk.CTkLabel(header, text="📊 Analysis Results",
                    font=ctk.CTkFont(size=13, weight="bold"),
                    text_color=COLORS["text_bright"]).pack(side="left", padx=10, pady=8)
        
        ctk.CTkButton(header, text="📂 Load Results", width=100, height=26,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_light"],
                     hover_color=COLORS["accent"],
                     command=self.browse_results).pack(side="right", padx=10, pady=6)
        
        # Main content with tabs
        self.tabview = ctk.CTkTabview(self, fg_color=COLORS["bg_dark"])
        self.tabview.pack(fill="both", expand=True, padx=5, pady=5)
        
        # Report tab - SPLIT VIEW (Static Analysis | LLM Analysis)
        self.report_tab = self.tabview.add("📝 Analysis Report")
        
        # Main horizontal container for split view
        report_main_frame = ctk.CTkFrame(self.report_tab, fg_color="transparent")
        report_main_frame.pack(fill="both", expand=True)
        
        # Left side - Static Analysis Report
        left_report_frame = ctk.CTkFrame(report_main_frame, fg_color=COLORS["bg_medium"],
                                         corner_radius=8)
        left_report_frame.pack(side="left", fill="both", expand=True, padx=(5, 2), pady=5)
        
        # Left header
        left_header = ctk.CTkFrame(left_report_frame, fg_color=COLORS["bg_light"],
                                   height=36, corner_radius=6)
        left_header.pack(fill="x", padx=8, pady=8)
        left_header.pack_propagate(False)
        
        ctk.CTkLabel(left_header, text="📊 Static Analysis Results",
                    font=ctk.CTkFont(size=12, weight="bold"),
                    text_color=COLORS["text_bright"]).pack(side="left", padx=10, pady=6)

        # Body: vertical jump sidebar + filter/text area side by side
        body_frame = ctk.CTkFrame(left_report_frame, fg_color="transparent")
        body_frame.pack(fill="both", expand=True, padx=8, pady=(0, 8))

        # ── Left vertical jump sidebar ──────────────────────────────
        jump_sidebar = ctk.CTkFrame(body_frame, fg_color=COLORS["bg_dark"],
                                    corner_radius=6, width=90)
        jump_sidebar.pack(side="left", fill="y", padx=(0, 6), pady=0)
        jump_sidebar.pack_propagate(False)

        ctk.CTkLabel(jump_sidebar, text="Jump to",
                    font=ctk.CTkFont(size=10, weight="bold"),
                    text_color=COLORS["text"]).pack(pady=(8, 6), padx=4)

        _sections = [
            ("📐 Metrics",    "FILE METRICS"),
            ("🔗 Dep",        "DEPENDENCY GRAPH"),
            ("🌳 AST",        "ABSTRACT SYNTAX TREE"),
            ("🔍 Semantic",   "SEMANTIC ANALYSIS"),
            ("🔀 CFG",        "CONTROL FLOW GRAPH"),
            ("🔢 Cyclo",      "CYCLOMATIC COMPLEXITY"),
        ]
        for btn_label, keyword in _sections:
            ctk.CTkButton(
                jump_sidebar, text=btn_label, height=28, width=80,
                font=ctk.CTkFont(size=10),
                fg_color=COLORS["bg_light"],
                hover_color=COLORS["accent"],
                anchor="w",
                command=lambda kw=keyword: self._jump_to_section(kw)
            ).pack(padx=5, pady=3, fill="x")

        # ── Right: filter bar + report textbox ─────────────────────
        right_content = ctk.CTkFrame(body_frame, fg_color="transparent")
        right_content.pack(side="left", fill="both", expand=True)

        # File filter bar
        filter_frame = ctk.CTkFrame(right_content, fg_color="transparent", height=34)
        filter_frame.pack(fill="x", pady=(0, 4))
        filter_frame.pack_propagate(False)

        ctk.CTkLabel(filter_frame, text="File:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(2, 6))

        self.file_filter_var = ctk.StringVar(value="All Files")
        self.file_filter_combo = ctk.CTkComboBox(
            filter_frame, width=220, height=26,
            variable=self.file_filter_var,
            values=["All Files"],
            font=ctk.CTkFont(size=11),
            command=self._filter_report
        )
        self.file_filter_combo.pack(side="left", padx=(0, 8))

        self.file_search_entry = ctk.CTkEntry(
            filter_frame, width=150, height=26,
            placeholder_text="search files...",
            font=ctk.CTkFont(size=11)
        )
        self.file_search_entry.pack(side="left")
        self.file_search_entry.bind("<KeyRelease>", self._on_filter_search)

        self.report_text = ctk.CTkTextbox(right_content,
                                          font=ctk.CTkFont(family="Consolas", size=11),
                                          fg_color=COLORS["bg_dark"],
                                          text_color=COLORS["text"],
                                          state="disabled")
        self.report_text.pack(fill="both", expand=True)
        self.report_text.configure(state="normal")
        self.report_text.insert("1.0", "No analysis report loaded.\n\nRun an analysis or load existing results.")
        self.report_text.configure(state="disabled")
        
        # Right side - LLM Analysis
        right_report_frame = ctk.CTkFrame(report_main_frame, fg_color=COLORS["bg_medium"],
                                          corner_radius=8)
        right_report_frame.pack(side="left", fill="both", expand=True, padx=(2, 5), pady=5)
        
        # Right header with controls
        right_header = ctk.CTkFrame(right_report_frame, fg_color=COLORS["bg_light"],
                                    height=36, corner_radius=6)
        right_header.pack(fill="x", padx=8, pady=8)
        right_header.pack_propagate(False)
        
        ctk.CTkLabel(right_header, text="🤖 LLM Analysis",
                    font=ctk.CTkFont(size=12, weight="bold"),
                    text_color=COLORS["text_bright"]).pack(side="left", padx=10, pady=6)
        
        self.llm_analyze_btn = ctk.CTkButton(right_header, text="▶ Analyze",
                                             width=90, height=26,
                                             font=ctk.CTkFont(size=11),
                                             fg_color=COLORS["accent"],
                                             hover_color=COLORS["accent_hover"],
                                             command=self.run_llm_analysis)
        self.llm_analyze_btn.pack(side="right", padx=5, pady=5)
        
        # LLM Settings frame
        llm_settings = ctk.CTkFrame(right_report_frame, fg_color="transparent", height=35)
        llm_settings.pack(fill="x", padx=8, pady=(0, 5))
        
        ctk.CTkLabel(llm_settings, text="Model:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(5, 5))
        
        self.llm_model_var = ctk.StringVar(value="ollama/codellama")
        self.llm_model_combo = ctk.CTkComboBox(llm_settings, width=210,
                                               values=["ollama/codellama",
                                                      "ollama/llama3",
                                                      "ollama/mistral",
                                                      "ollama/deepseek-coder",
                                                      "custom",
                                                      "api/openai",
                                                      "api/anthropic",
                                                      "api/custom-endpoint"],
                                               variable=self.llm_model_var,
                                               font=ctk.CTkFont(size=11),
                                               command=self._on_llm_model_change)
        self.llm_model_combo.pack(side="left", padx=5)

        # ── Custom GGUF path row (hidden until "custom" is selected) ──────
        self.gguf_frame = ctk.CTkFrame(right_report_frame, fg_color="transparent", height=32)
        # not packed yet — shown dynamically
        ctk.CTkLabel(self.gguf_frame, text="GGUF path:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(8, 4))
        self.gguf_path_var = ctk.StringVar()
        self.gguf_entry = ctk.CTkEntry(self.gguf_frame,
                                       textvariable=self.gguf_path_var,
                                       placeholder_text="/path/to/model.gguf",
                                       font=ctk.CTkFont(size=11))
        self.gguf_entry.pack(side="left", fill="x", expand=True, padx=(0, 4))
        ctk.CTkButton(self.gguf_frame, text="Browse", width=70, height=26,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_light"],
                     hover_color=COLORS["accent"],
                     command=self._browse_gguf).pack(side="left", padx=(0, 8))

        # ── API config row (hidden until api/* is selected) ───────────────
        self.api_frame = ctk.CTkFrame(right_report_frame, fg_color="transparent")
        # not packed yet — shown dynamically

        api_row1 = ctk.CTkFrame(self.api_frame, fg_color="transparent")
        api_row1.pack(fill="x", pady=(0, 3))

        ctk.CTkLabel(api_row1, text="API Key:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(8, 4))
        self.api_key_var = ctk.StringVar()
        self.api_key_entry = ctk.CTkEntry(api_row1,
                                          textvariable=self.api_key_var,
                                          placeholder_text="sk-...",
                                          show="*",
                                          font=ctk.CTkFont(size=11),
                                          width=200)
        self.api_key_entry.pack(side="left", padx=(0, 10))

        ctk.CTkLabel(api_row1, text="Model name:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(0, 4))
        self.api_model_var = ctk.StringVar(value="gpt-4o")
        self.api_model_entry = ctk.CTkEntry(api_row1,
                                            textvariable=self.api_model_var,
                                            font=ctk.CTkFont(size=11),
                                            width=160)
        self.api_model_entry.pack(side="left", padx=(0, 8))

        api_row2 = ctk.CTkFrame(self.api_frame, fg_color="transparent")
        api_row2.pack(fill="x", pady=(0, 3))

        ctk.CTkLabel(api_row2, text="Base URL:",
                    font=ctk.CTkFont(size=11),
                    text_color=COLORS["text"]).pack(side="left", padx=(8, 4))
        self.api_url_var = ctk.StringVar(value="https://api.openai.com/v1/chat/completions")
        self.api_url_entry = ctk.CTkEntry(api_row2,
                                          textvariable=self.api_url_var,
                                          font=ctk.CTkFont(size=11))
        self.api_url_entry.pack(side="left", fill="x", expand=True, padx=(0, 8))

        self.llm_status = ctk.CTkLabel(llm_settings, text="Ready",
                                       font=ctk.CTkFont(size=10),
                                       text_color=COLORS["success"])
        self.llm_status.pack(side="right", padx=10)
        
        # LLM output text
        self.llm_text = ctk.CTkTextbox(right_report_frame, 
                                       font=ctk.CTkFont(family="Consolas", size=11),
                                       fg_color=COLORS["bg_dark"],
                                       text_color=COLORS["text"])
        self.llm_text.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        self.llm_text.insert("1.0", "LLM Analysis will appear here.\n\n" +
                            "Click '▶ Analyze' to run local LLM analysis.\n\n" +
                            "Requirements:\n" +
                            "• Ollama installed (ollama.ai)\n" +
                            "• Model pulled (e.g., ollama pull codellama)\n\n" +
                            "The LLM will analyze:\n" +
                            "• Code quality issues\n" +
                            "• Potential bugs\n" +
                            "• Security vulnerabilities\n" +
                            "• Improvement suggestions")
        
        # CFG Gallery tab - VERTICAL LAYOUT (list on left, viewer on right)
        self.cfg_tab = self.tabview.add("🔀 Control Flow Graphs")
        
        # Main horizontal container
        cfg_main_frame = ctk.CTkFrame(self.cfg_tab, fg_color="transparent")
        cfg_main_frame.pack(fill="both", expand=True)
        
        # Left side - Function list (narrow)
        left_panel = ctk.CTkFrame(cfg_main_frame, fg_color=COLORS["bg_medium"],
                                  corner_radius=8, width=180)
        left_panel.pack(side="left", fill="y", padx=(5, 2), pady=5)
        left_panel.pack_propagate(False)
        
        # List header
        self.cfg_label = ctk.CTkLabel(left_panel,
                                      text="📋 Functions",
                                      font=ctk.CTkFont(size=12, weight="bold"),
                                      text_color=COLORS["text_bright"])
        self.cfg_label.pack(pady=(8, 4), padx=5)

        # File filter search entry
        ctk.CTkLabel(left_panel, text="File filter:",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text"]).pack(padx=7, pady=(0, 1), anchor="w")
        self.cfg_file_search = ctk.CTkEntry(
            left_panel, height=26,
            placeholder_text="filter by file...",
            font=ctk.CTkFont(size=11)
        )
        self.cfg_file_search.pack(padx=5, pady=(0, 4), fill="x")
        self.cfg_file_search.bind("<KeyRelease>", lambda _: self._schedule_cfg_filter())

        # Function search entry
        ctk.CTkLabel(left_panel, text="Function search:",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text"]).pack(padx=7, pady=(0, 1), anchor="w")
        self.cfg_search_entry = ctk.CTkEntry(
            left_panel, height=26,
            placeholder_text="search function...",
            font=ctk.CTkFont(size=11)
        )
        self.cfg_search_entry.pack(padx=5, pady=(0, 6), fill="x")
        self.cfg_search_entry.bind("<KeyRelease>", lambda _: self._schedule_cfg_filter())

        # Top-10 / All toggle
        top10_row = ctk.CTkFrame(left_panel, fg_color="transparent")
        top10_row.pack(padx=5, pady=(0, 6), fill="x")
        self.top10_btn = ctk.CTkButton(
            top10_row, text="🏆 Top 10 Complex",
            height=26, font=ctk.CTkFont(size=10),
            fg_color=COLORS["warning"], hover_color="#D4A020",
            text_color=COLORS["bg_dark"],
            command=self._toggle_top10
        )
        self.top10_btn.pack(fill="x")

        # Scrollable function list (vertical)
        self.cfg_gallery = ModernScrollableFrame(left_panel)
        self.cfg_gallery.pack(fill="both", expand=True, padx=5, pady=(0, 5))
        
        # Right side - CFG Viewer (takes most space)
        viewer_frame = ctk.CTkFrame(cfg_main_frame, fg_color=COLORS["bg_medium"],
                                    corner_radius=8)
        viewer_frame.pack(side="left", fill="both", expand=True, padx=(2, 5), pady=5)
        
        # Zoom controls toolbar
        zoom_frame = ctk.CTkFrame(viewer_frame, fg_color=COLORS["bg_light"], 
                                  height=40, corner_radius=6)
        zoom_frame.pack(fill="x", padx=8, pady=8)
        zoom_frame.pack_propagate(False)
        
        self.cfg_name_label = ctk.CTkLabel(zoom_frame, text="Select a function",
                                           font=ctk.CTkFont(size=13, weight="bold"),
                                           text_color=COLORS["accent"])
        self.cfg_name_label.pack(side="left", padx=10)
        
        # Zoom buttons on right
        ctk.CTkButton(zoom_frame, text="100%", width=50, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["accent"],
                     command=self.actual_size).pack(side="right", padx=3, pady=6)
        
        ctk.CTkButton(zoom_frame, text="Fit", width=45, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["accent"],
                     command=self.fit_to_window).pack(side="right", padx=3, pady=6)
        
        ctk.CTkButton(zoom_frame, text="−", width=35, height=28,
                     font=ctk.CTkFont(size=16, weight="bold"),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["accent"],
                     command=self.zoom_out).pack(side="right", padx=3, pady=6)
        
        ctk.CTkButton(zoom_frame, text="+", width=35, height=28,
                     font=ctk.CTkFont(size=16, weight="bold"),
                     fg_color=COLORS["bg_medium"],
                     hover_color=COLORS["accent"],
                     command=self.zoom_in).pack(side="right", padx=3, pady=6)
        
        self.zoom_label = ctk.CTkLabel(zoom_frame, text="100%",
                                       font=ctk.CTkFont(size=11),
                                       text_color=COLORS["text"])
        self.zoom_label.pack(side="right", padx=10)
        
        # Image canvas with scrollbars
        from tkinter import Canvas
        self.canvas_frame = ctk.CTkFrame(viewer_frame, fg_color=COLORS["bg_dark"],
                                         corner_radius=6)
        self.canvas_frame.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        
        # Add scrollbars
        from tkinter import Scrollbar
        v_scroll = Scrollbar(self.canvas_frame, orient="vertical")
        v_scroll.pack(side="right", fill="y")
        
        h_scroll = Scrollbar(self.canvas_frame, orient="horizontal")
        h_scroll.pack(side="bottom", fill="x")
        
        self.canvas = Canvas(self.canvas_frame, bg=COLORS["bg_dark"],
                            highlightthickness=0,
                            xscrollcommand=h_scroll.set,
                            yscrollcommand=v_scroll.set)
        self.canvas.pack(side="left", fill="both", expand=True)
        
        v_scroll.config(command=self.canvas.yview)
        h_scroll.config(command=self.canvas.xview)
        
        # Scroll bindings (mouse wheel)
        self.canvas.bind("<MouseWheel>", self.on_mousewheel)
        self.canvas.bind("<Button-4>", self.on_mousewheel)
        self.canvas.bind("<Button-5>", self.on_mousewheel)
        # Shift+scroll for horizontal
        self.canvas.bind("<Shift-MouseWheel>", self.on_horizontal_scroll)
        self.canvas.bind("<Shift-Button-4>", self.on_horizontal_scroll)
        self.canvas.bind("<Shift-Button-5>", self.on_horizontal_scroll)

        # ── Dependency Graph tab ──────────────────────────────────────────
        self.dep_tab = self.tabview.add("🔗 Dependency Graphs")

        dep_main_frame = ctk.CTkFrame(self.dep_tab, fg_color="transparent")
        dep_main_frame.pack(fill="both", expand=True)

        # Left — file list
        dep_left = ctk.CTkFrame(dep_main_frame, fg_color=COLORS["bg_medium"],
                                corner_radius=8, width=200)
        dep_left.pack(side="left", fill="y", padx=(5, 2), pady=5)
        dep_left.pack_propagate(False)

        self.dep_label = ctk.CTkLabel(dep_left, text="🔗 Graphs",
                                      font=ctk.CTkFont(size=12, weight="bold"),
                                      text_color=COLORS["text_bright"])
        self.dep_label.pack(pady=(8, 4), padx=5)

        ctk.CTkLabel(dep_left, text="Search:",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text"]).pack(padx=7, pady=(0, 1), anchor="w")
        self.dep_search_entry = ctk.CTkEntry(
            dep_left, height=26,
            placeholder_text="filter by name...",
            font=ctk.CTkFont(size=11)
        )
        self.dep_search_entry.pack(padx=5, pady=(0, 6), fill="x")
        self.dep_search_entry.bind("<KeyRelease>", lambda _: self._schedule_dep_filter())

        self.dep_gallery = ModernScrollableFrame(dep_left)
        self.dep_gallery.pack(fill="both", expand=True, padx=5, pady=(0, 5))

        # Right — viewer
        dep_viewer = ctk.CTkFrame(dep_main_frame, fg_color=COLORS["bg_medium"],
                                  corner_radius=8)
        dep_viewer.pack(side="left", fill="both", expand=True, padx=(2, 5), pady=5)

        dep_zoom_frame = ctk.CTkFrame(dep_viewer, fg_color=COLORS["bg_light"],
                                      height=40, corner_radius=6)
        dep_zoom_frame.pack(fill="x", padx=8, pady=8)
        dep_zoom_frame.pack_propagate(False)

        self.dep_name_label = ctk.CTkLabel(dep_zoom_frame, text="Select a graph",
                                           font=ctk.CTkFont(size=13, weight="bold"),
                                           text_color=COLORS["accent"])
        self.dep_name_label.pack(side="left", padx=10)

        ctk.CTkButton(dep_zoom_frame, text="100%", width=50, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"], hover_color=COLORS["accent"],
                     command=self.dep_actual_size).pack(side="right", padx=3, pady=6)
        ctk.CTkButton(dep_zoom_frame, text="Fit", width=45, height=28,
                     font=ctk.CTkFont(size=11),
                     fg_color=COLORS["bg_medium"], hover_color=COLORS["accent"],
                     command=self.dep_fit_to_window).pack(side="right", padx=3, pady=6)
        ctk.CTkButton(dep_zoom_frame, text="−", width=35, height=28,
                     font=ctk.CTkFont(size=16, weight="bold"),
                     fg_color=COLORS["bg_medium"], hover_color=COLORS["accent"],
                     command=self.dep_zoom_out).pack(side="right", padx=3, pady=6)
        ctk.CTkButton(dep_zoom_frame, text="+", width=35, height=28,
                     font=ctk.CTkFont(size=16, weight="bold"),
                     fg_color=COLORS["bg_medium"], hover_color=COLORS["accent"],
                     command=self.dep_zoom_in).pack(side="right", padx=3, pady=6)
        self.dep_zoom_label = ctk.CTkLabel(dep_zoom_frame, text="100%",
                                           font=ctk.CTkFont(size=11),
                                           text_color=COLORS["text"])
        self.dep_zoom_label.pack(side="right", padx=10)

        dep_canvas_frame = ctk.CTkFrame(dep_viewer, fg_color=COLORS["bg_dark"],
                                        corner_radius=6)
        dep_canvas_frame.pack(fill="both", expand=True, padx=8, pady=(0, 8))

        from tkinter import Scrollbar as _SB, Canvas as _CV
        dep_v_scroll = _SB(dep_canvas_frame, orient="vertical")
        dep_v_scroll.pack(side="right", fill="y")
        dep_h_scroll = _SB(dep_canvas_frame, orient="horizontal")
        dep_h_scroll.pack(side="bottom", fill="x")

        self.dep_canvas = _CV(dep_canvas_frame, bg=COLORS["bg_dark"],
                              highlightthickness=0,
                              xscrollcommand=dep_h_scroll.set,
                              yscrollcommand=dep_v_scroll.set)
        self.dep_canvas.pack(side="left", fill="both", expand=True)
        dep_v_scroll.config(command=self.dep_canvas.yview)
        dep_h_scroll.config(command=self.dep_canvas.xview)

        self.dep_canvas.bind("<MouseWheel>", self.on_dep_mousewheel)
        self.dep_canvas.bind("<Button-4>", self.on_dep_mousewheel)
        self.dep_canvas.bind("<Button-5>", self.on_dep_mousewheel)
        self.dep_canvas.bind("<Shift-MouseWheel>", self.on_dep_horizontal_scroll)
        self.dep_canvas.bind("<Shift-Button-4>", self.on_dep_horizontal_scroll)
        self.dep_canvas.bind("<Shift-Button-5>", self.on_dep_horizontal_scroll)

    def browse_results(self):
        directory = filedialog.askdirectory(title="Select Results Directory")
        if directory:
            self.load_results(directory)
    
    def load_results(self, directory):
        self.results_dir = directory

        # Reset top-10 mode
        self._cfg_top10_active = False
        self.top10_btn.configure(
            text="🏆 Top 10 Complex",
            fg_color=COLORS["warning"],
            hover_color="#D4A020",
            text_color=COLORS["bg_dark"]
        )

        # Clear thumbnails
        for thumb in self.cfg_thumbnails:
            thumb.destroy()
        self.cfg_thumbnails.clear()

        # Load report
        report_file = os.path.join(directory, 'analysis_report.txt')
        self.report_text.configure(state="normal")
        self.report_text.delete("1.0", "end")

        if os.path.exists(report_file):
            with open(report_file, 'r') as f:
                self._all_report_text = f.read()
            self._build_file_sections()
            self._build_complexity_map()
            self.report_text.insert("1.0", self._all_report_text)
            self.app.log_message(f"Loaded report from {directory}")
        else:
            self._all_report_text = "No analysis_report.txt found in this directory."
            self._file_sections = {}
            self._complexity_map = {}
            self.report_text.insert("1.0", self._all_report_text)
        self.report_text.configure(state="disabled")

        # Update file filter combo
        file_choices = ["All Files"] + sorted(self._file_sections.keys())
        self.file_filter_combo.configure(values=file_choices)
        self.file_filter_var.set("All Files")
        self.file_search_entry.delete(0, "end")

        # Load CFG images — support stem__cfg_func.png (folder) and cfg_func.png (single)
        self._all_cfg_data = []
        all_stems = set()

        for png_path in sorted(Path(directory).glob("*.png")):
            m_folder = re.match(r'(.+?)__cfg_(.+)\.png', png_path.name)
            m_single = re.match(r'cfg_(.+)\.png', png_path.name)
            if m_folder:
                stem = m_folder.group(1)
                func_name = m_folder.group(2)
                all_stems.add(stem)
            elif m_single:
                stem = ""
                func_name = m_single.group(1)
            else:
                continue
            self._all_cfg_data.append((str(png_path), func_name, stem))

        # Populate file dropdown
        self.cfg_file_search.delete(0, "end")
        self.cfg_search_entry.delete(0, "end")

        self._apply_cfg_filters(auto_select_first=True)

        # Load Dependency Graph images — dep_<name>.png or stem__dep_<name>.png
        self._all_dep_data = []
        for png_path in sorted(Path(directory).glob("*.png")):
            m_folder = re.match(r'(.+?)__dep_(.+)\.png', png_path.name)
            m_single = re.match(r'dep_(.+)\.png', png_path.name)
            if m_folder:
                dep_name = f"{m_folder.group(1)}: {m_folder.group(2)}"
            elif m_single:
                dep_name = m_single.group(1)
            else:
                continue
            self._all_dep_data.append((str(png_path), dep_name))

        self.dep_search_entry.delete(0, "end")
        self._apply_dep_filters(auto_select_first=True)

    def _build_file_sections(self):
        """Parse the combined report into per-file sections."""
        self._file_sections = {}
        if not self._all_report_text:
            return
        # Match section headers: ═══...═══ / FILE: name.c / ═══...═══
        pattern = re.compile(
            r'[═]{30,}\n\s*FILE:\s*(.+?)\s*\n[═]{30,}',
            re.MULTILINE
        )
        matches = list(pattern.finditer(self._all_report_text))
        if not matches:
            return
        for i, m in enumerate(matches):
            filename = m.group(1).strip()
            start = m.start()
            end = matches[i + 1].start() if i + 1 < len(matches) else len(self._all_report_text)
            self._file_sections[filename] = self._all_report_text[start:end]

    def _build_complexity_map(self):
        """Parse all CYCLOMATIC COMPLEXITY SUMMARY tables from the report."""
        self._complexity_map = {}
        # Each row looks like:  functionName                   | 12           | High
        row_re = re.compile(
            r'^\s*([\w:~<>,\s*&]+?)\s*\|\s*(\d+)\s*\|',
            re.MULTILINE
        )
        for m in row_re.finditer(self._all_report_text):
            func = m.group(1).strip()
            complexity = int(m.group(2))
            # Keep highest if duplicated across files
            if func not in self._complexity_map or complexity > self._complexity_map[func]:
                self._complexity_map[func] = complexity

    def _filter_report(self, value=None):
        """Update report textbox to show the selected file section."""
        selected = self.file_filter_var.get()
        self.report_text.configure(state="normal")
        self.report_text.delete("1.0", "end")
        if selected == "All Files" or selected not in self._file_sections:
            self.report_text.insert("1.0", self._all_report_text or "No report loaded.")
        else:
            self.report_text.insert("1.0", self._file_sections[selected])
        self.report_text.configure(state="disabled")

    def _on_filter_search(self, event=None):
        """Filter the file dropdown options based on search entry text."""
        query = self.file_search_entry.get().lower()
        all_keys = sorted(self._file_sections.keys())
        if query:
            filtered = ["All Files"] + [f for f in all_keys if query in f.lower()]
        else:
            filtered = ["All Files"] + all_keys
        self.file_filter_combo.configure(values=filtered)

    def _jump_to_section(self, keyword):
        """Scroll the report textbox to the first occurrence of keyword and briefly highlight it."""
        tb = self.report_text._textbox  # underlying tk.Text widget
        tb.tag_remove("jump_hl", "1.0", "end")
        idx = tb.search(keyword, "1.0", nocase=True, stopindex="end")
        if not idx:
            # Try narrower match (first word of keyword)
            idx = tb.search(keyword.split()[0], "1.0", nocase=True, stopindex="end")
        if idx:
            tb.see(idx)
            line = idx.split(".")[0]
            tb.tag_add("jump_hl", f"{line}.0", f"{line}.end")
            tb.tag_config("jump_hl",
                          background=COLORS["accent"],
                          foreground=COLORS["text_bright"])
            self.after(1800, lambda: tb.tag_remove("jump_hl", "1.0", "end"))

    def _schedule_cfg_filter(self):
        """Debounce: cancel any pending rebuild and schedule a new one in 400 ms."""
        if self._cfg_filter_job is not None:
            self.after_cancel(self._cfg_filter_job)
        self._cfg_filter_job = self.after(400, self._apply_cfg_filters)

    def _apply_cfg_filters(self, auto_select_first=False):
        """Re-populate the CFG list based on active file filter and function search text."""
        self._cfg_filter_job = None
        MAX_ITEMS = 150

        file_query = self.cfg_file_search.get().lower().strip()
        func_query = self.cfg_search_entry.get().lower().strip()

        # Destroy existing list items
        for thumb in self.cfg_thumbnails:
            thumb.destroy()
        self.cfg_thumbnails.clear()

        # Collect matching entries without building widgets yet
        matches = []
        for png_path, func_name, stem in self._all_cfg_data:
            if file_query and file_query not in stem.lower():
                continue
            if func_query and func_query not in func_name.lower():
                continue
            matches.append((png_path, func_name, stem))

        total = len(matches)

        # ── Top-10 mode: sort by complexity, keep highest 10 ──
        if self._cfg_top10_active and self._complexity_map:
            matches.sort(
                key=lambda t: self._complexity_map.get(t[1], 0),
                reverse=True
            )
            matches = matches[:10]
            total = len(matches)

        visible = matches[:MAX_ITEMS]

        for png_path, func_name, stem in visible:
            if self._cfg_top10_active and func_name in self._complexity_map:
                display = f"[{self._complexity_map[func_name]}] {stem + ': ' if stem else ''}{func_name}"
            else:
                display = f"{stem}: {func_name}" if stem else func_name
            item = CFGListItem(self.cfg_gallery, png_path, display,
                               self.on_cfg_item_click)
            item.pack(fill="x", pady=2)
            self.cfg_thumbnails.append(item)

        hidden = total - len(visible)
        prefix = "🏆 Top 10" if self._cfg_top10_active else "📋 Functions"
        if hidden > 0:
            self.cfg_label.configure(
                text=f"{prefix} ({len(visible)} shown, {hidden} hidden — refine filter)"
            )
        else:
            self.cfg_label.configure(text=f"{prefix} ({total})")

        if auto_select_first and self.cfg_thumbnails:
            self.on_cfg_item_click(self.cfg_thumbnails[0])
        elif not self.cfg_thumbnails:
            self.cfg_name_label.configure(text="No matching functions")

    def _toggle_top10(self):
        """Toggle top-10-by-complexity mode and rebuild the list."""
        self._cfg_top10_active = not self._cfg_top10_active
        if self._cfg_top10_active:
            self.top10_btn.configure(
                text="📋 Show All",
                fg_color=COLORS["accent"],
                hover_color=COLORS["accent_hover"],
                text_color=COLORS["text_bright"]
            )
        else:
            self.top10_btn.configure(
                text="🏆 Top 10 Complex",
                fg_color=COLORS["warning"],
                hover_color="#D4A020",
                text_color=COLORS["bg_dark"]
            )
        self._apply_cfg_filters(auto_select_first=True)

    # ─── Dependency graph helpers ──────────────────────────────────────────

    def _schedule_dep_filter(self):
        if self._dep_filter_job is not None:
            self.after_cancel(self._dep_filter_job)
        self._dep_filter_job = self.after(400, self._apply_dep_filters)

    def _apply_dep_filters(self, auto_select_first=False):
        self._dep_filter_job = None
        query = self.dep_search_entry.get().lower().strip()

        for item in self.dep_thumbnails:
            item.destroy()
        self.dep_thumbnails.clear()

        matches = [
            (p, n) for p, n in self._all_dep_data
            if not query or query in n.lower()
        ]

        for png_path, dep_name in matches:
            item = DepListItem(self.dep_gallery, png_path, dep_name,
                               self.on_dep_item_click)
            item.pack(fill="x", pady=2)
            self.dep_thumbnails.append(item)

        self.dep_label.configure(text=f"🔗 Graphs ({len(matches)})")

        if auto_select_first and self.dep_thumbnails:
            self.on_dep_item_click(self.dep_thumbnails[0])
        elif not self.dep_thumbnails:
            self.dep_name_label.configure(text="No dependency graphs found")

    def on_dep_item_click(self, item):
        for t in self.dep_thumbnails:
            t.set_selected(False)
        item.set_selected(True)
        self.load_dep_image(item.image_path, item.dep_name)

    def load_dep_image(self, path, name):
        try:
            self.dep_current_image = Image.open(path)
            self.dep_name_label.configure(text=f"🔗 {name}")
            self.dep_zoom_level = 1.0
            self.dep_fit_to_window()
        except Exception as e:
            self.app.log_message(f"Error loading dep image: {e}")

    def render_dep_image(self):
        if not self.dep_current_image:
            return
        w = int(self.dep_current_image.width  * self.dep_zoom_level)
        h = int(self.dep_current_image.height * self.dep_zoom_level)
        resized = self.dep_current_image.resize((w, h), Image.LANCZOS)
        self.dep_photo = ImageTk.PhotoImage(resized)
        self.dep_canvas.delete("all")
        self.dep_canvas.create_image(0, 0, anchor="nw", image=self.dep_photo)
        self.dep_canvas.config(scrollregion=(0, 0, w, h))
        self.dep_zoom_label.configure(text=f"{int(self.dep_zoom_level * 100)}%")

    def dep_zoom_in(self):
        self.dep_zoom_level *= 1.25
        self.render_dep_image()

    def dep_zoom_out(self):
        self.dep_zoom_level /= 1.25
        if self.dep_zoom_level < 0.1:
            self.dep_zoom_level = 0.1
        self.render_dep_image()

    def dep_fit_to_window(self):
        if not self.dep_current_image:
            return
        self.update_idletasks()
        cw = self.dep_canvas.winfo_width()  or 600
        ch = self.dep_canvas.winfo_height() or 400
        self.dep_zoom_level = min(cw / self.dep_current_image.width,
                                   ch / self.dep_current_image.height) * 0.95
        self.render_dep_image()

    def dep_actual_size(self):
        self.dep_zoom_level = 1.0
        self.render_dep_image()

    def on_dep_mousewheel(self, event):
        if event.num == 4 or event.delta > 0:
            self.dep_canvas.yview_scroll(-3, "units")
        else:
            self.dep_canvas.yview_scroll(3, "units")

    def on_dep_horizontal_scroll(self, event):
        if event.num == 4 or event.delta > 0:
            self.dep_canvas.xview_scroll(-3, "units")
        else:
            self.dep_canvas.xview_scroll(3, "units")

    # ─── CFG helpers ───────────────────────────────────────────────────────

    def on_cfg_item_click(self, item):
        # Deselect all
        for t in self.cfg_thumbnails:
            t.set_selected(False)
        item.set_selected(True)

        self.load_cfg_image(item.image_path, item.function_name)
    
    def load_cfg_image(self, path, name):
        try:
            self.current_image = Image.open(path)
            self.cfg_name_label.configure(text=f"📊 {name}()")
            self.zoom_level = 1.0
            self.fit_to_window()
        except Exception as e:
            self.app.log_message(f"Error loading image: {e}")
    
    def render_image(self):
        if not self.current_image:
            return
        
        width = int(self.current_image.width * self.zoom_level)
        height = int(self.current_image.height * self.zoom_level)
        
        resized = self.current_image.resize((width, height), Image.LANCZOS)
        self.photo = ImageTk.PhotoImage(resized)
        
        self.canvas.delete("all")
        self.canvas.create_image(0, 0, anchor="nw", image=self.photo)
        self.canvas.config(scrollregion=(0, 0, width, height))
        
        # Update zoom label
        zoom_pct = int(self.zoom_level * 100)
        self.zoom_label.configure(text=f"{zoom_pct}%")
    
    def zoom_in(self):
        self.zoom_level *= 1.25
        self.render_image()
    
    def zoom_out(self):
        self.zoom_level /= 1.25
        if self.zoom_level < 0.1:
            self.zoom_level = 0.1
        self.render_image()
    
    def fit_to_window(self):
        if not self.current_image:
            return
        
        self.update_idletasks()
        canvas_width = self.canvas.winfo_width()
        canvas_height = self.canvas.winfo_height()
        
        if canvas_width < 10 or canvas_height < 10:
            canvas_width = 600
            canvas_height = 400
        
        width_ratio = canvas_width / self.current_image.width
        height_ratio = canvas_height / self.current_image.height
        
        self.zoom_level = min(width_ratio, height_ratio) * 0.95
        self.render_image()
    
    def actual_size(self):
        self.zoom_level = 1.0
        self.render_image()
    
    def on_mousewheel(self, event):
        # Vertical scroll
        if event.num == 4 or event.delta > 0:
            self.canvas.yview_scroll(-3, "units")
        elif event.num == 5 or event.delta < 0:
            self.canvas.yview_scroll(3, "units")
    
    def on_horizontal_scroll(self, event):
        # Horizontal scroll (Shift+wheel)
        if event.num == 4 or event.delta > 0:
            self.canvas.xview_scroll(-3, "units")
        elif event.num == 5 or event.delta < 0:
            self.canvas.xview_scroll(3, "units")
    
    def run_llm_analysis(self):
        """AI analysis entry-point.

        If ``function_names.txt`` exists in the loaded results directory the
        tool runs the dedicated AI pipeline (function duplication check via
        sentence-similarity + cyclomatic complexity table).  Otherwise it
        falls back to the Ollama LLM analysis of the static-analysis report.
        """
        names_file = (
            os.path.join(self.results_dir, 'function_names.txt')
            if self.results_dir else None
        )

        if names_file and os.path.exists(names_file):
            # ── AI analysis path ──────────────────────────────────────────
            with open(names_file) as fh:
                func_names = [ln.strip() for ln in fh if ln.strip()]

            self.llm_analyze_btn.configure(state="disabled", text="Analyzing...")
            self.llm_status.configure(text="Running...", text_color=COLORS["warning"])
            self.llm_text.delete("1.0", "end")
            self.llm_text.insert(
                "1.0",
                "🔄 Running AI analysis…\n\n"
                "  • Computing pairwise function-similarity matrix\n"
                "  • Building cyclomatic-complexity report\n\n"
                "This may take a moment (HuggingFace Inference API).",
            )
            thread = threading.Thread(
                target=self._run_ai_analysis_thread,
                args=(func_names, self.results_dir),
                daemon=True,
            )
            thread.start()
        else:
            # ── Ollama fallback ───────────────────────────────────────────
            report_content = self.report_text.get("1.0", "end").strip()
            if not report_content or "No analysis report" in report_content:
                self.llm_text.delete("1.0", "end")
                self.llm_text.insert(
                    "1.0",
                    "❌ No analysis report loaded.\n\n"
                    "Please run static analysis first or load existing results.\n\n"
                    "Tip: re-run the analysis to also generate function_names.txt\n"
                    "     which enables the dedicated AI duplication checker.",
                )
                return

            self.llm_analyze_btn.configure(state="disabled", text="Analyzing...")
            self.llm_status.configure(text="Running...", text_color=COLORS["warning"])
            self.llm_text.delete("1.0", "end")
            self.llm_text.insert(
                "1.0",
                "🔄 Running LLM analysis…\n\nThis may take a moment depending on your hardware.",
            )
            thread = threading.Thread(
                target=self._run_llm_thread, args=(report_content,), daemon=True
            )
            thread.start()

    # ── AI analysis thread ────────────────────────────────────────────────────

    def _run_ai_analysis_thread(self, func_names, results_dir):
        """Compute similarity matrix + complexity report and display results."""
        script_dir = os.path.dirname(os.path.abspath(__file__))
        out = []

        out.append("🤖 AI Function Analysis\n")
        out.append("=" * 60 + "\n\n")

        # ── 1. Cyclomatic complexity table ────────────────────────────
        out.append("📊 CYCLOMATIC COMPLEXITY SUMMARY\n")
        out.append("-" * 60 + "\n")
        out.append(f"  {'Function':<35} {'Complexity':>10}  Risk\n")
        out.append("  " + "-" * 55 + "\n")

        rows = []
        for fn in func_names:
            cx = self._complexity_map.get(fn)
            if cx is not None:
                risk = (
                    "Low      (≤5)"  if cx <= 5  else
                    "Moderate (≤10)" if cx <= 10 else
                    "High     (≤20)" if cx <= 20 else
                    "Very High (>20)"
                )
                rows.append((fn, cx, risk))

        if rows:
            rows.sort(key=lambda r: r[1], reverse=True)
            for fn, cx, risk in rows:
                flag = "⚠ " if cx > 10 else "  "
                out.append(f"  {flag}{fn:<33} {cx:>10}   {risk}\n")
        else:
            out.append(
                "  (No complexity data found — ensure the static analysis has\n"
                "   been run and the Cyclomatic Complexity section is present\n"
                "   in analysis_report.txt)\n"
            )
        out.append("\n")

        # ── 2. Function duplication / similarity check ────────────────
        out.append("🔁 FUNCTION DUPLICATION CHECK\n")
        out.append("-" * 60 + "\n")

        if len(func_names) < 2:
            out.append("  (Need at least 2 functions for similarity analysis)\n")
        else:
            try:
                py_script = os.path.join(script_dir, "FuncAnalisys2.py")
                proc = subprocess.run(
                    ["python3", py_script, "similarity"],
                    input="\n".join(func_names),
                    capture_output=True,
                    text=True,
                    timeout=120,
                    cwd=script_dir,
                )

                if proc.returncode != 0:
                    out.append(
                        f"  ⚠ FuncAnalisys2.py exited with code {proc.returncode}\n"
                        f"  {proc.stderr[:600]}\n"
                    )
                else:
                    try:
                        data = json.loads(proc.stdout)
                    except json.JSONDecodeError as exc:
                        out.append(f"  ⚠ JSON parse error: {exc}\n  Raw: {proc.stdout[:300]}\n")
                        data = None

                    if data and 'error' in data:
                        out.append(f"  ⚠ {data['error']}\n")
                    elif data:
                        matrix = data['matrix']
                        funcs  = data['functions']
                        n = len(funcs)

                        # Collect high-similarity pairs (above threshold, excluding diagonal)
                        THRESHOLD = 0.70
                        pairs = []
                        for i in range(n):
                            for j in range(i + 1, n):
                                score = matrix[i][j]
                                if score >= THRESHOLD:
                                    pairs.append((funcs[i], funcs[j], score))
                        pairs.sort(key=lambda p: p[2], reverse=True)

                        out.append(
                            f"  Compared {n} function(s) using sentence-transformer embeddings.\n"
                            f"  Similarity threshold: {THRESHOLD:.2f}  |  Method: cosine similarity\n\n"
                        )
                        if pairs:
                            out.append(
                                f"  ⚠  {len(pairs)} potentially duplicate pair(s) detected:\n\n"
                            )
                            out.append(
                                f"  {'Function A':<28} {'Function B':<28} {'Score':>6}  Bar\n"
                            )
                            out.append("  " + "-" * 72 + "\n")
                            for fa, fb, score in pairs[:30]:
                                bar = "█" * int(round(score * 10))
                                out.append(
                                    f"  {fa:<28} {fb:<28}  {score:.3f}  {bar}\n"
                                )
                            if len(pairs) > 30:
                                out.append(f"  … and {len(pairs) - 30} more pair(s)\n")
                        else:
                            out.append(
                                "  ✓ No significant duplication detected "
                                f"(threshold {THRESHOLD:.2f})\n"
                            )

                        # Overall stats
                        all_scores = [
                            matrix[i][j]
                            for i in range(n) for j in range(i + 1, n)
                        ]
                        if all_scores:
                            avg = sum(all_scores) / len(all_scores)
                            mx  = max(all_scores)
                            out.append(
                                f"\n  Max pair similarity : {mx:.3f}\n"
                                f"  Avg pair similarity : {avg:.3f}\n"
                            )

            except subprocess.TimeoutExpired:
                out.append("  ⚠ Similarity analysis timed out (120 s)\n")
            except FileNotFoundError:
                out.append(
                    "  ⚠ FuncAnalisys2.py not found — make sure it exists in:\n"
                    f"    {script_dir}\n"
                )
            except Exception as exc:
                out.append(f"  ⚠ Unexpected error: {exc}\n")

        out.append("\n")

        # ── 3. File inventory ─────────────────────────────────────────
        batch_files = sorted(
            glob.glob(os.path.join(results_dir, 'function_implementations_batch_*.txt'))
        )
        out.append("📁 Generated Files\n")
        out.append("-" * 60 + "\n")
        names_path = os.path.join(results_dir, 'function_names.txt')
        out.append(
            f"  function_names.txt{'':<22}— {len(func_names)} function name(s)\n"
        )
        for bf in batch_files:
            sz = os.path.getsize(bf)
            out.append(
                f"  {os.path.basename(bf):<40} — {sz:,} chars (~{sz // 4:,} tokens)\n"
            )
        if not batch_files:
            out.append("  (No batch files found — re-run the analysis)\n")

        final = "".join(out)
        self.app.after(0, lambda: self._update_llm_result(final, success=True))

    # ── Custom GGUF helpers ───────────────────────────────────────────────────

    def _on_llm_model_change(self, value):
        """Show/hide config rows depending on model selection."""
        self._stop_ollama()  # kill any running inference when switching models

        # GGUF row
        if value == "custom":
            self.gguf_frame.pack(fill="x", padx=8, pady=(0, 4), before=self.llm_text)
        else:
            self.gguf_frame.pack_forget()

        # API row
        if value in _API_URL_DEFAULTS:
            url_default, model_default = _API_URL_DEFAULTS[value]
            if url_default:
                self.api_url_var.set(url_default)
            if model_default:
                self.api_model_var.set(model_default)
            self.api_frame.pack(fill="x", padx=8, pady=(0, 4), before=self.llm_text)
        else:
            self.api_frame.pack_forget()

    def _stop_ollama(self):
        """Kill the active ollama run process and unload the model from memory."""
        if self._ollama_proc is not None:
            try:
                self._ollama_proc.kill()
                self._ollama_proc.wait(timeout=5)
            except Exception:
                pass
            self._ollama_proc = None

        if self._ollama_model_loaded:
            model_to_stop = self._ollama_model_loaded
            self._ollama_model_loaded = None
            try:
                # Ask ollama to unload the model from VRAM/RAM
                subprocess.run(
                    ['ollama', 'stop', model_to_stop],
                    capture_output=True, timeout=10
                )
                # If it was an imported custom GGUF, remove it entirely
                if model_to_stop == 'custom_gguf':
                    subprocess.run(
                        ['ollama', 'rm', 'custom_gguf'],
                        capture_output=True, timeout=10
                    )
            except Exception:
                pass

    def _browse_gguf(self):
        path = filedialog.askopenfilename(
            title="Select GGUF model file",
            filetypes=[("GGUF models", "*.gguf"), ("All files", "*.*")]
        )
        if path:
            self.gguf_path_var.set(path)

    def _build_analysis_prompt(self, report_content):
        return (
            "You are a code analysis expert. Analyze the following static analysis "
            "report from a C code analyzer.\n\n"
            "Provide insights on:\n"
            "1. **Code Quality Summary** - Overall assessment of the code\n"
            "2. **Potential Issues** - Any bugs, vulnerabilities, or problems identified\n"
            "3. **Complexity Analysis** - Comment on the cyclomatic complexity values\n"
            "4. **Security Concerns** - Any security-related observations\n"
            "5. **Recommendations** - Suggestions for improvement\n\n"
            "Be concise but thorough. Format your response with clear sections.\n\n"
            "=== STATIC ANALYSIS REPORT ===\n"
            f"{report_content[:4000]}\n"
            "=== END REPORT ===\n\n"
            "Provide your analysis:"
        )

    def _call_api_llm(self, prompt):
        """Send prompt to an OpenAI-compatible or Anthropic REST API.
        Returns (response_text, error_text).  Uses only stdlib urllib."""
        import json, urllib.request, urllib.error

        model_type = self.llm_model_var.get()   # e.g. "api/openai"
        api_key    = self.api_key_var.get().strip()
        model_name = self.api_model_var.get().strip()
        base_url   = self.api_url_var.get().strip()

        if not api_key:
            return None, "API key is required. Enter it in the API Key field."
        if not model_name:
            return None, "Model name is required. Enter it in the Model name field."
        if not base_url:
            return None, "Base URL is required. Enter the full endpoint URL."

        headers = {"Content-Type": "application/json"}

        # ── Anthropic Messages API ────────────────────────────────────────
        if model_type == "api/anthropic":
            headers["x-api-key"]         = api_key
            headers["anthropic-version"] = "2023-06-01"
            body = json.dumps({
                "model": model_name,
                "max_tokens": 2048,
                "messages": [{"role": "user", "content": prompt}]
            }).encode()
            req = urllib.request.Request(base_url, data=body, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(req, timeout=120) as resp:
                    data = json.loads(resp.read().decode())
                text = data["content"][0]["text"]
                return text, None
            except urllib.error.HTTPError as e:
                err_body = e.read().decode(errors="replace")
                return None, f"HTTP {e.code}: {err_body}"
            except Exception as e:
                return None, str(e)

        # ── OpenAI / OpenAI-compatible Chat Completions API ───────────────
        else:
            headers["Authorization"] = f"Bearer {api_key}"
            body = json.dumps({
                "model": model_name,
                "messages": [
                    {"role": "system", "content": "You are a code analysis expert."},
                    {"role": "user",   "content": prompt}
                ],
                "max_tokens": 2048,
                "temperature": 0.3
            }).encode()
            req = urllib.request.Request(base_url, data=body, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(req, timeout=120) as resp:
                    data = json.loads(resp.read().decode())
                text = data["choices"][0]["message"]["content"]
                return text, None
            except urllib.error.HTTPError as e:
                err_body = e.read().decode(errors="replace")
                return None, f"HTTP {e.code}: {err_body}"
            except Exception as e:
                return None, str(e)

    # ── Ollama / API thread ───────────────────────────────────────────────────

    def _run_llm_thread(self, report_content):
        """Thread worker for LLM analysis"""
        try:
            model = self.llm_model_var.get()

            # ── Remote API path ───────────────────────────────────────────
            if model.startswith("api/"):
                prompt = self._build_analysis_prompt(report_content)
                self.app.after(0, lambda: self.llm_status.configure(
                    text="Calling API...", text_color=COLORS["warning"]))
                response, err = self._call_api_llm(prompt)
                if err:
                    self.app.after(0, lambda: self._update_llm_result(
                        f"API error:\n{err}", success=False))
                else:
                    self.app.after(0, lambda: self._update_llm_result(
                        response, success=True))
                return

            # ── Custom GGUF: import into Ollama then run ───────────────────
            if model == "custom":
                gguf_path = self.gguf_path_var.get().strip()
                if not gguf_path or not os.path.isfile(gguf_path):
                    self.app.after(0, lambda: self._update_llm_result(
                        "No valid GGUF file selected.\n\n"
                        "Choose 'custom' in the Model dropdown, then use the "
                        "Browse button to pick a .gguf file.",
                        success=False
                    ))
                    return

                model_name = "custom_gguf"
                self.app.after(0, lambda: self.llm_status.configure(
                    text="Importing GGUF...", text_color=COLORS["warning"]))

                # Write a minimal Modelfile
                import tempfile
                modelfile_content = f"FROM {gguf_path}\n"
                with tempfile.NamedTemporaryFile(mode='w', suffix='Modelfile',
                                                delete=False) as mf:
                    mf.write(modelfile_content)
                    modelfile_path = mf.name

                try:
                    create_result = subprocess.run(
                        ['ollama', 'create', model_name, '-f', modelfile_path],
                        capture_output=True, text=True, timeout=300
                    )
                finally:
                    os.unlink(modelfile_path)

                if create_result.returncode != 0:
                    err = create_result.stderr.strip() or create_result.stdout.strip()
                    self.app.after(0, lambda: self._update_llm_result(
                        f"Failed to import GGUF into Ollama:\n{err}\n\n"
                        "Make sure Ollama is installed and the GGUF file is valid.",
                        success=False
                    ))
                    return

                self.app.after(0, lambda: self.llm_status.configure(
                    text="Running...", text_color=COLORS["warning"]))
            else:
                # Extract model name for standard ollama models
                if model.startswith("ollama/"):
                    model_name = model.split("/")[1]
                else:
                    model_name = model
            
            # Build the prompt
            prompt = self._build_analysis_prompt(report_content)

            # Try to call ollama via Popen so we can kill it if needed
            self._ollama_model_loaded = model_name
            self._ollama_proc = subprocess.Popen(
                ['ollama', 'run', model_name],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            stdout, stderr = self._ollama_proc.communicate(input=prompt, timeout=120)
            returncode = self._ollama_proc.returncode
            self._ollama_proc = None

            if returncode == 0:
                response = stdout.strip()
                self.app.after(0, lambda: self._update_llm_result(response, success=True))
            else:
                error_msg = stderr.strip() or "Unknown error"
                self.app.after(0, lambda: self._update_llm_result(
                    f"Error running LLM:\n{error_msg}\n\n" +
                    "Make sure Ollama is installed and the model is available:\n" +
                    f"  ollama pull {model_name}",
                    success=False
                ))
                
        except subprocess.TimeoutExpired:
            if self._ollama_proc:
                try:
                    self._ollama_proc.kill()
                except Exception:
                    pass
                self._ollama_proc = None
            self.app.after(0, lambda: self._update_llm_result(
                "Analysis timed out (120s limit).\n\n" +
                "Try a smaller/faster model or reduce the input size.",
                success=False
            ))
        except FileNotFoundError:
            self.app.after(0, lambda: self._update_llm_result(
                "❌ Ollama not found!\n\n" +
                "Please install Ollama:\n" +
                "  curl -fsSL https://ollama.ai/install.sh | sh\n\n" +
                "Then pull a model:\n" +
                "  ollama pull codellama",
                success=False
            ))
        except Exception as e:
            self.app.after(0, lambda: self._update_llm_result(
                f"❌ Error: {str(e)}",
                success=False
            ))
    
    def _update_llm_result(self, text, success=True):
        """Update the LLM result text box"""
        self.llm_text.delete("1.0", "end")
        self.llm_text.insert("1.0", text)
        self.llm_analyze_btn.configure(state="normal", text="▶ Analyze")
        
        if success:
            self.llm_status.configure(text="✓ Complete", text_color=COLORS["success"])
            self.app.log_message("LLM analysis completed")
        else:
            self.llm_status.configure(text="✗ Error", text_color=COLORS["error"])
            self.app.log_message("LLM analysis failed")


class LogPanel(ctk.CTkFrame):
    """Bottom panel for log output"""
    def __init__(self, master, **kwargs):
        super().__init__(master, **kwargs)
        self.configure(fg_color=COLORS["bg_dark"], height=150)
        
        self.setup_ui()
    
    def setup_ui(self):
        # Header
        header = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=30)
        header.pack(fill="x", padx=2, pady=(2, 0))
        header.pack_propagate(False)
        
        ctk.CTkLabel(header, text="📋 Console",
                    font=ctk.CTkFont(size=11, weight="bold"),
                    text_color=COLORS["text"]).pack(side="left", padx=10, pady=4)
        
        ctk.CTkButton(header, text="Clear", width=50, height=22,
                     font=ctk.CTkFont(size=10),
                     fg_color=COLORS["bg_light"],
                     hover_color=COLORS["error"],
                     command=self.clear).pack(side="right", padx=5, pady=4)
        
        # Log text
        self.log_text = ctk.CTkTextbox(self, 
                                       font=ctk.CTkFont(family="Consolas", size=11),
                                       fg_color=COLORS["bg_medium"],
                                       text_color=COLORS["text"])
        self.log_text.pack(fill="both", expand=True, padx=2, pady=2)
        
        # Initial message
        self.log_text.insert("1.0", f"[{self.timestamp()}] Static Analysis Tool initialized\n")
        self.log_text.insert("end", f"[{self.timestamp()}] Ready. Select a C file to analyze.\n")
    
    def timestamp(self):
        return datetime.now().strftime("%H:%M:%S")
    
    def log(self, message):
        self.log_text.insert("end", f"{message}\n")
        self.log_text.see("end")
    
    def clear(self):
        self.log_text.delete("1.0", "end")
        self.log(f"[{self.timestamp()}] Console cleared")


class StaticAnalysisApp(ctk.CTk):
    """Main application window"""
    def __init__(self):
        super().__init__()
        
        self.title("Static Analysis Tool")
        self.geometry("1400x900")
        self.configure(fg_color=COLORS["bg_dark"])
        
        # Set window icon (if available)
        try:
            self.iconbitmap("icon.ico")
        except:
            pass
        
        self.setup_ui()
        self.protocol("WM_DELETE_WINDOW", self._on_close)
    
    def _on_close(self):
        try:
            self.results_panel._stop_ollama()
        except Exception:
            pass
        self.destroy()
    
    def setup_ui(self):
        # Menu bar simulation
        menubar = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=32)
        menubar.pack(fill="x")
        menubar.pack_propagate(False)
        
        ctk.CTkLabel(menubar, text="⚡ Static Analysis Tool",
                    font=ctk.CTkFont(size=13, weight="bold"),
                    text_color=COLORS["accent"]).pack(side="left", padx=15, pady=5)
        
        # Main content area
        main_frame = ctk.CTkFrame(self, fg_color=COLORS["bg_dark"])
        main_frame.pack(fill="both", expand=True, padx=5, pady=5)
        
        # Create paned layout
        # Left panel (Project Explorer)
        self.analysis_panel = AnalysisPanel(main_frame, self)
        self.analysis_panel.pack(side="left", fill="y", padx=(0, 2))
        self.analysis_panel.configure(width=320)
        self.analysis_panel.pack_propagate(False)
        
        # Right content area
        right_frame = ctk.CTkFrame(main_frame, fg_color=COLORS["bg_dark"])
        right_frame.pack(side="left", fill="both", expand=True)
        
        # Results panel
        self.results_panel = ResultsPanel(right_frame, self)
        self.results_panel.pack(fill="both", expand=True)
        
        # Log panel at bottom
        self.log_panel = LogPanel(self)
        self.log_panel.pack(fill="x", padx=5, pady=(0, 5))
        
        # Status bar
        statusbar = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=24)
        statusbar.pack(fill="x", side="bottom")
        statusbar.pack_propagate(False)
        
        ctk.CTkLabel(statusbar, text="Ready",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text"]).pack(side="left", padx=10, pady=3)
        
        ctk.CTkLabel(statusbar, text="v1.0 | IntelliJ-Style",
                    font=ctk.CTkFont(size=10),
                    text_color=COLORS["text"]).pack(side="right", padx=10, pady=3)
    
    def log_message(self, message):
        self.log_panel.log(message)


def main():
    app = StaticAnalysisApp()
    app.mainloop()


if __name__ == "__main__":
    main()
