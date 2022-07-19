

# 一.前言

## AMS在Android 10 和之前版本的区别

android 10: 在AMS的基础上加入了AMS的辅助管理类ActivityTaskManagerService,它负责Actiivty的启动，切换以及调度。并且ActivityTaskManagerService也属于SystemService，它也独处一个进程。

Android 9以及更早版本 : ActivityManagerService直接负责四大组件的启动，切换以及调度



# 二.System Server:

## System Server概念:

System Server进程主要负责创建系统服务,比如AMS，WMS，PMS都是由它创建而来的。
在System Server被创建以后，它会做以下几个工作：
1.启动Binder线程池 
2.创建用于管理所各种系统服务生命周期(创建，启动等)的SystemServiceManager  
3.启动了各种系统服务(AMS WMS PMS等) 
4.将系统服务注册到ServiceManager

## System Server启动流程：

首先来看看下图：当我们按下电源键的时候，Boot Rom会从闪存中读取一段程序也就是Boot Loader，Boot Loader启动后会拉起Linux Kernel，随后进行初始化，在初始化的时候，内核会去执行一个叫init.rc的脚本从而fork出init进程。init进程进而创建出Zygote进程。而在Zygoet完成准备工作（Zygote在准备阶段会做创建虚拟机以及运行环境，注册JNI方法，注册套接字等操作）后会马上fork出System Server进程。而其他的App进程也是由Zygote进程fork出来的。

![image-20211102174021152](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211102174021152.png)

## ServiceManager：

### ServiceManager的职责：

ServiceManager在Java层对应ServiceManager类，该类是对native层servirce_manager.c的封装，用于提供Binder通讯服务，并给系统服务(例如AMS,WMS)提供注册操作，注册后的系统服务其实就是一个Binder代理。

### ServiceManager和SystemServiceManager的区别

从启动顺序上看：
从上面的流程图也可以看出,在启动顺序上，ServiceManager在Zygote进程之前就创建好了,
而SystemServiceManager是由SystemServer创建的，在创建顺序上SystemServiceManager要晚于ServiceManager

从功能上看：
ServiceManager : 在Java层对应ServiceManager类，该类是对native层servirce_manager.c的封装，主要用于提供Binder通讯服务。并给系统服务(例如AMS,WMS)提供注册操作，注册后的系统服务其实就是一个Binder代理。SystemServiceManager : 它运行在Java层，主要用于管理各种系统服务的生命周期等

### SystemServer和ServiceManager的关系

SystemServer总的来说是一个功能合集，它通过SystemServiceManager来启动各种系统服务并维护它们的生命周期，SystemServer还会将这些系统注册到ServiceManager中，注册到ServiceManager的系统服务都可以看成是一个Binder服务代理者，所以可以把SystemServer当成是Binder服务代理者的总管理者，把SystemServiceManage看成r是它的管理工具。

# 三.AMS启动流程:

## 1.源码分析:

### 1. 入口方法：

因为AMS属于系统服务，而系统服务是由SystemServer进行管理的，而SystemServer有一个入口函数

```java
/**
 * The main entry point from zygote.
 */
public static void main(String[] args) {
    new SystemServer().run();
}
```

### 2. SystemServer.run

在main方法中，实例化了一个SystemServer对象并调用了它的run()方法。在run方法中我们可以找到这样子的一段代码块

```java
private void run() {
    // .................................... 
    // 无关代码块
    // .................................... 
  
    // Start services.
    try {
        traceBeginAndSlog("StartServices");
        startBootstrapServices();
        startCoreServices();
        startOtherServices();
        SystemServerInitThreadPool.shutdown();
    } catch (Throwable ex) {
        Slog.e("System", "******************************************");
        Slog.e("System", "************ Failure starting system services", ex);
        throw ex;
    } finally {
        traceEnd();
    }
  
    // .................................... 
    // 无关代码块
    // .................................... 
}
```

在run方法的该代码块中，调用了startBootstrapServices() , startCoreService() , startOtherServices()这三个方法启动了80多个服务，下面说说在这三个方法中分别启动了那些服务：

- `startBootstrapServices()`：这里启动了Boot级的系统服务，这些服务着比较强的依赖性，所以写在了一起
- `startCoreService()` ：这里启动了一些比较基本但是不依赖于系统服务的一些服务，比如GPU服务等
- `startOtherServices()`：这里启动了非必须马上启动的服务，比如蓝牙服务，Vr服务等

### 3. SystemServer.startBootstrapServices

AMS是在`startBootstrapServices()`中启动的,来看看startBootstrapServices()中相关代码块

```java
private void startBootstrapServices() {
    // ....................................
    // 无关代码块
    // ....................................
    
    // Activity manager runs the show.
    traceBeginAndSlog("StartActivityManager");
    //启动并获取ActivityTaskManagerService
    ActivityTaskManagerService atm = mSystemServiceManager.startService(
            ActivityTaskManagerService.Lifecycle.class).getService();
    //启动并获取ActivityManagerService对象
    mActivityManagerService = ActivityManagerService.Lifecycle.startService(
            mSystemServiceManager, atm);
    mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
    mActivityManagerService.setInstaller(installer);
    mWindowManagerGlobalLock = atm.getGlobalLock();
    traceEnd();

    // ..........................................
    //  无关代码块
    // ..........................................
   
    // Set up the Application instance for the system process and get started.
    traceBeginAndSlog("SetSystemProcess");
    mActivityManagerService.setSystemProcess();
    traceEnd();
}
```

#### 1）SystemServiceManager.startService分析

先来分析一下`startBootstrapServices()`方法中的这一行代码：
`ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();` 
可以看出通过调用SystemServiceManager中的startService().getService()方法创建了一个ActivityTaskManagerService对象

我们需要先看一看  `<T extends SystemService> T SystemServiceManager.startService(Class<T> serviceClass) `  的实现。

```java
public <T extends SystemService> T startService(Class<T> serviceClass) {
    try {
        final String name = serviceClass.getName();
        Slog.i(TAG, "Starting " + name);
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "StartService " + name);

        // Create the service.
        if (!SystemService.class.isAssignableFrom(serviceClass)) {
            throw new RuntimeException("Failed to create " + name
                    + ": service must extend " + SystemService.class.getName());
        }
        final T service;
        try {
            Constructor<T> constructor = serviceClass.getConstructor(Context.class);
            service = constructor.newInstance(mContext);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Failed to create service " + name
                    + ": service could not be instantiated", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Failed to create service " + name
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException("Failed to create service " + name
                    + ": service must have a public constructor with a Context argument", ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Failed to create service " + name
                    + ": service constructor threw an exception", ex);
        }

        startService(service);
        return service;
    } finally {
        Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    }
}
```

`startService(Class<T> serviceClass)`方法中该两行代码利用反射创建了一个Class<T> 实例化对象对象

- `Constructor<T> constructor = serviceClass.getConstructor(Context.class)`
- `service = constructor.newInstance(mContext)`

在startService方法末尾地方先调用了`startService(final SystemService service)`

```java
public void startService(@NonNull final SystemService service) {
    // Register it.
    mServices.add(service);
    // Start it.
    long time = SystemClock.elapsedRealtime();
    try {
        service.onStart();
    } catch (RuntimeException ex) {
        throw new RuntimeException("Failed to start service " + service.getClass().getName()
                + ": onStart threw an exception", ex);
    }
    warnIfTooLong(SystemClock.elapsedRealtime() - time, service, "onStart");
}
```

该方法将SystemService对象夹入mServices列表中，然后调用了SystemService对象的onStrat方法完成系统服务的启动，至此系统服务就启动了。

我们再回到`startBootstrapServices()`方法中
`ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();` 

从上面`startService(Class<T> serviceClass)`的代码中我们能够得知，在该方法结尾处将一个通过反射实例化出来的对象返回给调用该方法的类，也就返回了ActivityTaskManagerService.Lifecycle.class这个类的实例化对象，通过该类的实例化对象的getService创建出了一个ActivityTaskManagerService对象，那继续分析一下ActivityTaskManagerService.Lifecycle.class的代码

#### 2）ActivityTaskManagerService.Lifecycle.class分析

ActivityTaskManagerService.Lifecycle很明显是ActivityTaskManagerService的一个内部类，该类继承自SystemService(注意不是SystemServer)，它持有一个ActivityTaskManagerService引用，并在构造方法中将其实例化。并开放getService() 方法允许外部访问ActivityTaskManagerService对象，

```java
public static final class Lifecycle extends SystemService {
    private final ActivityTaskManagerService mService;

    public Lifecycle(Context context) {
        super(context);
        mService = new ActivityTaskManagerService(context);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.ACTIVITY_TASK_SERVICE, mService);
        mService.start();
    }

    @Override
    public void onUnlockUser(int userId) {
        synchronized (mService.getGlobalLock()) {
            mService.mStackSupervisor.onUserUnlocked(userId);
        }
    }

    @Override
    public void onCleanupUser(int userId) {
        synchronized (mService.getGlobalLock()) {
            mService.mStackSupervisor.mLaunchParamsPersister.onCleanupUser(userId);
        }
    }

    public ActivityTaskManagerService getService() {
        return mService;
    }
}
```

至此，对`startBootstrapServices()`中的
`ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();` 
这部分代码已经分析完了，通过这行代码，SystemServer启动了一个ActivityTaskManagerService，并且获取到了ActivityTaskManagerService对象

接下来分析这行代码
`mActivityManagerService = ActivityManagerService.Lifecycle.startService(mSystemServiceManager, atm);`
通过上一行代码获取到得ActivityTaskManagerService对象atm将作为参数传入`ActivityManagerService.Lifecycle.startService(mSystemServiceManager, atm)`当中，获取到一个ActivityManagerService对象

我们来看看`ActivityManagerService.Lifecycle.Class`方法以及其中的startService方法

#### 3）ActivityManagerService.Lifecycle.Class分析

```java
public static final class Lifecycle extends SystemService {
    private final ActivityManagerService mService;
    private static ActivityTaskManagerService sAtm;

    public Lifecycle(Context context) {
        super(context);
        mService = new ActivityManagerService(context, sAtm);
    }

    public static ActivityManagerService startService(
            SystemServiceManager ssm, ActivityTaskManagerService atm) {
        sAtm = atm;
        return ssm.startService(ActivityManagerService.Lifecycle.class).getService();
    }

    @Override
    public void onStart() {
        mService.start();
    }

    @Override
    public void onBootPhase(int phase) {
        mService.mBootPhase = phase;
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mService.mBatteryStatsService.systemServicesReady();
            mService.mServices.systemServicesReady();
        } else if (phase == PHASE_ACTIVITY_MANAGER_READY) {
            mService.startBroadcastObservers();
        } else if (phase == PHASE_THIRD_PARTY_APPS_CAN_START) {
            mService.mPackageWatchdog.onPackagesReady();
        }
    }

    @Override
    public void onCleanupUser(int userId) {
        mService.mBatteryStatsService.onCleanupUser(userId);
    }

    public ActivityManagerService getService() {
        return mService;
    }
}
```

这里的写法和`ActivityManagerService.Lifecycle`很类似，`ActivityManagerService.Lifecycle`是`ActivityManagerService`的静态内部类，它持有一个ActivityManagerService和ActivityTaskManagerService的引用。ActivityManagerService的引用在构造方法中初始化，而`ActivityManagerService.Lifecycle.stratService`方法中通过`SystemServiceManager.startService(Class<T>  serviceClass)`（该方法在1)中已经分析过）实例化并启动了一个ActivityManagerService对象。

#### 4） ActivityManagerService.setSystemProcess()分析

在startBootstrapService方法的后面又执行了一下这样的代码

`mActivityManagerService.setSystemProcess()`
看方法名觉得很奇怪，只能来看看源码了

```java
public void setSystemProcess() {
    try {
        // 将AMS自生注册到ServiceManager中
        ServiceManager.addService(Context.ACTIVITY_SERVICE, this, /* allowIsolated= */ true,
                DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PRIORITY_NORMAL | DUMP_FLAG_PROTO);
        // 注册其他服务到ServiceManager中
        ServiceManager.addService(ProcessStats.SERVICE_NAME, mProcessStats);
        ServiceManager.addService("meminfo", new MemBinder(this), /* allowIsolated= */ false,
                DUMP_FLAG_PRIORITY_HIGH);
        ServiceManager.addService("gfxinfo", new GraphicsBinder(this));
        ServiceManager.addService("dbinfo", new DbBinder(this));
        if (MONITOR_CPU_USAGE) {
            ServiceManager.addService("cpuinfo", new CpuBinder(this),
                    /* allowIsolated= */ false, DUMP_FLAG_PRIORITY_CRITICAL);
        }
        // 注册权限
        ServiceManager.addService("permission", new PermissionController(this));
        ServiceManager.addService("processinfo", new ProcessInfoService(this));
        ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(
                "android", STOCK_PM_FLAGS | MATCH_SYSTEM_ONLY);
        mSystemThread.installSystemApplicationInfo(info, getClass().getClassLoader());

        synchronized (this) {
            ProcessRecord app = mProcessList.newProcessRecordLocked(info, info.processName,
                    false,
                    0,
                    new HostingRecord("system"));
            app.setPersistent(true);
            app.pid = MY_PID;
            app.getWindowProcessController().setPid(MY_PID);
            app.maxAdj = ProcessList.SYSTEM_ADJ;
            app.makeActive(mSystemThread.getApplicationThread(), mProcessStats);
            mPidsSelfLocked.put(app);
            mProcessList.updateLruProcessLocked(app, false, null);
            updateOomAdjLocked(OomAdjuster.OOM_ADJ_REASON_NONE);
        }
    } catch (PackageManager.NameNotFoundException e) {
        throw new RuntimeException(
                "Unable to find android system package", e);
    }

    // Start watching app ops after we and the package manager are up and running.
    mAppOpsService.startWatchingMode(AppOpsManager.OP_RUN_IN_BACKGROUND, null,
            new IAppOpsCallback.Stub() {
                @Override public void opChanged(int op, int uid, String packageName) {
                    if (op == AppOpsManager.OP_RUN_IN_BACKGROUND && packageName != null) {
                        if (mAppOpsService.checkOperation(op, uid, packageName)
                                != AppOpsManager.MODE_ALLOWED) {
                            runInBackgroundDisabled(uid);
                        }
                    }
                }
            });
}
```

其实也不难理解，就是将注册自生以及一些服务到SystemService当中，这时候AMS也就成了SystemServer的一个Binder代理对象了

