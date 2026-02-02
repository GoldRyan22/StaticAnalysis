/* Test structure member access with -> and . operators */

struct Node {
    int value;
    struct Node *next;
};

struct List {
    struct Node *head;
    int length;
};

int getNodeValue(struct Node *node) {
    int val;
    val = node->value;
    return val;
}

void setNodeValue(struct Node *node, int newVal) {
    node->value = newVal;
}

struct Node *getNext(struct Node *node) {
    struct Node *next;
    next = node->next;
    return next;
}

int getListLength(struct List *list) {
    int len;
    len = list->length;
    return len;
}

void incrementLength(struct List *list) {
    int current;
    current = list->length;
    list->length = current + 1;
}

struct Node *getHead(struct List *list) {
    return list->head;
}

int getHeadValue(struct List *list) {
    struct Node *head;
    int val;
    
    head = list->head;
    val = head->value;
    
    return val;
}

void linkNodes(struct Node *first, struct Node *second) {
    first->next = second;
    second->next = 0;
}

int main() {
    struct Node node1;
    struct Node node2;
    struct Node *ptr1;
    struct Node *ptr2;
    struct List myList;
    int value;
    
    node1.value = 10;
    node2.value = 20;
    
    ptr1 = &node1;
    ptr2 = &node2;
    
    setNodeValue(ptr1, 100);
    value = getNodeValue(ptr1);
    
    linkNodes(ptr1, ptr2);
    
    myList.head = ptr1;
    myList.length = 2;
    
    value = getHeadValue(&myList);
    incrementLength(&myList);
    
    return 0;
}
