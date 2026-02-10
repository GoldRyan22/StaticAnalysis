#include <stdio.h>

int test() { int x; x = 5; return x; }

unsigned test2() { unsigned x; x = 5; return x; }

unsigned long test3() { unsigned long x; x = 5; return x; }

unsigned long long test4() { unsigned long long x; x = 5; return x; }

float test5() { float x; x = 5.0; return x; }

double test6() { double x; x = 5.0; return x; }

float test7() { double x; x = 5; return x; }

double test8() { float x; x = 5; return x; }

int* ptr;

struct Point { int x; int y; };

struct Point p;

struct Point* pptr;

struct Point test9() { struct Point p; p.x = 1; p.y = 2; return p; }

typedef struct empty{} emp;

emp e1;

int strlen(char* s) { return 0; }



void* malloc(int size) { return 0; }
void free(void* ptr) { }
char* strcpy(char* dest, char* src) { return dest; }
int rand() { return 42; }
void srand(int seed) { }
void* memset(void* ptr, int value, int num) { return ptr; }
void* malloc(int size) { return 0; }

int main() {

    void* vp;

    void *vp2;

    printf("test: %d\n", test());
    fopen("test.txt", "w");
    

    if (pptr == NULL);


    return 0;
}






