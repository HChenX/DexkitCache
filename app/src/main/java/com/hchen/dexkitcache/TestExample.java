/*
 * This file is part of DexkitCache.

 * DexkitCache is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HChenX
 */
package com.hchen.dexkitcache;

import androidx.annotation.NonNull;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.BaseDataList;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Method;

/**
 * 测试类
 *
 * @author 焕晨HChen
 */
final class TestExample {
    public void init() {
        ClassLoader classLoader = null; // 当前的 classloader，不要传 null，仅演示
        String sourceDir = null; // 软件 apk 目录
        String dataDir = null; // 软件数据目录
        DexkitCache.init("test_cache", classLoader, sourceDir, dataDir); // 初始化工具
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
