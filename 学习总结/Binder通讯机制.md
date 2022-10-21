# Binder通讯机制

## 一、Binder概述

#### 1.简介

- 从通讯层面看，Binder是一种通讯机制
- 从驱动层面看，Binder是一个虚拟物理设备驱动
- 从应用层看：Binder是一个封装好的Bean类

binder是android中的“血管”，Android系统中，各大服务进程(system_server)与用户进程间的通讯，AIDL以及许多跨进程通讯都是由借助Binder来实现的。



#### 2.常见的跨进程通讯方式和Binder的区别

###### 2.1 Android常见的跨进程通讯方式有以下几种

- 管道
- 消息队列
- Socket
- 文件
- Binder
- 共享内存

###### 2.2 而它们在进行一次通讯的时候拷贝的次数分别为：

- 管道：2次
- 消息队列：2次
- Socket：2次
- 文件：2次
- Binder：1次
- 共享内存：0次

###### 2.3 Binder相比较管道、消息队列、Socket、文件这些传统的进程间通讯方式，可以体现出以下这些优势

| 优势   | 描述                                                |
| ------ | --------------------------------------------------- |
| 性能   | 只需要一次数据拷贝，性能上仅次于共享内存            |
| 稳定性 | 基于C/S架构，职责明确，架构清晰                     |
| 安全性 | 为每个App分配UID，进程的UID是鉴别进程身份的重要标志 |

###### 2.4 Binder于共享内存

Binder性能次于共享内存，但是多个进程操作共享内存就会有同步和资源竞争的问题，从安全的角度上看，使用Binder更加具有优势。

###### 2.5 在安全方面

- 传统的进程间通讯方式对于通讯双方并没有做严格的身份验证机制，比如Socket通讯的IP地址是客户端手动输入，很容易伪造，而Binder通讯中的App UID由Binder分配，非用户手动输入，无法伪造。
- Binder机制从协议本身(RPC协议)就支持对通讯双方做身份验证，从而大大提升了安全性



#### 3. 补充：拷贝的概念

- 在Linux系统中，通讯的本质就是数据的拷贝

- ##### 数据拷贝是指：数据从用户空间复制到【内核虚拟地址空间】，或者是从【内核虚拟地址空间】复制到【私有虚拟地址空间】。而操作系统要求处于内核态的进程才能进入【内核虚拟地址空间】，所以每一次数据拷贝都会涉及到用户态 -> 内核态 -> 用户态的切换

- 当进程想从内核地址读取/写入数据的时候，都会涉及到数据的拷贝

  - 向【内核虚拟地址空间】写入数据：进程从用户态切换成内核态进入到【内核虚拟地址空间】，将数据从【私有虚拟地址空间】拷贝到【内核虚拟地址空间】中，然后再切换回用户态
  - 从【内核虚拟地址空间】读取数据，进程从用户态切换成内核态进入到【内核虚拟地址空间】，间数据从【内核虚拟地址空间】拷贝到【私有虚拟地址空间】中，然后再切换会用户态




#### 4. 补充：进程的内核地址空间与私有地址空间

##### 每个进程都有自己的一段虚拟地址，

- 这个虚拟地址中有最开始的一段【虚拟地址空间】会映射到【内核物理地址】，这段地址是进程之间共享的，叫【内核虚拟地址空间】
- 其余的地址为私有虚拟地址空间，这段地址是私有的
- 用户进程想要访问内核地址，必须要从用户态切换到内核态
- 以下图为例，32位的主机会为每一个进程分配4GB的虚拟内存空间，
  - **内核虚拟地址空间：**在这4GB的虚拟内存空间当中，起始的1GB空间是虚拟内核地址空间，所有进程的虚拟内核地址空间都会映射到同一块物理地址(内核物理地址，也就是内核在内存的位置)。所有现场都可以访问内核地址，但是需要从用户态切换到内核态，会有一定的性能开销。
  - **私有虚拟地址空间：**而其余的3GB是进程【私有虚拟地址空间】，不同进程的【私有虚拟地址空间】一般来说互不影响，用户可以手动为进程开辟**共享内存**，使得进程A的某一段虚拟地址和进程B的某一段虚拟地址映射到同一个物理内存(mmap)。不同进程间除了共享内存之外的【私有虚拟地址空间】所映射的物理内存为线程私有，别的进程无法访问，这就实现了进程之间数据的隔离保证了进程间数据的安全性。

