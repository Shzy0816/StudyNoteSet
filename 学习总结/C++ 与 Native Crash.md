# 一、C++ static

#### 1.定义在类内部的static

- 可以直接通过类名调用User::成员的方式访问（注意搞清楚 声明 、定义、初始化这三个概念）

  ```C++
  // 声明类
  class User{
      public:
      	int a;
      	// static修饰的非常量在声明阶段不能初始化
      	static int b;
      	// static修饰的const常量一定要初始化
      	static const int c = 100;
      	// static修饰的方法 - 静态方法
      	static int add(int a , int b);
      
      private:
      	static int pa;
  }
  
  // 下面这些初始化操作(定义操作)应该放在.cpp文件中，
  // 并且静态成员变量必须进行初始化，否则调用的时候会报错
  
  // static修饰的变量的定义， 定义完后系统会给予默认值0
  int User::b;
  // static修饰的变量的初始化(定义后的第一次手动复赋值就是初始化)
  User::b = 10;
  // 以上两句可以合并成一句 int User::b = 10;
  
  // 定义+初始化私有static变量
  int User::pa = 10;
  // 定义静态方法
  void int User::add(int a,int b){
      return a + b;
  }
  
  int main(){
      User::b;
      User::add();
      // User::pa 无法调用，需要通过公开的静态方法访问
  }
  ```

- static修饰的非常量不能初始化，比如 `static int b = 100;` 编译器会报错

- 对于非静态成员，每个对象都会有自己的拷贝。对于静态成员，有该类的所有对象共享访问

- 一个类中，静态方法无法访问非静态变量(重点)

- 静态成员存储在全局数据区

#### 2.限制作用域的static

- static修饰全局变量

  作用：限制全局变量的作用域，用static修饰的全局变量无法在文件之间共享，只能作用于当前的文件

  ```c++
  // 某c++文件
  int a; // 全局变量，可以在文件之间共享
  static int a; //静态变量，只允许当前文件使用
  ```

- static修饰函数(静态函数，PS：不是方法，是函数！)

  作用：限制函数的作用域，用static修饰的函数无法在文件之间共享，只能被作用与当前文件

  ```c++
  #include <iostream.h>
  
  //声明静态函数
  static void fun();
  
  void main(){
      fun();
  }
  
  //定义静态函数
  void fun(){
      int n = 10;
      cout<< n <<endl;
  }
  ```

- static 修饰局部变量(静态局部变量)

  - 静态局部变量在全局数据区分配(而不是栈空间)
  - 静态局部变量在函数首次执行的时候被声明和初始化一次，之后再调用该函数，局部静态变量不会再进行初始化
  - 静态局部变量一般在声明处进行初始化，如果没有显式初始化，会自动赋值0
  - 静态局部变量始终驻留在全局数据区，知道程序结束。但其作用域之只限制在声明之后，且函数闭包结束之前
  - 可以看看下面的例子

  ```C++
  #include <iostream.h>
  void fun();
  void main(){
      fun();
      fun();
      fun();
  }
  void fun(){
      static int n = 10;
      cout<< n << endl;
      n++;
  }
  
  输出结果
  10
  11
  12
  ```



# 二、C++虚函数

#### 1.虚函数简介

##### 1.1多态

用父类指针指向子类的实例，然后通过父类的指针调用子类的成员方法，这种技术可以让父类指针具有“多种形态”，这是一种泛型技术，也就多态技术。

##### 1.2 虚函数的概念

父类(基类)中声明为vistual并在一个或者多个派生类(子类)中重新定义的成员函数叫做虚函数(其实用visual修饰的函数就是虚函数)

##### 1.3虚函数的作用

- 实现动态联编(后面会介绍)
- 在定义了虚函数之后，可实现在派生类中对虚函数进行重写，从而实现统一的接口和不用的执行过程。（和java多态一个意思，父类变量指向子类实例，调用方法采取子类的实现，经典的口诀：方法看左边，实现看右边）



#### 2.虚函数的运用

##### 2.1继承关系中的非虚函数

```c++
#include <iostream.h>
using namespace std;

class Father{
public:
    Father(){};// 构造方法，无参
    ~Father(){}; //析构函数
    void show(void){
        cout<<"I am father!"<<endl;
    }
};

class Son: public Father{
public:
    Son(){};
    ~Son(){};
    void show(){
        cout<<"I am son!"<<endl;
    }
};

void main(){
    // 父类指针ptr
    Father *fatherPtr;
    // 父类实例father（c++中不用new就会自动初始化一个实例给这个变量）
    Father father;
    // 子类实例son
    Son son;
    // 父类指针指向父类对象
    fatherPtr = &father;
    fatherPtr->show();
    // 父类指针指向子类对象;
    fatherPtr = &son;
    fatherPtr->show();
    return 0;
}

输出结果
I am father!;
I am father!;
```

注意到这段代码中输出的结果都是`I am father!`

