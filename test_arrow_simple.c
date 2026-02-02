/* Simple test for -> operator */

struct Node {
    int value;
};

int test(struct Node *ptr) {
    int x;
    x = ptr->value;
    return x;
}

int main() {
    return 0;
}