#### 5）小结

整理一下AMS 和 ATMS的启动流程图吧

![AMS ATMS启动流程](/Users/shenyutao/Desktop/AMS ATMS启动流程.jpg)



# 四.ActivityThread

## 1.介绍

网上很多人说ActivityThread是Android的主线程，其实这个说法并不准确，它既可以是Android UI主线程，也可以是SystemServer进程的主线程也就是系统进程主线程。当它作为Android UI主线程的时候，它的main函数就是应用程序进程的入口函数

## 2.源码分析

### 1.ActivityThread的入口方法main

```java
public static void main(String[] args) {
    // ......................
    // 无关代码块
    // ......................

    //准备looper
    Looper.prepareMainLooper();

    // Find the value for {@link #PROC_START_SEQ_IDENT} if provided on the command line.
    // It will be in the format "seq=114" 
    long startSeq = 0;
    if (args != null) {
        for (int i = args.length - 1; i >= 0; --i) {
            if (args[i] != null && args[i].startsWith(PROC_START_SEQ_IDENT)) {
                startSeq = Long.parseLong(
                        args[i].substring(PROC_START_SEQ_IDENT.length()));
            }
        }
    }
    // 新建一个ActivityThread
    ActivityThread thread = new ActivityThread();
    // attach为false表示普通的应用进程，如果为true则是系统应用进程
    thread.attach(false, startSeq);
    
   
    if (sMainThreadHandler == null) {
        sMainThreadHandler = thread.getHandler();
    }


    // ......................
    // 无关代码块
    // ......................

    // 开启loop循环
    Looper.loop();

    throw new RuntimeException("Main thread loop unexpectedly exited");
}
```

### 2.ActivityThread与AMS的通讯(以Application的创建和启动为例)

这里将结合源码分析***Application***的创建和启动流程，并从中总结***ActivityThread***和***AMS***的通讯过程
从main函数中执行了这么一段代码`thread.attach(false, startSeq)`，我们来看看这段代码

#### 1）ActivityThread.Attch分析

```java
@UnsupportedAppUsage
private void attach(boolean system, long startSeq) {
    sCurrentActivityThread = this;
    mSystemThread = system;
    if (!system) {
        android.ddm.DdmHandleAppName.setAppName("<pre-initialized>",
                                                UserHandle.myUserId());
        RuntimeInit.setApplicationObject(mAppThread.asBinder());
        final IActivityManager mgr = ActivityManager.getService();
        try {
            mgr.attachApplication(mAppThread, startSeq);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
        // Watch for getting close to heap limit.
        BinderInternal.addGcWatcher(new Runnable() {
            @Override public void run() {
                if (!mSomeActivitiesChanged) {
                    return;
                }
                Runtime runtime = Runtime.getRuntime();
                long dalvikMax = runtime.maxMemory();
                long dalvikUsed = runtime.totalMemory() - runtime.freeMemory();
                if (dalvikUsed > ((3*dalvikMax)/4)) {
                    if (DEBUG_MEMORY_TRIM) Slog.d(TAG, "Dalvik max=" + (dalvikMax/1024)
                            + " total=" + (runtime.totalMemory()/1024)
                            + " used=" + (dalvikUsed/1024));
                    mSomeActivitiesChanged = false;
                    try {
                        ActivityTaskManager.getService().releaseSomeActivities(mAppThread);
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                }
            }
        });
    } else {
        // Don't set application object here -- if the system crashes,
        // we can't display an alert, we just want to die die die.
        android.ddm.DdmHandleAppName.setAppName("system_process",
                UserHandle.myUserId());
        try {
            mInstrumentation = new Instrumentation();
            mInstrumentation.basicInit(this);
            ContextImpl context = ContextImpl.createAppContext(
                    this, getSystemContext().mPackageInfo);
            mInitialApplication = context.mPackageInfo.makeApplication(true, null);
            mInitialApplication.onCreate();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to instantiate Application():" + e.toString(), e);
        }
    }

    ViewRootImpl.ConfigChangedCallback configChangedCallback
            = (Configuration globalConfig) -> {
        synchronized (mResourcesManager) {
            // We need to apply this change to the resources immediately, because upon returning
            // the view hierarchy will be informed about it.
            if (mResourcesManager.applyConfigurationToResourcesLocked(globalConfig,
                    null /* compat */)) {
                updateLocaleListFromAppContext(mInitialApplication.getApplicationContext(),
                        mResourcesManager.getConfiguration().getLocales());

                // This actually changed the resources! Tell everyone about it.
                if (mPendingConfiguration == null
                        || mPendingConfiguration.isOtherSeqNewer(globalConfig)) {
                    mPendingConfiguration = globalConfig;
                    sendMessage(H.CONFIGURATION_CHANGED, globalConfig);
                }
            }
        }
    };
    ViewRootImpl.addConfigCallback(configChangedCallback);
}
```

在这里我们主要注意第一个参数 `boolean system`如果该参数的值为true，那么将会创建一个系统进程主线程，若该参数为false，那么它将创建一个普通应用进程主线程。

我们主要来看一下这段代码

```java
final IActivityManager mgr = ActivityManager.getService();
try {
    mgr.attachApplication(mAppThread, startSeq);
} catch (RemoteException ex) {
    throw ex.rethrowFromSystemServer();
}
```

这段代码通过AIDL技术远程调用了 ***ActivityManagerService*** 的`attchApplication`方法，并将mAppThread当做代理对象传递给AMS，而mAppThread是一个 ***ApplicationThread*** 对象，它作为代理完成***AMS***和**ActivityThread**的通讯，***AMS***通过***ApplicationThread***代理对象来完成对***ActivityThread***的调度

```java
final ApplicationThread mAppThread = new ApplicationThread();
```

那么我们来看看 ***ActivityManagerService*** 的`attchApplication()`方法是怎么实现

#### 2）ActivityManagerService.attachApplication分析

```java
@Override
public final void attachApplication(IApplicationThread thread, long startSeq) {
    if (thread == null) {
        throw new SecurityException("Invalid application interface");
    }
    synchronized (this) {
        int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();
        final long origId = Binder.clearCallingIdentity();
        attachApplicationLocked(thread, callingPid, callingUid, startSeq);
        Binder.restoreCallingIdentity(origId);
    }
}
```

该方法的核心是调用了`attachApplicationLocked(thread, callingPid, callingUid, startSeq);`我们再来看看该方法的实现，这里就把核心部分粘上

#### 3）ActivityManagerService.attachApplicationLocked分析

```java
private boolean attachApplicationLocked(@NonNull IApplicationThread thread, int pid, 
                                        int callingUid, long startSeq) {
  
  // ......................................
  //  无关代码
  // ......................................

            if (app.isolatedEntryPoint != null) {
                // This is an isolated process which should just call an entry point instead of
                // being bound to an application.
                thread.runIsolatedEntryPoint(app.isolatedEntryPoint,app.isolatedEntryPointArgs);
            } else if (instr2 != null) {
                thread.bindApplication(processName, appInfo, providers,
                        instr2.mClass,
                        profilerInfo, instr2.mArguments,
                        instr2.mWatcher,
                        instr2.mUiAutomationConnection, testMode,
                        mBinderTransactionTrackingEnabled, enableTrackAllocation,
                        isRestrictedBackupMode || !normalMode, app.isPersistent(),
                        new Configuration(app.getWindowProcessController().getConfiguration()),
                        app.compat, getCommonServicesLocked(app.isolated),
                        mCoreSettingsObserver.getCoreSettingsLocked(),
                        buildSerial, autofillOptions, contentCaptureOptions);
            } else {
                thread.bindApplication(processName, appInfo, providers, null, profilerInfo,
                        null, null, null, testMode,
                        mBinderTransactionTrackingEnabled, enableTrackAllocation,
                        isRestrictedBackupMode || !normalMode, app.isPersistent(),
                        new Configuration(app.getWindowProcessController().getConfiguration()),
                        app.compat, getCommonServicesLocked(app.isolated),
                        mCoreSettingsObserver.getCoreSettingsLocked(),
                        buildSerial, autofillOptions, contentCaptureOptions);
            }
  // ......................................
  //  无关代码
  // ......................................
            
}
```

该方法中主要调用了`thread.bindApplication`方法，而thread其实就是前面作为参数传入的mAppThread，也就是 ***ApplicationThread*** 对象(这里 ***AMS*** 也利用了AIDL远程调用了 ***ActivityThread.ApplicaitionThread*** 中的`bindApplication()`方法)我们来看看`ApplicationThread.bindApplication()`中的实现

#### 4）ActivityThread.ApplicationThread.bindApplication分析

```java
public final void bindApplication(String processName, ApplicationInfo appInfo,
        List<ProviderInfo> providers, ComponentName instrumentationName,
        ProfilerInfo profilerInfo, Bundle instrumentationArgs,
        IInstrumentationWatcher instrumentationWatcher,
        IUiAutomationConnection instrumentationUiConnection, int debugMode,
        boolean enableBinderTracking, boolean trackAllocation,
        boolean isRestrictedBackupMode, boolean persistent, Configuration config,
        CompatibilityInfo compatInfo, Map services, Bundle coreSettings,
        String buildSerial, AutofillOptions autofillOptions,
        ContentCaptureOptions contentCaptureOptions) {
    if (services != null) {
        if (false) {
            // Test code to make sure the app could see the passed-in services.
            for (Object oname : services.keySet()) {
                if (services.get(oname) == null) {
                    continue; // AM just passed in a null service.
                }
                String name = (String) oname;

                // See b/79378449 about the following exemption.
                switch (name) {
                    case "package":
                    case Context.WINDOW_SERVICE:
                        continue;
                }

                if (ServiceManager.getService(name) == null) {
                    Log.wtf(TAG, "Service " + name + " should be accessible by this app");
                }
            }
        }

        // Setup the service cache in the ServiceManager
        ServiceManager.initServiceCache(services);
    }

    setCoreSettings(coreSettings);

    AppBindData data = new AppBindData();
    data.processName = processName;
    data.appInfo = appInfo;
    data.providers = providers;
    data.instrumentationName = instrumentationName;
    data.instrumentationArgs = instrumentationArgs;
    data.instrumentationWatcher = instrumentationWatcher;
    data.instrumentationUiAutomationConnection = instrumentationUiConnection;
    data.debugMode = debugMode;
    data.enableBinderTracking = enableBinderTracking;
    data.trackAllocation = trackAllocation;
    data.restrictedBackupMode = isRestrictedBackupMode;
    data.persistent = persistent;
    data.config = config;
    data.compatInfo = compatInfo;
    data.initProfilerInfo = profilerInfo;
    data.buildSerial = buildSerial;
    data.autofillOptions = autofillOptions;
    data.contentCaptureOptions = contentCaptureOptions;
    sendMessage(H.BIND_APPLICATION, data);
}
```

在该方法中，首先创建了一个 ***AppBindData*** 对象data并对其进行赋值，赋值完后通过`sendMessage(H.BIND_APPLICATION, data)`发送出去，因为 ***ApplicationThread*** 是 ***ActivityThread*** 的非静态内部类，所以可以调用 ***ActivityThread*** 的方法,而 `sendMessage()`就是***ActivityThread*** 的方法，我们来看看实现

#### 5）ActivityThread.sendMessage分析

```java
void sendMessage(int what, Object obj) {
    sendMessage(what, obj, 0, 0, false);
}
```

这里调用了一个多个重载方法，最终进入到下面的方法中

```java
private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
    if (DEBUG_MESSAGES) {
        Slog.v(TAG,
                "SCHEDULE " + what + " " + mH.codeToString(what) + ": " + arg1 + " / " + obj);
    }
    Message msg = Message.obtain();
    msg.what = what;
    msg.obj = obj;
    msg.arg1 = arg1;
    msg.arg2 = arg2;
    if (async) {
        msg.setAsynchronous(true);
    }
    mH.sendMessage(msg);
}
```

这里最后的 ***mH ***实际上一个 ***ActivityThread.H*** 类，***H*** 继承自 ***Handler***

```
class H extends Handler{...}
final H mH = new H();
```

既然是一个 ***Handler***，又发送了消息，那就得看看`handleMessage()`方法中是怎么处理消息的。（在`bindApplication()`的最后一行(上面展示过),调用了`sendMessage(H.BIND_APPLICATION, data)`,所以我们在该方法中看看case到H.BIND_APPLICATION消息是怎么处理的。）



#### 6）ActivityThread.H.handleMessage分析

```java
public void handleMessage(Message msg) {
    if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
    switch (msg.what) {
        case BIND_APPLICATION:
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "bindApplication");
            AppBindData data = (AppBindData)msg.obj;
            handleBindApplication(data);
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            break;
        
        // ............
        // 无关代码
        // ............
    }
    Object obj = msg.obj;
    if (obj instanceof SomeArgs) {
        ((SomeArgs) obj).recycle();
    }
    if (DEBUG_MESSAGES) Slog.v(TAG, "<<< done: " + codeToString(msg.what));
}


```

在处理BIND_APPLICATION类型的消息当中，主要调用了`handleBindApplication(data)`的方法,我们来看看实现

#### 7）ActivityThread.handleBindApplication分析

在该方法中创建除了Application实例，并且用`mInstrumentation.callApplicationOnCreate(app)`触发Application的onCreate，至此Application创建并启动

```java
@UnsupportedAppUsage
private void handleBindApplication(AppBindData data) {
    // ..............................
    // 无关代码
    // ..............................

    // Allow disk access during application and provider setup. This could
    // block processing ordered broadcasts, but later processing would
    // probably end up doing the same disk access.
    Application app;
    final StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
    final StrictMode.ThreadPolicy writesAllowedPolicy = StrictMode.getThreadPolicy();
    try {
        // If the app is being launched for full backup or restore, bring it up in
        // a restricted environment with the base application class.
        app = data.info.makeApplication(data.restrictedBackupMode, null);

        // Propagate autofill compat state
        app.setAutofillOptions(data.autofillOptions);

        // Propagate Content Capture options
        app.setContentCaptureOptions(data.contentCaptureOptions);

        mInitialApplication = app;

        // don't bring up providers in restricted mode; they may depend on the
        // app's custom Application class
        if (!data.restrictedBackupMode) {
            if (!ArrayUtils.isEmpty(data.providers)) {
                installContentProviders(app, data.providers);
            }
        }

        // Do this after providers, since instrumentation tests generally start their
        // test thread at this point, and we don't want that racing.
        try {
            mInstrumentation.onCreate(data.instrumentationArgs);
        }
        catch (Exception e) {
            throw new RuntimeException(
                "Exception thrown in onCreate() of "
                + data.instrumentationName + ": " + e.toString(), e);
        }
        try {
            mInstrumentation.callApplicationOnCreate(app);
        } catch (Exception e) {
            if (!mInstrumentation.onException(app, e)) {
                throw new RuntimeException(
                  "Unable to create application " + app.getClass().getName()
                  + ": " + e.toString(), e);
            }
        }
    } finally {
        // If the app targets < O-MR1, or doesn't change the thread policy
        // during startup, clobber the policy to maintain behavior of b/36951662
        if (data.appInfo.targetSdkVersion < Build.VERSION_CODES.O_MR1
                || StrictMode.getThreadPolicy().equals(writesAllowedPolicy)) {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }
  
    // ..............................
    // 无关代码
    // ..............................
    
}
```

