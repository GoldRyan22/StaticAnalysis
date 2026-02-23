#!/usr/bin/env python3
"""
Static Analysis Tool - GUI Interface
Provides a graphical interface to analyze C files and view results
"""

import os
import sys
import subprocess
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
from pathlib import Path
from PIL import Image, ImageTk
import threading
import glob
import re


class AnalysisWindow(tk.Toplevel):
    """First window: Select and analyze C files"""
    
    def __init__(self, parent):
        super().__init__(parent)
        self.parent = parent
        self.title("Static Analysis Tool - Analyze C File")
        self.geometry("900x700")
        
        self.selected_file = None
        self.output_dir = None
        self.is_analyzing = False
        
        self.setup_ui()
        
    def setup_ui(self):
        """Setup the analysis window UI"""
        # Main container
        main_frame = ttk.Frame(self, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)
        main_frame.columnconfigure(0, weight=1)
        main_frame.rowconfigure(3, weight=1)
        
        # Title
        title_label = ttk.Label(main_frame, text="Static Analysis Tool", 
                               font=('Arial', 16, 'bold'))
        title_label.grid(row=0, column=0, columnspan=2, pady=(0, 20))
        
        # File selection section
        file_frame = ttk.LabelFrame(main_frame, text="Select C File to Analyze", padding="10")
        file_frame.grid(row=1, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 10))
        file_frame.columnconfigure(1, weight=1)
        
        self.file_label = ttk.Label(file_frame, text="No file selected", 
                                    foreground="gray")
        self.file_label.grid(row=0, column=0, columnspan=2, sticky=tk.W, pady=(0, 10))
        
        ttk.Button(file_frame, text="Browse Files", 
                  command=self.browse_file).grid(row=1, column=0, padx=(0, 5))
        ttk.Button(file_frame, text="Browse Folder", 
                  command=self.browse_folder).grid(row=1, column=1, sticky=tk.W)
        
        # Output directory section
        output_frame = ttk.LabelFrame(main_frame, text="Output Directory", padding="10")
        output_frame.grid(row=2, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(0, 10))
        output_frame.columnconfigure(1, weight=1)
        
        self.output_label = ttk.Label(output_frame, text="Default: ./analysis_results", 
                                      foreground="gray")
        self.output_label.grid(row=0, column=0, columnspan=2, sticky=tk.W, pady=(0, 10))
        
        ttk.Button(output_frame, text="Select Output Directory", 
                  command=self.browse_output_dir).grid(row=1, column=0, sticky=tk.W)
        
        # Analysis output section
        log_frame = ttk.LabelFrame(main_frame, text="Analysis Log", padding="10")
        log_frame.grid(row=3, column=0, columnspan=2, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(0, 10))
        log_frame.columnconfigure(0, weight=1)
        log_frame.rowconfigure(0, weight=1)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, wrap=tk.WORD, 
                                                  height=20, state='disabled')
        self.log_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Action buttons
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=4, column=0, columnspan=2, pady=(10, 0))
        
        self.analyze_btn = ttk.Button(button_frame, text="Analyze", 
                                      command=self.start_analysis, state='disabled')
        self.analyze_btn.grid(row=0, column=0, padx=5)
        
        self.view_results_btn = ttk.Button(button_frame, text="View Results", 
                                          command=self.open_results_window, state='disabled')
        self.view_results_btn.grid(row=0, column=1, padx=5)
        
        ttk.Button(button_frame, text="Clear Log", 
                  command=self.clear_log).grid(row=0, column=2, padx=5)
        
        self.progress_bar = ttk.Progressbar(main_frame, mode='indeterminate')
        self.progress_bar.grid(row=5, column=0, columnspan=2, sticky=(tk.W, tk.E), pady=(10, 0))
        
    def browse_file(self):
        """Browse and select a single C file"""
        filename = filedialog.askopenfilename(
            title="Select C File",
            filetypes=[("C Files", "*.c"), ("All Files", "*.*")]
        )
        if filename:
            self.selected_file = filename
            self.file_label.config(text=filename, foreground="black")
            self.analyze_btn.config(state='normal')
            self.log_message(f"Selected file: {filename}")
            
    def browse_folder(self):
        """Browse folder and show C files to select"""
        folder = filedialog.askdirectory(title="Select Folder Containing C Files")
        if folder:
            c_files = list(Path(folder).glob("*.c"))
            if not c_files:
                messagebox.showwarning("No C Files", "No .c files found in selected folder")
                return
                
            # Show selection dialog
            selection_window = tk.Toplevel(self)
            selection_window.title("Select C File")
            selection_window.geometry("500x400")
            
            ttk.Label(selection_window, text=f"Found {len(c_files)} C file(s). Select one:", 
                     font=('Arial', 10)).pack(pady=10)
            
            listbox = tk.Listbox(selection_window, height=15)
            listbox.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
            
            for f in c_files:
                listbox.insert(tk.END, f.name)
                
            def on_select():
                selection = listbox.curselection()
                if selection:
                    self.selected_file = str(c_files[selection[0]])
                    self.file_label.config(text=self.selected_file, foreground="black")
                    self.analyze_btn.config(state='normal')
                    self.log_message(f"Selected file: {self.selected_file}")
                    selection_window.destroy()
                    
            ttk.Button(selection_window, text="Select", 
                      command=on_select).pack(pady=10)
            
    def browse_output_dir(self):
        """Select output directory for analysis results"""
        directory = filedialog.askdirectory(title="Select Output Directory")
        if directory:
            self.output_dir = directory
            self.output_label.config(text=directory, foreground="black")
            self.log_message(f"Output directory: {directory}")
            
    def log_message(self, message):
        """Add message to log"""
        self.log_text.config(state='normal')
        self.log_text.insert(tk.END, message + "\n")
        self.log_text.see(tk.END)
        self.log_text.config(state='disabled')
        
    def clear_log(self):
        """Clear the log"""
        self.log_text.config(state='normal')
        self.log_text.delete(1.0, tk.END)
        self.log_text.config(state='disabled')
        
    def start_analysis(self):
        """Start the analysis in a separate thread"""
        if not self.selected_file:
            messagebox.showerror("Error", "Please select a C file first")
            return
            
        if self.is_analyzing:
            messagebox.showwarning("In Progress", "Analysis already in progress")
            return
            
        self.is_analyzing = True
        self.analyze_btn.config(state='disabled')
        self.progress_bar.start()
        
        # Run analysis in separate thread
        thread = threading.Thread(target=self.run_analysis, daemon=True)
        thread.start()
        
    def run_analysis(self):
        """Execute the static analysis"""
        try:
            # Setup output directory
            if not self.output_dir:
                self.output_dir = os.path.join(os.path.dirname(self.selected_file), 
                                              "analysis_results")
            
            os.makedirs(self.output_dir, exist_ok=True)
            
            self.log_message("\n" + "="*60)
            self.log_message("Starting analysis...")
            self.log_message(f"Input file: {self.selected_file}")
            self.log_message(f"Output directory: {self.output_dir}")
            self.log_message("="*60 + "\n")
            
            # Get the directory containing the Java files
            script_dir = os.path.dirname(os.path.abspath(__file__))
            
            # Step 1: Preprocess
            self.log_message("[1/3] Preprocessing...")
            preprocess_script = os.path.join(script_dir, "preprocess.sh")
            
            base_name = os.path.basename(self.selected_file)
            file_dir = os.path.dirname(self.selected_file)
            preprocessed_file = os.path.join(file_dir, 
                                            base_name.replace('.c', '_preprocessed.c'))
            
            if os.path.exists(preprocess_script):
                result = subprocess.run(['bash', preprocess_script, self.selected_file],
                                      capture_output=True, text=True, cwd=script_dir)
                if result.returncode == 0:
                    self.log_message("✓ Preprocessing complete")
                    if result.stdout:
                        self.log_message(result.stdout)
                else:
                    self.log_message("⚠ Preprocessing skipped or failed")
                    preprocessed_file = self.selected_file
            else:
                self.log_message("⚠ Preprocessor not found, using original file")
                preprocessed_file = self.selected_file
            
            # Step 2: Compile Java files if needed
            self.log_message("\n[2/3] Checking Java compilation...")
            main_class = os.path.join(script_dir, "Main.class")
            main_java = os.path.join(script_dir, "Main.java")
            
            if not os.path.exists(main_class) or \
               os.path.getmtime(main_java) > os.path.getmtime(main_class):
                self.log_message("Compiling Java files...")
                result = subprocess.run(['javac', '*.java'],
                                      capture_output=True, text=True, 
                                      cwd=script_dir, shell=True)
                if result.returncode == 0:
                    self.log_message("✓ Compilation complete")
                else:
                    raise Exception(f"Java compilation failed:\n{result.stderr}")
            else:
                self.log_message("✓ Java files already compiled")
            
            # Step 3: Run analysis
            self.log_message("\n[3/3] Running static analysis...")
            
            # Save current directory
            original_dir = os.getcwd()
            
            try:
                # Change to script directory for analysis
                os.chdir(script_dir)
                
                result = subprocess.run(['java', 'Main', preprocessed_file, '--all'],
                                      capture_output=True, text=True, 
                                      timeout=60)
                
                if result.stdout:
                    self.log_message("\n" + result.stdout)
                    
                    # Save output to text file
                    output_file = os.path.join(self.output_dir, 'analysis_report.txt')
                    with open(output_file, 'w') as f:
                        f.write(result.stdout)
                    self.log_message(f"\n✓ Analysis report saved to: {output_file}")
                
                if result.stderr:
                    self.log_message("Warnings/Errors:\n" + result.stderr)
                
                # Move generated files to output directory
                self.log_message("\nMoving output files...")
                moved_count = 0
                
                for pattern in ['cfg_*.dot', 'cfg_*.png']:
                    for file in glob.glob(pattern):
                        dest = os.path.join(self.output_dir, os.path.basename(file))
                        os.rename(file, dest)
                        moved_count += 1
                        
                self.log_message(f"✓ Moved {moved_count} files to output directory")
                
                # Check for PNG files
                png_files = list(Path(self.output_dir).glob("cfg_*.png"))
                if png_files:
                    self.log_message(f"✓ Generated {len(png_files)} CFG visualization(s)")
                    self.view_results_btn.config(state='normal')
                else:
                    self.log_message("⚠ No PNG files generated (Graphviz may not be installed)")
                
                self.log_message("\n" + "="*60)
                self.log_message("✓ ANALYSIS COMPLETE")
                self.log_message("="*60)
                
            finally:
                # Restore original directory
                os.chdir(original_dir)
                
        except subprocess.TimeoutExpired:
            self.log_message("\n✗ Error: Analysis timed out (60s limit)")
        except Exception as e:
            self.log_message(f"\n✗ Error: {str(e)}")
            import traceback
            self.log_message(traceback.format_exc())
        finally:
            self.is_analyzing = False
            self.analyze_btn.config(state='normal')
            self.progress_bar.stop()
            
    def open_results_window(self):
        """Open the results viewer window"""
        if self.output_dir and os.path.exists(self.output_dir):
            ResultsWindow(self.parent, self.output_dir)
        else:
            messagebox.showerror("Error", "No results directory available")


