# Plt与Got

### 一.  处理器分类

**1. C语言编译过程有四个阶段 ：预处理 -> 编译 -> 汇编 -> 链接**

**2. 对应处理器：预处理器 -> 编译器 -> 汇编器 -> 链接器**



###  二. .c文件到可执行文件的过程

**（1）.c + .h 文件  —> .i文件的过程叫预处理** 

**（2）.i 文件  —> .s 文件的过程叫编译（）**

**（3）.s 文件  —> .o 文件的过程叫汇编（）**

**（4）多个.o文件  —> 不带后缀名的可执行文件的过程叫链接**  



### 三. 处理器

##### 1. 预处理器

(1) 预处理器，顾名思义就是负责进行c文件的预处理，输出.i文件

(2) 预处理器主要做了一下几件事：

​	【1】将所有的#define删除，并且展开所有的宏定义。（就是把文件中使用到宏定义的地方替换成宏定义的文本，然后再把宏定义删除，因为替换完以后宏定义就没用了）

​	【2】处理所有的条件编译指令 #ifdef #ifndef #endif等

​	【3】处理include，将#include只想的文件插入到该行处，展开头文件

​	【4】删除所有注释

​	【5】添加行号和文件标示，这样在调试和编译出错的时候才能知道是哪一行

​	【6】保留#program编译器指令，其他以#开头的都是预编译指令，但是这个指令例外，此为编译器指示字，所以此步骤需要保留，关于此指示字的具体用法，在后面的内容将会详细讲解。

##### 2.编译器

（1）编译器负责编译，将.i文件进行汇编语言的转换，将.i文件转换成.s文件，.s文件也就是汇编文件

（2）编译过程包括以下几个过程

​		【1】词法分析

​		【2】语法分析

​		【3】语义分析

​		【4】源代码优化

​		【5】目标代码生成

​		【6】目标代码优化

（3）编译就是高级语言翻译为汇编语言的过程，并且在该过程中会对代码进行优化

##### 3.汇编器

（1）汇编器负责汇编

（2）汇编就是将汇编语言转变成机器语言的过程，并生成目标文件 （也就是.o文件）

##### 4.链接器

（1）链接器负责链接

（2）链接就是将多个.o目标文件和需要的库链接，生成可执行文件

（3）链接也分为静态链接和动态链接

​		【1】**静态链接就是在编译阶段直接把静态库 .a文件合并到可执行文件中去，这样可执行文件会比较大。**

- 优点：对环境的依赖小，具有较好的兼容性

- 缺点：生成的程序较大，需要更多的系统资源，在装入内存时消耗的时间较多，一旦库函数有了更新，必须重新编译程序

- 静态库存在的问题

  - 若多个.o文件都是用同一个静态库，当这些.o文件被一起打包到同一个可执行文件当中的时候，该静态库中的代码将会被拷贝多次，会造成很严重的空间浪费
  - 如果某一个静态库更新了，则与其相关的可执行文件都需要重新编译

- 静态库的制作：将多个.o文件通过ar命令打包成.a文件

  ![image-20220831155228341](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831155228341.png)

​		【2】**动态链接就是在链接阶段仅仅加入一些描述信息，而程序执行的时候再把相应的动态库(.so)加入到内存，动态库很好解决了静态库中的两个问题**

- 优点：

  - 在程序链接的时候，仅仅建立与所需动态库的函数之间的关系(plt和got表)
  - 在程序运行且使用到动态库函数的时候，才将所需的动态库调入内存
  - 实现进程之间的资源共享，所有的进程共享内存中的同一份动态库

- 缺点

  - 以来动态库的程序不能独立运行
  - 动态库依赖版本问题

- 动态库的制作

  ![image-20220831160302152](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831160302152.png)

- 动态库的使用

  ![image-20220831160405352](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831160405352.png)

### 四. gcc命令

##### 1.  -E 只进行到预编译阶段  gcc -E hello.c -o hello.i 或者 gcc -E hello.c （这两条命令的区别就是前者可以看到hello.i文件，后者不生成文件直接在命令行中显示内容）

##### 2.  -S 只进行到预编译阶段  gcc -S hello.c -o hello.s 或者gcc -S hello.c （这两者都会生成hello.s文件）gcc -S 适用于所有.s之前的文件

