int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int fibonacci(int n) {
    if (n <= 0) {
        return 0;
    } else if (n == 1) {
        return 1;
    } else {
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}

int classify(int x) {
    int result;
    
    if (x < 0) {
        result = -1;
    } else if (x == 0) {
        result = 0;
    } else {
        result = 1;
    }
    
    return result;
}

int loopTest(int n) {
    int sum;
    int i;
    sum = 0;
    i = 0;
    
    while (i < n) {
        sum = sum + i;
        i = i + 1;
    }
    
    return sum;
}
