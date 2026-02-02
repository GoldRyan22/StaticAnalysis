int x;
int* ptr;

int add(int a, int b) {
    return a + b;
}

void swap(int* a, int* b) {
    int temp;
    temp = *a;
    *a = *b;
    *b = temp;
}

int main() {
    int local;
    int* p;
    
    local = 10;
    p = &local;
    
    x = 5;
    swap(&x, &local);
    
    return 0;
}
