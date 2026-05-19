package com.zzz.androidvocab.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

suspend fun <T> Flow<T>.firstValue(): T = first()