而***Application***的实例是这样创建的
`app = data.info.makeApplication(data.restrictedBackupMode, null)`
我们可以看看`data.info.makeApplication()`方法

#### 8）LoadedApk.makeApplication分析

```java
public Application makeApplication(boolean forceDefaultAppClass,
        Instrumentation instrumentation) {
    if (mApplication != null) {
        return mApplication;
    }

    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "makeApplication");

    Application app = null;

    String appClass = mApplicationInfo.className;
    if (forceDefaultAppClass || (appClass == null)) {
        appClass = "android.app.Application";
    }

    try {
        java.lang.ClassLoader cl = getClassLoader();
        if (!mPackageName.equals("android")) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "initializeJavaContextClassLoader");
            initializeJavaContextClassLoader();
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
        ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
        // 这里利用Instrumentation创建了一个Application实例
        app = mActivityThread.mInstrumentation.newApplication(
                cl, appClass, appContext);
        appContext.setOuterContext(app);
    } catch (Exception e) {
        if (!mActivityThread.mInstrumentation.onException(app, e)) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            throw new RuntimeException(
                "Unable to instantiate application " + appClass
                + ": " + e.toString(), e);
        }
    }
    mActivityThread.mAllApplications.add(app);
    mApplication = app;

    //执行handleBindApplication的时候，参数instrumentation为null，所以这里不会执行。
    if (instrumentation != null) {
        try {
            instrumentation.callApplicationOnCreate(app);
        } catch (Exception e) {
            if (!instrumentation.onException(app, e)) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                throw new RuntimeException(
                    "Unable to create application " + app.getClass().getName()
                    + ": " + e.toString(), e);
            }
        }
    }

    // Rewrite the R 'constants' for all library apks.
    SparseArray<String> packageIdentifiers = getAssets().getAssignedPackageIdentifiers();
    final int N = packageIdentifiers.size();
    for (int i = 0; i < N; i++) {
        final int id = packageIdentifiers.keyAt(i);
        if (id == 0x01 || id == 0x7f) {
            continue;
        }

        rewriteRValues(getClassLoader(), packageIdentifiers.valueAt(i), id);
    }

    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

    return app;
}
```

进而能推断出`app = mActivityThread.mInstrumentation.newApplication(cl, appClass, appContext)`，这里注意`handleBindApplication()`执行该方法的时候传入的参数Instrumentation为null，所以该方法中的这块代码不会执行，也就是***Application***不会在这里启动

```
if (instrumentation != null) {
        try {
            instrumentation.callApplicationOnCreate(app);
        } catch (Exception e) {
            if (!instrumentation.onException(app, e)) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                throw new RuntimeException(
                    "Unable to create application " + app.getClass().getName()
                    + ": " + e.toString(), e);
            }
        }
    }
```

#### 9）Instrumentation.newApplication分析

该方法通过反射创建了一个`Application`实例，`Application`就是这么创建的，我们也能注意到一点那就是Application的创建和启动其实都是交给***Instrumentation***这个类来处理的，稍后我们再分析一下这个类

```java
static public Application newApplication(Class<?> clazz, Context context)
        throws InstantiationException, IllegalAccessException,
        ClassNotFoundException {
    Application app = (Application) clazz.newInstance();
    app.attach(context);
    return app;
}
```



#### 10）小结

通过上述以上的流程分析，不难得出ActivityThread和ActivityServiceManager的通讯方式

- ***ActivityThread*** 利用 ***IActivityManager*** 代理通过AIDL远程调用 ***AMS*** 实例中的同名API
- ***AMS*** 则利用 ***IApplicationThread*** 代理通过AIDL远程调佣 ***ApplicationThread*** 中的API，***ApplicationThread* ** 再通过 ***Handler-Message*** 机制去控制  ***ActivityThread*** 实例

![image-20211104172723295](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211104172723295.png)

所以我们可以总结一下Application的启动流程

1. ***ActivityThread ***先利用 ***IActivityManager*** 代理通过AIDL远程访问 ***AMS*** 的 `attachApplication()`方法
2. ***AMS*** 的`attachApplication()`方法中又调用了自身的`attachApplicationLocked()`方法
3. 在`attachApplicationLocked()`方法中，***AMS*** 利用***IApplicationThread***代理通过AIDL远程调用 ***ActivityThread.ApplicationThread*** 的`bindApplication()`方法
4. `bindApplication()`方法中，调用了`ActivityThread.sendMessage()`方法向  **ActivityThread.H *(H就是一个Handler)*** 发送了一个类型为 ***BIND_APPLICATION*** 的消息
5. ***Activity.H***接受到Message后，在`handleMessage()`方法中判断消息类型为 ***BIND_APPLICAITON*** 后调用了`ActivityThread.handleBindApplication`方法，让 ***ActivityThread*** 创建并启动 ***Applicaiton***

# 五.Instrumentation

## 1.简介

Instrumentation是用于管理Activity，Application的生命周期的回调的辅助工具类

## 2.Instrumentation的创建

***Instrumentation***的创建流程和***Application***的创建流程是一样的，都是在***AMS***利用***IApplicationThread***代理远程调用***ActivityThread.ApplicationThread***后，向***Handler***发送一条***BIND_APPLICATION***进而调用***ActivityThread***的`handlerBindApplication()`后创建的，我们来看看`handleBindApplication()`方法中创建Instrumentation的部分

```java
private void handleBindApplication(AppBindData data) {
    // ............................
		// 无关代码
		// ............................
    
    // Instrumentation info affects the class loader, so load it before
    // setting up the app context.
    final InstrumentationInfo ii;
    if (data.instrumentationName != null) {
        try {
            ii = new ApplicationPackageManager(null, getPackageManager())
                    .getInstrumentationInfo(data.instrumentationName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(
                    "Unable to find instrumentation info for: " + data.instrumentationName);
        }

        // Warn of potential ABI mismatches.
        if (!Objects.equals(data.appInfo.primaryCpuAbi, ii.primaryCpuAbi)
                || !Objects.equals(data.appInfo.secondaryCpuAbi, ii.secondaryCpuAbi)) {
            Slog.w(TAG, "Package uses different ABI(s) than its instrumentation: "
                    + "package[" + data.appInfo.packageName + "]: "
                    + data.appInfo.primaryCpuAbi + ", " + data.appInfo.secondaryCpuAbi
                    + " instrumentation[" + ii.packageName + "]: "
                    + ii.primaryCpuAbi + ", " + ii.secondaryCpuAbi);
        }

        mInstrumentationPackageName = ii.packageName;
        mInstrumentationAppDir = ii.sourceDir;
        mInstrumentationSplitAppDirs = ii.splitSourceDirs;
        mInstrumentationLibDir = getInstrumentationLibrary(data.appInfo, ii);
        mInstrumentedAppDir = data.info.getAppDir();
        mInstrumentedSplitAppDirs = data.info.getSplitAppDirs();
        mInstrumentedLibDir = data.info.getLibDir();
    } else {
        ii = null;
    }


    // Continue loading instrumentation.
    if (ii != null) {
        // ............................
				// 无关代码
				// ............................

        try {
            final ClassLoader cl = instrContext.getClassLoader();
            mInstrumentation = (Instrumentation)
                cl.loadClass(data.instrumentationName.getClassName()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "Unable to instantiate instrumentation "
                + data.instrumentationName + ": " + e.toString(), e);
        }

       	// ............................
				// 无关代码
				// ............................
        }
    } else {
        mInstrumentation = new Instrumentation();
        mInstrumentation.basicInit(this);
    }

   	// ............................
		// 无关代码
		// ............................
}
```

可以看到如果`ii == null`，也就是`data.InstrumentationName ==null`的时候，***mInstrumentation***直接指向一个新创建的***Instrumentation***对象。
若`ii != null` ,也就是`data.InstrumentationNam != null` 的时候，***mInstrumentation***指向通过***classloader***加载的名为`data.InstrumentationName.getClassName()`的类的实例化对象

## 3.几个主要方法

### 1）Activity生命周期回调相关

#### 【1】callActivityOnCreate

```java
public void callActivityOnCreate(Activity activity, Bundle icicle) {
    prePerformCreate(activity);
    activity.performCreate(icicle);
    postPerformCreate(activity);
}
```

最终触发Activity的onCreate方法

#### 【2】callActivityOnStart

```java
public void callActivityOnStart(Activity activity) {
    activity.onStart();
}
```

直接触发Activity的onStart回调

#### 【3】callActivityOnResume

```java
public void callActivityOnResume(Activity activity) {
    activity.mResumed = true;
    activity.onResume();

    if (mActivityMonitors != null) {
        synchronized (mSync) {
            final int N = mActivityMonitors.size();
            for (int i = 0; i < N; i++) {
                final ActivityMonitor am = mActivityMonitors.get(i);
                am.match(activity, activity, activity.getIntent());
            }
        }
    }
}
```

直接触发Acticity的onResume回调

#### 【4】callActiityOnPause

```java
public void callActivityOnPause(Activity activity) {
    activity.performPause();
}
```

通过activity.performPause触发activity的onPause回调

#### 【5】callActivityOnRestart

```java
public void callActivityOnRestart(Activity activity) {
    activity.onRestart();
}
```

直接触发Activity的onRestrat回调

#### 【6】callActivityOnStop

```java
public void callActivityOnStop(Activity activity) {
    activity.onStop();
}
```

直接触发Activity的onStop回调

#### 【7】callActivityOnDestroy

```java
 public void callActivityOnDestroy(Activity activity) {
        activity.performDestroy();
 }
```

通过activity的performDestroy方法触发onDestroy回调

### 2）Application生命周期回调相关

#### 【1】callApplicationOnCreate

```java
public void callApplicationOnCreate(Application app) {
    app.onCreate();
}
```

触发Application的onCreate回调

# 六.Activitytask(ActivityT栈)

## 1.核心类

### 1.ActivityStack类

Activity栈的管理者，一个App进程只会持有一个***ActivityStack***，
***ActivityStaack***中会持有一个***TaskRecord***列表，而每个***TaskRecord***对应一个Activity栈 （一个App进程可能会持有多个Activity栈）

![](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211105183711281.png)

### 2.TaskRecord类

TaskRecord就是Activity栈，它持有一个ActivityRecord，每个ActivityRecord就是一个Activity的镜像，对应一个Actiivity

![image-20211105183902032](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211105183902032.png)

### 3.ActivityRecord

activity的镜像，用来存储Activity的信息，如所在的进程名称，应用的包名，所在的任务栈的taskAffinity等

![image-20211105184331490](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211105184331490.png)

### 4.ActivityTaskSupervisor

Activity的核心调度类，Activity的核心调度工作都是在这里处理，主要管理Task和Stack，它是在AMS启动时创建的


# 七.Activity

## 1.启动源码分析

### 1）Activity.stratActivity分析

从startActivity(intent)函数开始，我们看看它的源码

```java
public void startActivity(Intent intent) {
    this.startActivity(intent, null);
}
```

该函数又调用了命名同为startActivity的重载方法

```java
public void startActivity(Intent intent, @Nullable Bundle options) {
    if (options != null) {
        startActivityForResult(intent, -1, options);
    } else {
        // Note we want to go through this call for compatibility with
        // applications that may have overridden the method.
        startActivityForResult(intent, -1);
    }
}
```

在该重载方法中，由于传入的options == null ，所以会进入else分支中，调用startActivityForResult这个方法

### 2）Activity.stratActivityForResult分析

```java
public void startActivityForResult(@RequiresPermission Intent intent, int requestCode) {
    startActivityForResult(intent, requestCode, null);
}
```

该方法也调用了一个命名同为startActivityForResult的重载方法

```java
public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
                                   @Nullable Bundle options) {
    if (mParent == null) {
        options = transferSpringboardActivityOptions(options);
        // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
        Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                        this, mMainThread.getApplicationThread(), mToken, this,
                        intent, requestCode, options);
      	// 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
        if (ar != null) {
            mMainThread.sendActivityResult(
                    mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                    ar.getResultData());
        }
        if (requestCode >= 0) {
            mStartedActivity = true;
        }

        cancelInputsAndStartExitTransition(options);
    } else {
        if (options != null) {
            mParent.startActivityFromChild(this, intent, requestCode, options);
        } else {
            mParent.startActivityFromChild(this, intent, requestCode);
        }
    }
}
```

至此可以的出结论,执行了`startActivity(Intent intent)`这个函数，最终会走到 s`tartActivityForResult( Intent intent, int requestCode,Bundle options)`这个函数当中，顺便注意一下所有的`startActivity()`和其他`startActivityForResult同名重载方法`最总都会走到这个方法当中，并且当requestCode < 0 的时候，不会触发OnActivityResult回调

```java
public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
                                   @Nullable Bundle options) {
    if (mParent == null) {
        options = transferSpringboardActivityOptions(options);
      
        // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
        Instrumentation.ActivityResult ar =
                mInstrumentation.execStartActivity(
                        this, mMainThread.getApplicationThread(), mToken, this,
                        intent, requestCode, options);
        // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
      
        if (ar != null) {
            mMainThread.sendActivityResult(
                    mToken, mEmbeddedID, requestCode, ar.getResultCode(),
                    ar.getResultData());
        }
      
        //requestCode < 0 的时候，不会触发OnActivityResult回调
        if (requestCode >= 0) {
            mStartedActivity = true;
        }

        cancelInputsAndStartExitTransition(options);
    } else {
        if (options != null) {
            mParent.startActivityFromChild(this, intent, requestCode, options);
        } else {
            mParent.startActivityFromChild(this, intent, requestCode);
        }
    }
}
```

