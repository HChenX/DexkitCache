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

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.BaseDataList;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.FieldDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.base.BaseData;
import org.luckypray.dexkit.wrap.DexClass;
import org.luckypray.dexkit.wrap.DexField;
import org.luckypray.dexkit.wrap.DexMethod;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Dexkit 缓存构建与解析工具
 *
 * @author 焕晨HChen
 * @noinspection FieldCanBeLocal
 */
public class DexkitCache {
    private static final String TAG = "DexkitCache";
    private static final String MMKV_PATH = "/files/hchen/dexkit_cache";
    private static final String KEY_VERSION = "version";
    private static final String KEY_PACKAGE_INFO = "package_info";
    private static final String KEY_SYSTEM_VERSION = "system_version";
    private static final String TYPE_METHOD = "METHOD";
    private static final String TYPE_CLASS = "CLASS";
    private static final String TYPE_FIELD = "FIELD";
    private static String cacheName = "dexkit_cache";
    private static int version = 1;
    private static ClassLoader classLoader;
    private static String sourceDir = null;
    private static String dataDir = null;
    private static String mmkvPath = null;
    private static MMKV mmkv = null;
    private static Gson gson = null;
    private static DexKitBridge dexKitBridge = null;

    private DexkitCache() {
    }

    /**
     * 初始化 Dexkit 缓存工具
     *
     * @param cacheName   缓存文件名称
     * @param classLoader 类加载器
     * @param sourceDir   apk 路径
     * @param dataDir     apk 数据目录
     */
    public static void init(@NonNull String cacheName, @NonNull ClassLoader classLoader, @NonNull String sourceDir, @NonNull String dataDir) {
        init(cacheName, classLoader, sourceDir, dataDir, 1);
    }

    /**
     * 初始化 Dexkit 缓存工具
     *
     * @param cacheName   缓存文件名称
     * @param classLoader 类加载器
     * @param sourceDir   apk 路径
     * @param dataDir     apk 数据目录
     * @param version     当前使用的缓存版本，请注意不同版本会导致所有缓存被删除
     */
    public static void init(@NonNull String cacheName, @NonNull ClassLoader classLoader, @NonNull String sourceDir, @NonNull String dataDir, int version) {
        DexkitCache.cacheName = cacheName;
        DexkitCache.classLoader = classLoader;
        DexkitCache.sourceDir = sourceDir;
        DexkitCache.dataDir = dataDir;
        DexkitCache.version = version;
    }

    /**
     * 更换新的类加载器
     */
    public static void setClassLoader(@NonNull ClassLoader classLoader) {
        DexkitCache.classLoader = classLoader;
        autoReloadIfNeed(classLoader);
    }

    @NonNull
    private static DexKitBridge createDexkitBridge(@NonNull ClassLoader classLoader) {
        if (Objects.isNull(classLoader))
            throw new NullPointerException("[DexkitCache]: ClassLoader can't be null!");
        if (Objects.isNull(sourceDir))
            throw new NullPointerException("[DexkitCache]: Source dir can't be null!");
        if (Objects.isNull(dataDir))
            throw new NullPointerException("[DexkitCache]: Data dir can't be null!");
        if (Objects.nonNull(dexKitBridge)) {
            if (dexKitBridge.isValid())
                return dexKitBridge;
        }

        close();
        mmkvPath = dataDir + MMKV_PATH;
        gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        MMKV.initialize(mmkvPath, System::loadLibrary);
        mmkv = MMKV.mmkvWithID(cacheName, MMKV.MULTI_PROCESS_MODE);
        if (mmkv.containsKey(KEY_VERSION)) {
            int version = mmkv.getInt(KEY_VERSION, 1);
            if (version != DexkitCache.version) {
                mmkv.clear();
                mmkv.putInt(KEY_VERSION, DexkitCache.version);
            }
        } else mmkv.putInt(KEY_VERSION, DexkitCache.version);

        String packageInfo = PackageHelper.getPackageVersionName() + "(" + PackageHelper.getPackageVersionCode() + ")";
        if (mmkv.containsKey(KEY_PACKAGE_INFO)) {
            String oldInfo = mmkv.getString(KEY_PACKAGE_INFO, "unknown");
            if (!Objects.equals(packageInfo, oldInfo)) {
                mmkv.clear();
                mmkv.putString(KEY_PACKAGE_INFO, packageInfo);
            }
        } else mmkv.putString(KEY_PACKAGE_INFO, packageInfo);

        String systemVersion = SystemHelper.getSystemVersion("ro.system.build.version.incremental");
        if (mmkv.containsKey(KEY_SYSTEM_VERSION)) {
            String oldVersion = mmkv.getString(KEY_SYSTEM_VERSION, "unknown");
            if (!Objects.equals(systemVersion, oldVersion)) {
                mmkv.clear();
                mmkv.putString(KEY_SYSTEM_VERSION, systemVersion);
            }
        } else mmkv.putString(KEY_SYSTEM_VERSION, systemVersion);

        System.loadLibrary("dexkit");
        dexKitBridge = DexKitBridge.create(classLoader, false);

        return dexKitBridge;
    }

