package com.hj0128.moneybook.data.local

import androidx.room.TypeConverter
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.TransactionType

/**
 * enum 은 ordinal(숫자) 이 아니라 이름(문자열)으로 저장한다.
 * ordinal 로 저장하면 나중에 enum 순서만 바꿔도 기존 데이터가 전부 다른 뜻이 된다.
 */
class Converters {
    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name
}
