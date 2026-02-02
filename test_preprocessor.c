/* Test file with preprocessor directives */
#include <stdio.h>
#define MAX_SIZE 100
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#ifndef SOME_MACRO
#define SOME_MACRO 1
#endif

int globalVar;

int add(int a, int b) {
    int result;
    result = a + b;
    return result;
}

int main() {
    int x;
    int y;
    int sum;
    
    x = 10;
    y = 20;
    sum = add(x, y);
    
    return 0;
}