那么我们接着看看核心代码
`Instrumentation.ActivityResult ar = mInstrumentation.execStartActivity(this, mMainThread.getApplicationThread(), mToken, this,ntent, requestCode, options)`
这里调用了***Instrumentation***的`execStartActivity()`方法

- 注意其中的参数有一个mMainThread.getApplicationThread()，这是一个***IApplicationThread***类在【ActivityThread源码解析】当中，我们知道***IApplicationThread***是***ActivityThread.Application***的远程代理类，通过***IApplicationThread***远程调用***ActivityThread.ApplicationThread***中的同名方法，***ApplicationThread***再通过***Handler***操作ActivityThread
- 还有一个参数是mToken，它是保存***Activity***信息的***ActivtyRecord***类，mToken就代表一个***Activity***，通过参数传远程传递给***ATMS***以后，***ATMS***就能获取到***Activity***的信息了

那么我们再来看看Instrumentation.execStartActivity的源码

### 3）Instrumentation.execStartActivity分析

```java
public ActivityResult execStartActivity(
        Context who, IBinder contextThread, IBinder token, Activity target,
        Intent intent, int requestCode, Bundle options) {

  	// ..........无关代码
   
    try {
        intent.migrateExtraStreamToClipData();
        intent.prepareToLeaveProcess(who);
      
        // 核心代码1
        int result = ActivityTaskManager.getService()
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
      
        // 核心代码2 
      	checkStartActivityResult(result, intent);

    } catch (RemoteException e) {
        throw new RuntimeException("Failure from system", e);
    }
    return null;
}
```

先来看看核心代码2的部分也就是`checkStartActivityResult(result, intent);`

### 4）Instrumentation.checkStartActivityResult分析

```java
public static void checkStartActivityResult(int res, Object intent) {
    if (!ActivityManager.isStartResultFatalError(res)) {
        return;
    }

    switch (res) {
        case ActivityManager.START_INTENT_NOT_RESOLVED:
        case ActivityManager.START_CLASS_NOT_FOUND:
            if (intent instanceof Intent && ((Intent) intent).getComponent() != null)
                throw new ActivityNotFoundException(
                        "Unable to find explicit activity class "
                                + ((Intent) intent).getComponent().toShortString()
                                + "; have you declared this activity in your AndroidManifest.xml?");
            throw new ActivityNotFoundException(
                    "No Activity found to handle " + intent);
        case ActivityManager.START_PERMISSION_DENIED:
            throw new SecurityException("Not allowed to start activity "
                    + intent);
        case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
            throw new AndroidRuntimeException(
                    "FORWARD_RESULT_FLAG used while also requesting a result");
        case ActivityManager.START_NOT_ACTIVITY:
            throw new IllegalArgumentException(
                    "PendingIntent is not an activity");
        case ActivityManager.START_NOT_VOICE_COMPATIBLE:
            throw new SecurityException(
                    "Starting under voice control not allowed for: " + intent);
        case ActivityManager.START_VOICE_NOT_ACTIVE_SESSION:
            throw new IllegalStateException(
                    "Session calling startVoiceActivity does not match active session");
        case ActivityManager.START_VOICE_HIDDEN_SESSION:
            throw new IllegalStateException(
                    "Cannot start voice activity on a hidden session");
        case ActivityManager.START_ASSISTANT_NOT_ACTIVE_SESSION:
            throw new IllegalStateException(
                    "Session calling startAssistantActivity does not match active session");
        case ActivityManager.START_ASSISTANT_HIDDEN_SESSION:
            throw new IllegalStateException(
                    "Cannot start assistant activity on a hidden session");
        case ActivityManager.START_CANCELED:
            throw new AndroidRuntimeException("Activity could not be started for "
                    + intent);
        default:
            throw new AndroidRuntimeException("Unknown error code "
                    + res + " when starting " + intent);
    }
}
```

该方法用于检查Activity的启动结果，如果出现致命错误就会抛出相应的异常，比如我们熟悉的没有在清单文件中注册activcity时抛出的ActivityNotFoundException

我们再来看看核心代码1,这里ActivityTaskManager.getService()实际上返回了一个实现了IActivityTaskManager接口的类，该类也是一个远程代理类，通过该类可以远程调用ActivityTaskMangerService实例的同名方法(AIDL)。那么我们直接定位到`ActivityTaskManagerService.startActivity()`方法中

### 5）ActivityTaskManagerService.stratActivity分析

```java
@Override
public final int startActivity(IApplicationThread caller, String callingPackage,
        Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
        int startFlags, ProfilerInfo profilerInfo, Bundle bOptions) {
    return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
            resultWho, requestCode, startFlags, profilerInfo, bOptions,
            UserHandle.getCallingUserId());
}
```

然后我们可以发现它又调用了类中的`startActivityAsUser()`方法

### 6）ActivityTaskManagerService.startActivityAsUser分析

```java
@Override
public int startActivityAsUser(IApplicationThread caller, String callingPackage,
        Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
        int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId) {
    return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
            resultWho, requestCode, startFlags, profilerInfo, bOptions, userId,
            true /*validateIncomingUser*/);
}
```

 然后它又调用了一个重载方法

```java
int startActivityAsUser(IApplicationThread caller, String callingPackage,
        Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
        int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId,
        boolean validateIncomingUser) {
    enforceNotIsolatedCaller("startActivityAsUser");

    userId = getActivityStartController().checkTargetUser(userId, validateIncomingUser,
            Binder.getCallingPid(), Binder.getCallingUid(), "startActivityAsUser");

   
    return getActivityStartController().obtainStarter(intent, "startActivityAsUser")
            .setCaller(caller)
            .setCallingPackage(callingPackage)
            .setResolvedType(resolvedType)
            .setResultTo(resultTo)
            .setResultWho(resultWho)
            .setRequestCode(requestCode)
            .setStartFlags(startFlags)
            .setProfilerInfo(profilerInfo)
            .setActivityOptions(bOptions)
            .setMayWait(userId)
            .execute();  // ←←←←←←←←←←←←←←←←←←← 下一个方法的入口
}
```

这里先看看 `getActivityStartController().obtainStarter(intent, "startActivityAsUser")`

```java
ActivityStarter obtainStarter(Intent intent, String reason) {
    return mFactory.obtain().setIntent(intent).setReason(reason);
}
```

通过该类的工厂返回了一个ActivityStarter对象，再通过链式调用进行一些属性的设置，最后调用excute()
这里需要注意一下`.setMayWait(userId)`

```
ActivityStarter setMayWait(int userId) {
    mRequest.mayWait = true;
    mRequest.userId = userId;
    return this;
}
```

在`setMayWait`里，为***mRequest.mayWait***附上true值
我们再来看看excute里面的代码

### 7）ActivityStarter.excute分析

```java
int execute() {
    try {
        // TODO(b/64750076): Look into passing request directly to these methods to allow
        // for transactional diffs and preprocessing.
        if (mRequest.mayWait) {
            return startActivityMayWait(mRequest.caller, mRequest.callingUid,
                    mRequest.callingPackage, mRequest.realCallingPid, mRequest.realCallingUid,
                    mRequest.intent, mRequest.resolvedType,
                    mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                    mRequest.resultWho, mRequest.requestCode, mRequest.startFlags,
                    mRequest.profilerInfo, mRequest.waitResult, mRequest.globalConfig,
                    mRequest.activityOptions, mRequest.ignoreTargetSecurity, mRequest.userId,
                    mRequest.inTask, mRequest.reason,
                    mRequest.allowPendingRemoteAnimationRegistryLookup,
                    mRequest.originatingPendingIntent, mRequest.allowBackgroundActivityStart);
        } else {
            return startActivity(mRequest.caller, mRequest.intent, mRequest.ephemeralIntent,
                    mRequest.resolvedType, mRequest.activityInfo, mRequest.resolveInfo,
                    mRequest.voiceSession, mRequest.voiceInteractor, mRequest.resultTo,
                    mRequest.resultWho, mRequest.requestCode, mRequest.callingPid,
                    mRequest.callingUid, mRequest.callingPackage, mRequest.realCallingPid,
                    mRequest.realCallingUid, mRequest.startFlags, mRequest.activityOptions,
                    mRequest.ignoreTargetSecurity, mRequest.componentSpecified,
                    mRequest.outActivity, mRequest.inTask, mRequest.reason,
                    mRequest.allowPendingRemoteAnimationRegistryLookup,
                    mRequest.originatingPendingIntent, mRequest.allowBackgroundActivityStart);
        }
    } finally {
        onExecutionComplete();
    }
}
```

这里是判断***mRequest.mayWait***，然后分别调用了该类下的`startActivityMayWait()` 和 `startActivity()`，而上面提到***mRequest.mayWait***在`setMayWai()`方法中已经置为true，所以应该走`startActivityMayWait()`方法（但其实最终还是会走到下面的`startActivity()`方法当中）

我们来看看`startActivityMayWait()`

### 8）ActivityStarter.startActivityMayWait分析

我们看看看核心代码

```java
private int startActivityMayWait(IApplicationThread caller, int callingUid,
                                 String callingPackage, int requestRealCallingPid, int requestRealCallingUid,
                                 Intent intent, String resolvedType, IVoiceInteractionSession voiceSession,
                                 IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode,
                                 int startFlags, ProfilerInfo profilerInfo, WaitResult outResult,
                                 Configuration globalConfig, SafeActivityOptions options, boolean ignoreTargetSecurity,
                                 int userId, TaskRecord inTask, String reason,
                                 boolean allowPendingRemoteAnimationRegistryLookup,
                                 PendingIntentRecord originatingPendingIntent, boolean allowBackgroundActivityStart) {

      	//..........无关代码块
  
        // 这里会通过PMS去获取到将要启动的Activity的具体信息
  			ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);
  
        //..........无关代码块

        int res = startActivity(caller, intent, ephemeralIntent, resolvedType, aInfo, rInfo,
                voiceSession, voiceInteractor, resultTo, resultWho, requestCode, callingPid,
                callingUid, callingPackage, realCallingPid, realCallingUid, startFlags, options,
                ignoreTargetSecurity, componentSpecified, outRecord, inTask, reason,
                allowPendingRemoteAnimationRegistryLookup, originatingPendingIntent,
                allowBackgroundActivityStart);

        //..........无关代码块

}
```

核心就是调用了`startActivity()`方法

### 9）ActivityStarter.startActivity分析

这个方法也是`ActivityStarter.excute()`中else分支中所调用的方法

```java
private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
                          String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,
                          IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                          IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,
                          String callingPackage, int realCallingPid, int realCallingUid, int startFlags,
                          SafeActivityOptions options, boolean ignoreTargetSecurity, boolean componentSpecified,
                          ActivityRecord[] outActivity, TaskRecord inTask, String reason,
                          boolean allowPendingRemoteAnimationRegistryLookup,
                          PendingIntentRecord originatingPendingIntent, boolean allowBackgroundActivityStart) {

    if (TextUtils.isEmpty(reason)) {
        throw new IllegalArgumentException("Need to specify a reason.");
    }
    mLastStartReason = reason;
    mLastStartActivityTimeMs = System.currentTimeMillis();
    mLastStartActivityRecord[0] = null;
  
    // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
    mLastStartActivityResult = startActivity(caller, intent, ephemeralIntent, resolvedType,
            aInfo, rInfo, voiceSession, voiceInteractor, resultTo, resultWho, requestCode,
            callingPid, callingUid, callingPackage, realCallingPid, realCallingUid, startFlags,
            options, ignoreTargetSecurity, componentSpecified, mLastStartActivityRecord,
            inTask, allowPendingRemoteAnimationRegistryLookup, originatingPendingIntent,
            allowBackgroundActivityStart);
    // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
  
    if (outActivity != null) {
        // mLastStartActivityRecord[0] is set in the call to startActivity above.
        outActivity[0] = mLastStartActivityRecord[0];
    }

    return getExternalResult(mLastStartActivityResult);
}
```

没错，这里又调用了该类下的另一个`startActivity()`重载方法

```java
private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,tring callingPackage, int realCallingPid, int realCallingUid, int startFlags,afeActivityOptions options,boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity,TaskRecord inTask, boolean allowPendingRemoteAnimationRegistryLookup,PendingIntentRecord originatingPendingIntent, boolean allowBackgroundActivityStart) {
  
  	// ..........无关代码

    final int res = startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
            true /* doResume */, checkedOptions, inTask, outActivity, restrictedBgActivity);
    mSupervisor.getActivityMetricsLogger().notifyActivityLaunched(res, outActivity[0]);
    return res;
}
```

可以看到在结尾出又调用了一个同名重载方法

```java
private int startActivity(final ActivityRecord r, ActivityRecord sourceRecord,IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,ActivityRecord[] outActivity, boolean restrictedBgActivity) {
  
    int result = START_CANCELED;
    final ActivityStack startedActivityStack;
    try {
        mService.mWindowManager.deferSurfaceLayout();
        result = startActivityUnchecked(r, sourceRecord, voiceSession, voiceInteractor,
                startFlags, doResume, options, inTask, outActivity, restrictedBgActivity);
    } finally {
        // ..........无关代码
    }

    postStartActivityProcessing(r, result, startedActivityStack);

    return result;
}
```

该方法的核心是调用了`startActivityUnchecked()`
看看该方法的实现

### 10）ActivityStarter.startActivityUnchecked