class ResultsWindow(tk.Toplevel):
    """Second window: View analysis results and CFG images"""
    
    def __init__(self, parent, results_dir):
        super().__init__(parent)
        self.parent = parent
        self.results_dir = results_dir
        self.title(f"Analysis Results - {os.path.basename(results_dir)}")
        self.geometry("1200x800")
        
        self.cfg_images = []
        self.current_image_index = 0
        self.photo = None
        
        self.setup_ui()
        self.load_results()
        
    def setup_ui(self):
        """Setup the results window UI"""
        # Create paned window for split view
        paned = ttk.PanedWindow(self, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Left panel - Analysis results
        left_frame = ttk.Frame(paned)
        paned.add(left_frame, weight=1)
        
        ttk.Label(left_frame, text="Static Analysis Results", 
                 font=('Arial', 12, 'bold')).pack(pady=5)
        
        # Results directory info
        dir_frame = ttk.Frame(left_frame)
        dir_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Label(dir_frame, text="Results Directory:", 
                 font=('Arial', 9, 'bold')).pack(anchor=tk.W)
        ttk.Label(dir_frame, text=self.results_dir, 
                 foreground="blue", font=('Arial', 8)).pack(anchor=tk.W)
        
        button_frame = ttk.Frame(left_frame)
        button_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(button_frame, text="Change Directory", 
                  command=self.change_directory).pack(side=tk.LEFT, padx=2)
        ttk.Button(button_frame, text="Refresh", 
                  command=self.load_results).pack(side=tk.LEFT, padx=2)
        
        # Text widget for analysis results
        self.results_text = scrolledtext.ScrolledText(left_frame, wrap=tk.WORD, 
                                                      width=50, font=('Courier', 9))
        self.results_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Right panel - CFG Visualizations
        right_frame = ttk.Frame(paned)
        paned.add(right_frame, weight=1)
        
        ttk.Label(right_frame, text="Control Flow Graphs", 
                 font=('Arial', 12, 'bold')).pack(pady=5)
        
        # CFG selection
        cfg_frame = ttk.Frame(right_frame)
        cfg_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Label(cfg_frame, text="Select Function:").pack(side=tk.LEFT, padx=5)
        
        self.cfg_combo = ttk.Combobox(cfg_frame, state='readonly', width=30)
        self.cfg_combo.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=5)
        self.cfg_combo.bind('<<ComboboxSelected>>', self.on_cfg_selected)
        
        # Navigation buttons
        nav_frame = ttk.Frame(right_frame)
        nav_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.prev_btn = ttk.Button(nav_frame, text="◄ Previous", 
                                   command=self.show_previous_image)
        self.prev_btn.pack(side=tk.LEFT, padx=5)
        
        self.image_label_info = ttk.Label(nav_frame, text="No images")
        self.image_label_info.pack(side=tk.LEFT, expand=True)
        
        self.next_btn = ttk.Button(nav_frame, text="Next ►", 
                                   command=self.show_next_image)
        self.next_btn.pack(side=tk.LEFT, padx=5)
        
        # Canvas for image display with scrollbars
        canvas_frame = ttk.Frame(right_frame)
        canvas_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Scrollbars
        v_scrollbar = ttk.Scrollbar(canvas_frame, orient=tk.VERTICAL)
        v_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        h_scrollbar = ttk.Scrollbar(canvas_frame, orient=tk.HORIZONTAL)
        h_scrollbar.pack(side=tk.BOTTOM, fill=tk.X)
        
        self.image_canvas = tk.Canvas(canvas_frame, bg='white',
                                     yscrollcommand=v_scrollbar.set,
                                     xscrollcommand=h_scrollbar.set)
        self.image_canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        v_scrollbar.config(command=self.image_canvas.yview)
        h_scrollbar.config(command=self.image_canvas.xview)
        
        # Zoom controls
        zoom_frame = ttk.Frame(right_frame)
        zoom_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(zoom_frame, text="Zoom In", 
                  command=self.zoom_in).pack(side=tk.LEFT, padx=2)
        ttk.Button(zoom_frame, text="Zoom Out", 
                  command=self.zoom_out).pack(side=tk.LEFT, padx=2)
        ttk.Button(zoom_frame, text="Fit to Window", 
                  command=self.fit_to_window).pack(side=tk.LEFT, padx=2)
        ttk.Button(zoom_frame, text="Actual Size", 
                  command=self.actual_size).pack(side=tk.LEFT, padx=2)
        
        self.zoom_level = 1.0
        self.current_image = None
        
    def change_directory(self):
        """Change the results directory"""
        directory = filedialog.askdirectory(title="Select Results Directory",
                                           initialdir=self.results_dir)
        if directory:
            self.results_dir = directory
            self.title(f"Analysis Results - {os.path.basename(directory)}")
            self.load_results()
            
    def load_results(self):
        """Load analysis results from directory"""
        # Clear current results
        self.results_text.delete(1.0, tk.END)
        self.cfg_images = []
        self.cfg_combo['values'] = []
        
        # Load text report
        report_file = os.path.join(self.results_dir, 'analysis_report.txt')
        if os.path.exists(report_file):
            with open(report_file, 'r') as f:
                content = f.read()
                self.results_text.insert(1.0, content)
        else:
            self.results_text.insert(1.0, "No analysis report found.\n\n")
            self.results_text.insert(tk.END, f"Looking in: {self.results_dir}")
        
        # Load CFG images
        png_files = sorted(Path(self.results_dir).glob("cfg_*.png"))
        self.cfg_images = [str(f) for f in png_files]
        
        if self.cfg_images:
            # Extract function names
            function_names = []
            for img_path in self.cfg_images:
                filename = os.path.basename(img_path)
                # Extract function name from cfg_<function>.png
                match = re.match(r'cfg_(.+)\.png', filename)
                if match:
                    function_names.append(match.group(1))
                else:
                    function_names.append(filename)
            
            self.cfg_combo['values'] = function_names
            self.cfg_combo.current(0)
            self.current_image_index = 0
            self.display_current_image()
        else:
            self.image_label_info.config(text="No CFG images found")
            self.prev_btn.config(state='disabled')
            self.next_btn.config(state='disabled')
            
    def on_cfg_selected(self, event):
        """Handle CFG selection from dropdown"""
        self.current_image_index = self.cfg_combo.current()
        self.display_current_image()
        
    def show_previous_image(self):
        """Show previous CFG image"""
        if self.current_image_index > 0:
            self.current_image_index -= 1
            self.cfg_combo.current(self.current_image_index)
            self.display_current_image()
            
    def show_next_image(self):
        """Show next CFG image"""
        if self.current_image_index < len(self.cfg_images) - 1:
            self.current_image_index += 1
            self.cfg_combo.current(self.current_image_index)
            self.display_current_image()
            
    def display_current_image(self):
        """Display the currently selected CFG image"""
        if not self.cfg_images:
            return
            
        try:
            image_path = self.cfg_images[self.current_image_index]
            self.current_image = Image.open(image_path)
            self.zoom_level = 1.0
            
            self.render_image()
            
            # Update info label
            self.image_label_info.config(
                text=f"{self.current_image_index + 1} / {len(self.cfg_images)}")
            
            # Update button states
            self.prev_btn.config(state='normal' if self.current_image_index > 0 else 'disabled')
            self.next_btn.config(state='normal' if self.current_image_index < len(self.cfg_images) - 1 else 'disabled')
            
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load image: {str(e)}")
            
    def render_image(self):
        """Render the current image with current zoom level"""
        if not self.current_image:
            return
            
        # Apply zoom
        width = int(self.current_image.width * self.zoom_level)
        height = int(self.current_image.height * self.zoom_level)
        
        resized_image = self.current_image.resize((width, height), Image.LANCZOS)
        self.photo = ImageTk.PhotoImage(resized_image)
        
        # Update canvas
        self.image_canvas.delete("all")
        self.image_canvas.create_image(0, 0, anchor=tk.NW, image=self.photo)
        self.image_canvas.config(scrollregion=(0, 0, width, height))
        
    def zoom_in(self):
        """Zoom in the image"""
        self.zoom_level *= 1.2
        self.render_image()
        
    def zoom_out(self):
        """Zoom out the image"""
        self.zoom_level /= 1.2
        self.render_image()
        
    def fit_to_window(self):
        """Fit image to window"""
        if not self.current_image:
            return
            
        canvas_width = self.image_canvas.winfo_width()
        canvas_height = self.image_canvas.winfo_height()
        
        width_ratio = canvas_width / self.current_image.width
        height_ratio = canvas_height / self.current_image.height
        
        self.zoom_level = min(width_ratio, height_ratio) * 0.95
        self.render_image()
        
    def actual_size(self):
        """Show image at actual size"""
        self.zoom_level = 1.0
        self.render_image()


