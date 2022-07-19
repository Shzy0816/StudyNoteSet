# 1.类型转换

#### 1.1自动类型转换：把一个表示数据小的数值或者变量赋值给数据范围较大的变量

long a = 10；10默认是int类型，将int类型变量赋值给double，由于double表示的范围比int大，所以可以自动转换,遵循下面这个顺序

byte < short < int < long < float < double

char< short < int < long < float < double



byte 无法自动转化成char？

#### 1.2 强制类型转换: 把一个表示数据范围大的数值或者变量赋值给另一个表示数据范围小的变量,会造成数据丢失

int a = (int) 88.8  88.8浮点数默认为double类型，由于double > int ，所以需要强制转换，强制转换后, a = 88，丢失了0.88





# 2.运算符

#### 1.1 算数运算符 + - * / %

1.整数运算只能得到整数，如果有(单精度/双精度)浮点数的参与，那结果将会是(单精度/双精度)浮点数(包括%运算)



2.算术表达式中包含多个基本数据类型的时候，整个算数表达式的类型会进行**自动提升**

 （1）所有变量会自动提升到最高级的基本数据类型：比如下面这个例子，a,b,c,d分别为byte,char,int,double，其中double为最高级的基本数据类型，所以在运算过程中a,b,c,d都会自动提升到double类型

![image-20220226005147801](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220226005147801.png)

 （2）byte(1字节),short (2字节),char(3字节) 至少会被提升到int 类型。举个例子：a,b,c分别为byte,char,short类型的变量，但是它们在运算的时候全部自动提升到int类型的变量

![image-20220226004602312](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220226004602312.png)



#### 1.2 字符串的 ”+“ 操作

字符串的 + 操作并不是加法操作，而是字符串拼接







# 3.继承

#### 子类中所有的构造方法默认都会访问父类中的无参构造方法

#### 为什么呢？

- 因为子类会继承父类中的数据，可能还会使用父类的数据。所以，子类初始化之前，一定要先完成父类的初始化
- 如果没有明确指定，每一个子类构造方法的第一条语句默认是super()



#### 子类的初始化过程：

1.父类static

2.子类static

3.父类实例变量和非static块 ()

4.父类的构造函数

5.子类实例变量和非static块

6.子类构造函数

#### 关于覆盖和隐藏：

覆盖：被覆盖的方法相当于被擦除了，不可恢复(即使类型强制转换也无法调用)

隐藏：一直保存的某个位置，等需要的时候可以继续使用(强转后可以调用)

##### 关于父类子类之间的覆盖和隐藏关系：

1. 变量只能被隐藏不会被覆盖

2. 父类的实例变量和类变量能被子类的同名变量隐藏

3. 子类的同名实例变量可以隐藏父类的静态变量

4. 子类的同名静态变量可以隐藏父类的实例变量

5. 父类的静态方法被子类的同名静态方法隐藏

6. 父类的实例方法被子类的同名实例方法覆盖

7. 用final关键字修饰的方法最终不能被覆盖

   

##### 方法重写的注意事项

- 私有方法不能被重写(父类私有成员不能被子类继承)

- 子类方法访问权限不能低于父类(public > 默认 > 私有)

  

##### 继承中的注意事项

- 一个类只能继承一个父类
- Java中的类支持多层继承  A extend B , C extend A





# 修饰符

#### 1.权限修饰符

- **private： 只有该类内部能够访问**

- **protected： 只允许子类访问，不允许外部访问**
- **public：开放所有类访问**
- **package-private： 只给同一个包下的类访问**

![image-20220226124424307](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220226124424307.png)

#### 2.状态修饰符

- **final （最终态）**

  - 修饰成员变量，表示变量不可被修改
  - 修饰方法 ，表示最终方法，最终方法不可被重写
  - 修饰类，表示最终类，最终类不可被继承

- **static  （静态）**

  - 修饰成员变量，表示类变量
  - 修饰方法，表示类方法
  - 修饰类，表示静态类

- ##### static final 表示常量

- ##### final修饰局部变量

  - 变量是基本类型：final修饰的基本数据类型数据值不能变
  - 变量是引用类型：final修饰的引用类型变量的地址不能变，但是地址的内容可以改变





# 多态

- #### 概述

  同一个对象在不同时刻表现出来的形态