    /**
     * 查找成员
     *
     * @param key     此缓存的唯一 key，如果为 null 则不启用缓存
     * @param iDexkit dexkit 查找接口
     * @return 返回查找到的成员，可能是 Class、Method、Field
     */
    @NonNull
    public static <T> T findMember(@Nullable String key, @NonNull IDexkit iDexkit) {
        return findMember(key, classLoader, iDexkit);
    }

    /**
     * 查找成员
     *
     * @param key         此缓存的唯一 key，如果为 null 则不启用缓存
     * @param classLoader 指定类加载器，用于加载查找到的实例
     * @param iDexkit     dexkit 查找接口
     * @return 返回查找到的成员，可能是 Class、Method、Field
     * @noinspection IfCanBeSwitch
     */
    @NonNull
    public static <T> T findMember(@Nullable String key, @NonNull ClassLoader classLoader, @NonNull IDexkit iDexkit) {
        autoReloadIfNeed(classLoader);
        DexKitBridge dexKitBridge = createDexkitBridge(classLoader);
        if (key == null) {
            try {
                BaseData baseData = iDexkit.dexkit(dexKitBridge);
                if (baseData instanceof ClassData classData)
                    return (T) classData.getInstance(classLoader);
                else if (baseData instanceof MethodData methodData)
                    return (T) methodData.getMethodInstance(classLoader);
                else if (baseData instanceof FieldData fieldData)
                    return (T) fieldData.getFieldInstance(classLoader);
                else
                    throw new UnexpectedException("[DexkitCache]: Unknown BaseData type: " + baseData);
            } catch (ReflectiveOperationException e) {
                throw new UnexpectedException(e);
            }
        } else {
            String cacheData = mmkv.getString(key, "");
            if (cacheData.isEmpty()) {
                try {
                    BaseData baseData = iDexkit.dexkit(dexKitBridge);
                    if (baseData instanceof ClassData classData) {
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_CLASS, classData.toDexType().serialize())));
                        return (T) classData.getInstance(classLoader);
                    } else if (baseData instanceof MethodData methodData) {
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_METHOD, methodData.toDexMethod().serialize())));
                        return (T) methodData.getMethodInstance(classLoader);
                    } else if (baseData instanceof FieldData fieldData) {
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_FIELD, fieldData.toDexField().serialize())));
                        return (T) fieldData.getFieldInstance(classLoader);
                    } else {
                        throw new UnexpectedException("[DexkitCache]: Unknown BaseData type: " + baseData);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new UnexpectedException(e);
                }
            } else {
                MemberData data = gson.fromJson(cacheData, new TypeToken<MemberData>() {
                }.getType());
                try {
                    switch (data.type) {
                        case TYPE_CLASS -> {
                            return (T) new DexClass(data.serialize).getInstance(classLoader);
                        }
                        case TYPE_METHOD -> {
                            return (T) new DexMethod(data.serialize).getMethodInstance(classLoader);
                        }
                        case TYPE_FIELD -> {
                            return (T) new DexField(data.serialize).getFieldInstance(classLoader);
                        }
                        default ->
                            throw new UnexpectedException("[DexkitCache]: Unknown MemberData type: " + data);
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
                    throw new UnexpectedException(e);
                }
            }
        }
    }

    /**
     * 查找成员，列表类型
     *
     * @param key         此缓存的唯一 key，如果为 null 则不启用缓存
     * @param iDexKitList dexkit 查找接口
     * @return 返回一个列表，可能包含 Class、Method、Field 中的一种类型
     */
    @NonNull
    public static <T> T[] findMemberList(@Nullable String key, @NonNull IDexkitList iDexKitList) {
        return findMemberList(key, classLoader, iDexKitList);
    }

    /**
     * 查找成员，列表类型
     *
     * @param key         此缓存的唯一 key，如果为 null 则不启用缓存
     * @param classLoader 指定类加载器，用于加载查找到的实例
     * @param iDexKitList dexkit 查找接口
     * @return 返回一个列表，可能包含 Class、Method、Field 中的一种类型
     * @noinspection IfCanBeSwitch, unchecked
     */
    @NonNull
    @SuppressLint("NonUniqueDexKitData")
    public static <T> T[] findMemberList(@Nullable String key, @NonNull ClassLoader classLoader, @NonNull IDexkitList iDexKitList) {
        autoReloadIfNeed(classLoader);
        DexKitBridge dexKitBridge = createDexkitBridge(classLoader);
        var ref = new Object() {
            volatile Class<?> type = null;
        };
        if (key == null) {
            try {
                BaseDataList<?> baseDataList = iDexKitList.dexkit(dexKitBridge);
                if (baseDataList.first() instanceof ClassData) ref.type = Class.class;
                else if (baseDataList.first() instanceof MethodData) ref.type = Method.class;
                else if (baseDataList.first() instanceof FieldData) ref.type = Field.class;
                else
                    throw new UnexpectedException("[DexkitCache]: Unknown BaseData type: " + baseDataList.first());
                return baseDataList.stream().map((Function<Object, T>) baseData -> {
                    try {
                        if (baseData instanceof ClassData classData) {
                            return (T) classData.getInstance(classLoader);
                        } else if (baseData instanceof MethodData methodData) {
                            return (T) methodData.getMethodInstance(classLoader);
                        } else if (baseData instanceof FieldData fieldData) {
                            return (T) fieldData.getFieldInstance(classLoader);
                        } else
                            throw new UnexpectedException("[DexkitCache]: Unknown BaseData type: " + baseData);
                    } catch (ClassNotFoundException | NoSuchMethodException |
                             NoSuchFieldException e) {
                        throw new UnexpectedException(e);
                    }
                }).toArray(value -> (T[]) Array.newInstance(ref.type, value));
            } catch (ReflectiveOperationException e) {
                throw new UnexpectedException(e);
            }
        } else {
            String cacheData = mmkv.getString(key, "");
            if (cacheData.isEmpty()) {
                try {
                    BaseDataList<?> baseDataList = iDexKitList.dexkit(dexKitBridge);
                    ArrayList<String> serializeList = new ArrayList<>();
                    if (baseDataList.first() instanceof ClassData) ref.type = Class.class;
                    else if (baseDataList.first() instanceof MethodData) ref.type = Method.class;
                    else if (baseDataList.first() instanceof FieldData) ref.type = Field.class;
                    else
                        throw new UnexpectedException("[DexkitCache]: Unknown BaseData type: " + baseDataList.first());
                    T[] data = baseDataList.stream().map((Function<Object, T>) baseData -> {
                        try {
                            if (baseData instanceof ClassData classData) {
                                serializeList.add(classData.toDexType().serialize());
                                return (T) classData.getInstance(classLoader);
                            } else if (baseData instanceof MethodData methodData) {
                                serializeList.add(methodData.toDexMethod().serialize());
                                return (T) methodData.getMethodInstance(classLoader);
                            } else if (baseData instanceof FieldData fieldData) {
                                serializeList.add(fieldData.toDexField().serialize());
                                return (T) fieldData.getFieldInstance(classLoader);
                            } else
                                throw new UnexpectedException("[DexkitCache]: Unknown BaseData Type: " + baseData);
                        } catch (NoSuchFieldException | NoSuchMethodException |
                                 ClassNotFoundException e) {
                            throw new UnexpectedException(e);
                        }
                    }).toArray(value -> (T[]) Array.newInstance(ref.type, value));
                    if (baseDataList instanceof FieldDataList)
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_FIELD, serializeList)));
                    else if (baseDataList instanceof MethodDataList)
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_METHOD, serializeList)));
                    else if (baseDataList instanceof ClassDataList)
                        mmkv.putString(key, gson.toJson(new MemberData(TYPE_CLASS, serializeList)));
                    return data;
                } catch (ReflectiveOperationException e) {
                    throw new UnexpectedException(e);
                }
            } else {
                MemberData data = gson.fromJson(cacheData, new TypeToken<MemberData>() {
                }.getType());
                if (TYPE_CLASS.equals(data.type)) ref.type = Class.class;
                else if (TYPE_METHOD.equals(data.type)) ref.type = Method.class;
                else if (TYPE_FIELD.equals(data.type)) ref.type = Field.class;
                else
                    throw new UnexpectedException("[DexkitCache]: Unknown MemberData type: " + data);
                return data.serializeList.stream().map(descriptor -> {
                    try {
                        switch (data.type) {
                            case TYPE_CLASS -> {
                                return (T) new DexClass(descriptor).getInstance(classLoader);
                            }
                            case TYPE_METHOD -> {
                                return (T) new DexMethod(descriptor).getMethodInstance(classLoader);
                            }
                            case TYPE_FIELD -> {
                                return (T) new DexField(descriptor).getFieldInstance(classLoader);
                            }
                            default ->
                                throw new UnexpectedException("[DexkitCache]: Unknown MemberData type: " + data);
                        }
                    } catch (ClassNotFoundException | NoSuchMethodException |
                             NoSuchFieldException e) {
                        throw new UnexpectedException(e);
                    }
                }).toArray(value -> (T[]) Array.newInstance(ref.type, value));
            }
        }
    }

    /**
     * 关闭 Dexkit，并清理资源
     * <p>
     * 强烈建议在 try-catch-finally 中的 finally 块中调用！
     * <p>
     * 尽量避免 dexkit 在使用后未被关闭！
     */
    public static void close() {
        if (Objects.nonNull(dexKitBridge))
            dexKitBridge.close();
        dexKitBridge = null;

        if (mmkv != null)
            mmkv.close();
        mmkv = null;
        gson = null;
    }

    private static void autoReloadIfNeed(@NonNull ClassLoader classLoader) {
        if (!Objects.equals(DexkitCache.classLoader, classLoader)) {
            DexkitCache.classLoader = classLoader;
            close();
        }
    }

    private static final class MemberData {
        @NonNull
        public String type;
        public String serialize = "";
        public ArrayList<String> serializeList = new ArrayList<>();

        public MemberData(@NonNull String type, @NonNull String serialize) {
            this.type = type;
            this.serialize = serialize;
        }

        public MemberData(@NonNull String type, @NonNull ArrayList<String> serializeList) {
            this.type = type;
            this.serializeList = serializeList;
        }

        @Override
        @NonNull
        public String toString() {
            return "MemberData{" +
                "type='" + type + '\'' +
                ", serialize='" + serialize + '\'' +
                ", serializeList=" + serializeList +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MemberData that)) return false;
            return Objects.equals(type, that.type) &&
                Objects.equals(serialize, that.serialize) &&
                Objects.equals(serializeList, that.serializeList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, serialize, serializeList);
        }
    }

    private static class PackageHelper {
        private static final Object pkg;
        private static final Field mVersionNameField;
        private static final Field mVersionCodeField;

        static {
            try {
                @SuppressLint("PrivateApi")
                Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
                Method parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
                parsePackageMethod.setAccessible(true);
                Object packageParser = packageParserClass.getDeclaredConstructor().newInstance();
                pkg = parsePackageMethod.invoke(packageParser, new File(sourceDir), 0);
                assert pkg != null;
                mVersionNameField = pkg.getClass().getDeclaredField("mVersionName");
                mVersionNameField.setAccessible(true);
                mVersionCodeField = pkg.getClass().getDeclaredField("mVersionCode");
                mVersionCodeField.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                     InstantiationException | InvocationTargetException | NoSuchFieldException e) {
                throw new UnexpectedException(e);
            }
        }

        @NonNull
        public static String getPackageVersionName() {
            try {
                return ((String) Optional.ofNullable(mVersionNameField.get(pkg)).orElse("unknown")).trim();
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            }
        }

        public static int getPackageVersionCode() {
            try {
                return (int) Optional.ofNullable(mVersionCodeField.get(pkg)).orElse(-1);
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            }
        }
    }

    public static class SystemHelper {
        private static final Method method;

        static {
            try {
                @SuppressLint("PrivateApi")
                Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");
                method = systemPropertiesClass.getDeclaredMethod("get", String.class);
                method.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                throw new UnexpectedException(e);
            }
        }

        public static String getSystemVersion(@NonNull String key) {
            try {
                return (String) method.invoke(null, key);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new UnexpectedException(e);
            }
        }
    }
}