```java
private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,ActivityRecord[] outActivity, boolean restrictedBgActivity) {
  
    // ..........无关代码
    
    // 核心代码1
    // 判断是否需要为将要启动的Activity新建一个Task，若不需要，那么该Activity将会和准备暂停的Activity处于同一个Task中
    boolean newTask = false;
    final TaskRecord taskToAffiliate = (mLaunchTaskBehind && mSourceRecord != null)
            ? mSourceRecord.getTaskRecord() : null;
    int result = START_SUCCESS;
    if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
            && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
        newTask = true;
        result = setTaskFromReuseOrCreateNewTask(taskToAffiliate);
    } else if (mSourceRecord != null) {
        result = setTaskFromSourceRecord();
    } else if (mInTask != null) {
        result = setTaskFromInTask();
    } else {
        result = setTaskToCurrentTopOrCreateNewTask();
    }
    if (result != START_SUCCESS) {
        return result;
    }  
    
    // ..........无关代码 
  
    // 核心代码2
    mTargetStack.startActivityLocked(mStartActivity, topFocused, newTask, mKeepCurTransition,
                mOptions);
  
   // ..........无关代码 
				
    final ActivityStack topStack = mRootActivityContainer.getTopDisplayFocusedStack();
    final ActivityRecord topFocused = topStack.getTopActivity();
    final ActivityRecord top = topStack.topRunningNonDelayedActivityLocked(mNotTop);
    final boolean dontStart = top != null && mStartActivity.resultTo == null
            && top.mActivityComponent.equals(mStartActivity.mActivityComponent)
            && top.mUserId == mStartActivity.mUserId
            && top.attachedToProcess()
            && ((mLaunchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0
            || isLaunchModeOneOf(LAUNCH_SINGLE_TOP, LAUNCH_SINGLE_TASK))
            && (!top.isActivityTypeHome() || top.getDisplayId() == mPreferredDisplayId);
  
    if (dontStart) {
        topStack.mLastPausedActivity = null;
        if (mDoResume) {
            // 核心代码3 找到前台栈并启动栈顶的activity 
            mRootActivityContainer.resumeFocusedStacksTopActivities();

        }
        ActivityOptions.abort(mOptions);
        if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
            return START_RETURN_INTENT_TO_CALLER;
        }

        deliverNewIntent(top);
      
        mSupervisor.handleNonResizableTaskIfNeeded(top.getTaskRecord(), preferredWindowingMode,
                mPreferredDisplayId, topStack);

        return START_DELIVERED_TO_TOP;
    }

   // ..........无关代码
}
```

我们先来看核心代码1，主要看

```java
if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
            && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
        newTask = true;
        result = setTaskFromReuseOrCreateNewTask(taskToAffiliate);
    }
```

这里newTask是布尔类型变量，用于决定是否要为将要启动的***Activity***新建一个Task，这里的Task不是指任务，而是指先进先出的***Activity***栈，对应***TaskRecord***类。通过if语句的条件中我们可以知道，要想让newTask为true，必不可少的一个条件就是 `mLaunchFlags & FLAG_ACTIVITY_NEW_TASK != 0`

再来看看核心代码2，这里就不对这个函数做分析了，知道它的作用是将准备启动的activity放置相应的Activity栈也栈顶即可
`mTargetStack.startActivityLocked(mStartActivity, topFocused, newTask, mKeepCurTransition,mOptions);`

最后就是核心代码 3 `mRootActivityContainer.resumeFocusedStacksTopActivities();`

### 11）RootActivityContainer.resumeFocusedStacksTopActivities分析

```java
boolean resumeFocusedStacksTopActivities(
        ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {
    // ..........无关代码
  
    //从栈顶往下找，找到第一个状态不是finished的Activity,这里就是准备启动的Activity
    ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);
  
    // ..........无关代码
  
    // 暂停上一个Activity，这个我们到下个章节讲述
    if (mResumedActivity != null) {
            if (DEBUG_STATES) Slog.d(TAG_STATES,
                    "resumeTopActivityLocked: Pausing " + mResumedActivity);
            pausing |= startPausingLocked(userLeaving, false, next, false);
        }
    
    // ..........无关代码
  
    for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
        boolean resumedOnDisplay = false;
        final ActivityDisplay display = mActivityDisplays.get(displayNdx);
        for (int stackNdx = display.getChildCount() - 1; stackNdx >= 0; --stackNdx) {
            final ActivityStack stack = display.getChildAt(stackNdx);
            final ActivityRecord topRunningActivity = stack.topRunningActivityLocked();
            if (!stack.isFocusableAndVisible() || topRunningActivity == null) {
                continue;
            }
            if (stack == targetStack) {
                resumedOnDisplay |= result;
                continue;
            }
            if (display.isTopStack(stack) && topRunningActivity.isState(RESUMED)) {
                stack.executeAppTransition(targetOptions);
            } else {
                resumedOnDisplay |= topRunningActivity.makeActiveIfNeeded(target);
            }
        }
        if (!resumedOnDisplay) {
            final ActivityStack focusedStack = display.getFocusedStack();
            if (focusedStack != null) {
                // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
                focusedStack.resumeTopActivityUncheckedLocked(target, targetOptions);
                // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
            }
        }
    }
    return result;
}
```

继续追踪代码
`focusedStack.resumeTopActivityUncheckedLocked(target, targetOptions);`

### 12）ActivityStack.resumeTopActivityUncheckedLocked分析

```java
boolean resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options) {
    if (mInResumeTopActivity) {
        return false;
    }

    boolean result = false;
    try {
        mInResumeTopActivity = true;
        // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
        result = resumeTopActivityInnerLocked(prev, options);
        // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑

        // When resuming the top activity, it may be necessary to pause the top activity (for
        // example, returning to the lock screen. We suppress the normal pause logic in
        // {@link #resumeTopActivityUncheckedLocked}, since the top activity is resumed at the
        // end. We call the {@link ActivityStackSupervisor#checkReadyForSleepLocked} again here
        // to ensure any necessary pause logic occurs. In the case where the Activity will be
        // shown regardless of the lock screen, the call to
        // {@link ActivityStackSupervisor#checkReadyForSleepLocked} is skipped.
        final ActivityRecord next = topRunningActivityLocked(true);
        if (next == null || !next.canTurnScreenOn()) {
            checkReadyForSleep();
        }
    } finally {
        mInResumeTopActivity = false;
    }

    return result;
}
```

继续追踪`result = resumeTopActivityInnerLocked(prev, options);`

### 13）ActivityStack.resumeTopActivityInnerLocked分析

```java
private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
    // ....................
    // 无关代码
    // ....................
 
    mStackSupervisor.startSpecificActivityLocked(next, true, true);
  
    // ....................
    // 无关代码
    // ....................
}
```

继续追踪`mStackSupervisor.startSpecificActivityLocked(next, true, true);`

### 14）StackSupervisor.startSpecificActivityLocked分析

```java
void startSpecificActivityLocked(ActivityRecord r, boolean andResume, boolean checkConfig) {
    final WindowProcessController wpc =
            mService.getProcessController(r.processName, r.info.applicationInfo.uid);

    boolean knownToBeDead = false;
    if (wpc != null && wpc.hasThread()) {
        try {
            // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
            realStartActivityLocked(r, wpc, andResume, checkConfig);
            // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
            return;
        } catch (RemoteException e) {
            Slog.w(TAG, "Exception when starting activity "
                    + r.intent.getComponent().flattenToShortString(), e);
        }
      
        knownToBeDead = true;
    }

    if (getKeyguardController().isKeyguardLocked()) {
        r.notifyUnknownVisibilityLaunched();
    }

    try {
        if (Trace.isTagEnabled(TRACE_TAG_ACTIVITY_MANAGER)) {
            Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "dispatchingStartProcess:"
                    + r.processName);
        }
       
        final Message msg = PooledLambda.obtainMessage(
                ActivityManagerInternal::startProcess, mService.mAmInternal, r.processName,
                r.info.applicationInfo, knownToBeDead, "activity", r.intent.getComponent());
        mService.mH.sendMessage(msg);
    } finally {
        Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
    }
}
```

继续追踪`realStartActivityLocked(r, wpc, andResume, checkConfig);`

### 15）StackSupervisor.realStartActivityLocked分析

```java
boolean realStartActivityLocked(ActivityRecord r, WindowProcessController proc,
        boolean andResume, boolean checkConfig) throws RemoteException {
    // ....................
    // 无关代码
    // ....................

    try {
            // ..........
            // 无关代码
   					//...........
      
            // 创建Activity的launch transaction.
            final ClientTransaction clientTransaction = ClientTransaction.obtain(
                    proc.getThread(), r.appToken);

            final DisplayContent dc = r.getDisplay().mDisplayContent;
            // 注意这里addCallback中添加的是LaunchActivityItem实例
            clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
                    System.identityHashCode(r), r.info,
                    mergedConfiguration.getGlobalConfiguration(),
                    mergedConfiguration.getOverrideConfiguration(), r.compat,
                    r.launchedFromPackage, task.voiceInteractor, proc.getReportedProcState(),
                    r.icicle, r.persistentState, results, newIntents,
                    dc.isNextTransitionForward(), proc.createProfilerInfoIfNeeded(),
                            r.assistToken));

            // 注意这里clientTransaction.setLifecycleStateRequest方法中传入的是ResumeActivityItem实							例
            final ActivityLifecycleItem lifecycleItem;
            if (andResume) {
                lifecycleItem = ResumeActivityItem.obtain(dc.isNextTransitionForward());
            } else {
                lifecycleItem = PauseActivityItem.obtain();
            }
            clientTransaction.setLifecycleStateRequest(lifecycleItem);

            // 核心代码 ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
      			// 执行事务
            mService.getLifecycleManager().scheduleTransaction(clientTransaction);
            // 核心代码 ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
      
            if ((proc.mInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0
                    && mService.mHasHeavyWeightFeature) {
                if (proc.mName.equals(proc.mInfo.packageName)) {
                    if (mService.mHeavyWeightProcess != null
                            && mService.mHeavyWeightProcess != proc) {
                        Slog.w(TAG, "Starting new heavy weight process " + proc
                                + " when already running "
                                + mService.mHeavyWeightProcess);
                    }
                    mService.setHeavyWeightProcess(r);
                }
            }

        } catch (RemoteException e) {
            if (r.launchFailed) {
              
                // ....................
    						// 无关代码
    						// ....................
              
        }
    } finally {
        endDeferResume();
    }

		// ....................
    // 无关代码
    // ....................
    return true;
}
```

该函数创建了一个***ClientTransaction***对象，主要看一下对该对象的这两个操作
【1】调用该对象的***addCallBack***向其***ClientTransactionItem队列***中加入***LaunchActivityItem***对象。
***ClientTransactionItem***是一个抽象类，而***LaunchActivityItem***是***ClientTransactionItem***的实现类。这里需要记住加入***addCallBack***方法加入是***LaunchActivityItem***实例即可，也就是接下来分析过程中出现多的每一个***ClientTransactionItem***对象都视为***LaunchActivityItem***即可
【2】调用该对象的`setLifecycleStateRequest()`方法，设置了它的***mLifecycleStateRequest属性***，***mLifecycleStateRequest***属于***ActivityLifecycleItem***类，而***ActivityLifecycleItem***是抽象类，***ResumeActivityItem***是该类的实现类，这里需要记住`setLifecycleStateRequest()`方法将***mLifecycleStateRequest属性***设置成一个***ResumeActivityItem***类对象,也就是接下来出现的每一个***ActivityLifecycleItem***对象你只需要将其看成***ResumeActivityItem***对象即可

接下来我们看看`mService.getLifecycleManager().scheduleTransaction(clientTransaction);`，这里将刚刚创建好的***ClientTransaction***作为参数传入。
`mService.getLifecycleManager()`会获取到一个***ClientLifecycleManager***实例，我们就看看***ClientLifecycleManager***的`scheduleTransaction()`方法

### 16）ClientLifrcycleManager.scheduleTransaction分析

```java
void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    final IApplicationThread client = transaction.getClient();
    // 核心代码
    transaction.schedule();
    if (!(client instanceof Binder)) {
        // If client is not an instance of Binder - it's a remote call and at this point it is
        // safe to recycle the object. All objects used for local calls will be recycled after
        // the transaction is executed on client in ActivityThread.
        transaction.recycle();
    }
}
```

该方法就是调用了`transaction.schedule();`
transaction也就是作为参数传进来的***ClientTransaction***对象，我们就直接定位到`ClientTransactionde.schedule()`方法当中

### 17）ClientTransactionde.schedule分析

```java
public void schedule() throws RemoteException {
    mClient.scheduleTransaction(this);
}
```

这个方法就一行代码，先看看mClient是什么

```java
private IApplicationThread mClient;
```

终于来了，它是一个***IApplicationThread***代理，这个类在【ActivityThread】部分解释过，这里不再解释了,我们可以直接定位到`AppThread.ApplicationThread.scheduleTransaction()`方法中

### 18）AppThread.ApplicationThread.scheduleTransaction分析

```java
@Override
public void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    ActivityThread.this.scheduleTransaction(transaction);
}
```

通过该方法仅有中的一行代码，我们又可以直接定位到`ActivityThread.scheduleTransaction()`当中，而***ActivityThread***继承自***ClientTransactionHandler***，`ActivityThread.scheduleTransaction()`方法是对`ClientTransactionHandler.scheduleTransaction()`的重写，但是***ActivityThread***并没有重写该方法，所以方法实现和父类相同，我们看看`ClientTransactionHandler.scheduleTransaction()`

### 19）ClientTransactionHandler.scheduleTransaction分析

```java
/** Prepare and schedule transaction for execution. */
void scheduleTransaction(ClientTransaction transaction) {
    transaction.preExecute(this);
    sendMessage(ActivityThread.H.EXECUTE_TRANSACTION, transaction);
}
```

这里主要是执行了`sendMessage()`方法，`sendMessage()`方法在***ClientTransactionHandler***中是一个抽象方法，在***ActivityThread***中的具体实现已经介绍过，简单来讲就是向Handler(ActivityThread.H)发送了一条***EXECUTE_TRANSACTION***消息，我们直接定位到***ActivityThread.H***中的`hanleMessage()`方法并找到处理***EXECUTE_TRANSACTION***消息的部分

### 20）ActivityThread.H.handlerMessage分析

```java
public void handleMessage(Message msg) {
    if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
    switch (msg.what) {
        
        //无关代码..........
        
        case EXECUTE_TRANSACTION:
            final ClientTransaction transaction = (ClientTransaction) msg.obj;
         	 	// 核心代码
            mTransactionExecutor.execute(transaction);
            if (isSystem()) {
                transaction.recycle();
            }
            break;
        
       
        //无关代码..........
        
    }
    Object obj = msg.obj;
    if (obj instanceof SomeArgs) {
        ((SomeArgs) obj).recycle();
    }
    if (DEBUG_MESSAGES) Slog.v(TAG, "<<< done: " + codeToString(msg.what));
}
```

我们直接关注核心代码
`mTransactionExecutor.execute(transaction);`

### 21） TransactionExecutor.execute分析

