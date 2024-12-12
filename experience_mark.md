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

元组数量的计算：floor((BufferPool.getPageSize()*8) / (tuple size * 8 + 1))。

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