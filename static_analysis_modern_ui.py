#!/usr/bin/env python3
"""
Static Analysis Tool - Modern GUI Interface
IntelliJ-inspired dark theme using CustomTkinter
"""

import os
import sys
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
            
            self.app.log_message("\n" + "━" * 50)
            self.app.log_message("🚀 Starting Analysis...")
            self.app.log_message(f"   Input: {self.selected_file}")
            self.app.log_message(f"   Output: {self.output_dir}")
            self.app.log_message("━" * 50)
            
            script_dir = os.path.dirname(os.path.abspath(__file__))
            
            # Step 1: Preprocess
            self.app.log_message("\n[1/3] 🔄 Preprocessing...")
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
                    self.app.log_message("      ✓ Preprocessing complete")
                else:
                    self.app.log_message("      ⚠ Preprocessing skipped")
                    preprocessed_file = self.selected_file
            else:
                preprocessed_file = self.selected_file
            
            # Step 2: Compile Java
            self.app.log_message("\n[2/3] 🔨 Checking Java compilation...")
            main_class = os.path.join(script_dir, "Main.class")
            main_java = os.path.join(script_dir, "Main.java")
            
            if not os.path.exists(main_class) or \
               os.path.getmtime(main_java) > os.path.getmtime(main_class):
                self.app.log_message("      Compiling Java files...")
                result = subprocess.run(
                    ['javac', '*.java'],
                    capture_output=True, text=True,
                    cwd=script_dir, shell=True
                )
                if result.returncode == 0:
                    self.app.log_message("      ✓ Compilation complete")
                else:
                    raise Exception(f"Compilation failed: {result.stderr}")
            else:
                self.app.log_message("      ✓ Already compiled")
            
            # Step 3: Run analysis
            self.app.log_message("\n[3/3] 🔍 Running static analysis...")
            
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
                    self.app.log_message("      ✓ Analysis report saved")
                
                # Move output files
                moved = 0
                for pattern in ['cfg_*.dot', 'cfg_*.png']:
                    for file in glob.glob(pattern):
                        dest = os.path.join(self.output_dir, os.path.basename(file))
                        os.rename(file, dest)
                        moved += 1
                
                self.app.log_message(f"      ✓ Moved {moved} files")
                
            finally:
                os.chdir(original_dir)
            
            # Load results
            self.app.log_message("\n" + "━" * 50)
            self.app.log_message("✅ ANALYSIS COMPLETE")
            self.app.log_message("━" * 50)
            
            # Update results panel
            self.app.after(100, lambda: self.app.results_panel.load_results(self.output_dir))
            
        except Exception as e:
            self.app.log_message(f"\n❌ Error: {str(e)}")
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
                self.app.log_message("❌ No .c files found in the selected folder.")
                return

            if not self.output_dir:
                self.output_dir = os.path.join(self.selected_folder, "analysis_results")
            os.makedirs(self.output_dir, exist_ok=True)

            self.app.log_message("\n" + "━" * 50)
            self.app.log_message(f"🗂  Folder Analysis — {len(c_files)} file(s) found")
            self.app.log_message(f"   Folder : {self.selected_folder}")
            self.app.log_message(f"   Output : {self.output_dir}")
            self.app.log_message("━" * 50)

            script_dir = os.path.dirname(os.path.abspath(__file__))

            # Ensure Java is compiled once
            self.app.log_message("\n🔨 Checking Java compilation...")
            main_class = os.path.join(script_dir, "Main.class")
            main_java  = os.path.join(script_dir, "Main.java")
            if not os.path.exists(main_class) or \
               os.path.getmtime(main_java) > os.path.getmtime(main_class):
                result = subprocess.run(
                    ['javac', '*.java'],
                    capture_output=True, text=True, cwd=script_dir, shell=True
                )
                if result.returncode != 0:
                    raise Exception(f"Compilation failed: {result.stderr}")
                self.app.log_message("   ✓ Compiled")
            else:
                self.app.log_message("   ✓ Already compiled")

            NUM_THREADS = 4
            if len(c_files) > 12:
                # ── Parallel path ──────────────────────────────────────────
                self.app.log_message(f"\n🚀 Parallel analysis — {NUM_THREADS} threads")
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
                    self.app.log_message(f"\n[{idx}/{len(c_files)}] 🔍 {c_file.name}")
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
                        for pattern in ['cfg_*.dot', 'cfg_*.png']:
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

                        self.app.log_message(f"   ✓ Done ({moved} CFG file(s) saved)")

                    except subprocess.TimeoutExpired:
                        combined_sections.append(f"FILE: {c_file.name}\n❌ Timed out\n\n")
                        self.app.log_message(f"   ⚠ Timed out")
                    except Exception as e:
                        combined_sections.append(f"FILE: {c_file.name}\n❌ Error: {e}\n\n")
                        self.app.log_message(f"   ❌ Error: {e}")
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

            self.app.log_message("\n" + "━" * 50)
            self.app.log_message(f"✅ FOLDER ANALYSIS COMPLETE")
            self.app.log_message(f"   {len(c_files)} file(s) · {errors_total} error(s) · {warnings_total} warning(s)")
            self.app.log_message("━" * 50)

            self.app.after(100, lambda: self.app.results_panel.load_results(self.output_dir))

        except Exception as e:
            self.app.log_message(f"\n❌ Error: {str(e)}")
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

                # Move CFG files to shared output_dir under a lock to avoid races
                stem = c_file.stem
                moved = 0
                with lock:
                    for pattern in ['cfg_*.dot', 'cfg_*.png']:
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
                self.app.log_message(f"   [{idx}/{total}] ✓ {c_file.name} ({moved} CFG file(s))")

        except subprocess.TimeoutExpired:
            with lock:
                results.append((idx, f"FILE: {c_file.name}\n❌ Timed out\n\n", 0, 0))
            self.app.log_message(f"   [{idx}/{total}] ⚠ {c_file.name} — Timed out")
        except Exception as e:
            with lock:
                results.append((idx, f"FILE: {c_file.name}\n❌ Error: {e}\n\n", 0, 0))
            self.app.log_message(f"   [{idx}/{total}] ❌ {c_file.name} — {e}")

    def _analyze_chunk(self, chunk, total, script_dir, lock, results):
        """Process a list of (idx, c_file) pairs sequentially (runs in a worker thread)."""
        for idx, c_file in chunk:
            self._analyze_one_file(c_file, idx, total, script_dir, lock, results)


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

        # File filter bar
        filter_frame = ctk.CTkFrame(left_report_frame, fg_color="transparent", height=34)
        filter_frame.pack(fill="x", padx=8, pady=(0, 4))
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

        self.report_text = ctk.CTkTextbox(left_report_frame, 
                                          font=ctk.CTkFont(family="Consolas", size=11),
                                          fg_color=COLORS["bg_dark"],
                                          text_color=COLORS["text"])
        self.report_text.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        self.report_text.insert("1.0", "No analysis report loaded.\n\nRun an analysis or load existing results.")
        
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
        self.llm_model_combo = ctk.CTkComboBox(llm_settings, width=200,
                                               values=["ollama/codellama", 
                                                      "ollama/llama3",
                                                      "ollama/mistral",
                                                      "ollama/deepseek-coder",
                                                      "custom"],
                                               variable=self.llm_model_var,
                                               font=ctk.CTkFont(size=11))
        self.llm_model_combo.pack(side="left", padx=5)
        
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
    
    def browse_results(self):
        directory = filedialog.askdirectory(title="Select Results Directory")
        if directory:
            self.load_results(directory)
    
    def load_results(self, directory):
        self.results_dir = directory

        # Clear thumbnails
        for thumb in self.cfg_thumbnails:
            thumb.destroy()
        self.cfg_thumbnails.clear()

        # Load report
        report_file = os.path.join(directory, 'analysis_report.txt')
        self.report_text.delete("1.0", "end")

        if os.path.exists(report_file):
            with open(report_file, 'r') as f:
                self._all_report_text = f.read()
            self._build_file_sections()
            self.report_text.insert("1.0", self._all_report_text)
            self.app.log_message(f"📄 Loaded report from {directory}")
        else:
            self._all_report_text = "No analysis_report.txt found in this directory."
            self._file_sections = {}
            self.report_text.insert("1.0", self._all_report_text)

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

    def _filter_report(self, value=None):
        """Update report textbox to show the selected file section."""
        selected = self.file_filter_var.get()
        self.report_text.delete("1.0", "end")
        if selected == "All Files" or selected not in self._file_sections:
            self.report_text.insert("1.0", self._all_report_text or "No report loaded.")
        else:
            self.report_text.insert("1.0", self._file_sections[selected])

    def _on_filter_search(self, event=None):
        """Filter the file dropdown options based on search entry text."""
        query = self.file_search_entry.get().lower()
        all_keys = sorted(self._file_sections.keys())
        if query:
            filtered = ["All Files"] + [f for f in all_keys if query in f.lower()]
        else:
            filtered = ["All Files"] + all_keys
        self.file_filter_combo.configure(values=filtered)
    
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
        visible = matches[:MAX_ITEMS]

        for png_path, func_name, stem in visible:
            display = f"{stem}: {func_name}" if stem else func_name
            item = CFGListItem(self.cfg_gallery, png_path, display,
                               self.on_cfg_item_click)
            item.pack(fill="x", pady=2)
            self.cfg_thumbnails.append(item)

        hidden = total - len(visible)
        if hidden > 0:
            self.cfg_label.configure(
                text=f"📋 Functions ({len(visible)} shown, {hidden} hidden — refine filter)"
            )
        else:
            self.cfg_label.configure(text=f"📋 Functions ({total})")

        if auto_select_first and self.cfg_thumbnails:
            self.on_cfg_item_click(self.cfg_thumbnails[0])
        elif not self.cfg_thumbnails:
            self.cfg_name_label.configure(text="No matching functions")

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
            self.app.log_message(f"❌ Error loading image: {e}")
    
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
        """Run LLM analysis on the current code"""
        # Get the analysis report content
        report_content = self.report_text.get("1.0", "end").strip()
        
        if not report_content or "No analysis report" in report_content:
            self.llm_text.delete("1.0", "end")
            self.llm_text.insert("1.0", "❌ No analysis report loaded.\n\nPlease run static analysis first or load existing results.")
            return
        
        # Disable button and show progress
        self.llm_analyze_btn.configure(state="disabled", text="Analyzing...")
        self.llm_status.configure(text="Running...", text_color=COLORS["warning"])
        self.llm_text.delete("1.0", "end")
        self.llm_text.insert("1.0", "🔄 Running LLM analysis...\n\nThis may take a moment depending on your hardware.")
        
        # Run in thread
        thread = threading.Thread(target=self._run_llm_thread, args=(report_content,), daemon=True)
        thread.start()
    
    def _run_llm_thread(self, report_content):
        """Thread worker for LLM analysis"""
        try:
            model = self.llm_model_var.get()
            
            # Extract model name for ollama
            if model.startswith("ollama/"):
                model_name = model.split("/")[1]
            else:
                model_name = model
            
            # Build the prompt
            prompt = f"""You are a code analysis expert. Analyze the following static analysis report from a C code analyzer.

Provide insights on:
1. **Code Quality Summary** - Overall assessment of the code
2. **Potential Issues** - Any bugs, vulnerabilities, or problems identified
3. **Complexity Analysis** - Comment on the cyclomatic complexity values
4. **Security Concerns** - Any security-related observations
5. **Recommendations** - Suggestions for improvement

Be concise but thorough. Format your response with clear sections.

=== STATIC ANALYSIS REPORT ===
{report_content[:4000]}
=== END REPORT ===

Provide your analysis:"""

            # Try to call ollama
            result = subprocess.run(
                ['ollama', 'run', model_name],
                input=prompt,
                capture_output=True,
                text=True,
                timeout=120
            )
            
            if result.returncode == 0:
                response = result.stdout.strip()
                self.app.after(0, lambda: self._update_llm_result(response, success=True))
            else:
                error_msg = result.stderr.strip() or "Unknown error"
                self.app.after(0, lambda: self._update_llm_result(
                    f"❌ Error running LLM:\n{error_msg}\n\n" +
                    "Make sure Ollama is installed and the model is available:\n" +
                    f"  ollama pull {model_name}", 
                    success=False
                ))
                
        except subprocess.TimeoutExpired:
            self.app.after(0, lambda: self._update_llm_result(
                "❌ Analysis timed out (120s limit).\n\n" +
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
            self.app.log_message("🤖 LLM analysis completed")
        else:
            self.llm_status.configure(text="✗ Error", text_color=COLORS["error"])
            self.app.log_message("❌ LLM analysis failed")


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
    
    def setup_ui(self):
        # Menu bar simulation
        menubar = ctk.CTkFrame(self, fg_color=COLORS["bg_medium"], height=32)
        menubar.pack(fill="x")
        menubar.pack_propagate(False)
        
        ctk.CTkLabel(menubar, text="⚡ Static Analysis Tool",
                    font=ctk.CTkFont(size=13, weight="bold"),
                    text_color=COLORS["accent"]).pack(side="left", padx=15, pady=5)
        
        # Theme toggle
        self.theme_var = ctk.StringVar(value="dark")
        ctk.CTkSwitch(menubar, text="Light Mode", 
                     variable=self.theme_var, onvalue="light", offvalue="dark",
                     command=self.toggle_theme,
                     font=ctk.CTkFont(size=11)).pack(side="right", padx=15, pady=5)
        
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
    
    def toggle_theme(self):
        if self.theme_var.get() == "light":
            ctk.set_appearance_mode("light")
        else:
            ctk.set_appearance_mode("dark")
    
    def log_message(self, message):
        self.log_panel.log(message)


def main():
    app = StaticAnalysisApp()
    app.mainloop()


if __name__ == "__main__":
    main()
