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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dexkit 缓存构建与解析工具
 *
 * @author 焕晨HChen
 * @noinspection FieldCanBeLocal
 */
public class DexkitCache {
    private static final String TAG = "DexkitCache";
    private static final String MMKV_PATH = "/files/hchen/dexkit_cache/mmkv";
    private static final String KEY_VERSION = "version";
    private static final String KEY_PACKAGE_INFO = "package_info";
    private static final String KEY_SYSTEM_VERSION = "system_version";
    private static final String TYPE_METHOD = "METHOD";
    private static final String TYPE_CLASS = "CLASS";
    private static final String TYPE_FIELD = "FIELD";
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
     * @param classLoader 类加载器
     * @param sourceDir   apk 路径
     * @param dataDir     apk 数据目录
     */
    public static void init(@NonNull ClassLoader classLoader, @NonNull String sourceDir, @NonNull String dataDir) {
        init(classLoader, sourceDir, dataDir, 1);
    }

    /**
     * 初始化 Dexkit 缓存工具
     *
     * @param classLoader 类加载器
     * @param sourceDir   apk 路径
     * @param dataDir     apk 数据目录
     * @param version     当前使用的缓存版本，请注意不同版本会导致所有缓存被删除
     */
    public static void init(@NonNull ClassLoader classLoader, @NonNull String sourceDir, @NonNull String dataDir, int version) {
        DexkitCache.classLoader = classLoader;
        DexkitCache.sourceDir = sourceDir;
        DexkitCache.dataDir = dataDir;
        DexkitCache.version = version;
    }

    @NonNull
    private static DexKitBridge createDexkitBridge() {
        return createDexkitBridge(null);
    }

    @NonNull
    private static DexKitBridge createDexkitBridge(ClassLoader classLoader) {
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
        mmkv = MMKV.mmkvWithID("dexkit_cache", MMKV.MULTI_PROCESS_MODE);
        if (mmkv.containsKey(KEY_VERSION)) {
            int version = mmkv.getInt(KEY_VERSION, 1);
            if (version != DexkitCache.version) {
                mmkv.clear();
                mmkv.putInt(KEY_VERSION, DexkitCache.version);
            }
        } else mmkv.putInt(KEY_VERSION, DexkitCache.version);

        String packageInfo = PackageHelper.getPackageVersionName(sourceDir) + "(" + PackageHelper.getPackageVersionCode(sourceDir) + ")";
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
        if (classLoader == null) dexKitBridge = DexKitBridge.create(sourceDir);
        else dexKitBridge = DexKitBridge.create(classLoader, false);

        return dexKitBridge;
    }

    /**
     * 查找成员
     *
     * @param key     此缓存的唯一 key
     * @param iDexkit dexkit 查找接口
     * @return 返回查找到的成员，可能是 Class、Method、Field
     */
    @NonNull
    public static <T> T findMember(@NonNull String key, @NonNull IDexkit iDexkit) {
        return findMember(key, classLoader, iDexkit);
    }

