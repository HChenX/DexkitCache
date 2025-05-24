<div align="center">
<h1>DexkitCache</h1>

![stars](https://img.shields.io/github/stars/HChenX/DexkitCache?style=flat)
![Github repo size](https://img.shields.io/github/repo-size/HChenX/DexkitCache)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/HChenX/DexkitCache)](https://github.com/HChenX/DexkitCache/releases)
[![GitHub Release Date](https://img.shields.io/github/release-date/HChenX/DexkitCache)](https://github.com/HChenX/DexkitCache/releases)
![last commit](https://img.shields.io/github/last-commit/HChenX/DexkitCache?style=flat)
![language](https://img.shields.io/badge/language-java-purple)
![language](https://img.shields.io/badge/language-aidl-purple)

<p><b><a href="README-en.md">English</a> | <a href="README.md">简体中文</a></b></p>
<p>简易的 Dexkit 缓存构建与解析工具</p>
</div>

---

## ✨ 工具介绍

- 简易的 Dexkit 缓存构建与解析工具。

---

## ✨ 导入依赖

- 添加依赖：

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' } // 添加 JitPack 库
    }
}

dependencies {
    implementation 'com.github.HChenX:DexkitCache:0.2' // 引入依赖
    implementation 'org.luckypray:dexkit:XX' // dexkit
    implementation 'com.tencent:mmkv:XX' // 缓存储存工具
    implementation 'com.google.code.gson:gson:XX' // 缓存序列化与反序列化工具
}
```

- 同步项目并下载完成后即可使用本工具。

---

## 🛠 使用方法

- 使用方法：

```java
public class Test {
    public void init() {
        ClassLoader classLoader = null; // 当前的 classloader，不要传 null，仅演示
        String sourceDir = null; // 软件 apk 目录
        String dataDir = null; // 软件数据目录
        DexkitCache.init(classLoader, sourceDir, dataDir); // 初始化工具
    }

    public void use() {
        Class<?> clazz = DexkitCache.findMember("test_key", new IDexkit() {
            @NonNull
            @Override
            public BaseData dexkit(@NonNull DexKitBridge bridge) throws ReflectiveOperationException {
                ClassData classData = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("test class")
                    )
                ).single();
                return classData;
            }
        });

        Method[] methods = DexkitCache.findMemberList("test_list_key", new IDexkitList() {
            @NonNull
            @Override
            public BaseDataList<?> dexkit(@NonNull DexKitBridge bridge) throws ReflectiveOperationException {
                MethodDataList list = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .declaredClass(ClassMatcher.create()
                            .usingStrings("test class")
                        )
                        .usingStrings("test method")
                    )
                );
                return list;
            }
        });
    }

    public void close() {
        DexkitCache.close();
    }
}
```

- 几句简单代码即可实现！

---

## 🌟 混淆配置

```text
// 一般可以不配置
-keep class com.hchen.dexkitcache.* {*;}
```

---

## 🎉结尾

💖 **感谢你的支持，Enjoy your day!** 🚀
