# JVM笔记



类代码执行顺始化顺序：

  类加载阶段：

- 类变量初始化语句 (static修饰的变量)
- 类变量初始化块 (static代码块)

 类实例创建（类加载阶段之后）：

- 成员变量初始化语句 
- 成员变量初始化块；
- 构造函数；

# 类加载

1.类主动加载和被动加载 ， 主要看该类会不会被初始化

1.类加载过程
加载 -> 链接 -> 初始化

其中链接阶段又分为： 验证 准备 解析	
验证阶段：
	1.目的在于确保Class文件的字节流中宝航的信息符合当前虚拟机的要求，保证被加载类不会危害虚拟机自身安全
	2.主要包括四种验证，文件格式验证，元数据验证，字节码验证，符号应用验证(cofe baby的例子)
准备阶段：
	1.为变量分配分配内存并设置该类变量的默认初始值，即零值	
	2.这里不包含用final static修饰的变量，这类变量在编译的时候就会分配内存了，在准备阶段会显示初始化
	3.这里不会为示例变量分配初始化，类变量会分配再方法区中，而示例变量会随着对象一起分配到java堆中
解析阶段: 
	 暂时省略





线程私有  程序计数器 本地方法栈 虚拟机栈
线程共享  方法区 堆

大部分的垃圾回收是在堆区
小部分的垃圾回收发生在方法区



一个JVM对应一个Runtime实例，Rumtime就是运行时的环境，也就是程序计数器，本地方法栈，虚拟机栈，方法区和堆的统一



线程：
java中的每一个线程都于操作系统的本地线程直接映射
当虚拟机中是剩下守护线程的时候，虚拟机将会被回收



# 程序计数器Program Counter Register

jvm中的pc寄存器是物理pc寄存器的低一种抽象模拟

## 作用：

pc寄存器用来存储指向下一条指令的地址，即将要执行的指令代码，由执行引擎来读取下一条指令。
当前线程所执行的字节码的行号指示器

## 设计目的：

保证线程切换后能够恢复到原来的位置 。

## 特点：

1.内存小几乎可以忽略不计
2.也是运行速度最快的存储区域
3.生命周期和所属线程保持一致
4.任何时间一个线程都会有一个方法在执行，也就是所谓的当前方法。程序计数器会存储当前线程正在执行的java方法的jvm指令地址。或者，如果是在执行native方法，则是为指定值
5.这是唯一一个在java虚拟机规范没有规定任何OOM情况的区域

需要注意的是，发生OOM的区域不一定有GC机制，比如线程私有的虚拟机栈，它就是一个栈结构，没有内存溢出，但是它也会发生StackOverflowError，超出允许栈结构的大小。而堆和方法区这种有GC机制的地方，发生OOM就可能和GC机制有关



下面贴一张图

![image-20211108225657634](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20211108225657634.png)



# 虚拟机栈

#### 栈帧：

每一个栈帧都是代表一个方法，为于栈顶的方法叫当前方法

#### 栈帧的结构：

##### 栈帧的主要有五个结构：

###### 1.局部变量表 (大小在编译的时候确定)

1）非static方法的静态变量表的第一个索引是该类的实例this
2）局部变量表中的变量还包括在方法中定义的局部变量
3）方法参数变量也会在局部变量表中

###### 2.返回地址

###### 3.操作数栈

用数组实现的栈，既有栈先进后出的特点，也有数组能够索引的特点



###### 4.动态连接 ？

###### 5.一些附加信息 ？ 







# 变量分类

#### 按照数据类型分：

1.基本数据类型 
2.引用数据类型

#### 按照类中声明的位置划分：

1.成员变量 ：被static修饰的叫类变量  ， 没有被static修饰的叫实例变量 
2.局部变量 











# GC

## 一.垃圾回收算法：

### 1.引用计数器算法：

### 算法实现：

给对象添加一个引用计数器，
每当一个地方引用该对象的时候，计数器的值+1，
当引用失效的时候，计数器的值-1

## 缺点：

如果存在相互引用的情况，则GC将永远不会回收这类对象。比如A持有B的引用，B也持有A的引用，这个时候如果A和B都没用的时候，它们的计数器永远为1，所以GC永远不会回收A和B。这也是Java不采用该算法的原因



### 2.可达性分析算法

这个算法的基本思想是通过一系列称为“GC Roots”的对象作为起始点，从这些节点向下搜索，搜索所走过的路径称为引用链，当一个对象到GC Roots没有任何引用链（即GC Roots到对象不可达）时，则证明此对象是不可用的。

那么问题又来了，如何选取GCRoots对象呢？在Java语言中，可以作为GCRoots的对象包括下面几种：

(1). 虚拟机栈（栈帧中的局部变量区，也叫做局部变量表）中引用的对象。

(2). 方法区中的类静态属性引用的对象。

(3). 方法区中常量引用的对象。

(4). 本地方法栈中JNI(Native方法)引用的对象。

![img](https://images2015.cnblogs.com/blog/249993/201703/249993-20170302205315766-1323892362.png)





## 二.四种引用

强引用：只要存在强引用就不会进行垃圾回收
软引用：有用但非必须的对象，在内存溢出之前才会进行回收(也就是在内存快要不够用的时候才会回收)，SoftReference
弱引用：非必须对象，只能生存到下一次垃圾收集，WeakReference
虚引用：无用对象，垃圾收集时会受到系统通知，PhantomReference