    /**
     * 查找成员
     *
     * @param key         此缓存的唯一 key
     * @param classLoader 指定类加载器，用于加载查找到的实例
     * @param iDexkit     dexkit 查找接口
     * @return 返回查找到的成员，可能是 Class、Method、Field
     * @noinspection IfCanBeSwitch
     */
    @NonNull
    public static <T> T findMember(@NonNull String key, ClassLoader classLoader, @NonNull IDexkit iDexkit) {
        DexKitBridge dexKitBridge = createDexkitBridge(classLoader);
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
                ;
                throw new UnexpectedException(e);
            } finally {
                close();
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
                    case null, default ->
                        throw new UnexpectedException("[DexkitCache]: Unknown MemberData type: " + data);
                }
            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
            }
        }
    }

    /**
     * 查找成员，列表类型
     *
     * @param key         此缓存的唯一 key
     * @param iDexKitList dexkit 查找接口
     * @return 返回一个列表，可能包含 Class、Method、Field 中的一种类型
     */
    @NonNull
    public static <T> List<T> findMemberList(@NonNull String key, @NonNull IDexkitList iDexKitList) {
        return findMemberList(key, classLoader, iDexKitList);
    }

    /**
     * 查找成员，列表类型
     *
     * @param key         此缓存的唯一 key
     * @param classLoader 指定类加载器，用于加载查找到的实例
     * @param iDexKitList dexkit 查找接口
     * @return 返回一个列表，可能包含 Class、Method、Field 中的一种类型
     * @noinspection IfCanBeSwitch, unchecked
     */
    @NonNull
    public static <T> List<T> findMemberList(@NonNull String key, ClassLoader classLoader, @NonNull IDexkitList iDexKitList) {
        DexKitBridge dexKitBridge = createDexkitBridge(classLoader);
        String cacheData = mmkv.getString(key, "");
        if (cacheData.isEmpty()) {
            try {
                BaseDataList<?> baseDataList = iDexKitList.dexkit(dexKitBridge);
                ArrayList<String> serializeList = new ArrayList<>();
                List<?> data = baseDataList.stream().map((Function<Object, Object>) baseData -> {
                    try {
                        if (baseData instanceof ClassData classData) {
                            serializeList.add(classData.toDexType().serialize());
                            return classData.getInstance(classLoader);
                        } else if (baseData instanceof MethodData methodData) {
                            serializeList.add(methodData.toDexMethod().serialize());
                            return methodData.getMethodInstance(classLoader);
                        } else if (baseData instanceof FieldData fieldData) {
                            serializeList.add(fieldData.toDexField().serialize());
                            return fieldData.getFieldInstance(classLoader);
                        } else
                            throw new UnexpectedException("[DexkitCache]: Unknown BaseData Type: " + baseData);
                    } catch (NoSuchFieldException | NoSuchMethodException |
                             ClassNotFoundException e) {
                        throw new UnexpectedException(e);
                    } finally {
                        close();
                    }
                }).collect(Collectors.toCollection(ArrayList::new));
                if (baseDataList instanceof FieldDataList)
                    mmkv.putString(key, gson.toJson(new MemberData(TYPE_FIELD, serializeList)));
                else if (baseDataList instanceof MethodDataList)
                    mmkv.putString(key, gson.toJson(new MemberData(TYPE_METHOD, serializeList)));
                else if (baseDataList instanceof ClassDataList)
                    mmkv.putString(key, gson.toJson(new MemberData(TYPE_CLASS, serializeList)));
                return (List<T>) data;
            } catch (ReflectiveOperationException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
            }
        } else {
            MemberData data = gson.fromJson(cacheData, new TypeToken<MemberData>() {
            }.getType());
            return (List<T>) data.serializeList.stream().map((Function<String, Object>) descriptor -> {
                try {
                    switch (data.type) {
                        case TYPE_CLASS -> {
                            return new DexClass(descriptor).getInstance(classLoader);
                        }
                        case TYPE_METHOD -> {
                            return new DexMethod(descriptor).getMethodInstance(classLoader);
                        }
                        case TYPE_FIELD -> {
                            return new DexField(descriptor).getFieldInstance(classLoader);
                        }
                        case null, default ->
                            throw new UnexpectedException("[DexkitCache]: Unknown MemberData type: " + data);
                    }
                } catch (ClassNotFoundException | NoSuchMethodException |
                         NoSuchFieldException e) {
                    throw new UnexpectedException(e);
                } finally {
                    close();
                }
            }).collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /**
     * 关闭 Dexkit，并清理资源
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

    private static final class MemberData {
        @NonNull
        public String type;
        public String serialize = "";
        public ArrayList<String> serializeList = new ArrayList<>();

        public MemberData(@NonNull String type, String serialize) {
            this.type = type;
            this.serialize = serialize;
        }

        public MemberData(@NonNull String type, ArrayList<String> serializeList) {
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
        private static final Method parsePackageMethod;
        private static final Object packageParser;

        static {
            try {
                @SuppressLint("PrivateApi")
                Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");
                parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
                parsePackageMethod.setAccessible(true);
                packageParser = packageParserClass.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                     InstantiationException | InvocationTargetException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
            }
        }

        @NonNull
        public static String getPackageVersionName(@NonNull String sourceDir) {
            try {
                File apkPath = new File(sourceDir);
                Object pkg = parsePackageMethod.invoke(packageParser, apkPath, 0);
                assert pkg != null;
                Field mVersionNameField = pkg.getClass().getDeclaredField("mVersionName");
                mVersionNameField.setAccessible(true);
                return ((String) Optional.ofNullable(mVersionNameField.get(pkg)).orElse("unknown")).trim();
            } catch (IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
            }
        }

        public static int getPackageVersionCode(@NonNull String sourceDir) {
            try {
                File apkPath = new File(sourceDir);
                Object pkg = parsePackageMethod.invoke(packageParser, apkPath, 0);
                assert pkg != null;
                Field mVersionCodeField = pkg.getClass().getDeclaredField("mVersionCode");
                mVersionCodeField.setAccessible(true);
                return (int) Optional.ofNullable(mVersionCodeField.get(pkg)).orElse(-1);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
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
            } finally {
                close();
            }
        }

        public static String getSystemVersion(@NonNull String key) {
            try {
                return (String) method.invoke(null, key);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new UnexpectedException(e);
            } finally {
                close();
            }
        }
    }
}