![image-20221020095325170](Binder%E9%80%9A%E8%AE%AF%E6%9C%BA%E5%88%B6.assets/image-20221020095325170.png)



## 二、Binder原理与运用

#### 1.Binder数据传输原理图

![19729408-4c065256f304372e](Binder%E9%80%9A%E8%AE%AF%E6%9C%BA%E5%88%B6.assets/19729408-4c065256f304372e.webp)

PS：注意区分一下用户地址空间，内核地址空间以及内核地址的概念：

- 内核地址：内核所处的**物理地址**，为了方便区分，本文都叫**内核物理地址**

- 用户地址空间：就是进程**虚拟私有地址空间**，这是一段**虚拟地址**

- 内核地址空间：映射到内核地址的进程**虚拟地址**，本文都叫**内核虚拟地址空间**

  

Binder基于C/S架构，二C/S架构必然会有服务端和客户端，比如上图的Server进程和Client进程，而它们之间传输数据的方式主要就是通过mmap映射实现的 (后面会详细讲述mmap)。这里结合上图分析一下Binder机制中Server段和Client端的通讯过程。

1. Binder驱动会用binder_mmap方法将Server进程【私有虚拟地址空间】的一段地址(地址SVA,Server Visutal Address) 映射到【内核物理地址】(地址KPA,Kernal Physical Address)当中
2. 内核物理地址是共享的，Client的【内核虚拟地址空间】也会与【内核物理地址】映射，那在Client的【内核虚拟地址空间】中必然会找到与地址KPA映射的虚拟地址段(地址CVKA，Client Visutal Kernal Address)。
3. Client找到虚拟地址CVKA之后，将需要传输给Server的数据拷贝到地址CVKA中，而由于虚拟地址CVKA位于Client的【内核虚拟地址空间】，所以在拷贝之前Client需要从用户态切换到内核态。
4. 当Client把数据拷贝到CVKA之后，由于CVKA与内核物理地址KPA存在映射，KPA与Server端的虚拟地址SVA存在映射，而SVA位于Server端的【私有虚拟地址空间】，所以Server无需进行数据【拷贝】，可以直接从虚拟地址KPA中获取到Client传输过来的数据。



#### 2.内核函数mmap

第1小节中提到了mmap，这是一个内核函数



#### 3.Binder在Android通讯服务框架中的运用

##### 3.1 RPC协议架构

RPC协议是基于C/S架构的协议，主要包含注册中心、服务发现、服务注册、序列化、传输这几个模块

![e1fe9925bc315c604892d1812f8e251a49547726](Binder%E9%80%9A%E8%AE%AF%E6%9C%BA%E5%88%B6.assets/e1fe9925bc315c604892d1812f8e251a49547726.webp)

- 服务端先将自己注册到注册中心
- 客户都需要使用服务的时候，到注册中心查找到对应的服务，并将数据序列化后通过网络传输发送给服务器
- 服务器收到程序后，调用处理程序，并将结果通过网络传输的方式返回给客户端

这里只是简单地介绍了RPC框架的大概，正常的RPC框架要复杂地多，本文的重点不在RPC协议，如想深入了解，请自行查阅资料



##### 3.2 Binder在Android RPC中的运用

![Android  RPC](Binder%E9%80%9A%E8%AE%AF%E6%9C%BA%E5%88%B6.assets/Android%20%20RPC.png)

Android的服务通讯框架就采用了RPC架构，大致的架构图如上图所示。

- ##### 服务发现与查找

  - ServiceManager就是RPC架构中的注册中心，用于管理一系列的服务
  - Service想向Client提供服务，必须注册到ServiceManager，这就是**服务注册**
  - Client想使用Service提供的服务，就需要到ServiceManger查找，这就是**服务查找**

- ##### 跨进程传输

  - 在《2.1 RPC协议架构》中的数据传输涉及到的是**网络传输**，而Android服务通讯框架中涉及到的是**跨进程传输**

  - 跨进程传输必不可少的是**序列化和反序列化**，将数据转换成能在进程之间传输的形态

  - Binder在Android服务通讯RPC框架中充当了**数据传输者**的角色

    - Client从服务掉获取到Service端的Stub.Proxy对象，这其实就是一个BinderProxy.java对象

    - 通过BinderProxy会调用Native层的BpBinder对象将数据传输给Binder驱动

    - Binder驱动将数据转发给Service端的BBinder对象，然后由BBinder对象转发给Service的实体(Stub)，然后调用相关处理逻辑进行数据的处理，再将结果返回给Client

      

