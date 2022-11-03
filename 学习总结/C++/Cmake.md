# Cmake

## 一、cmake相关方法

### 1. add_library

#### 1.1 添加库文件

```cmake
#生成库
add_library(<name> #库名称
			SHARED|STATIC|MODULE #动态库|静态库|模块库
			[EXCLUDE_FROM_ALL] #设置之后会使该库文件不被输出到ARCHIVE_OUTPUT_DIRECTORY，LIBRARY_OUTPUT_DIRECTORY，RUNTIME_OUTPUT_DIRECTORY这三个目录下
			[source1] [souece2...] #文件的路径, .c .cpp文件
			)
			
#示例：将helloA.c和helloB.c编译链接生libhello.so
add_library(hello
			SHARED
			main/src/cpp/helloA.c
			main/src/cpp/helloB.c)
```

- `name`：生成的库文件的名称
- `SHARED|STATIC|MODULE` ：生成的库文件类型，其中动态库|静态库 生成的文件分别为 lib${name}.so | lib${name}.a 
- `[source1] [souece2...]`：指定路径下的文件(.c | .cpp文件)，最后会编译链接成一个名为 lib${name}.so | lib${name}.a 的库文件

- 新生成的库文件会被输出到以下几个目录**ARCHIVE_OUTPUT_DIRECTORY，LIBRARY_OUTPUT_DIRECTORY，RUNTIME_OUTPUT_DIRECTORY**，可以用SET直接修改这些值
- `[EXCLUDE_FROM_ALL]` ：设置之后会使该库文件不被输出到**ARCHIVE_OUTPUT_DIRECTORY，LIBRARY_OUTPUT_DIRECTORY，RUNTIME_OUTPUT_DIRECTORY**这三个目录下

#### 1.2 导入库文件

```cmake
#导入库
add_library(<name> #库名称
			SHARED|STATIC|MODULE #动态库|静态库|模块库
			IMPORTED #用来表示这是一个导入的库
			[GLOBAL] #库文件的作用域
			)
			
#示例，从src/main/libs目录下导入libhello.so并命名为lib-hello
add_library(lib-hello
			SHARED
			IMPORTED)
set_target_porperties(lib-hello
					  PROPERTIES IMPORTED_LOCATION
					  src/main/libs/libhello.so)
```

- `name` 别名
- `SHARED|STATIC|MODULE` ：导入库文件的类型 动态库|静态库|模块库
- `GLOBAL:`导入库的作用域为创建它的目录以及下级目录，如果有GLOBAL，导入库的作用域拓展到全工程
- 需要指定IMPORTED_LOCATION属性来确定导入库文件的路径，通过`set_target_properties`来设置，具体看示例



#### 1.3 别名

```cmake
add_library(<name> ALIAS <target>)
#示例
add_library(hello ALIAS lib-hello)
```

- 设置后可以用name来代替target



### 2. add_executable

```cmake
add_executable(<target1> <target2...> #若干个链接文件)
```

- 该方法会生成一个可执行文件



### 3. find_library

```cmake
#找到系统库，并命名为lib-log，使用的时候需要${lib-log}
find_library(lib-log
			log)
```

- 该方法用于找到系统的库文件



### 4. include_directories

```cmake
include_directories([After|BEFORE] # 还未用到
					[SYSTEM] #还未用到
					[dir1] [dir2...] #若干个头文件搜索路径，不能使用相对路径
					)
					
#示例:指定当前cmake下的所有目标文件的头文件搜索路径
include_directories(/src/main/include)
```

- 给源文件添加头文件搜索路径：将指定目录添加到编译器的头文件搜索目录之下、
- 用该方法添加进来的头文件搜索路径，适用于当前CMakeList.txt下的**所有目标**



### 5. target_include_directories

``` cmake
target_include_directories(<target> #目标
						   [SYSTEM]
						   [BEFORE|AFTER]
						   #若干个头文件搜索路径以及导入方式 
						   <INTERFACE|PUBLIC|PRIVATE> [dir1]
						   <INTERFACE|PUBLIC|PRIVATE> [dir2]
						   ...)
#示例,指定main目标的头文件搜索路径为src/main/include
include_directories(main src/main/include)
```

- 给目标添加文件搜索路径，该方法添加得到头文件搜索路径支队**指定目标**有效

- 链接头文件的方式

  假设有这么一个场景，hello.h中定义了一个sayHello方法

  ```cmake
  #生成libmain.so库，将会与main.h一起提供给用户使用
  add_library(main
  			main.c)
  #指定libmain.so库需要的头文件
  target_include_directories(main 
  						   PRIVATE|PUBLIC|INTERFACE #重点关注这里！！！
  						   hello.h)
  ```

  上面的指令最终会生成一个libmain.so文件，并与main.h一起提供给用户使用。

  而用户只能通过头文件main.h中声明的方法来使用so库。

  - PRIVATE
    - main.h中不包含hello.h，main.c中包含
    - main.h中不包含hello.h，说明用户无法使用hello.h中的方法
    - 说明hello.h是libmain.so库私有，不提供给用户使用
  - INTERFACE
    - main.h中包含hello.h, main.c中不包含
    - main.h中包含hello.h，说明用户能够使用hello.h中定义的方法
    - main.c不包含hello.h，说明libmain.so库的实现不依赖hello.h
  - PUBLIC
    - PUBLIC = PRIVATE + INTERFACE
    - main.h和main.c中都会包含hello.h
    - 说明libmain.so的实现依赖于hello.h，同时hello.h将提供给用户使用



