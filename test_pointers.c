int x;
int y;
int* ptr;
int** doublePtr;
void* voidPtr;

int add(int a, int b) {
    return a + b;
}

void swap(int* a, int* b) {
    int temp;
    temp = *a;
    *a = *b;
    *b = temp;
}

int* getPointer() {
    return &x;
}

int main() {
    int local;
    int* p;
    
    local = 10;
    p = &local;
    
    x = 5;
    y = 10;
    
    swap(&x, &y);
    
    ptr = getPointer();
    *ptr = 42;
    
    return 0;
}

void dangling() {
    int* ptr;
    int local;
    
    local = 5;
    ptr = &local;
}

void voidPtrTest() {
    int x;
    void* vp;
    
    x = 10;
    vp = &x;
}
