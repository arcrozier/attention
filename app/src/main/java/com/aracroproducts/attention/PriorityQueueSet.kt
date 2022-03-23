package com.aracroproducts.attention

import java.util.*

/**
 * A priority queue that does not allow duplicates. See {@link PriorityQueue}
 */
class PriorityQueueSet<E>(comparator: Comparator<E>) : Queue<E> {

    private val priorityQueue = PriorityQueue(comparator)
    private val elements = HashSet<E>()

    override fun add(element: E): Boolean {
        if (elements.contains(element)) return false
        elements.add(element)
        priorityQueue.add(element)
        return true
    }

    /**
     * Adds all of the elements of the specified collection to this collection.
     *
     * @return `true` if any of the specified elements was added to the collection, `false` if the collection was not modified.
     */
    override fun addAll(elements: Collection<E>): Boolean {
        var added = false
        for (element in elements) {
            added = added || add(element)
        }
        return added
    }

    /**
     * Removes all elements from this collection.
     */
    override fun clear() {
        elements.clear()
        priorityQueue.clear()
    }

    override fun iterator(): MutableIterator<E> {
        return priorityQueue.iterator()
    }

    override fun remove(): E {
        val returned = priorityQueue.remove()
        elements.remove(returned)
        return returned
    }

    /**
     * Checks if the specified element is contained in this collection.
     */
    override fun contains(element: E): Boolean {
        return elements.contains(element)
    }

    /**
     * Checks if all elements in the specified collection are contained in this collection.
     */
    override fun containsAll(elements: Collection<E>): Boolean {
        return elements.containsAll(elements)
    }

    /**
     * Returns `true` if the collection is empty (contains no elements), `false` otherwise.
     */
    override fun isEmpty(): Boolean {
        return elements.isEmpty()
    }

    /**
     * Removes a single instance of the specified element from this
     * collection, if it is present.
     *
     * @return `true` if the element has been successfully removed; `false` if it was not present in the collection.
     */
    override fun remove(element: E): Boolean {
        val removed = elements.remove(element)
        if (removed) priorityQueue.remove(element)
        return removed
    }

    /**
     * Removes all of this collection's elements that are also contained in the specified collection.
     *
     * @return `true` if any of the specified elements was removed from the collection, `false` if the collection was not modified.
     */
    override fun removeAll(elements: Collection<E>): Boolean {
        var removed = false
        for (element in elements) {
            val temp = this.elements.remove(element)
            if (temp) {
                priorityQueue.remove(element)
                removed = true
            }
        }
        return removed
    }

    /**
     * Retains only the elements in this collection that are contained in the specified collection.
     *
     * @return `true` if any element was removed from the collection, `false` if the collection was not modified.
     */
    override fun retainAll(elements: Collection<E>): Boolean {
        val returned = this.elements.retainAll(elements = elements)
        if (returned) priorityQueue.retainAll(elements)
        return returned
    }

    override fun offer(p0: E): Boolean {
        val accepted = priorityQueue.offer(p0)
        if (accepted) elements.add(p0)
        return accepted
    }

    override fun poll(): E? {
        val returned = priorityQueue.poll()
        if (returned != null) elements.remove(returned)
        return returned
    }

    override fun element(): E {
        return priorityQueue.element()
    }

    override fun peek(): E? {
        return priorityQueue.peek()
    }

    override fun hashCode(): Int {
        return priorityQueue.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other is PriorityQueueSet<*>) {
            return priorityQueue == other.priorityQueue
        }
        return false
    }

    override fun toString(): String {
        return priorityQueue.toString()
    }

    override val size: Int
        get() = priorityQueue.size

}