##### 3. -c  只进行到链接阶段 gcc -c hello.c 和 gcc -c hello.c -o hello.o （这两者都会生成hello.o文件）gcc -c 命令适用于所有.o文件之前的文件

##### 4. -o 输出文件，后接文件名

##### 5. gcc  hello.c  或者 gcc hello.c -o hello (两者都会生成可执行文件，只不过后者可以指定名字)



### 五.ELF文件

#### 1. 简介

ELF文件全称Executable and Linkable format，在Linux系统中主要有三种ELF文件

（1）可执行文件

（2）目标文件

（3）共享文件

需要注意的是ELF文件并不是以.elf为后缀名的文件，ELF只是一种文件的格式规范，linux中的可执行文件、目标文件（.o）以及共享文件（.so）都符合ELF格式规范，我的理解是：只要想被Linux加载到内存并执行的文件都需要符合ELF格式。

#### 2. ELF文件格式

ELF文件主要包括ELF header， Program header table，Section以及Section header table，如下图所示：

![image-20220831145939996](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831145939996.png)

##### （1）ELF Header：

​	在Linux操作系统中可以用readelf -h elf文件 的执行查看elf文件的ELF Header

![image-20220831150114892](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831150114892.png)

​	该头部主要记录了整个ELF文件的全局信息

​	【1】Magic ：魔数，符合ELF文件格式的都以该魔数开头

​	【2】Entry point address：入口地址，也就是main函数的地址

​	【3】Start of program headers ：Program header相对于整个ELF文件的位置(64bytes的意思就是位于ELF文件头后64 bytes的位置)

​	【4】Strat of section headers ： section headers相对于整个ELF文件的位置

​	【5】size of header ： ELF文件头的大小

​	【6】size of program headers ：Program header的大小

​	【7】Size of Section header ：Section header table (节头表)的大小



##### （2）Program header table

程序头表，它记录着每个Section对应的操作，在Linux操作系统中可以通过readelf -l elf文件名 来查看elf文件的程序头表

![image-20220831150200074](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831150200074.png)

Program Headers：下面的每一行表示一个Segment，每个Segment会包含一系列的节。

Section to Segment mapping：下面的每一行与Program Headers中的每一行对应（Program Headers第n行对应Section to Segment mapping第n行，表示第n行的所有Section归属于第n个Segment)

【1】Type 列：这里主要关注Load，表示该Segment将被加载到内存

【2】offset：Segment相对于ELF文件的偏移地址

【3】FileSize：Segment的大小

【4】VirtAddr ：映射的虚拟地址

【5】PhysAddr ： 映射的物理地址，和虚拟地址相同

【6】MenSiz ：存储器给该Segment分配的空间

注意，含有.bss节的Segment中，MemSiz可能会比FileSiz大。因为 .bss保存有未初始化的全局变量，存储器也需要为这些全局变量分配空间。所以如果代码中有未初始化的全局变量，MenSiz 就会大于 FileSiz。

##### （3）Section header table

节头表，它记录所有节的位置，在Linux操作系统中可以通过readelf -S elf文件名查看

![image-20220831150345985](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831150345985.png)

【1】.text节就是存放代码的位置

【2】.plt  .got  .plt.got这三个表指向相关代码

【3】.rodata ：只读数据节，此节数据不可修改，存放常量

【4】.data ：数据节，存放以及初始化的常量和全局变量

【5】.bss ： 未初始化的全局变量

### 四. Plt 和 Got

#### 1.Got表

###### （1）概述

​	Got表全称Global Offset Table,也就是全局偏移表，它的每一个表项都会指向一个函数的绝对地址，在x86架构中，Got表的前三个表项Got[0],Got[1],Got[2]是固定的

###### （2）Got表的特点

- Got表前三项为特殊项，分别保存.dynamic段地址(其实就是.dynamic section)，本镜像的link_map数据结构和_dl_runtime_resolve函数地址，**在编译阶段，除了.dynamic段地址以外的两个地址将会填入零地址，在程序启动时由动态连接器进行填充**
- Got[0]：dynamic，这里指向的就是连接器的地址。
- Got[1]：保存的是一个地址，指向已经加载的共享库的链表地址
- Got[2]：保存的是一个函数地址，定义入下：Got[2] = &_dl_runtime_resolve,这个函数的主要作用就是找到某个符号的地址，并把它写道与此符号相关的GOT项中，然后将控制转移到目标函数

