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

[LRU缓存实现](https://leetcode.cn/problems/lru-cache/)（双向链表），map的值改为LRUnode

flushPage应该将脏页写入磁盘并标记为不脏，同时将其留在BufferPool中

# lab3

基于成本的优化策略

精确估计查询计划的成本非常困难，在本次实验，我们仅关注连接和基于表访问的成本，我们不关心访问方法的选择性(因为我们只有table scans一个访问方法)或额外操作的成本(例如聚合操作)

**在这个实验中，你只需要考虑左深的计划。**

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

## ex3

```
joincost(t1 join t2) = scancost(t1) + ntups(t1) x scancost(t2) //IO cost
       					 + ntups(t1) x ntups(t2)  //CPU cost
```

数据库领域的 Cardinality 表示**去重后唯一值（Unique Values）的数量**

连接基数估计问题比过滤器选择性估计问题更难，实验只要求简单实现，给出了3个优化规则`（给那么多参数干嘛）`：

+ 当Predicate.Op 是 EQUALS时：
  + 当表一是join的字段是主键，对应表二的字段也是主键的话，就返回card数小的那个值。
  + 当表一是join的字段是主键，对应表二的字段不是主键的话，就返回card2。
  + 当表一是join的字段不是主键，对应表二的字段是主键的话，就返回card1。
  + 对于没有主键的等值连接，很难说清楚输出的元组数量–它可以是两个表的基数乘积的大小(如果两个表都有所有元组的值相同)–或者为0；可以构造一个简单的启发式(比如，两个表中较大的那个表的大小)`(选择“两个表中较大的那个表的大小”才能通过后面的测试用例，why)`
+ 同理Predicate.Op 是 NOT_EQUALS时，计算**记录总数-等值记录数**，即用card1*card2减去上面对应情况的值

+ 当Predicate.Op 不是 EQUALS时：
  + 那么官方文档中给了一个公式，就是返回 0.3 * card1 *card2 。

+ 最后一种情况，当card结果小于等于0时，直接返回1。

选择性=唯一值的数量/总记录数

数据库在选择索引时，也是会估计基数，然后计算出选择性，使用选择性可以衡量一个字段的不重复记录数有多少，如果一个字段的选择性很低接近0，那么就没必要用索引了，因为会有大量重复的数据，导致我们不断的去回表；如果一个字段的选择性很高，接近于1，说明该字段的记录数是很多不重复，那样通过索引我们可以加快查询的速度。

**所以这也启示我们，建表时如果字段没有重复值要声明Unique，选择性的估计才好做**

## ex4

已经实现了成本估计的方法，接下来将要对查询计划进行优化。(限定了只支持LeftDeepTree。)

Selinger优化器

列举出所有的连接顺序，计算出每种连接顺序的代价，然后选择代价最小的连接顺序去执行。

按照枚举的方式去弄，有n!种方案，时间复杂度很高。所以本实验采用的是一种基于**动态规划**的查询计划生成。

TiDB 中使用的是 Join Reorder 算法[(图示)](https://blog.csdn.net/qq_44766883/article/details/127414263?ops_request_misc=%257B%2522request%255Fid%2522%253A%2522f06f734cdbb774257ce031b65c71442b%2522%252C%2522scm%2522%253A%252220140713.130102334.pc%255Fblog.%2522%257D&request_id=f06f734cdbb774257ce031b65c71442b&biz_id=0&utm_medium=distribute.pc_search_result.none-task-blog-2~blog~first_rank_ecpm_v1~rank_v31_ecpm-4-127414263-null-null.nonecase&utm_term=6.830&spm=1018.2226.3001.4450)，是一种贪心算法。**Mysql**数据库对于多表关联也采用的是贪心算法。tidb或者mysql使用贪心算法只能得到局部最优执行计划，但是计算最优解所消耗的代价较小，而postgreSQL使用动态规划能够得到最优执行计划，但是计算最优解算法复杂度较高，代价较大。

思路是这样的:先找出部分表的最优连接顺序，然后固定这些表的顺序，然后去连接其它表，这样也可以达到最优。

举个例子，我们有5张表进行连接；首先，我们找到5张表中两表连接代价最低的两张表，固定这两张表的顺序；然后用产生的结果去连接剩下的三张表，选出最底代价的顺序；然后5张表的连接顺序就完成了。这样，排列问题就变成了一个子集问题了，如ABCDE五张表，可以(A, B)(C, D, E)，可以(B, A)(C, D, E)等等。所以，对给定n个关系的集合，最多有2的n次方个子集，就算是n = 10，方案数也才1024，可见优化了很多。

```
1. j = set of join nodes
2. for (i in 1...|j|):
3.     for s in {all length i subsets of j}
4.       bestPlan = {}
5.       for s' in {all length d-1 subsets of s}
6.            subplan = optjoin(s')
7.            plan = best way to join (s-s') to subplan
8.            if (cost(plan) < cost(bestPlan))
9.               bestPlan = plan
10.      optjoin(s) = bestPlan
11. return optjoin(j)
```

![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/ed9cbb5724791702d16c9e6fdce736e5.png)

暴力遍历所有可能的Join排列，然后分别估计它们的总的Cost，然后选出总Cost最小的一组作为查询优化的结果。

**planCache相当于记忆化数组**

该exercise已经给出了许多辅助方法，其中

+ `enumeraterSubsets`为我们返回列表v的大小为size的子集。

  原始方法的实现思路是通过不断扩展现有子集集合来生成所有的子集，直到达到目标大小。这是通过两个嵌套循环实现的。

  改进：回溯生成全排列	QueryTest 2.6s->2.4s	bigOrderJoinsTest 5s->1.5s

+ `computeCostAndCardOfSubplan`给出连接的子集joinSet，以及需要从集合中移除的连接joinToRemove，该方法计算将joinToRemove加入到joinSet-{joinToRemove}的最佳排序方式。它返回CostCard对象，该对象包含成本、基数和最佳的连接顺序(以列表形式返回)。

​	如果无法找到最优的计划(例如，没有最左连接是可能的)，computeCostAndCardOfSubplan方法可能返回null，或者所有计划的成	本均大于bestCostSoFar参数。该方法通过参数planCache(先前以排序连接的缓存)来快速查找将将joinToRemove加入到joinSet-	{joinToRemove}的最快方法。

+ `computeCostAndCardOfSubplan`算法大概流程就是：

  1. 获取joinToRemove节点的基本信息；

  2. 生成一个连接方案：

     + 当只有一个joinToRemove节点时，该节点本身就是一个连接方案；

     + 当有多个节点时，先获取删除了joinToRemove节点后的joinSet的子最佳方案，然后再生成joinToRemove节点的表与子最佳方案进行连接的方案：

       （1）如果joinToRemove节点左表在子最佳方案中，左表=子最佳方案，右表=joinToRemove节点右表；

       （2）否则如果joinToRemove节点右表在最佳方案中，左表=joinToRemove节点左表，右表=子最佳方案。


  3. 计算当前连接方案的cost；

  4. 交换一次join两边顺序，再计算cost，并比较两次的cost得到最佳方案；

  5. 生成最终结果。



**查询优化器的构成**：

1. Parser.Java在初始化时会收集并构造所有表格的统计信息，并存到statsMap中。当有查询请求发送到Parser中时，会调用parseQuery方法去处理‘

2. parseQuery方法会把解析器解析后的结果去构造出一个LogicalPlan实例，然后调用LogicalPlan实例的physicalPlan方法去执行，然后返回的是结果记录的迭代器，也就是我们在lab2中做的东西都会在physicalPlan中会被调用。

总体的，lab3的查询优化应该分为两个阶段：

+ 第一阶段：收集表的统计信息，有了统计信息我们才可以进行估计；
+ 第二阶段：根据统计信息进行估计，找出最优的执行方案。

（2.4节讲了好多优化，有机会再实现 ToT)

# lab4

ACID:

- 原子性：通过两段锁协议和BufferPool的管理实现simpleDB的原子性；
- 一致性：通过原子性实现事务的一致性，simpleDB中没有解决其他一致性问题（例如，键约束）；
- 隔离性：严格的两段锁提供隔离；
- 持久性：事务提交时将脏页强制写进磁盘。

两段锁协议：

+ 第一阶段：扩展阶段，事务可以申请获得任意数据上的任意锁，但是不能释放任何锁；
+ 第二阶段：收缩阶段，事务可以释放任何数据上的任何类型的锁，但是不能再次申请任何锁。

​	遵守两段锁协议可能会发生死锁：两段锁协议并不要求事务必须一次将所有要使用的数据全部加锁，因此遵守两段锁协议的事务可能发生死锁。

​	两段封锁法可以这样来实现：事务开始后就处于加锁阶段，一直到执行ROLLBACK和COMMIT之前都是加锁阶段。ROLLBACK和COMMIT使事务进入解锁阶段，即在ROLLBACK和COMMIT模块中DBMS释放所有封锁。

​	simpleDB中的实现过程为：事务提交之前可以获取任何锁，事务提交之后释放该事务所拥有的所有锁。同时在获取锁的过程中进行死锁检测。

简化工作：

+ 你不应该从缓冲池中驱逐脏的（更新的）页面，如果它们被一个未提交的事务锁定（NO STEAL）。

+ 在事务提交时，你应该把脏页强制到磁盘上（例如，把页写出来）（FORCE）。

+ 假设SimpleDB在处理transactionComplete命令时不会崩溃
+ 建议以页为单位进行锁定，请不要实现表级的锁定（尽管它是可能的）。

## ex1

![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/a8e6b8c03d86cfee572ee02db5e8f3ba.png)

锁管理通过Map存储记录，直接通过`ConcurrentHashMap`管理锁和事务之间的关系。

利用synchronized保证对共享资源（如锁状态）的并发访问是安全的。

（可以对每个页面写一个reentrantlock，通过显式的`ReentrantLock`来控制，实现防止多个线程同时修改同一个页，更简单些）

采用了自旋的方式不断获取锁，并设置一个获取锁的超时时间，超过这个时间了就抛出异常。

选择解决死锁的方式：超时等待，对每个事务设置一个获取锁的超时时间，如果在超时时间内获取不到锁，我们就认为可能发生了死锁，将该事务进行中断。

（另外方法：循环等待图检测：建立事务等待关系的等待图，当等待图出现了环时，说明有死锁发生，在加锁前就进行死锁检测，如果本次加锁请求会导致死锁，就终止该事务。）

通过wait+Notify这两个底层原语解决：别的事务持有锁，导致当前事务无法获取锁之后的等待和通知机制。

`我一开始想使用Java的ReentrantReadWriteLock读写锁来实现，结果lab有几个测试（DeadlockTest和BTreeDeadlockTest）是一个事务在多个线程中执行，这就导致提交事务的线程无法释放事务在其他线程获取的锁（原因是Java的ReentrantReadWriteLock存在限制，不能够释放非本线程持有的锁）。之后改用StampedLock。但在进行lab5 BTreeTest时由于高并发、大数据量竞争导致我的设计一直有错误的数据。遂抛弃读写锁，改用简单的实现（在每一个页面加上可重入锁，然后进行操作）。`来自[剑神](https://www.kwang.top/DB/simple_db)

## ex2

​	SimpleDB设计使得在读取或修改BufferPool.getPage()中的页面之前，可以获取这些页面上的锁。因此，我们建议**在getPage()中获取锁**，而不是在每个操作符中添加对锁定例程的调用。

​	读取某页前，需要获取页面的共享锁；写入某页前，需要获取页面的互斥锁。我们可以发现在getPage()方法中，已经通过Permissions对象来确定对页的操作类型；Permission对象也表明了当我们访问对象前需要获取哪种类型的锁。

​	如果一个事务t在页p上找不到空槽，事务t应该立即释放页p的锁。虽然这显然与两阶段锁定的规则相矛盾，但这是可以的，因为事务t没有使用页面中的任何数据，因为更新p的并发事务t不可能影响t的答案或者结果。

## ex3

被修改的脏页不能被替换算法从BufferPool中替换出去，增加一条对Page是否是脏页的判断即可，遇到脏页不能进行任何操作，直接找下个页，全是脏页就得抛异常。

//驱逐之前缓存池中是有pagesize+1个页面的，新加入的页面是head.next，所以遍历到head.next即可

//对于testAllDirtyFails测试用例，读的时候会从表的起始开始读，一定会有一个刚读的不脏的页面在队头( ？？？待确认)  

## ex4

- 当进行提交时，进行一次指定TransactionId的刷盘；
- 当进行回滚时，从BufferPool中清除掉该事务造成的脏页，并将原始版本重新读到BufferPool中。
- 当事务提交或者终止时，应该释放BufferPool中保留的关于事务的任何状态，包括释放事务持有的任何锁

页面判脏时可以得到事务id，根据这一点来查询需要处理的关于事务id的页面。

## ex5

超时判断死锁

TODO 基于循环依赖图的死锁判定和解除



```
// sleep to get some interesting thread interleavings
Thread.sleep(1);
```

这个会导致运行很久？testTenThreads运行了48分钟才成功（对后面的实验有影响的话再来研究）

# lab5

![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/010706cfe2c7e8f096ac67776bf1565d.png)

**磁盘上Header Page是懒初始化的，因此出现的位置是不固定的，Internal Page和Leaf Page同样如此，之所以可以这样，是因为存在一个root ptr page,它起到的作用就类似文件系统中的超级块:**![在这里插入图片描述](https://i-blog.csdnimg.cn/blog_migrate/834ef728c9486a6916b82dd3bb3fdf92.png)

内部节点可以看成保存着一个个**BTreeEntry**，内部节点对key的查找、插入、删除、迭代，都是以entry为单位的。通过BTreeEntry可以获取key、LeftChild、RightChild这三种信息，然后存入相应数组中。

叶节点和内部节点不一样的地方在于，其保存的是一个个真正的数据，也就是保存着一个个Tuple。还有就是其的链表结构用于顺序查找。

## ex1

- 如果当前的节点就是叶节点了，那么直接返回，这里的叶节点要么是只有一个根节点，要么是进行递归所找到的叶节点；
- 如果filed为空，则递归找到最左边的节点，用于迭代器；
- 否则，就在当前这个内部节点进行查找，判断关键字的大小，从而进行下一步的递归。