- #### 多态的前提和体现

  - 有继承/实现的关系

  - 有方法重写

  - 有父类引用指向子类对象

    class Animal 

    class Cat extend Animal 

    Animal cat = new Cat()

- #### 多态中成员访问的特点

  - 成员变量：编译看左边，执行看左边
  - 成员方法：编译看左边，执行看右边
  - 为什么成员变量和成员方法的访问不一样？成员方法有重写，成员变量没有

- #### 多态的好处和弊端

  - 好处：提高程序的扩展性

    具体体现：定义方法的时候，使用父类型作为参数，在将来的使用的时候，使用具体的子类型重写后的同名方法参与操作

  - 多态的弊端： 不能使用子类的特有功能。

    比如 Animal animal = new Cat()

    如果Cat类中的play方法没有在Animal中定义，那么就不能调用animal.play()

- #### 多态中的转型

  - 向上转型 ： 父类引用指向对象Animal  animal = new Cat()
  - 向下转型 ： 父类引用强转为子类对象 Cat cat = (Cat) (new Animal())



# 抽象类

1.一个没有方法体的方法定义为**抽象方法**，而类中如果有抽象方法，该类必须为抽象类



# 接口

- #### 接口不能实例化

  接口如何实例化呢？参照多态的方式，接口只能通过实现类对象实例化，这叫接口多态

  多态的形式：具体类多态，抽象类多态，接口多态

  多态的前提：有继承或者实现关系；有方法重写；有父类对象引用指向子类对象或者有接口对象指向实现类对象

- #### 接口的实现类

  - 要么重写接口中的所有抽象方法
  - 要么是抽象类

- #### 接口的成员特点

  - 接口成员变量默认被static final修饰：

    也就是在接口中定义 public int numer = 1 相当于 public static final int numer = 1

  - 接口中的方法只能是抽象方法(可以不用写abstract)
    void method() 相当于 public abstract method()

- #### 类和接口的关系

  - 类和类的关系

    继承关系，只能单继承，但是可以多层继承

  - 类和接口的关系

    实现关系，可以单实现，也可以多实现，也就是一个类可以实现多个接口

  - 接口和接口的关系

    继承关系，可以单继承，也可以多继承(Java 多继承体现在这)

- #### 抽象类和接口的区别

  - 成员区别

    抽象类： 变量，常量，有构造方法，既可以有抽象方法也可以有非抽象方法

    接口：常量；抽象方法

  - 关系区别

    类与类  ：继承，单继承

    类与接口：实现，包括单实现和多实现

    接口与接口 ：单继承，多继承

  - 设计理念区别

    抽象类： 对类抽象，包括属性，行为

    接口： 对行为抽象

# 内部类

- #### 内部类的访问特点

  - 非静态内部类可以直接访问外部类的成员，包括私有
  - 外部类要访问内部类的成员变量，必须创建对象，并且外部类可以访问内部类私有成员

- #### 成员内部类

  定义在类的成员位置

  ![image-20220228104021354](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220228104021354.png)

  成员内部类，外界如何创建对象？

  范例： Outer.Inner oi = new Outer().new Inner();

- #### 局部内部类

  定义在方法中, 外部类通过实例化才可访问成员变量，并且可以访问内部类私有成员

  局部内部类也可以访问外部类的成员，包括私有

  ![image-20220228104518938](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220228104518938.png)

- #### 匿名内部类

  - 格式：

    new  类名/接口名() {

    ​		 重写方法

    }

  - 重写方法说明诞生了一个新的类，这个类可以继承自别的类，也可以是实现了某个接口，

    但是这个类没有名字，而是直接new出了该类的实例化对象，所以叫匿名内部类





# Object

- #### toString方法

  默认是返回该对象的hashCode的十六进制，建议进行重写，编译器可以自动生成

- #### equal方法

  默认比较两个对象的地址，和 == 一样，可以进行重写进行内容比较，编译器也可以自动生成







# 基本类型包装类 （自动拆装箱）

- #### 装箱

  把基本数据类型转换为对应的包装类类型 ： Integer.valueOf()

  ```
  Interger i = 100 ；
  ```

  把一个基本数据类型的100赋值给一个Integer对象，对该语句编译器是不会报错的，为什么呢？

  这就涉及到自动装箱，经过反编译后我们可以看到这条代码被修改成了

  ```
  Integer i = Integer.valueOf(100)；
  ```

  也就是jdk帮我们自动把基本数据类型转换为其对应的包装类，这就是自动装箱

