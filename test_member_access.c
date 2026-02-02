/* Test -> and . operators for structure member access */

struct Node {
    int value;
    struct Node *next;
};

int getValue(struct Node *ptr) {
    int val;
    val = ptr->value;
    return val;
}

void setValue(struct Node *ptr, int newVal) {
    ptr->value = newVal;
}

int *getNext(struct Node *ptr) {
    int *next;
    next = ptr->next;
    return next;
}

void setNext(struct Node *ptr, struct Node *nextPtr) {
    ptr->next = nextPtr;
}

int main() {
    struct Node node1;
    struct Node node2;
    struct Node *ptr1;
    struct Node *ptr2;
    int value;
    
    node1.value = 10;
    node2.value = 20;
    
    ptr1 = &node1;
    ptr2 = &node2;
    
    setValue(ptr1, 100);
    value = getValue(ptr1);
    
    setNext(ptr1, ptr2);
    ptr2 = getNext(ptr1);
    
    value = ptr2->value;
    
    return 0;
}
