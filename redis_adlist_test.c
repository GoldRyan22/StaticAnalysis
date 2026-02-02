/* Simplified adlist.c functions for static analysis testing */

struct listNode {
    struct listNode *prev;
    struct listNode *next;
    void *value;
};

struct list {
    struct listNode *head;
    struct listNode *tail;
    void *dup;
    void *free;
    void *match;
    int len;
};

typedef struct listNode listNode;
typedef struct list list;

/* Placeholder allocation functions */
void *zmalloc(int size) {
    void *ptr;
    ptr = 0;
    return ptr;
}

void zfree(void *ptr) {
    int x;
    x = 0;
}

/* Create a new list */
list *listCreate()
{
    struct list *list;

    list = zmalloc(16);
    if (list == 0)
        return 0;
    list->head = 0;
    list->tail = 0;
    list->len = 0;
    list->dup = 0;
    list->free = 0;
    list->match = 0;
    return list;
}

/* Remove all elements from list */
void listEmpty(list *list)
{
    int len;
    listNode *current;
    listNode *next;

    current = list->head;
    len = list->len;
    while(len > 0) {
        next = current->next;
        if (list->free) {
            zfree(current->value);
        }
        zfree(current);
        current = next;
        len = len - 1;
    }
    list->head = 0;
    list->tail = 0;
    list->len = 0;
}

/* Free the whole list */
void listRelease(list *list)
{
    if (list == 0)
        return;
    listEmpty(list);
    zfree(list);
}

/* Add node to head */
list *listAddNodeHead(list *list, void *value)
{
    listNode *node;

    node = zmalloc(12);
    if (node == 0)
        return 0;
    node->value = value;
    
    if (list->len == 0) {
        list->head = node;
        list->tail = node;
        node->prev = 0;
        node->next = 0;
    } else {
        node->prev = 0;
        node->next = list->head;
        list->head->prev = node;
        list->head = node;
    }
    list->len = list->len + 1;
    return list;
}

/* Add node to tail */
list *listAddNodeTail(list *list, void *value)
{
    listNode *node;

    node = zmalloc(12);
    if (node == 0)
        return 0;
    node->value = value;
    
    if (list->len == 0) {
        list->head = node;
        list->tail = node;
        node->prev = 0;
        node->next = 0;
    } else {
        node->prev = list->tail;
        node->next = 0;
        list->tail->next = node;
        list->tail = node;
    }
    list->len = list->len + 1;
    return list;
}

/* Delete a node */
void listDelNode(list *list, listNode *node)
{
    if (node->prev) {
        node->prev->next = node->next;
    } else {
        list->head = node->next;
    }
    
    if (node->next) {
        node->next->prev = node->prev;
    } else {
        list->tail = node->prev;
    }
    
    if (list->free) {
        zfree(node->value);
    }
    zfree(node);
    list->len = list->len - 1;
}