### 6. link_libraries

```
link_libraries()
```



### 7. target_link_libraries

```cmake
target_link_libraries(<target>
					  #若干个库文件以及对应的链接方式，链接方式可以不写，默认使用PUBLIC
					  <PRIVATE|PUBLIC|INTERFACE> <item1> #这里指的是库文件的路径
					  <PRIVATE|PUBLIC|INTERFACE> <item2>
					  ...)
#示例
target_link_libraries(main
					  libhello.so
					  libworld.so)
#或者		
target_link_libraries(main
					  PUBLIC libhello.so
					  PRIVATE libworld.so)
```

- 链接方式

  假设有以下场景，先将B库链接到A库，然后再将C库链接到A库

  ```cmake
  #PUBLIC
  target_link_libraries(A 
   					  PUBLIC B)
  target_link_libraries(C A)
  
  #PRIVAT
  target_link_libraries(A 
   					  PRIVATE B)
  target_link_libraries(C A)
  
  #INTERFACE
  target_link_libraries(A 
   					  INTERFACE B)
  target_link_libraries(C A)
  ```

  - PUBLIC

    B库为共有库，C和A都可以使用B

  - PRIVATE

    B库为A库私有，A可以使用B，但是C不能使用B

  - INTERFACE

    这个就有点奇葩，A不能使用B，但是C可以使用B

### 8. link_directories

```cmake
link_directories([AFTER|BEFORE] #暂时不管
				 [SYSTEM] #暂时不管
				 [dir1] [dir2] ... #若干个搜索路径
)

#示例
link_directories(/src/main/lib
				 /src/main/lib2)
```

- 设置库文件搜索路径(也不一定是库文件，也可以是其它的可链接文件)，作用于当前CMakeList.txt中的所有目标

  

### 9. targe_link_directories

```cmake
# 指定目标的可链接文件(.a .so .o文件)搜索路径，指定完后链接就可以不用全路径去指定可链接文件了
target_link_directories(<target>
				 [AFTER|BEFORE]
				 #若干个路径以及链接方式
				 <PUBLIC|PRIVATE|INTERFACE> [dir1]
				 <PUBLIC|PRIVATE|INTERFACE> [dir2]
				 ...
)

# 示例
# 假设libhello.so位于src/main/lib目录下
# 指定main目标的库文件搜索路径为src/main/lib
target_link_directories(main
						PRIVATE /src/main/lib)
# 链接libhello.so，此时就可以不用/src/main/lib/libhello.so了			
target_link_directories(main
						libhello.so)
```

- 指定一个目标的库文件搜索路径(也不一定是库文件，也可以是其它的可链接文件)

  

### 10. add_subdirectory

```cmake
add_subdirectory(子目录路径
				 输出路径 #可选 
				 )
#示例
add_subdirectory(/src/main/sub)
```

- 指定子目录的文件，子目录下必须也包含CMakeList.txt，并且该CMakeLIst.txt会被执行

  

### 11. project

```cmake
project(项目名)
#示例
project(hello)
```

- 设置project后，会影响到PROJECT_SOURCE_DIR变量
- 比如在/src/main/cpp/hello/CMakeList.txt中设置了project，那么 ${PROJECT_SOURCE_DIR} =  ...前面路径略/src/main/cpp/hello
- 这里需要注意一个细节：如果一个cmake文件A添加了子CMake文件B，并且A中设置了projcet
  - 如果B没设置project, 那么在B中调用 ${PROJECT_SOURCE_DIR} =  A所在目录
  - 如果B也设置project, 那么在B中调用 ${PROJECT_SOURCE_DIR} =  B所在目录



### 12. message

```cmake
#示例
message(打印消息)
```



## 二. 系统变量

知道目前用到的有以下这些变量，以后接触到了再添加

| 变量名                         | 含义                                        |
| ------------------------------ | ------------------------------------------- |
| PROJECT_SOURCE_DIR             | 工程的路径，在上面以及解释过                |
| ANDROID_ABI                    | 当前使用的架构，可能是arm64-v8 x86          |
| CMAKE_LIBRARY_OUTPUT_DIRECTORY | cmake编译生成文件的输出路径                 |
| CMAKE_LIBRARY_ARCHITECTURE     | cmake工程路径，输出的值为 .  ，表示当前路径 |
| CMAKE_SOURCE_DIR               | cmake所在的目录                             |



## 三. Gradle配置相关

```groovy
android{
    ...
            
        defaultConfig{
            externalNativeBuild{
                cmake{
                    // 指定c++ 11，也可以不填采用默认的
                    cppFlags "-std=c++11"
                    // 编译库文件的架构，这里表示只用arm64-v8a架构进行编译，
                    // 如果不过滤，会采用 arm64-v8a ，armeabi-v7a，x86，x86_64进行编译
                    abiFilters "arm64-v8a"
                }
            }
        }
    
    ...
        externalNativeBuild{
            cmake{
                // 指定入口cmake文件
                path file('src/main/cpp/CMakeLists.txt')
                // 指定cmake版本
                version '3.22.1'
            }
        }
    
    ...
        sourceSets{
            main{
                // 指定so库存放路径，默认放在src/main/jniLibs目录下
                // apk打包时会将编译过程中输出到output目录下的新生成的库文件拷贝到该目录下
                // 用户也可以将应用的第三方so库放到该目录
                // 需要注意，如果在cmake中更改了库文件的输出路径，不能输出到该路径下
                jniLibs.srcDirs = ['libs']
            }
        }
    
}
```

