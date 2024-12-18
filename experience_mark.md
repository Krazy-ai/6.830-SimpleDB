# lab 1

![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/1ce5633db368bb0f18289bdbc02b9eeb.png)

![img](https://pic1.zhimg.com/v2-42c83a12e88341f72dc9918bb83f2f46_1440w.jpg)

## ex1

![img](https://pic3.zhimg.com/v2-c2b33fa120b9e2eb3fec854324d7ed0a_1440w.jpg)

TupleDesc是表头那一行的描述信息，Tuple是每一行元组信息。

## ex2

**实验里一个file对应一个table**。

可视为catalog里存放table集合，创建table子类用map映射。

## ex3

用HashMap实现，后续的实验还会加上BufferPool满时的页置换策略。

## ex4

![img](https://pica.zhimg.com/v2-322369c7249f08f3c58f800c3de0a606_1440w.jpg)

一个HeapPage能存多少行Tuple，取决于他要保存的一个个元组有多长且都是什么元素。

元组数量的计算：floor((BufferPool.getPageSize()*8) / (tuple size * 8 + 1`页眉`))。

 以byte为单位的header数组的长度是：ceiling(no. tuple slots / 8) 。

这里的位图和位运算是关键。

`HeapPage的Iterator:`tuples是间隔存储的，即有的tuples[slot]是空的，我们必须返回所有非空的tuples[slot]，因此我们不能简单的返回一个Arrays.asList(tuples).iterator()

## ex5

随机访问：RandomAccessFile或SeekableByteChannel 。**随机访问（Random Access）** 指的是能够直接跳转到文件中的任意位置进行读取或写入，而无需按顺序读取整个文件（seek()方法）。

iterator与HeapPage的iterator有关，根据页面大小不断换成新一页HeapPage的iterator，重写多个方法。

## ex6

定义DbFileIterator为字段，open为file的iterator。

加前缀即为简单字符串修改。

## ex7

读的文件最后要换行

# lab2

## ex1

OpIterator意为可操作迭代器,在SimpleDB中的含义为: 迭代器遍历元素的时候可以同时进行一些操作，具体遍历时执行什么操作由子类决定。![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/107fb87e22773259bbf31154dac80c58.png)

操作迭代器意味着迭代器自身在遍历数据时，会根据自身实现搞点事情，Operator接口模板化了部分流程，各个需要在迭代器遍历时进行操作的子类，去实现readNext这个核心方法，并且每次获取下一个元组的时候，搞点事情即可。

Operator采用**装饰器模式**封装原始迭代器遍历行为，并在其基础上增加了遍历时进行操作的行为。装饰器模式需要有被装饰的对象，这里通过setChildren进行设置，但是这里与普通的装饰器模式不同，因为不同的操作会涉及到不同的个数的被装饰对象。

![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/edd466a7e60f098d3e08bebe37bd908f.png)

Operator的实现类都是装饰器，而SeqScan是迭代器的实现，也就是被装饰的对象

Filter循环：![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/b22597f866da3dd528d970988cf89d74.png)

```
//TODO super和child的open各是什么以及顺序
```

Join循环：![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/500193990ab1b2e2729c1ffca49c39a8.png)

这里需要**保留驱动表当前正在匹配的行**作为成员变量，用于固定外循环，不然每次只加外循环。（花了一个半小时Debug ToT）

```
//TODO 回顾，用t1固定外循环，不然每次只加外循环
```

## ex2

分“分组”和“聚合”两步来操作

**Aggregator聚合器干的事情就是接收传入的Tuple,然后内部进行计算**

fetchNext()中返回迭代器时我们不必自己实现一个，可以直接使用TupleIterator

Aggregate判断一下Field类型，返回IntegerAggregator或StringAggretor即可。

```
//TODO 为什么int类型记录count不行，为什么在过程中计算平均值不行
```

## ex3

![img](https://picx.zhimg.com/v2-4d295f899d61fe804026cb9244197b8f_1440w.jpg)

先实现每个Heap Page上的插入删除操作，然后实现每个Heap File上的插入删除，然后再实现BufferPool中的插入删除。

测试用例有错误（浪费时间ToT)

## ex4

**Insert和Delete采用的也是装饰器模式**

+ 装饰器对象继承被装饰对象的抽象父类或者父类接口，这样我们才可以在使用时能够用基类指针接收被装饰后的对象实现
+ 装饰器对象内部需要调用被装饰对象的方法获取原数据，然后再此基础上进行计算然后返回一个结果，或者在原有数据基础上增加附加信息，或者啥也不干，只进行相关信息记录。
+ fetchNext方法这里就是Insert装饰器对象需要实现的方法，其内部调用被装饰器对象的next方法获取所有数据，然后执行insert操作，同时计算插入数据条数，最终返回的是插入的数据条数。

```
@return A 1-field tuple containing the number of inserted records, or
*         null if called more than once.
```

返回的结果依然是元组的形式，但是这个元组只有1个属性

每次fetch之后必然会榨干迭代器，所以应该保持一个变量，每次调用fetch的时候标记。

## ex5

LRU缓存实现（双向链表）https://leetcode.cn/problems/lru-cache/，map的值改为LRUnode

flushPage应该将脏页写入磁盘并标记为不脏，同时将其留在BufferPool中

# lab3

基于成本的优化策略

精确估计查询计划的成本非常困难，在本次实验，我们仅关注连接和基于表访问的成本，我们不关心访问方法的选择性(因为我们只有table scans一个访问方法)或额外操作的成本(例如聚合操作)

## ex1

直方图统计

+ equal:

```
// (h / w) / ntups --> 当前元素的平均个数 / 总元素个数
```

w取**width+1**是为了确保selectivity的范围在(0,1)之间   对某些测试用例的精度有影响

+ greater_than:

```
// b_part = (b_right - const) / w_b --> 满足要求元素占当前桶内占比
// b_f = h_b / ntups  --> 当前桶内元素个数在总元素个数中的占比
// selectivity = b_f * b_part --> 当前桶内满足要求元素个数占总元素个数百分比
```

在数据库中，字符串大小的比较本质上还是数字类型的比较，比如这里StringHistoram是基于IntHistogram间接实现的，那么我们可以取String的高四位，按照相同位置的字符的ASCII码值的大小进行排序的，并不需要每位进行ASCII码值比较，可以使用移位来进行实现，高位的ASCII码值移位到左边，这样越大的String其得到的值也越大。

## ex2

对表中的每个列建立直方图，需要对表中元素进行两次迭代（需要一个迭代器指针遍历元组），第一遍迭代找出最大最小的值，第二遍迭代进行addValue，建立直方图。

定义接口Histogram，方便一起实现int和String的直方图。