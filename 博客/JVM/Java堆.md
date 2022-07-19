# Java堆

### 堆的对象管理

- 在《Java虚拟机规范》中堆Java堆的描述是：所有对象实例以及数组都应该在运行时分配到堆上
- 但是从实际使用的角度来看，不是绝对，存在某些特殊情况下的对象产生不在堆上奉陪内存
- 这里注意，规范上是绝对，实际上是相对
- **方法结束后，堆中的对象不会马上被移除，需要通过GC执行垃圾回收后才会回收**



### 堆的概述

1. 一个JVM进程存在一个堆内存，堆是JVM内存管理的核心区域
2. Java堆区在JVM启动时被创建，其空间大小也被确定，是JVM管理的最大一块内存（堆的大小可以调整）
3. 本质上堆是一组在物理上不连续的内存空间，但是逻辑上是连续的空间（参考HSDB分析的内存结构）
4. 所有线程共享堆，但是堆内对于线程处理还是做了一个线程私有的部分（TLAB）



### 堆内存的细分

![image-20220121200129963](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220121200129963.png)

- java7之前内存逻辑划分为 **新生代 + 养老区 + 永久区**
- java8之后内存逻辑划分为 **新生代 + 养老区 + 元空间
- 实际上不管永久代与元空间其实都是指方法区中对于长期存在的常量对象的保存



### 堆空间的分代思想

##### 老年代和年轻代

- 年轻代用于放置临时对象或者生命周期短的对象
- 老年代用于方式生命周期长的对象
- 常规的内存回收只管年轻代

##### 为什么需要分代？有什么好处？

- 经研究表明，不同对象的生命周期不一致，但是在具体的使用过程中70%-90%的对象是临时对象
- 分代唯一的理由是优化GC的性能，如果没有分代，那么所有对象在一块空间，GC想要回收扫描他就必须扫描所有的对象，分代之后，长期持有的对象可以挑出，短期持有的对象可以固定在一个位置回收，省掉很大一部分的空间利用



### 堆的默认大小

初始大小：电脑物理内存/ 64
最大内存大小：电脑物理内存 / 4

##### RunTime类

该类对应JVM的运行时数据区

```
long initialMemory = Runtime.getRuntime().totalMemory();
long maxMemory = Runtime.getRuntime().maxMemory();
long freeMemory = Runtime.getRuntime().freeMemory();
```



### 堆区详解

![image-20220121230642178](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220121230642178.png)



##### 对象在堆区中的位置变化

这里上两张图结构图：

![image-20220122020240807](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220122020240807.png)

![image-20220121230926209](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220121230926209.png)

年轻代又分为Eden区和survivor区，
1.所有对象一出生都会在Eden区，并且对象年龄为0。Eden区满了之后会触发minor GC, minor GC只回收年轻代中需要回收的对象。每一次minor GC，若该对象没有被回收，那么对象的年龄就会 + 1。对象到达阈值以后该对象会进入老年代(android阈值为6)
2.当老年代空间满了以后会触发full GC，full GC会同时回收年轻代和老年代中所有需要回收的对象



##### minor GC

Eden区满了以后，会调用minor GC，该GC只会回收年轻代中需要回收的对象，然后对不可回收的对象进行标记(就是把不可回收对象的年龄 + 1)，对象年龄达到阈值后对象会进入老年代空间

![image-20220121232409163](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220121232409163.png)



##### full GC

当老年代空间满了以后会触发full GC，full GC会回收年轻代和老年代中所有需要回收的对象

.![image-20220122015443791](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220122015443791.png)





##### 相关命令

jps ，查看当前所有java进程id

![image-20220122005437241](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220122005437241.png)

jstat -gc 进程id 查看

![image-20220122005635584](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220122005635584.png)

- 结尾C代表总量

- 结尾U代表已使用量
- S0，S1代表survivor区的from 和 to
- E代表Eden区
- OC代表老年代总量 
- OU代表老年代已使用量





##### Eden区：

对象一创建就会位于该区



##### Survivor区：

