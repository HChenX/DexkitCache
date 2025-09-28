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

/**
 * Dexkit 数据存储与解析接口
 *
 * @author 焕晨HChen
 */
public interface IDexkit<D> {
    /**
     * dexkit 查找
     *
     * @param bridge dexkit 实例
     */
    @NonNull
    D dexkit(@NonNull DexKitBridge bridge) throws ReflectiveOperationException;
}
