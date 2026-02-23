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
        
        # Analyze button
        self.analyze_btn = ctk.CTkButton(self, text="▶  Run Analysis",
                                         font=ctk.CTkFont(size=14, weight="bold"),
                                         fg_color=COLORS["success"],
                                         hover_color="#5AAC64",
                                         height=40,
                                         command=self.start_analysis)
        self.analyze_btn.pack(fill="x", padx=5, pady=10)
        
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
    
    def start_analysis(self):
        if not self.selected_file:
            messagebox.showwarning("No File", "Please select a C file first")
            return
        
        self.analyze_btn.configure(state="disabled", text="Analyzing...")
        self.progress.start()
        
        thread = threading.Thread(target=self.run_analysis, daemon=True)
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
                
                if result.stdout:
                    # Save report
                    report_file = os.path.join(self.output_dir, 'analysis_report.txt')
                    with open(report_file, 'w') as f:
                        f.write(result.stdout)
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
        self.analyze_btn.configure(state="normal", text="▶  Run Analysis")
        self.progress.stop()
        self.progress.set(0)


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
        self.cfg_label.pack(pady=8, padx=5)
        
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
                self.report_text.insert("1.0", f.read())
            self.app.log_message(f"📄 Loaded report from {directory}")
        else:
            self.report_text.insert("1.0", "No analysis_report.txt found in this directory.")
        
        # Load CFG images
        png_files = sorted(Path(directory).glob("cfg_*.png"))
        
        if png_files:
            self.cfg_label.configure(text=f"📋 Functions ({len(png_files)})")
            
            for png_path in png_files:
                match = re.match(r'cfg_(.+)\.png', png_path.name)
                func_name = match.group(1) if match else png_path.stem
                
                item = CFGListItem(self.cfg_gallery, str(png_path), func_name,
                                   self.on_cfg_item_click)
                item.pack(fill="x", pady=2)
                self.cfg_thumbnails.append(item)
            
            # Select first
            if self.cfg_thumbnails:
                self.on_cfg_item_click(self.cfg_thumbnails[0])
        else:
            self.cfg_label.configure(text="No CFG images found")
    
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