```java
public void execute(ClientTransaction transaction) {
    if (DEBUG_RESOLVER) Slog.d(TAG, tId(transaction) + "Start resolving transaction");

    final IBinder token = transaction.getActivityToken();
    if (token != null) {
        final Map<IBinder, ClientTransactionItem> activitiesToBeDestroyed =
                mTransactionHandler.getActivitiesToBeDestroyed();
        final ClientTransactionItem destroyItem = activitiesToBeDestroyed.get(token);
        if (destroyItem != null) {
            if (transaction.getLifecycleStateRequest() == destroyItem) {
                activitiesToBeDestroyed.remove(token);
            }
            if (mTransactionHandler.getActivityClient(token) == null) {
                Slog.w(TAG, tId(transaction) + "Skip pre-destroyed transaction:\n"
                        + transactionToString(transaction, mTransactionHandler));
                return;
            }
        }
    }

    if (DEBUG_RESOLVER) Slog.d(TAG, transactionToString(transaction, mTransactionHandler));

    // 核心代码1  activity实例的创建，onAttach和onCreate回调的触发在这条线上
    executeCallbacks(transaction);
    // 核心代码2  activity的onStart和onResume回调触发在这条线上
    executeLifecycleState(transaction);
    mPendingActions.clear();
    if (DEBUG_RESOLVER) Slog.d(TAG, tId(transaction) + "End resolving transaction");
}
```

通过核心代码1`executeCallbacks(transaction)`
我们又可以直接定位到TransactionExcutor.executeCallbacks方法中

#### 【1】Activity实例创建，onAttch和onCtreate回调触发

##### 「1」 TransactionExecutor.executeCallbacks分析

```java
public void executeCallbacks(ClientTransaction transaction) {
    
  	// 无关代码 ..........

    final int size = callbacks.size();
    for (int i = 0; i < size; ++i) {
        final ClientTransactionItem item = callbacks.get(i);
        if (DEBUG_RESOLVER) Slog.d(TAG, tId(transaction) + "Resolving callback: " + item);
        final int postExecutionState = item.getPostExecutionState();
        final int closestPreExecutionState = mHelper.getClosestPreExecutionState(r,
                item.getPostExecutionState());
        if (closestPreExecutionState != UNDEFINED) {
            cycleToPath(r, closestPreExecutionState, transaction);
        }
        //核心代码 
        item.execute(mTransactionHandler, token, mPendingActions);
        item.postExecute(mTransactionHandler, token, mPendingActions);
        if (r == null) {
            r = mTransactionHandler.getActivityClient(token);
        }

        if (postExecutionState != UNDEFINED && r != null) {
            final boolean shouldExcludeLastTransition =
                    i == lastCallbackRequestingState && finalState == postExecutionState;
            cycleToPath(r, postExecutionState, shouldExcludeLastTransition, transaction);
        }
    }
}
```

该方法遍历***ClientTransaction***对象的callbacks，也就是***ClientTransactionItem列表***，并执行每个列表元素对象的`excute()`方法，而从上面我们知道，***ClientTransactionItem***是抽象类，该列表中的元素对象的具体实现类应该是***LaunchActivityItem***，我们来看看`LaunchActivityItem.excute()`方法

##### 「2」 LaunchActivityItem.excute分析

```java
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
    ActivityClientRecord r = new ActivityClientRecord(token, mIntent, mIdent, mInfo,
            mOverrideConfig, mCompatInfo, mReferrer, mVoiceInteractor, mState, mPersistentState,
            mPendingResults, mPendingNewIntents, mIsForward,
            mProfilerInfo, client, mAssistToken);
    // 核心代码
    client.handleLaunchActivity(r, pendingActions, null);
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

该方法的核心代码是`client.handleLaunchActivity(r, pendingActions, null);`，
首先我们得确定client是什么？
从直观上看，它就是该方法中***ClientTransactionHandler***类型的参数，而在`TransactionExcutor.excuteCallbacks()`方法中调用方法是传入的参数是mTransactionHandler

![image-20211109161345259](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211109161345259.png)

所以我们需要知道mTransactionHandler是什么？
![image-20211109161502869](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211109161502869.png)

很明显，mTransactionHandler是一个***ClientTransactionHandler***对象，它会在***TransactionExcutor***的构造方法中赋值，而当前都是用的***TransactionExcutor***对象是在***ActivityThread***中创建的，所以在***ActivityThread***中可以找到
![image-20211109161847185](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211109161847185.png)

而之前也提到过***ActiivtyThread***继承自***ClientTransactionHandler***，所以this就是***ActivityThread***实例。
![image-20211109162149064](/Users/shenyutao/Library/Application Support/typora-user-images/image-20211109162149064.png)

我们回到`client.handleLaunchActivity(r, pendingActions, null)`，现在可以确定client就是***ActivityThread***了，那么就可以直接定位到`ActivityThread.handleLaunchActivity()`方法当中

##### 「3」ActivityThread.handleLaunchActivity分析

```java
public Activity handleLaunchActivity(ActivityClientRecord r,
        PendingTransactionActions pendingActions, Intent customIntent) {
    
    // 无关代码 .......... 
  
    final Activity a = performLaunchActivity(r, customIntent);
  
  	// 无关代码 .......... 
    
}
```

继续跟进一下，我们就进入该类下的performLaunchActivity

##### 「4」ActivityThread.performLaunchActivity分析

```java
/**  Core implementation of activity launch. */
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    
    // 无关代码 ..........
  
    Activity activity = null;
    try {
        // 通过mInstrumentation新建一个activity实例
        java.lang.ClassLoader cl = appContext.getClassLoader();
        activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
        
      	// 无关代码 ..........
      
    } catch (Exception e) {
      
        // 无关代码 ..........
    }
  

    try {
      
        // 无关代码 ..........

        if (activity != null) {
          
            // 无关代码 ..........
          
            // 触发activity的onAttach回调
            activity.attach(appContext, this, getInstrumentation(), r.token,
                    r.ident, app, r.intent, r.activityInfo, title, r.parent,
                    r.embeddedID, r.lastNonConfigurationInstances, config,
                    r.referrer, r.voiceInteractor, window, r.configCallback,
                    r.assistToken);

            // 无关代码 ..........
            
            // 触发activity的onCreate回调
            if (r.isPersistable()) {
                mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
            } else {
                mInstrumentation.callActivityOnCreate(activity, r.state);
            }
          
            // 无关代码 ..........
           
        }
        
        // 无关代码 ..........

    } catch (SuperNotCalledException e) {
        // 无关代码 ..........

    } catch (Exception e) {
        // 无关代码 ..........
    }

    return activity;
}
```

在***ActivityThread.performLaunchActivity***当中利用***Instrumentation***对象先创建了一个Activity，然后触发***activity.onAttach***回调，最后有通过***Instrumentation***触发了***activity.onCreate***回调

我们再回过头来看看 核心代码2：
`executeLifecycleState(transaction);`

#### 【2】Activity onStrat和onResume回调触发分析

##### 「1」 TransactionExecutor.executeLifecycleState分析

```java
private void executeLifecycleState(ClientTransaction transaction) {
    // 从transaction中获取到ActivityLifecycleItem类的属性，ActivityLifecycleItem是抽象类，这里lifecycleItem对应的具体实现类是ResumeActivityItem,可以回头看看 15) 部分
    final ActivityLifecycleItem lifecycleItem = transaction.getLifecycleStateRequest();
    if (lifecycleItem == null) {
        return;
    }

    final IBinder token = transaction.getActivityToken();
    final ActivityClientRecord r = mTransactionHandler.getActivityClient(token);
    if (DEBUG_RESOLVER) {
        Slog.d(TAG, tId(transaction) + "Resolving lifecycle state: "
                + lifecycleItem + " for activity: "
                + getShortActivityName(token, mTransactionHandler));
    }

    if (r == null) {
        return;
    }
    
    cycleToPath(r, lifecycleItem.getTargetState(), true , transaction);

    // 核心代码
    lifecycleItem.execute(mTransactionHandler, token, mPendingActions);
    lifecycleItem.postExecute(mTransactionHandler, token, mPendingActions);
}
```

核心代码调用了`lifecycleItem.execute()`,而lifecycleItem在声明的时候是一个***ActivityLifecycleItem***对象，在 15）中有解释过可以将后续分析流程出现的***ActivityLifecycleItem***对象都视为***ResumeActivityItem***类即可。

那么我们直接将代码定位到ResumeActivityItem.execute()上

##### 「2」ResumeActivityItem.execute分析

```java
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityResume");
    // 核心代码
    client.handleResumeActivity(token, true, mIsForward,"RESUME_ACTIVITY");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

很显然，这里的核心代码是`client.handleResumeActivity(token, true, mIsForward,"RESUME_ACTIVITY")`，client就是***ActivityThread***对象实例，这个在    21）-> 【1】->「2」中做过解释。（而且***ClientTransactionHandler***是个抽象类，它的实现类也只有***ActivityThread***）

于是我们将代码直接定位到ActivityThread的handleResumeActivity中

##### 「3」ActivityThread.hanldeResumeActivity分析

```java
public void handleResumeActivity(IBinder token, boolean finalStateRequest, boolean isForward,
        String reason) {
    
    // ..........无关代码

    // 核心代码
    final ActivityClientRecord r = performResumeActivity(token, finalStateRequest, reason);
    
    // ..........无关代码 
}
```

我们根据核心代码直接定位到performResumeActivity中

##### 「4」ActivityThread,performResumeActity分析

```java
public ActivityClientRecord performResumeActivity(IBinder token, boolean finalStateRequest,
        String reason) {
    final ActivityClientRecord r = mActivities.get(token);
  
    // .....无关代码
  
    try {
        r.activity.onStateNotSaved();
        r.activity.mFragments.noteStateNotSaved();
        checkAndBlockForNetworkAccess();
        if (r.pendingIntents != null) {
            deliverNewIntents(r, r.pendingIntents);
            r.pendingIntents = null;
        }
        if (r.pendingResults != null) {
            deliverResults(r, r.pendingResults, reason);
            r.pendingResults = null;
        }
        // 核心代码
        r.activity.performResume(r.startsNotResumed, reason);

        r.state = null;
        r.persistentState = null;
        r.setState(ON_RESUME);

        reportTopResumedActivityChanged(r, r.isTopResumedActivity, "topWhenResuming");
    } catch (Exception e) {
        if (!mInstrumentation.onException(r.activity, e)) {
            throw new RuntimeException("Unable to resume activity "
                    + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
        }
    }
    return r;
}
```

根据该方法中的核心代码`r.activity.performResume(r.startsNotResumed, reason)`我们可以直接定位到Activity.performResume

##### 「5」Activity.performResume

```java
final void performResume(boolean followedByPause, String reason) {
    dispatchActivityPreResumed();
    // 核心代码1 
    performRestart(true /* start */, reason);
    
    // ..........无关代码
  
    // 核心代码2 
    // 通过mInstrumentation触发activity的onResume回调
    mInstrumentation.callActivityOnResume(this);
    
    // ..........无关代码
}
```

该方法先是调用了performStart方法触发Activity的onStart回调，然后再利用Instrumentation.callActivityOnResume()方法触发onResume回调，如下所示

```java
public void callActivityOnResume(Activity activity) {
    activity.mResumed = true;
    activity.onResume();

    // ..........无关代码
}
```

最后再来看看performStart方法中的实现

##### 「6」Activity.performStart

```java
final void performRestart(boolean start, String reason) {
    //..........无关代码
    mInstrumentation.callActivityOnRestart(this);      
  	// ..........无关代码
}
```

原理差不多，都是通过Instrumentation来触发回调

```java
public void callActivityOnRestart(Activity activity) {
    activity.onRestart();
}
```

至此Activity启动流程算是分析完毕了，整理一下该流程，见【3.流程图】



## 2.Pause源码分析

### 1）.简述

当一个Activity要启动的时候，需要先Pause上当前正在展示的Activity然后再resume准备启动的Activity，那我们就分析一下这流程线的Avtivity是如何被Pause的

而启动流程在上面已经讲述过一遍，启动的部分就不再分析，我们直接定位到启动过程中涉及暂停上一个Activity的部分

### 2）RootActivityContainer.resumeFocusedStacksTopActivities

```java
boolean resumeFocusedStacksTopActivities(
        ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {
    // ..........无关代码

		//从栈顶往下找，找到第一个状态不是finished的Activity,这里就是准备启动的Activity
		ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);

		// ..........无关代码

		// 暂停上一个Activity
		if (mResumedActivity != null) {
 		       if (DEBUG_STATES) Slog.d(TAG_STATES,
 		               "resumeTopActivityLocked: Pausing " + mResumedActivity);
            // 核心代码
  		      pausing |= startPausingLocked(userLeaving, false, next, false);
        }

		// ..........无关代码

		for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
   		 boolean resumedOnDisplay = false;
  		  final ActivityDisplay display = mActivityDisplays.get(displayNdx);
  		  for (int stackNdx = display.getChildCount() - 1; stackNdx >= 0; --stackNdx) {
    		    final ActivityStack stack = display.getChildAt(stackNdx);
    		    final ActivityRecord topRunningActivity = stack.topRunningActivityLocked();
 		        if (!stack.isFocusableAndVisible() || topRunningActivity == null) {
      		      continue;
      		  }
    		    if (stack == targetStack) {
                resumedOnDisplay |= result;
      		      continue;
    		    }
   		     if (display.isTopStack(stack) && topRunningActivity.isState(RESUMED)) {
   		         stack.executeAppTransition(targetOptions);
    		    } else {
     		       resumedOnDisplay |= topRunningActivity.makeActiveIfNeeded(target);
      		  }
    		}
  		  if (!resumedOnDisplay) {
   		     final ActivityStack focusedStack = display.getFocusedStack();
     		   if (focusedStack != null) {
                // 启动栈顶Activity
      		      focusedStack.resumeTopActivityUncheckedLocked(target, targetOptions);
      		  }
  		  }
		}
		return result;
}
```

 `pausing |= startPausingLocked(userLeaving, false, next, false)`Actiivty的暂停就是从这开始的

### 3）ActivityStack.startPausingLocked

