# C语言编译过程

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

​		【1】静态链接就是在编译阶段直接把静态库 .o文件加入到可执行文件中去，这样可执行文件会比较大

​		【2】动态链接就是在链接阶段仅仅加入一些描述信息，而程序执行的时候再把相应的动态库(.so)加入到内存



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

![image-20220811231436809](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220811231436809.png)

（1）ELF Header：

​	在Linux操作系统中可以用readelf -h elf文件 的执行查看elf文件的ELF Header

​	![image-20220811232637354](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220811232637354.png)

​	该头部主要记录了整个ELF文件的全局信息

​	【1】Magic ：魔数，符合ELF文件格式的都以该魔数开头

​	【2】Entry point address：入口地址，也就是main函数的地址

​	【3】Start of program headers ：Program header相对于整个ELF文件的位置(64bytes的意思就是位于ELF文件头后64 bytes的位置)

​	【4】Strat of section headers ： section headers相对于整个ELF文件的位置

​	【5】size of header ： ELF文件头的大小

​	【6】size of program headers ：Program header的大小

​	【7】Size of Section header ：Section header table (节头表)的大小



（2）Program header table

​	程序头表，它记录着每个Section对应的操作，在Linux操作系统中可以通过readelf -h elf文件名 来查看elf文件的程序头表![image-20220811234709877](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220811234709877.png)

Program Headers：下面的每一行表示一个Segment，每个Segment会包含一系列的节。

Section to Segment mapping：下面的每一行与Program Headers中的每一行对应（Program Headers第n行对应Section to Segment mapping第n行，表示第n行的所有Section归属于第n个Segment)

【1】Type 列：这里主要关注Load，表示该Segment将被加载到内存

【2】offset：Segment相对于ELF文件的偏移地址

【3】FileSize：Segment的大小

【4】VirtAddr ：映射的虚拟地址

【5】PhysAddr ： 映射的物理地址，和虚拟地址相同

【6】MenSiz ：存储器给该Segment分配的空间

注意，含有.bss节的Segment中，MemSiz可能会比FileSiz大。因为 .bss保存有未初始化的全局变量，存储器也需要为这些全局变量分配空间。所以如果代码中有未初始化的全局变量，MenSiz 就会大于 FileSiz。

（3）Section header table

节头表，它记录所有节的位置，在Linux操作系统中可以通过readelf -S elf文件名查看

![image-20220811235624703](C:\Users\asus\AppData\Roaming\Typora\typora-user-images\image-20220811235624703.png)

【1】.text节就是存放代码的位置

【2】.plt  .got  .plt.got就是与Hook相关的节

【3】.rodata ：只读数据节，此节数据不可修改，存放常量

【4】.data ：数据节，存放以及初始化的常量和全局变量

【5】.bss ： 未初始化的全局变量

### 四.相关文献