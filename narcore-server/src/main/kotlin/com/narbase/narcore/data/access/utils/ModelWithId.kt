/*
 * NARBASE TECHNOLOGIES CONFIDENTIAL
 * ______________________________
 * [2017] - [2022] Narbase Technologies
 * All Rights Reserved.
 * Created by shalaga44
 * On: 24/Nov/2022.
 */

package com.narbase.narcore.data.access.utils

interface ModelWithId<Key : Comparable<Key>> {
    val id: Key?
}