## 三、源码解析

### 1.AIDL

#### 1.1 简介

AIDL（Andorid Interface Definition Lauguage）是一种IDL语言，用于生成可以在Android上两个进程之间进程通讯(IPC)代码

#### 1.2 AIDL代码解析

##### 1.2.1 AIDL文件以及生的java类

PS：这里不再介绍AIDL的使用，我们直接创建一份AIDL文件，并生成代码

**aidl文件如下：**

```java
package com.example.aidl;

interface IMyAidlInterface {
    int add(int a , int b);
    int multiple(int a, int b);
}

```

**编译后生成的java类**

```java
package com.example.aidl;
// Declare any non-default types here with import statements

public interface IMyAidlInterface extends android.os.IInterface
{
  /** Default implementation for IMyAidlInterface. */
  public static class Default implements com.example.aidl.IMyAidlInterface
  {
    /**
         * Demonstrates some basic types that you can use as parameters
         * and return values in AIDL.
         */
    @Override public int add(int a, int b) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int multiple(int a, int b) throws android.os.RemoteException
    {
      return 0;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.example.aidl.IMyAidlInterface
  {
    private static final java.lang.String DESCRIPTOR = "com.example.aidl.IMyAidlInterface";
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.example.aidl.IMyAidlInterface interface,
     * generating a proxy if needed.
     */
    public static com.example.aidl.IMyAidlInterface asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.example.aidl.IMyAidlInterface))) {
        return ((com.example.aidl.IMyAidlInterface)iin);
      }
      return new com.example.aidl.IMyAidlInterface.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
        case TRANSACTION_add:
        {
          data.enforceInterface(descriptor);
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          int _result = this.add(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(_result);
          return true;
        }
        case TRANSACTION_multiple:
        {
          data.enforceInterface(descriptor);
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          int _result = this.multiple(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(_result);
          return true;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements com.example.aidl.IMyAidlInterface
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
           * Demonstrates some basic types that you can use as parameters
           * and return values in AIDL.
           */
      @Override public int add(int a, int b) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(a);
          _data.writeInt(b);
          boolean _status = mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return getDefaultImpl().add(a, b);
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int multiple(int a, int b) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(a);
          _data.writeInt(b);
          boolean _status = mRemote.transact(Stub.TRANSACTION_multiple, _data, _reply, 0);
          if (!_status && getDefaultImpl() != null) {
            return getDefaultImpl().multiple(a, b);
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      public static com.example.aidl.IMyAidlInterface sDefaultImpl;
    }
    static final int TRANSACTION_add = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_multiple = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    public static boolean setDefaultImpl(com.example.aidl.IMyAidlInterface impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Stub.Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Stub.Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }
    public static com.example.aidl.IMyAidlInterface getDefaultImpl() {
      return Stub.Proxy.sDefaultImpl;
    }
  }
  /**
       * Demonstrates some basic types that you can use as parameters
       * and return values in AIDL.
       */
  public int add(int a, int b) throws android.os.RemoteException;
  public int multiple(int a, int b) throws android.os.RemoteException;
}
```



##### 1.2.2 AIDL java接口类解析

这个自动生成的接口类有点长，我们先看看整体

![image-20221020133918466](Binder%E9%80%9A%E8%AE%AF%E6%9C%BA%E5%88%B6.assets/image-20221020133918466.png)

###### 【1】继承关系

IMyAidlInterface文件编译后会生成一个IMyAidlInterface接口类，并且该接口类继承自IInterface接口

###### 【2】接口方法

该接口还包含两个接口方法add和multiple

由于该接口继承自IInterface，所以该接口也会继承IInterface的接口方法asBinder

```java
package android.os;

/**
 * Base class for Binder interfaces.  When defining a new interface,
 * you must derive it from IInterface.
 */
public interface IInterface
{
    /**
     * Retrieve the Binder object associated with this interface.
     * You must use this instead of a plain cast, so that proxy objects
     * can return the correct result.
     */
    public IBinder asBinder();
}
```

###### 【3】空实现类Default

该接口类中包含一个名为Default的空实现类

```java
/**
* 空实现类
**/
public static class Default implements com.example.aidl.IMyAidlInterface{
    @Override public int add(int a, int b) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int multiple(int a, int b) throws android.os.RemoteException
    {
      return 0;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
```