class MainApplication(tk.Tk):
    """Main application window"""
    
    def __init__(self):
        super().__init__()
        self.title("Static Analysis Tool")
        self.geometry("400x300")
        
        self.setup_ui()
        
    def setup_ui(self):
        """Setup main window UI"""
        main_frame = ttk.Frame(self, padding="20")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)
        
        # Title
        title_label = ttk.Label(main_frame, 
                               text="Static Analysis Tool for C",
                               font=('Arial', 18, 'bold'))
        title_label.grid(row=0, column=0, pady=(0, 30))
        
        # Description
        desc_label = ttk.Label(main_frame,
                              text="Analyze C source files and visualize\ncontrol flow graphs",
                              font=('Arial', 10),
                              justify=tk.CENTER)
        desc_label.grid(row=1, column=0, pady=(0, 30))
        
        # Buttons
        btn_frame = ttk.Frame(main_frame)
        btn_frame.grid(row=2, column=0, pady=10)
        
        ttk.Button(btn_frame, text="Analyze New File", 
                  command=self.open_analysis_window,
                  width=20).pack(pady=5)
        
        ttk.Button(btn_frame, text="View Existing Results",
                  command=self.open_results_window,
                  width=20).pack(pady=5)
        
        ttk.Button(btn_frame, text="Exit",
                  command=self.quit,
                  width=20).pack(pady=5)
        
        # Footer
        footer_label = ttk.Label(main_frame,
                                text="© 2026 Static Analysis Tool",
                                font=('Arial', 8),
                                foreground='gray')
        footer_label.grid(row=3, column=0, pady=(30, 0))
        
    def open_analysis_window(self):
        """Open the analysis window"""
        AnalysisWindow(self)
        
    def open_results_window(self):
        """Open results viewer for existing results"""
        directory = filedialog.askdirectory(title="Select Results Directory")
        if directory:
            # Check if directory contains results
            report_file = os.path.join(directory, 'analysis_report.txt')
            png_files = list(Path(directory).glob("cfg_*.png"))
            
            if os.path.exists(report_file) or png_files:
                ResultsWindow(self, directory)
            else:
                messagebox.showwarning("No Results",
                    "Selected directory doesn't appear to contain analysis results.\n"
                    "Looking for: analysis_report.txt or cfg_*.png files")


def main():
    """Main entry point"""
    app = MainApplication()
    app.mainloop()


if __name__ == "__main__":
    main()