PS:  即使父类指针fatherPtr指向子类对象后，第二个show()的结果也不是`I am B!`，主要原因是父类show方法并未使用visutal修饰，程序采取静态联编，而静态联编选择函数是基于指向对象的指针类型，而fatherPtr是指向Father类型指针，因此会一直调用Father的show方法



##### 2.2 继承关系中的虚函数

```c++
#include <iostream.h>
using namespace std;

class Father{
public:
    Father(){};// 构造方法，无参
    vistual ~Father(){}; //析构函数
    vistual void show(void){
        cout<<"I am father!"<<endl;
    }
};

class Son: public Father{
public:
    Son(){};
    ~Son(){};
    void show(){
        cout<<"I am son!"<<endl;
    }
};

void main(){
    // 父类指针ptr
    Father *fatherPtr;
    // 父类实例father（c++中不用new就会自动初始化一个实例给这个变量）
    Father father;
    // 子类实例son
    Son son;
    // 父类指针指向父类对象
    fatherPtr = &father;
    fatherPtr->show();
    // 父类指针指向子类对象;
    fatherPtr = &son;
    fatherPtr->show();
    return 0;
}

输出结果
I am father!;
I am son!;
```

PS：这就体现了虚函数的作用以及C++的多态。当父类指针指向子类的对象的时候，调用方法采取子类的实现。因为被vistual修饰的方法采用的是动态联编。



2.3 非继承关系中的虚函数

```c++
#include <iostream.h>
using namespace std;

class A{
public:
    A(){};// 构造方法，无参
    ~A(){}; //析构函数
    vistual void show(void){
        cout<<"I am A!"<<endl;
    }
};

class B{
public:
    B(){};
    ~B(){};
    void show(){
        cout<<"I am B!"<<endl;
    }
};

void main(){
    // 父类指针ptr
    A *APtr;
    // 父类实例father（c++中不用new就会自动初始化一个实例给这个变量）
    A a;
    // 子类实例son
    B b;
    // 父类指针指向父类对象
    APtr = &a;
    APtr->show();
    // 父类指针指向子类对象，因为没有继承关系，所以需要强制转换
    APtr = (A*) &b;
    APtr->show();
    return 0;
}

输出结果
I am A!;
I am B!;
```

PS：如果两个类之间没有继承关系，两个类的show方法都需要加vistual修饰



#### 3.拓展问题

##### 3.1 [2.2]节中为什么析构函数要定义为虚函数(PS：虚构函数就是~Father()这种的)？

注：析构函数在对象要被销毁前调用

在用基类操作派生类的时候，为了防止执行基类的析构函数，而不执行派生类的析构函数。

派生类的析构函数不执行有可能会导致派内存泄漏(有些资源需要在析构函数中释放)



# 三、C++动态联编和静态联编

#### 1.静态联编

##### 1.1 概述

静态联编就是在程序的编译阶段就确定函数的调用地址

##### 1.2 静态联编的

- 静态联编对成员函数的选择是基于**指针的类型或者引用标识的类型**

#### 2.动态联编

##### 2.1概述

相较于静态联编，动态联编在编译时只知道要运行的函数名字，并不能知道具体的函数地，在程序运行的时候才确定函数的具体地址。

##### 2.2 动态联编的特点

- 动态联编对成员函数的选择是基于**对象的类型**
- 动态联编要求基类的动态联编方法用**visual修饰**
- 动态联编要求派生类中动态联编的方法返回值的类型，参数个数以及类型，方法名称都必须和基类的动态联编方法(虚函数)相同
  - 如果满足这个条件，派生类才会采取动态联编的方法
  - 如果不满足这个条件，派生类类虚函数将丢失其须特性，在调用的时候采取静态联编
- 动态联编只能通过**指针或者引用对象标识**来操作虚函数
- 动态联编规定，只能通过指向基类的指针或者基类对象来调用虚函数



# 四、构造方法和析构函数

#### 1.构造方法

##### 2.1 概述

析构函数是成员函数的一种，它的名字与类名相同



#### 2.析构函数

##### 2.1 概述

析构函数是成员函数的一种，它的名字与类名相同，但前面要加上`~`，且没有返回值，但是可以有参数(无参构造方法和带参构造方法)

##### 2.1 特点

- 一个类有且只有一个析构函数

- 如果定义了的时候没有写析构函数，编译器会默认生成析构函数

- 析构函数在对象消亡的时候调用（自动生成的对象会自动消亡，new生成的对象需要通过delete消亡）

- 对象如果在生存期间用new运算符分动态分为**对象成员**分配了内存，就需要在消亡的时候用delte释放内除，有了析构函数，只要在析构函数中调用delete语句，就能确保对象运行中用new运算符为**对象成员**分配的空间在对象消亡时被释放。例如下面的例子

  ```c++
  class User{
  public:
      Phone phone;
      User(){}
      ~User(){}
  };
  
  User::User(){
      phone = new Phone();
  }
  
  User::~User(){
      delete phone;
  }
  ```

