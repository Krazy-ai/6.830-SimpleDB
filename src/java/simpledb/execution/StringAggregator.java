package simpledb.execution;

import simpledb.common.DbException;
import simpledb.common.Type;
import simpledb.storage.*;
import simpledb.transaction.TransactionAbortedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;
    private int gbFieldIdx;
    private Type gbFieldType;
    private int aggregateFieldIdx;
    private Op operator;
    private final Map<Field, Integer> map;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        if(what != Op.COUNT){
            throw new IllegalArgumentException("StringAggregator only supports COUNT");
        }
        this.gbFieldIdx = gbfield;
        this.gbFieldType = gbfieldtype;
        this.aggregateFieldIdx = afield;
        this.operator = what;
        this.map = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field gbField = gbFieldIdx == Aggregator.NO_GROUPING ? null : tup.getField(gbFieldIdx);
        if (gbField != null && !gbField.getType().equals(gbFieldType)) {
            throw new IllegalArgumentException("group field type does not match");
        }
        map.merge(gbField, 1, Integer::sum);
        //map.compute(gbField, (k, v) -> v == null ? 1 : v + 1);
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
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
