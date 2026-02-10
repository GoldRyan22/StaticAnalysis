import java.util.*;
import java.io.*;
import java.nio.file.*;

// Standard library function and type definitions
public class StandardLibrary {
    private Map<String, LibraryFunction> functions;
    private Map<String, String> typedefs;
    private Set<String> usedFunctions;
    private Set<String> usedTypes;
    
    public StandardLibrary() {
        this.functions = new HashMap<>();
        this.typedefs = new HashMap<>();
        this.usedFunctions = new HashSet<>();
        this.usedTypes = new HashSet<>();
        initializeStandardLibrary();
    }
    
    private void initializeStandardLibrary() {
        // stdio.h
        addFunction("printf", "int", "char*", "...");
        addFunction("scanf", "int", "char*", "...");
        addFunction("fprintf", "int", "FILE*", "char*", "...");
        addFunction("fscanf", "int", "FILE*", "char*", "...");
        addFunction("sprintf", "int", "char*", "char*", "...");
        addFunction("sscanf", "int", "char*", "char*", "...");
        addFunction("snprintf", "int", "char*", "size_t", "char*", "...");
        addFunction("vprintf", "int", "char*", "va_list");
        addFunction("vscanf", "int", "char*", "va_list");
        addFunction("vfprintf", "int", "FILE*", "char*", "va_list");
        addFunction("vfscanf", "int", "FILE*", "char*", "va_list");
        addFunction("vsprintf", "int", "char*", "char*", "va_list");
        addFunction("vsscanf", "int", "char*", "char*", "va_list");
        addFunction("vsnprintf", "int", "char*", "size_t", "char*", "va_list");
        addFunction("fopen", "FILE*", "char*", "char*");
        addFunction("fclose", "int", "FILE*");
        addFunction("fread", "size_t", "void*", "size_t", "size_t", "FILE*");
        addFunction("fwrite", "size_t", "void*", "size_t", "size_t", "FILE*");
        addFunction("fgets", "char*", "char*", "int", "FILE*");
        addFunction("fputs", "int", "char*", "FILE*");
        addFunction("fgetc", "int", "FILE*");
        addFunction("fputc", "int", "int", "FILE*");
        addFunction("getc", "int", "FILE*");
        addFunction("getchar", "int");
        addFunction("putc", "int", "int", "FILE*");
        addFunction("putchar", "int", "int");
        addFunction("puts", "int", "char*");
        addFunction("ungetc", "int", "int", "FILE*");
        addFunction("fseek", "int", "FILE*", "long", "int");
        addFunction("ftell", "long", "FILE*");
        addFunction("rewind", "void", "FILE*");
        addFunction("feof", "int", "FILE*");
        addFunction("ferror", "int", "FILE*");
        addFunction("clearerr", "void", "FILE*");
        addFunction("perror", "void", "char*");
        addFunction("remove", "int", "char*");
        addFunction("rename", "int", "char*", "char*");
        addFunction("tmpfile", "FILE*");
        addFunction("tmpnam", "char*", "char*");
        addFunction("fflush", "int", "FILE*");
        addFunction("freopen", "FILE*", "char*", "char*", "FILE*");
        addFunction("setbuf", "void", "FILE*", "char*");
        addFunction("setvbuf", "int", "FILE*", "char*", "int", "size_t");
        addFunction("fgetpos", "int", "FILE*", "fpos_t*");
        addFunction("fsetpos", "int", "FILE*", "fpos_t*");
        addFunction("gets_s", "char*", "char*", "rsize_t");
        
        // stdio.h - _s (secure) variants
        addFunction("tmpfile_s", "errno_t", "FILE**");
        addFunction("tmpnam_s", "errno_t", "char*", "rsize_t");
        addFunction("fopen_s", "errno_t", "FILE**", "char*", "char*");
        addFunction("freopen_s", "errno_t", "FILE**", "char*", "char*", "FILE*");
        addFunction("fprintf_s", "int", "FILE*", "char*", "...");
        addFunction("fscanf_s", "int", "FILE*", "char*", "...");
        addFunction("printf_s", "int", "char*", "...");
        addFunction("scanf_s", "int", "char*", "...");
        addFunction("snprintf_s", "int", "char*", "rsize_t", "char*", "...");
        addFunction("sprintf_s", "int", "char*", "rsize_t", "char*", "...");
        addFunction("sscanf_s", "int", "char*", "char*", "...");
        addFunction("vfprintf_s", "int", "FILE*", "char*", "va_list");
        addFunction("vfscanf_s", "int", "FILE*", "char*", "va_list");
        addFunction("vprintf_s", "int", "char*", "va_list");
        addFunction("vscanf_s", "int", "char*", "va_list");
        addFunction("vsnprintf_s", "int", "char*", "rsize_t", "char*", "va_list");
        addFunction("vsprintf_s", "int", "char*", "rsize_t", "char*", "va_list");
        addFunction("vsscanf_s", "int", "char*", "char*", "va_list");
        
        addTypedef("FILE", "struct _IO_FILE");
        addTypedef("size_t", "unsigned long");
        addTypedef("fpos_t", "long");
        addTypedef("va_list", "char*");
        addTypedef("errno_t", "int");
        addTypedef("rsize_t", "unsigned long");
        
        // stdlib.h
        addFunction("malloc", "void*", "size_t");
        addFunction("calloc", "void*", "size_t", "size_t");
        addFunction("realloc", "void*", "void*", "size_t");
        addFunction("free", "void", "void*");
        addFunction("free_sized", "void", "void*", "size_t");
        addFunction("free_aligned_sized", "void", "void*", "size_t", "size_t");
        addFunction("aligned_alloc", "void*", "size_t", "size_t");
        addFunction("exit", "void", "int");
        addFunction("abort", "void");
        addFunction("atexit", "int", "void*");
        addFunction("system", "int", "char*");
        addFunction("getenv", "char*", "char*");
        addFunction("atoi", "int", "char*");
        addFunction("atol", "long", "char*");
        addFunction("atoll", "long long", "char*");
        addFunction("atof", "double", "char*");
        addFunction("strtol", "long", "char*", "char**", "int");
        addFunction("strtoll", "long long", "char*", "char**", "int");
        addFunction("strtoul", "unsigned long", "char*", "char**", "int");
        addFunction("strtoull", "unsigned long long", "char*", "char**", "int");
        addFunction("strtod", "double", "char*", "char**");
        addFunction("strtof", "float", "char*", "char**");
        addFunction("strtold", "long double", "char*", "char**");
        addFunction("rand", "int");
        addFunction("srand", "void", "unsigned");
        addFunction("qsort", "void", "void*", "size_t", "size_t", "void*");
        addFunction("bsearch", "void*", "void*", "void*", "size_t", "size_t", "void*");
        addFunction("abs", "int", "int");
        addFunction("labs", "long", "long");
        addFunction("llabs", "long long", "long long");
        addFunction("div", "div_t", "int", "int");
        addFunction("ldiv", "ldiv_t", "long", "long");
        addFunction("lldiv", "lldiv_t", "long long", "long long");
        addFunction("mblen", "int", "char*", "size_t");
        addFunction("mbtowc", "int", "wchar_t*", "char*", "size_t");
        addFunction("wctomb", "int", "char*", "wchar_t");
        addFunction("mbstowcs", "size_t", "wchar_t*", "char*", "size_t");
        addFunction("wcstombs", "size_t", "char*", "wchar_t*", "size_t");
        addFunction("memalignment", "size_t", "void*");
        addFunction("call_once", "void", "once_flag*", "void*");
        addFunction("strfromd", "int", "char*", "size_t", "char*", "double");
        addFunction("strfromf", "int", "char*", "size_t", "char*", "float");
        addFunction("strfroml", "int", "char*", "size_t", "char*", "long double");
        addFunction("_Exit", "void", "int");
        addFunction("at_quick_exit", "int", "void*");
        addFunction("quick_exit", "void", "int");
        
        addTypedef("div_t", "struct { int quot; int rem; }");
        addTypedef("ldiv_t", "struct { long quot; long rem; }");
        addTypedef("lldiv_t", "struct { long long quot; long long rem; }");
        addTypedef("wchar_t", "int");
        addTypedef("once_flag", "int");
        addTypedef("QVoid", "void");
        
        // string.h
        addFunction("memcpy", "void*", "void*", "void*", "size_t");
        addFunction("memccpy", "void*", "void*", "void*", "int", "size_t");
        addFunction("memmove", "void*", "void*", "void*", "size_t");
        addFunction("memset", "void*", "void*", "int", "size_t");
        addFunction("memset_explicit", "void*", "void*", "int", "size_t");
        addFunction("memcmp", "int", "void*", "void*", "size_t");
        addFunction("memchr", "void*", "void*", "int", "size_t");
        addFunction("strcpy", "char*", "char*", "char*");
        addFunction("strncpy", "char*", "char*", "char*", "size_t");
        addFunction("strcat", "char*", "char*", "char*");
        addFunction("strncat", "char*", "char*", "char*", "size_t");
        addFunction("strcmp", "int", "char*", "char*");
        addFunction("strncmp", "int", "char*", "char*", "size_t");
        addFunction("strcoll", "int", "char*", "char*");
        addFunction("strxfrm", "size_t", "char*", "char*", "size_t");
        addFunction("strchr", "char*", "char*", "int");
        addFunction("strrchr", "char*", "char*", "int");
        addFunction("strstr", "char*", "char*", "char*");
        addFunction("strpbrk", "char*", "char*", "char*");
        addFunction("strcspn", "size_t", "char*", "char*");
        addFunction("strspn", "size_t", "char*", "char*");
        addFunction("strtok", "char*", "char*", "char*");
        addFunction("strlen", "size_t", "char*");
        addFunction("strnlen", "size_t", "char*", "size_t");
        addFunction("strdup", "char*", "char*");
        addFunction("strndup", "char*", "char*", "size_t");
        addFunction("strerror", "char*", "int");
        
        addTypedef("QChar", "char");
        
        // time.h
        addFunction("time", "time_t", "time_t*");
        addFunction("clock", "clock_t");
        addFunction("difftime", "double", "time_t", "time_t");
        addFunction("mktime", "time_t", "struct tm*");
        addFunction("strftime", "size_t", "char*", "size_t", "char*", "struct tm*");
        addFunction("gmtime", "struct tm*", "time_t*");
        addFunction("localtime", "struct tm*", "time_t*");
        addFunction("asctime", "char*", "struct tm*");
        addFunction("ctime", "char*", "time_t*");
        addFunction("timegm", "time_t", "struct tm*");
        addFunction("timespec_get", "int", "struct timespec*", "int");
        addFunction("timespec_getres", "int", "struct timespec*", "int");
        addFunction("gmtime_r", "struct tm*", "time_t*", "struct tm*");
        addFunction("localtime_r", "struct tm*", "time_t*", "struct tm*");
        
        // time.h - _s (secure) variants
        addFunction("asctime_s", "errno_t", "char*", "rsize_t", "struct tm*");
        addFunction("ctime_s", "errno_t", "char*", "rsize_t", "time_t*");
        addFunction("gmtime_s", "struct tm*", "time_t*", "struct tm*");
        addFunction("localtime_s", "struct tm*", "time_t*", "struct tm*");
        
        addTypedef("time_t", "long");
        addTypedef("clock_t", "long");
        
        // ctype.h
        addFunction("isalnum", "int", "int");
        addFunction("isalpha", "int", "int");
        addFunction("isblank", "int", "int");
        addFunction("iscntrl", "int", "int");
        addFunction("isdigit", "int", "int");
        addFunction("isgraph", "int", "int");
        addFunction("islower", "int", "int");
        addFunction("isprint", "int", "int");
        addFunction("ispunct", "int", "int");
        addFunction("isspace", "int", "int");
        addFunction("isupper", "int", "int");
        addFunction("isxdigit", "int", "int");
        addFunction("tolower", "int", "int");
        addFunction("toupper", "int", "int");
        
        // stdint.h
        addTypedef("int8_t", "signed char");
        addTypedef("int16_t", "short");
        addTypedef("int32_t", "int");
        addTypedef("int64_t", "long long");
        addTypedef("uint8_t", "unsigned char");
        addTypedef("uint16_t", "unsigned short");
        addTypedef("uint32_t", "unsigned");
        addTypedef("uint64_t", "unsigned long long");
        addTypedef("intptr_t", "long");
        addTypedef("uintptr_t", "unsigned long");
        addTypedef("intmax_t", "long long");
        addTypedef("uintmax_t", "unsigned long long");
        addTypedef("int_fast8_t", "signed char");
        addTypedef("int_fast16_t", "int");
        addTypedef("int_fast32_t", "int");
        addTypedef("int_fast64_t", "long long");
        addTypedef("uint_fast8_t", "unsigned char");
        addTypedef("uint_fast16_t", "unsigned");
        addTypedef("uint_fast32_t", "unsigned");
        addTypedef("uint_fast64_t", "unsigned long long");
        addTypedef("int_least8_t", "signed char");
        addTypedef("int_least16_t", "short");
        addTypedef("int_least32_t", "int");
        addTypedef("int_least64_t", "long long");
        addTypedef("uint_least8_t", "unsigned char");
        addTypedef("uint_least16_t", "unsigned short");
        addTypedef("uint_least32_t", "unsigned");
        addTypedef("uint_least64_t", "unsigned long long");
        
        // unistd.h
        addFunction("read", "ssize_t", "int", "void*", "size_t");
        addFunction("write", "ssize_t", "int", "void*", "size_t");
        addFunction("close", "int", "int");
        addFunction("open", "int", "char*", "int", "...");
        addFunction("pipe", "int", "int*");
        addFunction("fork", "pid_t");
        addFunction("execl", "int", "char*", "char*", "...");
        addFunction("execle", "int", "char*", "char*", "...");
        addFunction("execlp", "int", "char*", "char*", "...");
        addFunction("execv", "int", "char*", "char**");
        addFunction("execve", "int", "char*", "char**", "char**");
        addFunction("execvp", "int", "char*", "char**");
        addFunction("_exit", "void", "int");
        addFunction("getpid", "pid_t");
        addFunction("getppid", "pid_t");
        addFunction("getuid", "uid_t");
        addFunction("geteuid", "uid_t");
        addFunction("getgid", "gid_t");
        addFunction("getegid", "gid_t");
        addFunction("setuid", "int", "uid_t");
        addFunction("setgid", "int", "gid_t");
        addFunction("seteuid", "int", "uid_t");
        addFunction("setegid", "int", "gid_t");
        addFunction("chdir", "int", "char*");
        addFunction("getcwd", "char*", "char*", "size_t");
        addFunction("dup", "int", "int");
        addFunction("dup2", "int", "int", "int");
        addFunction("access", "int", "char*", "int");
        addFunction("unlink", "int", "char*");
        addFunction("rmdir", "int", "char*");
        addFunction("link", "int", "char*", "char*");
        addFunction("symlink", "int", "char*", "char*");
        addFunction("readlink", "ssize_t", "char*", "char*", "size_t");
        addFunction("sleep", "unsigned", "unsigned");
        addFunction("usleep", "int", "useconds_t");
        addFunction("pause", "int");
        addFunction("alarm", "unsigned", "unsigned");
        addFunction("lseek", "off_t", "int", "off_t", "int");
        addFunction("fsync", "int", "int");
        addFunction("fdatasync", "int", "int");
        addFunction("truncate", "int", "char*", "off_t");
        addFunction("ftruncate", "int", "int", "off_t");
        addFunction("isatty", "int", "int");
        addFunction("ttyname", "char*", "int");
        addFunction("ttyname_r", "int", "int", "char*", "size_t");
        addFunction("gethostname", "int", "char*", "size_t");
        addFunction("gethostid", "long");
        addFunction("getlogin", "char*");
        addFunction("getlogin_r", "int", "char*", "size_t");
        addFunction("chown", "int", "char*", "uid_t", "gid_t");
        addFunction("fchown", "int", "int", "uid_t", "gid_t");
        addFunction("lchown", "int", "char*", "uid_t", "gid_t");
        addFunction("fchownat", "int", "int", "char*", "uid_t", "gid_t", "int");
        addFunction("confstr", "size_t", "int", "char*", "size_t");
        addFunction("crypt", "char*", "char*", "char*");
        addFunction("encrypt", "void", "char*", "int");
        addFunction("fchdir", "int", "int");
        addFunction("fexecve", "int", "int", "char**", "char**");
        addFunction("fpathconf", "long", "int", "int");
        addFunction("getgroups", "int", "int", "gid_t*");
        addFunction("getopt", "int", "int", "char**", "char*");
        addFunction("getpgid", "pid_t", "pid_t");
        addFunction("getpgrp", "pid_t");
        addFunction("getsid", "pid_t", "pid_t");
        addFunction("faccessat", "int", "int", "char*", "int", "int");
        addFunction("linkat", "int", "int", "char*", "int", "char*", "int");
        addFunction("lockf", "int", "int", "int", "off_t");
        addFunction("nice", "int", "int");
        addFunction("pathconf", "long", "char*", "int");
        addFunction("pread", "ssize_t", "int", "void*", "size_t", "off_t");
        addFunction("pwrite", "ssize_t", "int", "void*", "size_t", "off_t");
        addFunction("readlinkat", "ssize_t", "int", "char*", "char*", "size_t");
        addFunction("setpgid", "int", "pid_t", "pid_t");
        addFunction("setpgrp", "pid_t");
        addFunction("setregid", "int", "gid_t", "gid_t");
        addFunction("setreuid", "int", "uid_t", "uid_t");
        addFunction("setsid", "pid_t");
        addFunction("swab", "void", "void*", "void*", "ssize_t");
        addFunction("symlinkat", "int", "char*", "int", "char*");
        addFunction("sync", "void");
        addFunction("sysconf", "long", "int");
        addFunction("tcgetpgrp", "pid_t", "int");
        addFunction("tcsetpgrp", "int", "int", "pid_t");
        addFunction("unlinkat", "int", "int", "char*", "int");
        
        addTypedef("ssize_t", "long");
        addTypedef("pid_t", "int");
        addTypedef("uid_t", "unsigned");
        addTypedef("gid_t", "unsigned");
        addTypedef("off_t", "long");
        addTypedef("useconds_t", "unsigned");
        
        // pthread.h
        addFunction("pthread_create", "int", "pthread_t*", "pthread_attr_t*", "void*", "void*");
        addFunction("pthread_exit", "void", "void*");
        addFunction("pthread_join", "int", "pthread_t", "void**");
        addFunction("pthread_detach", "int", "pthread_t");
        addFunction("pthread_self", "pthread_t");
        addFunction("pthread_equal", "int", "pthread_t", "pthread_t");
        addFunction("pthread_cancel", "int", "pthread_t");
        addFunction("pthread_kill", "int", "pthread_t", "int");
        addFunction("pthread_mutex_init", "int", "pthread_mutex_t*", "pthread_mutexattr_t*");
        addFunction("pthread_mutex_destroy", "int", "pthread_mutex_t*");
        addFunction("pthread_mutex_lock", "int", "pthread_mutex_t*");
        addFunction("pthread_mutex_trylock", "int", "pthread_mutex_t*");
        addFunction("pthread_mutex_unlock", "int", "pthread_mutex_t*");
        addFunction("pthread_cond_init", "int", "pthread_cond_t*", "pthread_condattr_t*");
        addFunction("pthread_cond_destroy", "int", "pthread_cond_t*");
        addFunction("pthread_cond_wait", "int", "pthread_cond_t*", "pthread_mutex_t*");
        addFunction("pthread_cond_timedwait", "int", "pthread_cond_t*", "pthread_mutex_t*", "struct timespec*");
        addFunction("pthread_cond_signal", "int", "pthread_cond_t*");
        addFunction("pthread_cond_broadcast", "int", "pthread_cond_t*");
        addFunction("pthread_rwlock_init", "int", "pthread_rwlock_t*", "pthread_rwlockattr_t*");
        addFunction("pthread_rwlock_destroy", "int", "pthread_rwlock_t*");
        addFunction("pthread_rwlock_rdlock", "int", "pthread_rwlock_t*");
        addFunction("pthread_rwlock_wrlock", "int", "pthread_rwlock_t*");
        addFunction("pthread_rwlock_unlock", "int", "pthread_rwlock_t*");
        addFunction("pthread_attr_init", "int", "pthread_attr_t*");
        addFunction("pthread_attr_destroy", "int", "pthread_attr_t*");
        addFunction("pthread_attr_setdetachstate", "int", "pthread_attr_t*", "int");
        addFunction("pthread_attr_getdetachstate", "int", "pthread_attr_t*", "int*");
        addFunction("pthread_attr_getguardsize", "int", "pthread_attr_t*", "size_t*");
        addFunction("pthread_attr_setguardsize", "int", "pthread_attr_t*", "size_t");
        addFunction("pthread_attr_getinheritsched", "int", "pthread_attr_t*", "int*");
        addFunction("pthread_attr_setinheritsched", "int", "pthread_attr_t*", "int");
        addFunction("pthread_attr_getschedparam", "int", "pthread_attr_t*", "struct sched_param*");
        addFunction("pthread_attr_setschedparam", "int", "pthread_attr_t*", "struct sched_param*");
        addFunction("pthread_attr_getschedpolicy", "int", "pthread_attr_t*", "int*");
        addFunction("pthread_attr_setschedpolicy", "int", "pthread_attr_t*", "int");
        addFunction("pthread_attr_getscope", "int", "pthread_attr_t*", "int*");
        addFunction("pthread_attr_setscope", "int", "pthread_attr_t*", "int");
        addFunction("pthread_attr_getstack", "int", "pthread_attr_t*", "void**", "size_t*");
        addFunction("pthread_attr_setstack", "int", "pthread_attr_t*", "void*", "size_t");
        addFunction("pthread_attr_getstacksize", "int", "pthread_attr_t*", "size_t*");
        addFunction("pthread_attr_setstacksize", "int", "pthread_attr_t*", "size_t");
        addFunction("pthread_atfork", "int", "void*", "void*", "void*");
        addFunction("pthread_barrier_destroy", "int", "pthread_barrier_t*");
        addFunction("pthread_barrier_init", "int", "pthread_barrier_t*", "pthread_barrierattr_t*", "unsigned");
        addFunction("pthread_barrier_wait", "int", "pthread_barrier_t*");
        addFunction("pthread_barrierattr_destroy", "int", "pthread_barrierattr_t*");
        addFunction("pthread_barrierattr_getpshared", "int", "pthread_barrierattr_t*", "int*");
        addFunction("pthread_barrierattr_init", "int", "pthread_barrierattr_t*");
        addFunction("pthread_barrierattr_setpshared", "int", "pthread_barrierattr_t*", "int");
        addFunction("pthread_condattr_getclock", "int", "pthread_condattr_t*", "clockid_t*");
        addFunction("pthread_condattr_setclock", "int", "pthread_condattr_t*", "clockid_t");
        addFunction("pthread_condattr_getpshared", "int", "pthread_condattr_t*", "int*");
        addFunction("pthread_condattr_setpshared", "int", "pthread_condattr_t*", "int");
        addFunction("pthread_condattr_init", "int", "pthread_condattr_t*");
        addFunction("pthread_condattr_destroy", "int", "pthread_condattr_t*");
        addFunction("pthread_getconcurrency", "int");
        addFunction("pthread_setconcurrency", "int", "int");
        addFunction("pthread_getcpuclockid", "int", "pthread_t", "clockid_t*");
        addFunction("pthread_getschedparam", "int", "pthread_t", "int*", "struct sched_param*");
        addFunction("pthread_setschedparam", "int", "pthread_t", "int", "struct sched_param*");
        addFunction("pthread_setschedprio", "int", "pthread_t", "int");
        addFunction("pthread_getspecific", "void*", "pthread_key_t");
        addFunction("pthread_setspecific", "int", "pthread_key_t", "void*");
        addFunction("pthread_key_create", "int", "pthread_key_t*", "void*");
        addFunction("pthread_key_delete", "int", "pthread_key_t");
        addFunction("pthread_mutex_consistent", "int", "pthread_mutex_t*");
        addFunction("pthread_mutex_getprioceiling", "int", "pthread_mutex_t*", "int*");
        addFunction("pthread_mutex_setprioceiling", "int", "pthread_mutex_t*", "int", "int*");
        addFunction("pthread_mutex_timedlock", "int", "pthread_mutex_t*", "struct timespec*");
        addFunction("pthread_mutexattr_getprioceiling", "int", "pthread_mutexattr_t*", "int*");
        addFunction("pthread_mutexattr_setprioceiling", "int", "pthread_mutexattr_t*", "int");
        addFunction("pthread_mutexattr_getprotocol", "int", "pthread_mutexattr_t*", "int*");
        addFunction("pthread_mutexattr_setprotocol", "int", "pthread_mutexattr_t*", "int");
        addFunction("pthread_mutexattr_getpshared", "int", "pthread_mutexattr_t*", "int*");
        addFunction("pthread_mutexattr_setpshared", "int", "pthread_mutexattr_t*", "int");
        addFunction("pthread_mutexattr_getrobust", "int", "pthread_mutexattr_t*", "int*");
        addFunction("pthread_mutexattr_setrobust", "int", "pthread_mutexattr_t*", "int");
        addFunction("pthread_mutexattr_gettype", "int", "pthread_mutexattr_t*", "int*");
        addFunction("pthread_mutexattr_settype", "int", "pthread_mutexattr_t*", "int");
        addFunction("pthread_mutexattr_init", "int", "pthread_mutexattr_t*");
        addFunction("pthread_mutexattr_destroy", "int", "pthread_mutexattr_t*");
        addFunction("pthread_once", "int", "pthread_once_t*", "void*");
        addFunction("pthread_rwlock_timedrdlock", "int", "pthread_rwlock_t*", "struct timespec*");
        addFunction("pthread_rwlock_timedwrlock", "int", "pthread_rwlock_t*", "struct timespec*");
        addFunction("pthread_rwlock_tryrdlock", "int", "pthread_rwlock_t*");
        addFunction("pthread_rwlock_trywrlock", "int", "pthread_rwlock_t*");
        addFunction("pthread_rwlockattr_getpshared", "int", "pthread_rwlockattr_t*", "int*");
        addFunction("pthread_rwlockattr_setpshared", "int", "pthread_rwlockattr_t*", "int");
        addFunction("pthread_rwlockattr_init", "int", "pthread_rwlockattr_t*");
        addFunction("pthread_rwlockattr_destroy", "int", "pthread_rwlockattr_t*");
        addFunction("pthread_setcancelstate", "int", "int", "int*");
        addFunction("pthread_setcanceltype", "int", "int", "int*");
        addFunction("pthread_spin_destroy", "int", "pthread_spinlock_t*");
        addFunction("pthread_spin_init", "int", "pthread_spinlock_t*", "int");
        addFunction("pthread_spin_lock", "int", "pthread_spinlock_t*");
        addFunction("pthread_spin_trylock", "int", "pthread_spinlock_t*");
        addFunction("pthread_spin_unlock", "int", "pthread_spinlock_t*");
        addFunction("pthread_testcancel", "void");
        
        addTypedef("pthread_t", "unsigned long");
        addTypedef("pthread_attr_t", "struct pthread_attr");
        addTypedef("pthread_mutex_t", "struct pthread_mutex");
        addTypedef("pthread_mutexattr_t", "struct pthread_mutexattr");
        addTypedef("pthread_cond_t", "struct pthread_cond");
        addTypedef("pthread_condattr_t", "struct pthread_condattr");
        addTypedef("pthread_rwlock_t", "struct pthread_rwlock");
        addTypedef("pthread_rwlockattr_t", "struct pthread_rwlockattr");
        addTypedef("pthread_barrier_t", "struct pthread_barrier");
        addTypedef("pthread_barrierattr_t", "struct pthread_barrierattr");
        addTypedef("pthread_spinlock_t", "int");
        addTypedef("pthread_once_t", "int");
        addTypedef("pthread_key_t", "unsigned");
        
        // sys/types.h
        addTypedef("dev_t", "unsigned long");
        addTypedef("ino_t", "unsigned long");
        addTypedef("mode_t", "unsigned");
        addTypedef("nlink_t", "unsigned long");
        addTypedef("blksize_t", "long");
        addTypedef("blkcnt_t", "long");
        addTypedef("fsblkcnt_t", "unsigned long");
        addTypedef("fsfilcnt_t", "unsigned long");
        addTypedef("id_t", "unsigned");
        addTypedef("key_t", "int");
        
        // sched.h
        addFunction("sched_yield", "int");
        addFunction("sched_get_priority_max", "int", "int");
        addFunction("sched_get_priority_min", "int", "int");
        addFunction("sched_setscheduler", "int", "pid_t", "int", "struct sched_param*");
        addFunction("sched_getscheduler", "int", "pid_t");
        addFunction("sched_setparam", "int", "pid_t", "struct sched_param*");
        addFunction("sched_getparam", "int", "pid_t", "struct sched_param*");
        addFunction("sched_setaffinity", "int", "pid_t", "size_t", "cpu_set_t*");
        addFunction("sched_getaffinity", "int", "pid_t", "size_t", "cpu_set_t*");
        addFunction("sched_rr_get_interval", "int", "pid_t", "struct timespec*");
        addFunction("smt", "int", "struct sched_param*");
        
        addTypedef("cpu_set_t", "struct cpu_set");
        addTypedef("clockid_t", "int");
    }
    