- 对象消亡分自动消亡和手动消亡

  - 自动消亡：自动生成的对象会自动消亡
  - 手动消亡：用new方式生成的对象需要通过delete的方式手动进行消亡（如果不及时用delete消亡，就会造成内存泄漏）
  - 下面的例子体会一下

  ```c++
  #include <iostream.h>
  using namespace std;
  
  class User{
  public:
      User(){
          cout<< "User创建"<<endl;
      }
      ~User(){
          cout<< "User销毁"<<endl;
      }
  };
  
  int main(){
      // 自动创建两个User对象，构造方法调用两次
      User users[2];
      // new方式创建，构造方法调用一次
      User* userPtr = new User;
      // 手动销毁，析构函数调用一次
      delete userPtr;
      // new方式创建，构造方法调用两次
      User* usersPtr = new User[2];
      // 手动销毁，析构函数调用两次
      delete usersPtr;
      cout<< "Main end----------"<<endl;
      return 0;
  }
  
  输出结果：
  User创建
  User创建
  User创建
  User销毁
  User创建
  User创建
  User销毁
  User销毁
  Main End----------
  User销毁
  User销毁
  ```

  

# 五.信号与Native Crash

#### 1.概述：

- 信号是进程间通讯方式中的一种，需要与信号量区别开来
- 信号`signal`可以理解为由操作系统传递给进程的事件，只是用来通知程序发生了什么事情，并不会传递任何数据
- 信号是一种中断，因为它可以改变那程序的流程，当信号传递给进程的时候，进程将停下其正在执行的操作，并去处理信号，也即是**信号处理是异步的**

#### 2.信号相关库

##### 2.1 对于window

- `#include <csignal>` 或 `#include <signal.h>` 是处理信号的C-library。

- 该库包含 signal 与 raise 两个功能函数。

  - 函数 signal 用于捕获信号，可指定信号处理的方式。
  - 函数 raise产生一个信号，并向当前正在执行的程序发送该信号

- 常见信号：

  | 宏      | **信号**                                                     |
  | ------- | ------------------------------------------------------------ |
  | SIGABRT | （信号中止）异常终止，例如由...发起 [退出](http://www.cplusplus.com/abort) 功能。 |
  | SIGFPE  | （信号浮点异常）错误的算术运算，例如零分频或导致溢出的运算（不一定是浮点运算）。 |
  | SIGILL  | （信号非法指令）无效的功能图像，例如非法指令。这通常是由于代码中的损坏或尝试执行数据。 |
  | SIGINT  | （信号中断）交互式注意信号。通常由应用程序用户生成。         |
  | SIGSEGV | （信号分段违规）对存储的无效访问：当程序试图在已分配的内存之外读取或写入时。 |
  | SIGTERM | （信号终止）发送到程序的终止请求。                           |

- `signal (int sig, void (*func)(int));`函数

  - `int sig` 代表一个型号编号

  - `void (*func)(int)` 表示一个返回值为void并且只有一个int类型参数的函数指针

    比如 `void handlerSign(int a){}`

- `int raise (signal sig)`函数：

  - 向进程自身发送一个 编号为sig的信号

##### 2.1 对于Linux



#### 3.Native Crash

##### 3.1 Native Crash简介

- Native Crash就是Native层的奔溃检测
- **Native层奔溃**：在程序出现异常之后(比如内存越界，除0)，由内核向进程发送异常信号，进程收到异常信号后结束自身
- Native Crash就是通过捕获这些信号量，经过相应的处理后将结果通过JNI层传递给Java层，使得java层能够知道Native层发生的异常

##### 3.2 实现Native Crash基本流程

###### 3.2.1 代码示例

```c++
#include <iostream>
#include <signal.h>


void nativeCrash(int sig){
    // nativeCrash函数逻辑，这里用cout模拟一下
    std::cout << "native crash";
    // 处理完后替换回默认的信号处理函数
    signal(sig , SIG_DFL);
    // 重新给自己的进程发送一遍信号，让程序默认处理自身
    raise(sig);
}

int main() {
    // 注册信号，收到SIGILL不再执行kill函数，而是执行nativeCrash
    signal(SIGILL,nativeCrash);

    // 模拟收到信号的场景
    raise(SIGILL);

    while (1){
        std::cout << "while";
    }
    return 0;
}

输出：
native crash
```

这段函数输出一个native crash说明程序nativeCrash方法成功注册，

并且程序并没有因为while循环而阻塞，说明SIGILL信号默认处理方式得到执行



###### 3.2.1 总结一下native crash流程

- 实现一个void类型并且有且只有一个int类型参数的函数SignHandler
- 将SignHandler注册到所有异常信号中，收到异常信号时不在执行原来的信号处理函数，而是执行SignHandler
- 在SignHandler中对异常信号进行分析处理，并将结果通过JNI层返回给java层
- 将信号处理方式替换回默认处理方式
- 通过raise重新发送一遍信号