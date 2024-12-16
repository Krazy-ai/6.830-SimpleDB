package simpledb.execution;

import simpledb.common.Type;
import simpledb.storage.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbFieldIdx;
    private Type gbFieldType;
    private int aggregateFieldIdx;
    private Op operator;
    private final Map<Field, Integer> map;
    private Map<Field, Integer> count;
    /**
     * Aggregate constructor
     *
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbFieldIdx = gbfield;
        this.gbFieldType = gbfieldtype;
        this.aggregateFieldIdx = afield;
        this.operator = what;
        this.map = new HashMap<>();
        this.count = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field gbField = gbFieldIdx == Aggregator.NO_GROUPING ? null : tup.getField(gbFieldIdx);
        Field aggregateField = tup.getField(aggregateFieldIdx);
        if (!(aggregateField instanceof IntField)) {
            throw new IllegalArgumentException("aggregate field type does not match");
        }
        if (gbField != null && !gbField.getType().equals(gbFieldType)) {
            throw new IllegalArgumentException("group field type does not match");
        }
        IntField intField = (IntField) aggregateField;
        map.compute(gbField, (k, v)->{
            switch (operator){
                case MIN:
                    return v==null? intField.getValue() : Math.min(intField.getValue(), v);
                case MAX:
                    return v==null? intField.getValue() : Math.max(intField.getValue(), v);
                case SUM:
                    return v==null? intField.getValue() : v+intField.getValue();
                case COUNT:
                    return v==null? 1 : v+1;
                case AVG: {
                    //TODO 为什么int类型记录count不行，为什么在过程中计算平均值不行
                    count.compute(gbField, (key, val)->val==null? 1 : val+1);
                    return v==null? intField.getValue() : v+intField.getValue();
                }
                default: {
                    throw new IllegalArgumentException("operator illegal");
                }
            }
        });
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        Type[] types;
        String[] names;
        if(gbFieldIdx == Aggregator.NO_GROUPING) {
            types = new Type[]{ Type.INT_TYPE };
            names = new String[]{"aggregateVal"};
        } else {
            types = new Type[] { gbFieldType, Type.INT_TYPE };
            names = new String[]{"groupVal", "aggregateVal"};
        }
        TupleDesc tupleDesc = new TupleDesc(types, names);
        List<Tuple> tupleList = map.entrySet().stream().map(entry -> {
            Field gbField = entry.getKey();
            int aggVal = entry.getValue();
            if(operator == Op.AVG) aggVal /= count.get(gbField);
            Tuple tuple = new Tuple(tupleDesc);
            if (gbFieldIdx == Aggregator.NO_GROUPING) {
                tuple.setField(0, new IntField(aggVal));
            } else {
                tuple.setField(0, gbField);
                tuple.setField(1, new IntField(aggVal));
            }
            return tuple;
        }).collect(Collectors.toList());
        return new TupleIterator(tupleDesc, tupleList);
    }

}
