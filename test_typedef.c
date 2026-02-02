typedef int myint_t;
typedef double real_t;

myint_t x;
real_t y;

myint_t add(myint_t a, myint_t b) {
    return a + b;
}

int main() {
    myint_t result;
    result = add(5, 10);
    if(result > 10) {
        y = 3.14;
    } else {
        y = 2.71;
    }
    return 0;
}
