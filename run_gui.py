#!/usr/bin/env python3
"""
Static Analysis Tool - GUI Interface
Version: 1.0
Author: Static Analysis Team
Date: February 2026

This is the main entry point for the graphical user interface.
For documentation, see:
- QUICKSTART.md for quick start
- UI_README.md for full documentation
- GUI_SUMMARY.md for overview
- ARCHITECTURE.md for technical details
"""

# Import check before main application
def check_dependencies():
    """Check if all required dependencies are available"""
    import sys
    
    missing = []
    warnings = []
    
    # Check tkinter
    try:
        import tkinter
    except ImportError:
        missing.append("tkinter - GUI framework")
        print("ERROR: tkinter is not installed")
        print("Install with: sudo apt-get install python3-tk (Ubuntu/Debian)")
        print("           or: brew install python-tk (macOS)")
    
    # Check PIL
    try:
        import PIL
    except ImportError:
        missing.append("Pillow (PIL) - Image handling")
        warnings.append("Pillow is not installed. Images may not display.")
        print("WARNING: Pillow (PIL) is not installed")
        print("Install with: pip3 install pillow")
        print()
    
    if missing:
        print("\nCritical dependencies missing:")
        for dep in missing:
            print(f"  - {dep}")
        print("\nRun ./setup_ui.sh to install dependencies")
        sys.exit(1)
    
    if warnings:
        print("\nWarnings:")
        for warn in warnings:
            print(f"  ⚠ {warn}")
        print()
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            sys.exit(0)

# Check dependencies before importing the main application
check_dependencies()

# Now import and run the main application
from static_analysis_ui import main

if __name__ == "__main__":
    print("Starting Static Analysis Tool GUI...")
    print("For help, see QUICKSTART.md or UI_README.md")
    print()
    main()