```java
final boolean startPausingLocked(boolean userLeaving, boolean uiSleeping,
        ActivityRecord resuming, boolean pauseImmediately) {
    
    // ..........无关代码
   
    // 获取到当前处于resume状态的activity，也是准备pause的activity
    ActivityRecord prev = mResumedActivity;
    
    // 如果当前没有处于激活状态的Activcity，不执行暂停操作 直接return
    if (prev == null) {
        if (resuming == null) {
            Slog.wtf(TAG, "Trying to pause when nothing is resumed");
            mRootActivityContainer.resumeFocusedStacksTopActivities();
        }
        return false;
    }
    // 如果当前准备暂停的Activity是正在resume的Activity，直接return
    if (prev == resuming) {
        Slog.wtf(TAG, "Trying to pause activity that is in process of being resumed");
        return false;
    }

    // ..........无关代码
  
    //当前正在暂停的Activity
    mPausingActivity = prev;
    //上一次暂停的Activity
    mLastPausedActivity = prev;
  
    // ..........无关代码

    if (prev.attachedToProcess()) {
        if (DEBUG_PAUSE) Slog.v(TAG_PAUSE, "Enqueueing pending pause: " + prev);
        try {
            // ..........无关代码
          
            // 核心代码：
            // mService就是ActivityTaskManagerService，这里执行了创建了一个PauseActivityItem实例，         						  			 scheduleTransaction方法会根据这个实例的类型，做出暂停操作
            mService.getLifecycleManager().scheduleTransaction(prev.app.getThread(),
                    prev.appToken, PauseActivityItem.obtain(prev.finishing, userLeaving,
                            prev.configChangeFlags, pauseImmediately));
        } catch (Exception e) {
            // Ignore exception, if process died other code will cleanup.
            Slog.w(TAG, "Exception thrown during pause", e);
            mPausingActivity = null;
            mLastPausedActivity = null;
            mLastNoHistoryActivity = null;
        }
    } else {
        mPausingActivity = null;
        mLastPausedActivity = null;
        mLastNoHistoryActivity = null;
    }

    // ..........无关代码
}
```

我们来看看核心代码，
`mService.getLifecycleManager().scheduleTransaction(prev.app.getThread(),prev.appToken,PauseActivityItem.obtain(prev.finishing, userLeaving,prev.configChangeFlags, pauseImmediately));`
mService是***ActivityTaskManagerService***，
需要注意的是自最后一个参数要求传入***ActivityLifecycleItem***，在分析启动流程的时候提到过***ActivityLifecycleItem***是一个抽象类以及它有一个实现类***ResumeActicityItem***，而***PauseActivityItem***是***ActivityLifecycleItem***的另一个实现类，这里就传入了一个***PauseActicityItem***实例，所以后续可以将***ActivityLifecycleItem***视为***PauseActivityItem***

那么我们来看看`ActivityTaskManagerService.getLifecycleManager()`能够获取到***ATMS***持有的***ClientLifecycleManager***对象，那我们来看看`ClientLifecycleManager.scheduleTranscation()`方法的实现

### 4）ActivityTaskManagerService.scheduleTransaction分析

```java
void scheduleTransaction(@NonNull IApplicationThread client, @NonNull IBinder activityToken,
        @NonNull ActivityLifecycleItem stateRequest) throws RemoteException {
    final ClientTransaction clientTransaction = transactionWithState(client, activityToken,
            stateRequest);
    scheduleTransaction(clientTransaction);
}
```

这里没什么好说的，就是调佣了一个重载方法

```java
void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    final IApplicationThread client = transaction.getClient();
    // 核心代码
    transaction.schedule();
    if (!(client instanceof Binder)) {
        // If client is not an instance of Binder - it's a remote call and at this point it is
        // safe to recycle the object. All objects used for local calls will be recycled after
        // the transaction is executed on client in ActivityThread.
        transaction.recycle();
    }
}
```

通过核心代码 `transaction.schedule()`我们又可以定位到`ClientTransaction.schedule()`方法当中

### 5）ClientTransaction.shedule分析

```java
private IApplicationThread mClient;

..........
  
public void schedule() throws RemoteException {
    mClient.scheduleTransaction(this);
}
```

mClient属于***IApplicationThread***类，该类是***ActivityThread.ApplicationThread***的远程代理类，调用`mClient.scheduleTransaction(this)`相当于调用了`ActivityThread.ApplicationThread.shceduleTransaction()`方法，所以我们直接定位

### 6）ActivityThread.ApplicationThread.scheduleTransaction分析

```java
public void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
    ActivityThread.this.scheduleTransaction(transaction);
}
```

该方法调用了ActivityThread的scheduleTransaction，我们直接定位到ActivityThread.scheduleTransaction

### 7）ActivityThread.scheduleTransaction分析

```java
void scheduleTransaction(ClientTransaction transaction) {
    transaction.preExecute(this);
    sendMessage(ActivityThread.H.EXECUTE_TRANSACTION, transaction);
}
```

该方法就是通过***sendMessage***方法向***ActicityThread.H***发送了一条***EXECUTE_TRANSACTION***类型的消息，而***ActivityThread.H***是一个***Handler***，我们可以直接定位到***Activity.H.handleMessage***中找到处理***EXECUTE_TRANSACTION***类型消息的部分

### 8）ActivityThread.H.handleMessage分析

```java
public void handleMessage(Message msg) {
    // .........无关代码
    switch (msg.what) {
        
        // ...........无关代码

        case EXECUTE_TRANSACTION:
            final ClientTransaction transaction = (ClientTransaction) msg.obj;
            // 核心代码
            mTransactionExecutor.execute(transaction);
            if (isSystem()) {
                transaction.recycle();
            }
            break;
    }
  
    // ..........无关代码
}
```

这里调用了`TransactionExcutor.excute()`方法，我们直接定位到该方法中

### 9）TransactionExecutor.execute

```java
public void execute(ClientTransaction transaction) {
    // ..........无关代码
    executeLifecycleState(transaction);
    // ..........无关代码
}
```

这条线不需要分析excuteCallbacks，我们继续定位

### 10）TransactionExcutor.executeLifecycleState

```java
private void executeLifecycleState(ClientTransaction transaction) {
    // 获取到transaction持有的一个ActivityLifecycle对象，也就是PauseActivityItem对象 
    final ActivityLifecycleItem lifecycleItem = transaction.getLifecycleStateRequest();
    
    // ..........无关代码
  
    // 核心代码
    lifecycleItem.execute(mTransactionHandler, token, mPendingActions);
    lifecycleItem.postExecute(mTransactionHandler, token, mPendingActions);
}
```

这里通过`Transaction.getLifecycleStateRequest()`方法获取的***ActivityLifecycleItem***对就是之前set的***PauseActivityItem***对象。所以通过`lifecycleItem.execute(mTransactionHandler, token, mPendingActions)`我们可以直接定位到`PauseActivityItem.excute()`方法当中

### 11）PauseActivityItem.excute分析

```java
@Override
public void execute(ClientTransactionHandler client, IBinder token,
        PendingTransactionActions pendingActions) {
    Trace.traceBegin(TRACE_TAG_ACTIVITY_MANAGER, "activityPause");
    // 核心代码
    client.handlePauseActivity(token, mFinished, mUserLeaving, mConfigChanges, pendingActions,
            "PAUSE_ACTIVITY_ITEM");
    Trace.traceEnd(TRACE_TAG_ACTIVITY_MANAGER);
}
```

核心代码中的client其实就是一个***ActiivtyThread***对象，在分析启动流程【ResumeActivityItem.excute】的时候有解释，这里就不再解释了，那么我们直接定位到`ActivityThread.handlePauseActivity()`当中

### 12）ActivityThread.handlePauseActicity分析

```java
public void handlePauseActivity(IBinder token, boolean finished, boolean userLeaving,
        int configChanges, PendingTransactionActions pendingActions, String reason) {
    ActivityClientRecord r = mActivities.get(token);
    if (r != null) {
        if (userLeaving) {
            performUserLeavingActivity(r);
        }

        r.activity.mConfigChangeFlags |= configChanges;
        // 核心代码
        performPauseActivity(r, finished, reason, pendingActions);

        if (r.isPreHoneycomb()) {
            QueuedWork.waitToFinish();
        }
        mSomeActivitiesChanged = true;
    }
}
```

该方法的核心是 `performPauseActivity(r, finished, reason, pendingActions)`,我们继续定位

### 13）ActicityThread.performPauseActivity分析

```java
private Bundle performPauseActivity(ActivityClientRecord r, boolean finished, String reason,
        PendingTransactionActions pendingActions) {
    
    // ..........无关代码

    // 核心代码 1 
    // 这里当activity未处于finished状态并且SDK版本在honeycomb之前，都会触发onSaveInstanceState回调
    final boolean shouldSaveState = !r.activity.mFinished && r.isPreHoneycomb();
    if (shouldSaveState) {
        //这个方法就不不做分析了，它最通过Instrumentation触发activity的onSaveInstance回调
        callActivityOnSaveInstanceState(r);
    }
    
    // 核心代码 2 
    performPauseActivityIfNeeded(r, reason);

    // .........无关代码

}
```

我们先来看看核心代码1

```java
final boolean shouldSaveState = !r.activity.mFinished && r.isPreHoneycomb();
if (shouldSaveState) {
   callActivityOnSaveInstanceState(r);
}
```

这里会通过shouldSaveState的值来决定是否要执行`callActivityOnSaveInstanceState(r)`，而shouldSaveState由r.activity.mFinished和r.isPreHoneycomb()决定。
r.acticity.misFinshed是判断该Activity是否为mFinished状态
而isPreHoneyComb则是判断SDK版本是否在HoneyComeb(3.0)之前，我们可以看看它的实现

```java
private boolean isPreHoneycomb() {
    return activity != null && activity.getApplicationInfo().targetSdkVersion
            < android.os.Build.VERSION_CODES.HONEYCOMB;
}
```

也就是说，如果Activity只是被调到后台不被销毁并且SDK版本小于3.0时，在onPause回调前会触发onSaveInstance回调



然后我们根据核心代码2 继续定位`performPauseActivityIfNeeded(r, reason)`

### 14）ActicityThread.performPauseActivityIfNeeded

```java
private void performPauseActivityIfNeeded(ActivityClientRecord r, String reason) {
    //..........无关代码
      
    mInstrumentation.callActivityOnPause(r.activity);
  
    // ..........无关代码    
}
```

这里调用了`Instrumentation.callActivityOnPause()`方法

### 15） Instrumentation.callActivityOnPause分析

```java
public void callActivityOnPause(Activity activity) {
    activity.performPause();
}
```

这里调用了`Activity.performPause()`方法

### 16）Activity.performPause分析

```java
final void performPause() {
    dispatchActivityPrePaused();
    mDoReportFullyDrawn = false;
    mFragments.dispatchPause();
    mCalled = false;
    // 这里触发了onPause回调
    onPause();
    writeEventLog(LOG_AM_ON_PAUSE_CALLED, "performPause");
    mResumed = false;
    if (!mCalled && getApplicationInfo().targetSdkVersion
            >= android.os.Build.VERSION_CODES.GINGERBREAD) {
        throw new SuperNotCalledException(
                "Activity " + mComponent.toShortString() +
                        " did not call through to super.onPause()");
    }
    dispatchActivityPostPaused();
}
```

这里就触发了onPause回调，至此，启动一个新的Activity时，暂停另外一个Activity的流程结束。这里给出流程图，见【3.流程图】

## 3.流程图

### 1）Activity启动流程图

![AMS](/Users/shenyutao/Desktop/Framework/AMS.jpg)

### 2）Activity Pause流程图



![AMS Pause](/Users/shenyutao/Desktop/Framework/AMS Pause.jpg)

# 八.WMS创建流程

## 1.源码分析

### 1）SystemServer.main

因为***WMS***也属于系统服务，所以我们可以从***SystemServer***的入口方法开始找

```java
public static void main(String[] args) {
    new SystemServer().run();
}
```

这里实例化了一个***SystemServer***然后调用了它的`run()`方法

### 2）SystemServer.run

```java
private void run() {
    
    // Start services.
    try {
        traceBeginAndSlog("StartServices");
        startBootstrapServices();
        startCoreServices();
        // WMS在这里启动
        startOtherServices();
        SystemServerInitThreadPool.shutdown();
    } catch (Throwable ex) {
        Slog.e("System", "******************************************");
        Slog.e("System", "************ Failure starting system services", ex);
        throw ex;
    } finally {
        traceEnd();
    }

}
```

在【AMS启动流程】中我们知道，在***SystemServer***的`run()`方法当中分别调用`startBootstrapService()`，`startCoreService()`和`startOtherService()`三个方法分别启动了不同类别的系统线程。而WMS是在`startCoreServices()`方法中被启动的。我们定位到该方法中看看它是如何启动的

### 3）SystemServer.startOtherService

```java
private void startOtherServices() {
    
    // ..........无关代码
  
    WindowManagerService wm = null;
   
    // ..........无关代码
  
    traceBeginAndSlog("StartWindowManagerService");
    // WMS needs sensor service ready
    ConcurrentUtils.waitForFutureNoInterrupt(mSensorServiceStart, START_SENSOR_SERVICE);
    mSensorServiceStart = null;
    // 核心代码1 
    // WMS创建
    wm = WindowManagerService.main(context, inputManager, !mFirstBoot, mOnlyCore,
            new PhoneWindowManager(), mActivityManagerService.mActivityTaskManager);
    // 将WMS和InputService添加到ServiceManager中
    ServiceManager.addService(Context.WINDOW_SERVICE, wm, /* allowIsolated= */ false,
            DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PROTO);
    ServiceManager.addService(Context.INPUT_SERVICE, inputManager,
            /* allowIsolated= */ false, DUMP_FLAG_PRIORITY_CRITICAL);
    traceEnd();
    traceBeginAndSlog("SetWindowManagerService");
    mActivityManagerService.setWindowManager(wm);
    traceEnd();

    traceBeginAndSlog("WindowManagerServiceOnInitReady");
    // 核心代码2
    // WMS启动
    wm.onInitReady();
    traceEnd();
  
    // ..........无关代码
}
```

核心代码1创建了WMS的实例，核心代码2启动了WMS，我们逐一分析一下

#### 【1】WindowManagerService.main

```java
public static WindowManagerService main(final Context context, final InputManagerService im,
        final boolean showBootMsgs, final boolean onlyCore, WindowManagerPolicy policy,
        ActivityTaskManagerService atm) {
    return main(context, im, showBootMsgs, onlyCore, policy, atm,
            SurfaceControl.Transaction::new);
}
```

这里调用了一次重载方法

```java
public static WindowManagerService main(final Context context, final InputManagerService im,
        final boolean showBootMsgs, final boolean onlyCore, WindowManagerPolicy policy,
        ActivityTaskManagerService atm, TransactionFactory transactionFactory) {
    DisplayThread.getHandler().runWithScissors(() ->
            sInstance = new WindowManagerService(context, im, showBootMsgs, onlyCore, policy,
                    atm, transactionFactory), 0);
    return sInstance;
}
```

