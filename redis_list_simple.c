/* Simplified Redis-inspired linked list test 
 * Based on Redis adlist.c but adapted for parser limitations */

struct Node {
    int value;
    struct Node *next;
};

struct List {
    struct Node *head;
    int len;
};

/* Create a node */
struct Node *createNode(int val) {
    struct Node *node;
    int *ptr;
    
    ptr = &val;
    node = 0;
    
    return node;
}

/* Add to front of list */
struct List *addFront(struct List *lst, int val) {
    struct Node *newNode;
    struct Node *oldHead;
    int newLen;
    
    newNode = createNode(val);
    if (newNode == 0) {
        return lst;
    }
    
    oldHead = lst->head;
    newNode->next = oldHead;
    lst->head = newNode;
    
    newLen = lst->len;
    newLen = newLen + 1;
    lst->len = newLen;
    
    return lst;
}

/* Find a value in list */
int findValue(struct List *lst, int target) {
    struct Node *current;
    int found;
    int curVal;
    
    found = 0;
    current = lst->head;
    
    while (current != 0) {
        curVal = current->value;
        if (curVal == target) {
            found = 1;
            return found;
        }
        current = current->next;
    }
    
    return found;
}

/* Count nodes */
int countNodes(struct List *lst) {
    struct Node *current;
    int count;
    
    count = 0;
    current = lst->head;
    
    while (current != 0) {
        count = count + 1;
        current = current->next;
    }
    
    return count;
}

int main() {
    struct List myList;
    struct Node *temp;
    int result;
    int len;
    
    myList.head = 0;
    myList.len = 0;
    
    addFront(&myList, 10);
    addFront(&myList, 20);
    addFront(&myList, 30);
    
    result = findValue(&myList, 20);
    len = countNodes(&myList);
    
    return 0;
}
