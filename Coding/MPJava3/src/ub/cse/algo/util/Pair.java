package ub.cse.algo.util;

/**
 * Simple pair class.
 * Pretty straight forward
 *
 * @param <F> First type
 * @param <S> Second type
 */
public class Pair <F, S> {
    private F first;
    private S second;

    /**
     * @param first: first value for this pair
     * @param second: second value for this pair
     */
    public Pair (F first, S second) {
        this.first = first;
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public void setFirst(F first) {
        this.first = first;
    }

    public S getSecond() {
        return second;
    }

    public void setSecond(S second) {
        this.second = second;
    }
}