DisplayThread是一个单例的前台线程，这个线程用来处理需要低延时显示的相关操作，并只能由WindowManager、DisplayManager和InputManager实时执行快速操作。

WMS的实例化是在Runnable的run方法中执行的(表现为代码中的lambda表达式)，然后这个Runnable是由DisplayThread的Handler负责执行，所以可以得出，WMS的实例化是在DisplayThread中进行的。在执行该Runnable的时候，SystemServer主线程会处于等待状态不会直接return，我们可以来分析一下

直接定位到Handler.runWithScissors方法

##### 「1」Handler.runWithScissors

```java
public final boolean runWithScissors(@NonNull Runnable r, long timeout) {
    if (r == null) {
        throw new IllegalArgumentException("runnable must not be null");
    }
    if (timeout < 0) {
        throw new IllegalArgumentException("timeout must be non-negative");
    }
    
    // 核心代码 1 
    if (Looper.myLooper() == mLooper) {
        r.run();
        return true;
    }
    
    // 核心代码 2 
    BlockingRunnable br = new BlockingRunnable(r);
    return br.postAndWait(this, timeout);
}
```

我们看到核心代码1（不执行）：
Looper.myLooper()可以得到当前所处线程的Looper，而mLooper是该Handler所处线程的Looper，由于该Handler位于DisplayThread中，而当前线程是SystemServer的主线程，所以两者不相等，if闭包中的代码不执行。

于是执行到核心代码2 ：
创建一个BlockingRunnable对象，并调用了postAndWait方法，我们直接定位到BlockingRunnable.postAndWait当中，这里注意一下第二个参数timeOut是0

##### 「2」Handler.BlockingRunnable.postAndWait

```java
public boolean postAndWait(Handler handler, long timeout) {
    //核心代码 1 
   if (!handler.post(this)) {
        return false;
    }

    synchronized (this) {
        if (timeout > 0) {
            final long expirationTime = SystemClock.uptimeMillis() + timeout;
            while (!mDone) {
                long delay = expirationTime - SystemClock.uptimeMillis();
                if (delay <= 0) {
                    return false; // timeout
                }
                try {
                    wait(delay);
                } catch (InterruptedException ex) {
                }
            }
        } else {
            // 核心代码 2 
            while (!mDone) {
                try {
                    // bject的wait方法，会让当前线程进入等待状态，需要通过notify唤醒
                    wait();
                } catch (InterruptedException ex) {
                }
            }
        }
    }
    return true;
}
```

这里直接调用了Handler.post执行任务，这里传入了this,说明BlockingRunnable也实现了Runnable接口，所以我们需要看看它的run方法

```java
public void run() {
    try {
        mTask.run();
    } finally {
        synchronized (this) {
            mDone = true;
            // 唤醒线程
            notifyAll();
        }
    }
}
```

mTask就是执行的任务，对应执行创建WMS的Runnable。这里我们可以看到，当任务执行结束以后，也就是WMS创建完成后，会将mDone对象变成true（mDone一开始为false），并唤醒其他线程。

我们再回去看看Handler.BlockingRunnable.postAndWait方法，由于传入的参数timeOut 为 0 ，所以会走else分支，在else分支中，while中的mDone在任务还未执行完的时候为false，也就是不会跳出循环一直执行wait()，当任务执行完以后跳出循环。而wait()会让当前线程也就是SystemServer主线程进入等待状态，在任务执行完后通过notifyAll()将其唤醒。所以SystemServer主线程在这里会进行等待，知道收到WMS完成创建的通知后才继续工作

```java
else {
        // 核心代码 2 
        while (!mDone) {
            try {
                wait();
                } catch (InterruptedException ex) 
            {
            }
        }
}
```

至此，WMS就被创建出来了



#### 【2】WindowManagerService.onInitReady

  调用了该方法后，WMS就启动了，这里就不做分析了



# 九.DecorView的创建和

## 1.WIndow创建源码分析

### 1）Activity.performLaunchActivity

```java
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    

    i
    Activity activity = null;
    try {
        java.lang.ClassLoader cl = appContext.getClassLoader();
        // 创建actvity
        activity = mInstrumentation.newActivity(
                cl, component.getClassName(), r.intent);
        
      	// ..........无关代码
    } catch (Exception e) {
        // ..........无关代码
    }

    try {
        // ..........无关代码

        if (activity != null) {
            // .........无关代码
            
            // activity attach
            activity.attach(appContext, this, getInstrumentation(), r.token,
                    r.ident, app, r.intent, r.activityInfo, title, r.parent,
                    r.embeddedID, r.lastNonConfigurationInstances, config,
                    r.referrer, r.voiceInteractor, window, r.configCallback,
                    r.assistToken);

            // 触发activity onCreate回调
            if (r.isPersistable()) {
                mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
            } else {
                mInstrumentation.callActivityOnCreate(activity, r.state);
            }
            
            // ..........无关代码
        }
       
        // ..........无关代码

    } catch (SuperNotCalledException e) {
        throw e;

    } catch (Exception e) {
        // ..........无关代码
    }

    return activity;
}
```

### 3）Activity.attach

```java
final void attach(Context context, ActivityThread aThread,
                  Instrumentation instr, IBinder token, int ident,
                  Application application, Intent intent, ActivityInfo info,
                  CharSequence title, Activity parent, String id,
                  NonConfigurationInstances lastNonConfigurationInstances,
                  Configuration config, String referrer, IVoiceInteractor voiceInteractor,
                  Window window, ActivityConfigCallback activityConfigCallback, IBinder assistToken) {
    // ..........无关对象

    // 核心代码1 
    // 创建一个Window对象，并设置监听等
    mWindow = new PhoneWindow(this, window, activityConfigCallback);
    mWindow.setWindowControllerCallback(this);
    mWindow.setCallback(this);
    mWindow.setOnWindowDismissedCallback(this);
    mWindow.getLayoutInflater().setPrivateFactory(this);

    // 核心代码2
    // 绑定WindowManager
    mWindow.setWindowManager(
            (WindowManager) context.getSystemService(Context.WINDOW_SERVICE),
            mToken, mComponent.flattenToString(),
            (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0);
    if (mParent != null) {
        mWindow.setContainer(mParent.getWindow());
    }
    mWindowManager = mWindow.getWindowManager();
     
    // ..........无关代码
}
```

核心代码1：创建了一个PhoneWindow，它是Window的唯一实现类
核心代码2：将window和WMS进行绑定，我们来看看过程

```java
public void setWindowManager(WindowManager wm, IBinder appToken, String appName,
        boolean hardwareAccelerated) {
    mAppToken = appToken;
    mAppName = appName;
    mHardwareAccelerated = hardwareAccelerated;
    if (wm == null) {
        wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
    }
    // 核心代码
    mWindowManager = ((WindowManagerImpl)wm).createLocalWindowManager(this);
}
```

我们根据核心代码继续定位

```java
public WindowManagerImpl createLocalWindowManager(Window parentWindow) {
    return new WindowManagerImpl(mContext, parentWindow);
}
```

可以看到，这里实例化了一个WindowManagerImpl对象



## 2.DecorView常创建源码分析

### 1）Activity.setContentView

```java
public void setContentView(View view) {
        getWindow().setContentView(view);
        initWindowDecorActionBar();
    }
```

该方法的核心是 `getWindow().setContentView(view)`，而这里 `getWindow()`得到的便是在`Activity.attach()`中创建的***PhoneWindow***对象

我们来看看`PhoneWindow.setContentView()`方法

### 2）PhoneWindow.setContentView

```java
public void setContentView(View view, ViewGroup.LayoutParams params) {
    if (mContentParent == null) {
        // 核心代码
        // 安装DecorView并加入ContentView
        installDecor();
    } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
        mContentParent.removeAllViews();
    }

    if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
        view.setLayoutParams(params);
        final Scene newScene = new Scene(mContentParent, view);
        transitionTo(newScene);
    } else {
        // 往ContentView中添加布局文件映射的View
        mContentParent.addView(view, params);
    }
    
    mContentParent.requestApplyInsets();
    //通知Activity
    final Callback cb = getCallback();
    if (cb != null && !isDestroyed()) {
        // 这个回调就是Activity在attach中注册的，不知道为啥是空实现
        cb.onContentChanged();
    }
    mContentParentExplicitlySet = true;
}
```

在该方法中主要做了这几件事：
1.核心代码`installDecor()`：创建一个***DecorView***并为其添加contentView，并且让mContentParent变量指向***DecorView***的contentView
2.往***DecorView***的contentView中加入要显示的视图，该视图是指***Activity***的***xml布局文件***经过解析后的得出的ViewGroup。***DecorView***的contentView实际上是一个***FrameLayout***
3.通知***Activity***

这里我们就重点分析一下核心代码`installDecor()`

### 3）PhoneWindow.installDecor

```java
private void installDecor() {
    mForceDecorInstall = false;
    if (mDecor == null) {
        // 核心代码 1 
        // 生成一个DecorView实例
        mDecor = generateDecor(-1);
        mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mDecor.setIsRootNamespace(true);
        if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
            mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
        }
    } else {
        mDecor.setWindow(this);
    }
    
    if (mContentParent == null) {
        // 核心代码 2 
        // 设置DecorView布局，并让mContentView变量指向DecorView布局中的ContentView
        mContentParent = generateLayout(mDecor);
        
        // .........无关代码
    }
}
```

我们来看看两个核心代码：
1.`mDecor = generateDecor(-1)`: 创建了一个DecorView对象，这里不做分析
2.`mContentParent = generateLayout(mDecor)` 为***DecorView***对象设置布局，然后让mContentParent变量指向***DecorView***的contentView

我们再来看看`generateLayout`方法的实现

4）PhoneWindow.generateLayout

```java
protected ViewGroup generateLayout(DecorView decor) {
    // ..........无关代码
    
    //核心代码
    ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
    if (contentParent == null) {
        throw new RuntimeException("Window couldn't find content container view");
    }

    // ..........无关代码

    return contentParent;
}
```

该方法最终返回了contentParent局部变量，它是通过(ViewGroup)findViewById(ID_ANDROID_CONTENT)得到的ViewGroup对象，其实就是对应***DecorView***的contentView。
我们可以定位到findViewById中看看

### 5）PhoneWindow.findViewById

```
@Nullable
public <T extends View> T findViewById(@IdRes int id) {
    return getDecorView().findViewById(id);
}
```

调用该方法传入的参数是***ID_ANDROID_CONTENT***，对应***com.android.internal.R.id.content***，它就是***DecorView***布局中的contentView，同时它是一个***FrameLayout***

```
public static final int ID_ANDROID_CONTENT = com.android.internal.R.id.content;
```

至此，***DecorView***创建的过程已经分析完，但是需要注意，这个时候***DecorView***还未添加到***WMS***当中，而***DecorView***添加到***WMS***当中是在***Activity***的***onResume***阶段，而在【七.Activity】的【1.启动源码分析】中，我们能够知道***Activity***的***onResume***的阶段会经过`ActivityThread.handleActivityResum()`，***DecorView***就是在这个时候被添加到***WMS***当中的，我们来分析一下流程





## 3.DecorView添加到WMS源码分析

### 1）ActivityThread.handleResumeActivity

```java
public void handleResumeActivity(IBinder token, boolean finalStateRequest, boolean isForward,
        String reason) {
    // ..........无关代码
    

    if (!r.activity.mFinished && willBeVisible && r.activity.mDecor != null && !r.hideForNow) {
        // ..........无关代码
        if (r.activity.mVisibleFromClient) {
            r.activity.makeVisible();
        }
    }

    // 无关代码
}
```

可以看到该方法中调用了 `r.activity.makeVisible()`显示该***Activity***，我们看看该方法的实现

### 2）Activity.makeVisible

```java
void makeVisible() {
    if (!mWindowAdded) {
        ViewManager wm = getWindowManager();
        // 将DecorView加入WMS中
        wm.addView(mDecor, getWindow().getAttributes());
        mWindowAdded = true;
    }
    // 显示DecorView
    mDecor.setVisibility(View.VISIBLE);
}
```

该方法先将***DecorView***通过`getWindowManager()`获取到的ViewManager（这里就是指WindowManagerImpl）加入WMS中，然后显示***DecorView***
我们来看看`wm.addView(mDecor, getWindow().getAttributes())`

### 3）WindowManagerImpl.addView

```java
public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
    applyDefaultToken(params);
    mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);
}
```

mGlobal是一个***WindowManagerGlobal***对象，这里调用了它的`addView()`方法，所以我们直接定位到`WindowManagerGlobal.addView()`当中

### 4）WindowManagerGlobal.addView

```java
public void addView(View view, ViewGroup.LayoutParams params,
        Display display, Window parentWindow) {
    
    // ..........无关代码
    
    ViewRootImpl root;
   
    // ..........无关代码

    synchronized (mLock) {
        
        // ..........无关代码
        
        root = new ViewRootImpl(view.getContext(), display);
        view.setLayoutParams(wparams);
       
        mViews.add(view);
        mRoots.add(root);
        mParams.add(wparams);

        try {
            // 将decorView 绑定到 ViewRootImpl当中，该方法中会调用Requestlayout方法
            root.setView(view, wparams, panelParentView);
        } catch (RuntimeException e) {
            if (index >= 0) {
                removeViewLocked(index, true);
            }
            throw e;
        }
    }
}
```

root.setView 会调用 requestlayout() ，该方法这个之前分析过，它会开启View的三大流程，最后DecorView就被显示出来了

# 十.Activity四大启动方式分析

### 1.standard模式

 用strandard模式启动的activity，会创建一个新的实例然后加入当前位于前台的Activity栈顶部，如果重复启动同一个 Activity，那么将会重复创建实例

### 2.SingleTop模式

该模式下启动的Activity，
1.若当前位于前台的Activity栈有该Activity实例并且该Activity实例就位于栈顶，则不会重复创建实例，会触发onNewInstance回调。
2.其他情况，则会创建一个新的实例

### 3.SingleTask模式

该模式下启动Activity，会检查该在ActivityStack中是否存在，如果存在的话
1.若该Activity所处的Activity栈是后台栈，将该Activity栈变成前台栈
2.若该Activity并非位于Activity栈顶部，那么在它上面的Activity都将出栈使得该Activity位于栈顶，并且该Activity的onNewInstance被触发

### 4.SingleInstance模式

该模式下启动的Activity会独自占有一个Activity栈，启动Activity会直接将其所处的栈移至前台