###### 【4】Stub类和Stub.Proxy类

该接口还包含了一个Stub抽象类，该类也是aidl的核心类

- Stub类继承自Binder类并且实现了IMyInterface接口

- Stub实现了IMyInterface接口并实现了asBinder方法，然后将其余两个接口方法`int add(int a, int b)`和`int multiple(int a, int b)`作为抽象方法提供给用户实现

- Stub类继承自Binder类并重写了其中的onTransact方法

  ```java
  @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
  {
    java.lang.String descriptor = DESCRIPTOR;
    switch (code)
    {
      case INTERFACE_TRANSACTION:
      {
        reply.writeString(descriptor);
        return true;
      }
      // 如果客户端请求add方法
      case TRANSACTION_add:
      {
        data.enforceInterface(descriptor);
        // 反序列化客户端的请求参数
        int _arg0;
        _arg0 = data.readInt();
        int _arg1;
        _arg1 = data.readInt();
        // 调用add方法处理参数
        int _result = this.add(_arg0, _arg1);
        // 将结果写入reply进行序列化
        reply.writeNoException();
        reply.writeInt(_result);
        return true;
      }
      // 如果客户端请求multiple方法
      case TRANSACTION_multiple:
      {
        data.enforceInterface(descriptor);
        // 反序列化请求参数
        int _arg0;
        _arg0 = data.readInt();
        int _arg1;
        _arg1 = data.readInt();
        // 调用multiple方法处理参数
        int _result = this.multiple(_arg0, _arg1);
        // 将结果写入reply进行序列化
        reply.writeNoException();
        reply.writeInt(_result);
        return true;
      }
      default:
      {
        return super.onTransact(code, data, reply, flags);
      }
    }
  }
  ```

  梳理一下该方法中的逻辑

  - **反序列化请求参数**

  - **根据请求code调用相应的方法来处理请求参数**

    每个code会对应一个方法，这个AIDL会自动生成，这里TRANSACTION_add对应add方法，TRANSACTION_multiple对应multiple方法

    ```java
    public static abstract class Stub extends android.os.Binder implements com.example.aidl.IMyAidlInterface{
        // .....
        
        static final int TRANSACTION_add = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_multiple = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        
        // .....
    }
    ```

  - **将响应写入reply进行反序列化**