- Survivor区有两块，分别为Survivor0(S0)和Survivor1(S1)，根据作用还可以分为From Survivor区和To Survivor区

- 而 Survivor0 和 Survivor1 哪一块作为From Survivor区，哪一块作为To Survivor区，是不固定的。

- 在同一时间内，Survivor0 和 Survivor0 两块区域一定有一块是空的，非空的那块作为From Survivor区，空的那块作为To Survivor区

- JVM 每次只会使用 Eden 和其中的一块 Survivor 区域来为对象服务，所以无论什么时候，**总是有一块 Survivor 区域是空闲着的**。
  因此，年轻代**实际可用的内存空间为 9/10 ( 即90% )的新生代空间**。
  
  

##### Survivor区的作用：

- 保证对象内存的连续，提高GC的工作效率



##### 过早提升问题：

由于 Survivor 空间不足， 从 Eden 存活下来的和原来在 Survivor 空间中不够老的对象所需内存大于另外一个Survivor区， 就会提升到老年代， 这属于一个典型的 JVM 内存问题。 称为 "premature promotion"(过早提升)，这会加速老年代的内存消耗从而增加Full GC的触发频率从而导致性能下降



##### 对象在年轻代位置的变化：

- 我们就只看一个对象在年轻代的位置变化：它一出生会位于Eden区，接着会不断在Survivor0和Survivor1区切换，直到进入老年代

1. 对于一个刚刚创建的对象A，它一开始位于Eden区

2. 第一次minor GC触发，如果它没被回收，它会进入S0区，并且A年龄  + 1

3. 第二次minor GC触发，如果它没被回收，它会进入S1区，并且A年龄  + 1

4. 第三次minor GC触发，如果它没被回收，它会进入S0区，并且A年龄  + 1

5. 第四次minor GC触发，如果它没被回收，它会进入S1区，并且A年龄  + 1

6. 第....次 ，当A年龄达到阈值，进入老年代

   

- 再来看看多个对象的位置变化

1. Eden区满后，触发第一次minor GC，对Eden区进行对象回收(第一次minor GC时候survivor0和survivor2区都是空的，所以只回收Eden区)，回收后Eden区中的存活对象的内存不再连续，此时将所有的存活对象移到Survivor0区中内存连续的空间
2. Eden区又满后，触发第二次minor GC，对Eden区和Survivor0区进行对象回收(Survivor1区是空的，所以不用回收)，回收后Eden区和survivor0区中的存活对象内存可能不再连续，此时会将所有的存活对象移动到Survivor1区中内存连续的空间
3. Eden区再次满了后，触发第三次minor GC，对Eden区和Survivor1区进行对象回收(Survivor0区是空的，所以不用回收)，回收后Eden区和survivor1区中的存活对象内存可能不再连续，此时会将所有的存活对象移动到Survivor0区中内存连续的空间
4. Eden区满，触发第N次GC.......................
5. 之后存活对象会不断在Survivor0区和Survivor1区中切换，直到年龄达到阈值后进入老年代



![image-20220122010450472](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220122010450472.png)

##### 内存抖动：频繁产生临时变量导致不断地触发GC



### GC

##### GC分类

JVM在进行GC时，并非每次都堆上面三个内存区域一起回收，大部分的只会针对于Eden区进行

在JVM标准中，GC按照回收区域划分为两种：

- 一种是部分采集：
  1.新生代采集(Minor GC / YongGC) ：只采集新生代的数据
  2.老年代采集(Major GC / OldGC) : 只采集老年代数据，目前只有CMS会单独采集老年代
  3.混合采集(Mixed GC):采集新生代和老年代部分数据，目前只有G1使用
- 另一种是整采集(Full GC) : 收集整个堆和方法区的所有垃圾



##### GC触发策略

年轻代触发机制

- 当年轻代空间不足的时候，就会触发Minor GC，这里指的是Eden区满了
- 因为Java大部分对象都是临时对象，所以Minor GC触发非常平凡，一般回收速度也快
- Minor GC会触发STW行为，暂停其它用户的线程



老年代GC触发机制