- #### 拆箱

  把包装类型转换为其对应的基本数据类型  ： Integer.intValue();

  ```
  Integer i = 0；
  i ++;
  ```

  经过反编译后,可以看到

  ```
  Integer i = Integer.valueOf(0)；// 自动装箱
  i = Integer.valueOf(i.intValue()// 自动拆箱 + 1); // 自动装箱
  ```

  其中Integer.valueOf(0)就是自动装箱的过程，而i.intValue()就是自动拆箱的过程



# 异常

Throwable是所有Error和Exception的超类

#### 异常体系：

![image-20220301083908489](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220301083908489.png)

Error:严重问题，程序本身不能处理的问题

Exception：称为异常类，它表示程序本身可以处理的问题

- RuntimeException：在编译期是不检查的，在运行时抛出的异常，非受检异常。`RuntimeException`是那些可能在 Java 虚拟机正常运行期间抛出的异常的超类
- 非RuntimeException：编译期就必须处理，否则程序不能通过编译，受检异常，Java编译器要求程序必须捕获或声明抛出这种异常。



### JVM的默认处理方案

如果程序出现了问题，我们没有做任何处理，最终JVM会做默认的处理

- 把异常的名称，异常原因以及异常出现的位置等信息输出在控制台
- 程序停止执行







# 集合Collection

- #### 并发修改异常

  - 描述：在用迭代器遍历list，或者用forEach方式遍历list的时候，往list中添加/删除元素，就会抛出CurrentModifiedException
  - 解决方案：
    - 改用fori循环遍历
    - 用Iterator遍历list可以直接调用iterator.remove()方法删除元素，但是iterator没有提供add方法
    - 用ListIterator遍历list，ListIterator就提供了add和remove方法

- #### 列表迭代器

  ![image-20220301142437031](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220301142437031.png)





# 数组和链表

#### 数组：查询快，增删慢

- 查询数据通过索引定位，查询任意数据耗时相同，查询效率高
- 删除数据时，要将原始数据删除，同时后面每个数据前移，删除效率低
- 添加数据时，添加位置后的每个数据后移 ，再添加元素，添加效率低

#### 链表：查询慢，增删快





# HashSet

#### 特点

- 看源码可以知道它维护了一个HashMap

- 底层数据结构是哈希表
- 对集合的迭代顺序不做任何保证，也就是说不保证存储和取出的元素顺序一致
- 没有索引，所以不能用普通的fori循环遍历
- 由于是Set集合，所以是不包含重复元素的集合

#### 哈希表

- jdk8 之前 数组 + 链表
- jdk8 之后 数据 + 链表 + 红黑树



# LinkedHashSet

#### 特点

- 维护了一个linkedHashMap，就是把HashSet中的HashMap改成了linkedHashMap
- 由哈希表和链表实现的Set接口
- 由链表保证元素有序，也就是说元素的存取和取出的顺序是一致的
- 由哈希表保证元素唯一





# TreeSet

#### 特点：

- 维护了一个TreeMap，就是把HashSet中的HashMap改成了TreeMap

- 元素唯一

- 元素会进行排序：

  TreeSet()：根据元素的自然排序进行排序

  TreeSet(Comparator comparator)：根据指定的比较器进行排序

- 没有索引，不能用fori循环遍历

#### 注意事项：

- 如果TreeSet不是用TreeSet(Comparator comparator)创建，那么调用add方法添加的对象必须实现Comparable接口（Integer这种的已经实现好了），主要是TreeMap.put方法中会进行Comparable强转，如果没实现Comparable接口的话会报错

  ![image-20220302113821091](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302113821091.png)

- Comparable中的ComparaTo方法返回值：

  - 等于0 相同元素
  - 大于0 ，升序排序
  - 小于0 ，降序排序

- 如果TreeSet用TreeSet(Comparator comparator)，则添加的元素无需实现Comparable接口

  ![image-20220302113708570](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302113708570.png)

- Comparator中的compara方法返回值：

  - 等于0 相同元素
  - 大于0 ，升序排序
  - 小于0 ，降序排序





# 泛型

#### 介绍：

是Jdk5中引入的特性，它提供了编译时类型安全检测机制，该机制允许在编译时期检测到非法的类型，它的本质是参数化类型，也就是说所操作的数据类型被指定指定为一个参数