![image-20220831152422505](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831152422505.png)

###### （2）延迟绑定

（1）缺少延迟绑定的Got方案

​		Got表项中保存着函数的调用地址，当程序需要调用动态库函数的时候，都需要到Got表中寻找相应的函数地址。试想，如果一个程序中需要调用大量的动态库函数，那么在链接阶段连接器就需要解析所有需要用的动态库函数的绝对地址并将它们填充到Got表对应的表项中，工作量大，时间久，就会造成程序运行慢的假象

（2）延迟绑定的Got方案

​		为了解决上述问题，就把动态库函数的链接延迟到了程序运行时期，也就当程序第一次执行到某个动态库函数的时候，才去解析该函数并将其绝对地址填入Got表中。而**延迟绑定**的技术就是通过**Plt表**实现的



#### 2.Plt表

###### （1）概述 

​	Plt表格中存放的是一系列的指令(比如jmp指令等)，我们现在先考虑动态库函数已经链接的情况

###### （2）PLT表结构特点

- PLT表中的第一项为公共表项(表项结构先push指令，在jump指定)

- 其余一个表项分配给一个动态库函数（表项结构先jump，在push，再jump）

  ![image-20220831162205704](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831162205704.png)

- 每一项PLT都会到对应的GOT表中读取目标动态库函数的地址

###### 	(3)PLT表和Got表的联系

- 我们可以将PLT表和GOT表抽象成以下的数据结构，如下图所示

  ![](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/image-20220831163201562.png)

- 其中Got[0],Got[1],Got[2]以及plt[0]都是特殊表项，所以Plt[1]将会对应Got[3]，以此类推

###### 	(4) 延迟绑定详解

**first.第一次加载动态库函数时的过程**

1. 调用puts函数，访问PLT表的<plt-0x80482b0>项
2. <plt-0x80482b0>项执行jmp指令跳转到**<got-0x80496f8>** 
3. **<got-0x80496f8>** 属于Got表地址范围，该地址内容也是一个地址<plt-0x80482b6>，这是该plt表项下一条指令的地址（<plt-0x80482b6>在<plt-0x80482b0>下面），所以跳转到<plt-0x80482b6>
4. <plt-0x80482b6>中执行了push指令(push  $0x0，**？？？这句指令是啥意思我也不知道**)，接着到下一个地址<plt-0x80482bb>执行里面的指令 jmp *<plt0-0x80482a0>跳转到plt表的头项地址<plt-0x80482a0>（也就是前面提到的Plt[0]这个特殊项）
5. <plt0-0x80482a0>中先将Got表地址<got0-0x80496f0>中的程序(其实就是链接器)加载到寄存器，接着执行下一条指令 jmp *<got2-0x80496f4>跳转到got表的<got2-0x80496f4>地址中
6. 而<got2-0x80496f4>地址，也就是Got[2]，指向的就是_dl_runtime_resolve函数地址
7. _dl_runtime_resolve函数就会解析puts函数的绝对地址<puts-0xf7e7c7e0>，并将**<got-0x80496f8>**函数中的内容修改为puts函数的绝对地址<puts-0xf7e7c7e0>（所以**<got-0x80496f8>**一开始存放的并不是puts函数的地址，经过_dl_runtime_resolve处理后才是puts函数的地址）
8. _dl_runtime_resolve函数执行puts函数

![在这里插入图片描述](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTYzMjE0,size_16,color_FFFFFF,t_70.png)

**second 再次调用puts函数**

1. 调用puts函数，访问PLT表的<plt-0x80482b0>项
2. <plt-0x80482b0>项执行jmp指令跳转到**<got-0x80496f8>** ，此时该got表项中存放的不再是Plt表项吓一条指定的地址<plt-0x80482b6>，而是puts函数的绝对地址<puts-0xf7e7c7e0>
3. 通过<puts-0xf7e7c7e0>地址直接访问puts函数

![在这里插入图片描述](C%E8%AF%AD%E8%A8%80%E7%BC%96%E8%AF%91%E8%BF%87%E7%A8%8B.assets/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTYzMjE0,size_16,color_FFFFFF,t_70-16619384684665.png)