- **在Stub类中，还存在着一个内部类Proxy（重点）**

  ```java
  private static class Proxy implements com.example.aidl.IMyAidlInterface
      {
        private android.os.IBinder mRemote;
        Proxy(android.os.IBinder remote)
        {
          mRemote = remote;
        }
        @Override public android.os.IBinder asBinder()
        {
          return mRemote;
        }
        public java.lang.String getInterfaceDescriptor()
        {
          return DESCRIPTOR;
        }
        /**
             * Demonstrates some basic types that you can use as parameters
             * and return values in AIDL.
             */
        @Override public int add(int a, int b) throws android.os.RemoteException
        {
          // 创建两个Parcel对象用于序列化和反序列化
          android.os.Parcel _data = android.os.Parcel.obtain();
          android.os.Parcel _reply = android.os.Parcel.obtain();
          int _result;
          try {
            // 将请求参数写入_data中，其实这一步就是请求参数的序列化
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(a);
            _data.writeInt(b);
            // 调用transact方法跨进程调用远程服务，并将Parcel实例_reply作为参数传入，用于接收序列化之后的响应数据
            // transact方法返回的_status是一个boolean变量，true代表成功，false代表请求失败
            boolean _status = mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
            // 如果请求失败，则调用默认实现类中的add方法
            if (!_status && getDefaultImpl() != null) {
              return getDefaultImpl().add(a, b);
            }
            // 反序列化
            // 如果请求成功，就可以从_reply中读取响应数据
            _reply.readException();
            _result = _reply.readInt();
          }
          finally {
            // 回收parcel实例
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
      
        @Override public int multiple(int a, int b) throws android.os.RemoteException
        {
          // 创建两个Parcel对象用于序列化和反序列化
          android.os.Parcel _data = android.os.Parcel.obtain();
          android.os.Parcel _reply = android.os.Parcel.obtain();
          int _result;
          try {
            // 将请求参数写入_data中，其实这一步就是请求参数的序列化
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(a);
            _data.writeInt(b);
            // 调用transact方法跨进程调用远程服务，并将Parcel实例_reply作为参数传入，用于接收序列化之后的响应数据
            // transact方法返回的_status是一个boolean变量，true代表成功，false代表请求失败
            boolean _status = mRemote.transact(Stub.TRANSACTION_multiple, _data, _reply, 0);
            if (!_status && getDefaultImpl() != null) {
              return getDefaultImpl().multiple(a, b);
            }
            // 反序列化
            // 如果请求成功，就可以从_reply中读取响应数据
            _reply.readException();
            _result = _reply.readInt();
          }
          finally {
            // 资源回收
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
        public static com.example.aidl.IMyAidlInterface sDefaultImpl;
      }
  ```

  - Proxy类继承自IMyAidlInterface接口并实现了其中的两个方法，这两个方法的实现流程都是一致的。这里结合注释，以add方法为例讲述一下其中的逻辑

    1. **创建了两个Parcel实例 _data 和 _reply ** 

       _data用于将请求参数进行序列化

       _reply用于接受经过序列化的响应数据，并进行反序列化操作 

    2. **将请求参数int a 和 int b写入_data当中实现序列化**

    3. **调用`mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);`**

       这里重点关注前面三个参数：

       Stub.TRANSACTION_add：方法对应的下标

       _data: 序列化后的请求参数

       _reply: 用于接收经过序列化处理的响应数据的Parcel实例

       **PS1**：mRemote其实就是远程服务的Stub代理，Stub.TRANSACTION_add参数表示客户端请求的是服务端的add方法，mutiple方法自然也会有用一个值表示，它们定义在Stub类中（上面已经提到过）。

       **PS2**：执行完mRemote.transact方法之后，由于mRemote是某一个服务进程Stub类的跨进程代理类，所以服务进程的Stub类的onTransacte方法就会被触发，在该方法中会对请求参数进行处理，并将结果返回给客户端（忘记了再看看前面的！）。

    4. **执行完mRemote.transact方法后**

       ​	若请求失败，则会调用默认空实现类的add方法返回结果

       ​    若请求成功，就可以到_reply中获取反序列化后的响应数据

    5. **释放Parcel资源**

    6. **返回响应结果**

    

##### 1.2.3 Stub.Proxy类中的mRemote到底是什么?(BinderProxy的引入)

###### 【1】从AIDL的使用说起

一般我们使用AIDL，就是创建一个Service，再onBinder的时候返回一个Stub的实现类，并实现接口方法

```java
public class MyService extends Service {
    private final StubImpl stub = new StubImpl();

    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }

    public static class StubImpl extends IMyAidlInterface.Stub {
        @Override
        public int add(int a, int b) throws RemoteException {
            return a + b;
        }

        @Override
        public int multiple(int a, int b) throws RemoteException {
            return a * b;
        }
    }
}
```

客户端在绑定Service的时候，在onServiceConnected回调中收到一个IBinder对象，然后调用`IMyAidlInterface.Stub.asInterface(service)`后获取到一个IMyAidlInterface接口实例，通过该接口实例即可跨进程调用Service进程中stub的add和multiple用方法

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(this, MyService.class);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IMyAidlInterface iMyAidlInterface = IMyAidlInterface.Stub.asInterface(service);
                try {
                    iMyAidlInterface.add(1,1);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, Context.BIND_AUTO_CREATE);
    }
}
```

###### 【2】问题思考：客户端在onServiceConnected回调中拿到的IBinder对象是不是Service.onBind方法返回的stub对象？

 答：

- 如果在客户端和Service在同一个进程，那么客户端在onServiceConnected回调中拿到的对象就是Service.onBind返回的stub对象

- 如果在客户端和Service在同一个进程，那么客户端在onServiceConnected回调中拿到的对象就是Service.onBind返回的stub对象对应的BinderProxy对象，BinderProxy对象需要Stub.Proxy类进行装饰后使用，这体现在Stub.asInterface方法当中

  ```java
  public static com.example.aidl.IMyAidlInterface asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof com.example.aidl.IMyAidlInterface))) {
            // 如果客户端和服务端在同一个进程，说明obj就是stub对象
          return ((com.example.aidl.IMyAidlInterface)iin);
        }
        //如果客户端和服务端在不同的进程，说明obj是stub对象对应的Proxy对象，需要用Stub.Proxy装饰后使用
        return new com.example.aidl.IMyAidlInterface.Stub.Proxy(obj);
      }
  ```



