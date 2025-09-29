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

import com.tencent.mmkv.MMKV;

/**
 * MMKV 初始化时调用
 *
 * @author 焕晨HChen
 * */
public interface IInitialization {
    /**
     * 初始化 MMKV
     * */
    void initialization(@NonNull MMKV mmkv);
}
