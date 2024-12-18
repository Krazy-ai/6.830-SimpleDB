package simpledb.optimizer;

import simpledb.execution.Predicate;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    private int bucketCounts;
    private int min;
    private int max;
    private int[] buckets;
    private double width;
    private int ntups;
    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
    	// some code goes here
        this.bucketCounts = buckets;
        this.min = min;
        this.max = max;
        this.buckets = new int[buckets];
        this.width = (double)(max - min) / buckets;
        this.ntups = 0;
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
    	// some code goes here
        int index = getIndex(v);
        buckets[index]++;
        ntups++;
    }

    private int getIndex(int v) {
        if (v > max || v < min) {
            throw new IllegalArgumentException("value out of range");
        }
        return v == max ? (bucketCounts - 1) : ((int) ((v - min) / width));
    }
    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
    	// some code goes here
        double selectivity = 0.0;
        switch (op){
            case EQUALS:{
                if (v < min || v > max) {
                    return 0.0;
                }
                // (h / w) / ntups --> 当前元素的平均个数 / 总元素个数
                // 这里width+1是为了确保selectivity的范围在(0,1)之间   对某些测试用例的精度有影响
                int h = buckets[getIndex(v)];
                return  (double) h / ((int) width + 1) / ntups;
            }
            case GREATER_THAN:{
                if (v <= min) {
                    return 1.0;
                }
                if (v >= max) {
                    return 0.0;
                }
                int index = getIndex(v);
                for(int i=index+1; i<bucketCounts; i++){
                    selectivity += (buckets[i] + 0.0) / ntups;
                }
                int h_b = buckets[index];
                // b_part = (b_right - const) / w_b --> 满足要求元素占当前桶内占比
                // b_f = h_b / ntups  --> 当前桶内元素个数在总元素个数中的占比
                // selectivity = b_f * b_part --> 当前桶内满足要求元素个数占总元素个数百分比
                double b_f = (double) h_b / ntups;
                double b_part = ((index + 1) * width - v) / width;
                selectivity += b_f * b_part;
                return selectivity;
            }
            case LESS_THAN:
                return 1 - estimateSelectivity(Predicate.Op.GREATER_THAN_OR_EQ, v);
            case LESS_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.LESS_THAN, v+1);
            case GREATER_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.GREATER_THAN, v-1);
            case NOT_EQUALS:
                return 1 - estimateSelectivity(Predicate.Op.EQUALS, v);
            default:
                return selectivity;
        }
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity() {
        // some code goes here
        double avg = 0.0;
        for (int i = 0; i < bucketCounts; i++) {
            avg += (buckets[i] + 0.0) / ntups;
        }
        return avg;
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            double b_l = i * width;
            double b_r = (i + 1) * width;
            sb.append(String.format("[%f, %f]:%d\n", b_l, b_r, buckets[i]));
        }
        return sb.toString();
    }
}