    private void addFunction(String name, String returnType, String... paramTypes) {
        List<String> params = new ArrayList<>(Arrays.asList(paramTypes));
        functions.put(name, new LibraryFunction(name, returnType, params));
    }
    
    private void addTypedef(String name, String baseType) {
        typedefs.put(name, baseType);
    }
    
    public void scanForUsedSymbols(ProgramNode program) {
        usedFunctions.clear();
        usedTypes.clear();
        scanNode(program);
    }
    
    private void scanNode(ASTNode node) {
        if (node == null) return;
        
        if (node instanceof ProgramNode) {
            ProgramNode prog = (ProgramNode) node;
            for (ASTNode decl : prog.declarations) {
                scanNode(decl);
            }
        }
        else if (node instanceof FuncDeclNode) {
            FuncDeclNode func = (FuncDeclNode) node;
            scanType(func.retType);
            for (VarDeclNode arg : func.args) {
                scanNode(arg);
            }
            scanNode(func.body);
        }
        else if (node instanceof VarDeclNode) {
            VarDeclNode var = (VarDeclNode) node;
            scanType(var.type);
            scanNode(var.initExpr);
        }
        else if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            for (ASTNode stmt : block.statements) {
                scanNode(stmt);
            }
        }
        else if (node instanceof IfStmtNode) {
            IfStmtNode ifStmt = (IfStmtNode) node;
            scanNode(ifStmt.condition);
            scanNode(ifStmt.thenBranch);
            scanNode(ifStmt.elseBranch);
        }
        else if (node instanceof WhileStmtNode) {
            WhileStmtNode whileStmt = (WhileStmtNode) node;
            scanNode(whileStmt.condition);
            scanNode(whileStmt.body);
        }
        else if (node instanceof ReturnStmtNode) {
            ReturnStmtNode retStmt = (ReturnStmtNode) node;
            scanNode(retStmt.expr);
        }
        else if (node instanceof BinaryExprNode) {
            BinaryExprNode binExpr = (BinaryExprNode) node;
            scanNode(binExpr.left);
            scanNode(binExpr.right);
        }
        else if (node instanceof UnaryExprNode) {
            UnaryExprNode unaryExpr = (UnaryExprNode) node;
            scanNode(unaryExpr.expr);
        }
        else if (node instanceof FuncCallNode) {
            FuncCallNode funcCall = (FuncCallNode) node;
            usedFunctions.add(funcCall.name);
            for (ASTNode arg : funcCall.args) {
                scanNode(arg);
            }
        }
        else if (node instanceof TypedefDeclNode) {
            TypedefDeclNode typedef = (TypedefDeclNode) node;
            scanType(typedef.baseType);
        }
        else if (node instanceof StructDeclNode) {
            StructDeclNode struct = (StructDeclNode) node;
            for (VarDeclNode field : struct.fields) {
                scanNode(field);
            }
        }
    }
    
    private void scanType(String type) {
        if (type == null) return;
        
        // Extract base type name (remove pointers and qualifiers)
        String baseType = type.replaceAll("\\*", "").trim();
        baseType = baseType.replaceAll("\\bconst\\b", "").trim();
        baseType = baseType.replaceAll("\\bvolatile\\b", "").trim();
        baseType = baseType.replaceAll("\\brestrict\\b", "").trim();
        baseType = baseType.replaceAll("\\bstruct\\b", "").trim();
        baseType = baseType.replaceAll("\\bunion\\b", "").trim();
        baseType = baseType.replaceAll("\\benum\\b", "").trim();
        
        // Check if it's a typedef we know about
        if (typedefs.containsKey(baseType)) {
            usedTypes.add(baseType);
        }
    }
    
    public void registerUsedSymbols(SymbolTable symbolTable) {
        // Register used typedefs
        for (String typeName : usedTypes) {
            if (typedefs.containsKey(typeName)) {
                String baseType = typedefs.get(typeName);
                symbolTable.addSymbol(typeName, baseType, "typedef");
            }
        }
        
        // Register used functions
        for (String funcName : usedFunctions) {
            if (functions.containsKey(funcName)) {
                LibraryFunction func = functions.get(funcName);
                Symbol symbol = new Symbol(funcName, func.returnType, "function", 0);
                symbol.returnType = func.returnType;
                symbol.paramTypes.addAll(func.paramTypes);
                symbolTable.addSymbol(symbol);
            }
        }
    }
    
    public boolean isStandardFunction(String name) {
        return functions.containsKey(name);
    }
    
    public boolean isStandardType(String name) {
        return typedefs.containsKey(name);
    }
    
    public void printUsedSymbols() {
        if (!usedFunctions.isEmpty() || !usedTypes.isEmpty()) {
            System.out.println("\n=== Standard Library Symbols Used ===");
        }
        
        if (!usedTypes.isEmpty()) {
            System.out.println("\nTypes:");
            for (String type : usedTypes) {
                System.out.println("  " + type + " = " + typedefs.get(type));
            }
        }
        
        if (!usedFunctions.isEmpty()) {
            System.out.println("\nFunctions:");
            for (String func : usedFunctions) {
                LibraryFunction libFunc = functions.get(func);
                if (libFunc != null) {
                    System.out.println("  " + libFunc);
                }
            }
        }
    }
    
    // Helper class to store function information
    private static class LibraryFunction {
        String name;
        String returnType;
        List<String> paramTypes;
        
        LibraryFunction(String name, String returnType, List<String> paramTypes) {
            this.name = name;
            this.returnType = returnType;
            this.paramTypes = paramTypes;
        }
        
        @Override
        public String toString() {
            String params = String.join(", ", paramTypes);
            return returnType + " " + name + "(" + params + ")";
        }
    }
}