- Major GC是针对老年代的垃圾回收，在年轻代存活对象晋升老年代时，如果发现老年代没有足够的空间容纳，就会先触发一次Minor GC **(触发这次minor GC的目的是，即将晋升老年代的对象有可能在该次minor GC被回收，回收完后剩余的存活对象能够放入来年代剩余的内存当中，减少一次不必要的Major GC)** ，如果之后空间仍不足，就会进行Major GC，所以Major GC之前常常会有一次Minor GC（并非绝对，目前只有CMS收集器有单独回收老年代的策略而不进行Minor GC）。
- Major GC比Minor GC速度慢十倍，如果Major GC触发后内存还是不足则会触发OOM



 Full GC的触发机制：

- 调用System.gc()方法，调用此方法时，系统会建议进行Full GC，并非绝对发生。
- 方法区空间不足
- 老年代空间不足,
- 年轻代的晋升对象所需内存大于老年代剩余内存。
- 通过Minor GC进入老年代的平均大小大于老年代的可用内存
- 在Eden使用Survivor进行复制时，对象大小大于Survivor的可用内存

Full GC回收范围包括年轻代、老年代及方法区，同样Full GC空间仍不足，就会OOM。





### TLAB

- 堆区是线程共享区，任何线程都可以访问堆中的共享数据
- 由于对象实例的创建很频繁，在并发环境下对重划分内存空间是线程不安全的，如果需要避免多个线程对同一地址操作，需要加锁，而加锁会影响对象分配速度
- 所以JVM默认在堆区中开辟了一块空间(1%),专门服务于每一个线程。他为每一个线程分配了一个私有缓存区域，包含在Eden中，这就是TLAB
- 多线程同时分配内存时，使用TLAB可用避免一系列的非线程安全问题
- TLAB会所谓内存分配的首选，TLAB总空间只会占Eden空间的1%
- 一旦对象在TLAB空间分配失败，JVM会尝试使用加锁来保证数据操作的原子性，从而直接在Eden区中分配



### 对象逃逸

- 要给对象的作用域仅限于方法内部在使用的情况下，这种情况叫做非逃逸
- 一个对象如果被外部其它类调用，或者是作用于属性中，此种现象成为对象逃逸
- 对象逃逸行为发生在字节码被编译后JIT对于代码的进一步优化

![image-20220123233720287](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220123233720287.png)

![image-20220123233832320](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220123233832320.png)





### JIT逃逸分析对代码优化

##### 使用逃逸分析，编译器可用对代码做出优化：

##### 1.栈上分配 *-Xmx1G -Xms1G -XX:-DoEscapeAnalysis -XX:+PrintGCDetails*

- JIT编译器在编译期间根据逃逸分析计算结果，如果发现当前对象没有发生逃逸，那么当前对象分配可能就会被优化为**栈上分配**，会将对象直接分配在栈中，而不是分配在堆中，所以在有大量临时变量出现的情况下，能够有效地减少GC触发次

##### 2.标量替换 -Xmx1G -Xms1G -XX:+EliminateAllocations -XX:+PrintGCDetails

- 有的对象可能不需要作为一个连续的内存结构存在也能被访问到，那么对象部分可以不存储在内存，而是存储在CPU寄存器中
- 标量(Scalar) : 指一个无法再分解成更小数据的数据。Java中的常量，基本数据类型
- 聚合量 (Aggregate) : java中的聚合量是类 ，封装行为就是聚合
- 标量替换指的是：在未发生逃逸的情况下，函数内部生成的聚合量(也就是类)，在JIT优化后会将其拆分成标量(也就是基本数据类型),这样就不需要再堆区中为该类变量进行内存分配了，所以在有大量临时变量出现的情况下，也能够有效地减少GC触发次



##### 逃逸分析技术的弊端

逃逸分析技术在99年发布，到jdk1.6后推出，但是这个技术至今还未完全成熟，原因是虽然逃逸分析可以做标量替换，栈上分配，锁消除等操作，但是逃逸分析自身也需要一系列复杂分析算法的运算，这也是一个相对耗时的过程