参数化类型，顾名思义就是**将类型由原来的具体的类型参数化，然后在使用/调用时传入具体的类型**，

#### 分类：

- 泛型类

  ![image-20220302132006372](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132006372.png)

  ![image-20220302132515304](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132515304.png)

  泛型参数在类实例创建的时候确定

  

- 泛型方法

  ![image-20220302132118326](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132118326.png)

  ![image-20220302132314270](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132314270.png)

  泛型参数在传参的时候确定

  

- 泛型接口

  ![image-20220302132659239](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132659239.png)

  ![image-20220302132806943](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302132806943.png)

泛型参数在类实现该接口的时候确定，当然如果在类实现该接口的时候仍然不能确定具体的类型，可以这样

![image-20220302133011872](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302133011872.png)



#### 类型通配符

- **类型通配符：<?>**

  表示可以匹配任何的类型

  

- **类型通配符上限  <? extents 类型>**

  List<? extends User> ： 表示类型是User或者其子类

  

- **类型通配符下限  <? super 类型>** 

  List<? super User> ： 表示类型是User或者其父类

- ##### 使用场景

  用于限制引用的指向，比如下面的例子

  ![image-20220302195249714](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302195249714.png)

- **相关API**

  ![image-20220302195618603](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302195618603.png)



#### 可变参数

- 可变参数就是参数个数可变，用做方法的形参出现，那么方法参数个数就是可变了

- 范例：public static int num(int ...a){ }

  ![image-20220302194348994](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302194348994.png)

- 可变参数一定要放在最后

  ![image-20220302194544799](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302194544799.png)





# Map

#### Map概述：

- interface<K,V>  K：键的类型    V：值的类型

  Key是唯一的，V可以重复



#### Map的遍历：因为Map没有实现Iterable接口，无法直接使用增强for或者迭代器遍历。

![image-20220302200838251](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302200838251.png)





# Collections（注意结尾有s）

#### collections概述：

- 是针对集合操作的工具类

  ![image-20220302201153459](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220302201153459.png)







# IO流

#### 汉字存储

- 如果是GBK编码，占用两个字节
- 如果是UTF-8编码，占用三个字节

#### 为什么会出现字符流

- 由于字节流操作中文不是特别方便，所以Java就提供字符流

  字符流 = 字节流 + 编码表

- 用字节流复制文本文件时，文本文件也会有中文，但是没有问题，原因是最终底层操作会自动进行字节拼接，拼接成中文，如何识别是中文呢？

  - 汉字在存储的时候，无论选择那种编码存储，第一个字节都是负数

#### 编码表

![image-20220304160050158](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220304160050158.png)

![image-20220304160218181](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220304160218181.png)

![image-20220304160701137](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220304160701137.png)

#### 缓冲流

文件的读写操作终究是要交给操作系统内核完成，所以每次进行文件读取和写入操作的时候，程序都需要从**用户态切换到内核态**，操作完以后再切换回用户态。**而用户态到内核态的切换会造成比较大的开销**，如果每次读取都发生一次状态切换，那效率是很低下的。

- **所以对与写入操作，可以开辟一块内存区域，也就是缓冲区，先将数据写入这片内存区域，等到该内存满了以后再一次性将该内存中的数据写入文件当中**。
- **而对于读取操作，可以开辟一块内存区域，也就是读缓冲区，内核先一次性将文件里的部分内容读取出来放到这个缓冲区中，直到该缓冲区满。用户读需要的时候再到该缓冲区中获取，直到缓冲区没有数据以后，内核再从文件中读取数据充满该缓冲区**



举个搬西瓜例子：

- 没有缓冲区的时候，西瓜是一小车一小车发往商场
- 有缓冲区的时候，西瓜显示一小车一小车发给一辆大货车，装满大货车后由大货车发往商场，此时大货车就充当了缓冲区这个角色



- **字节缓冲流** 

  BufferedInputStream(FileInputStream) 和 BufferedOutputStream(FileOutputStream)

- **字符缓冲流**

  BufferedReader(InputStreamReader) 和 BufferedWriter(OutputStreamWriter)



#### 文件的异常处理

![image-20220307175606456](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220307175606456.png)



#### 小结

![image-20220307174123379](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220307174123379.png)

![image-20220307174336011](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220307174336011.png)

