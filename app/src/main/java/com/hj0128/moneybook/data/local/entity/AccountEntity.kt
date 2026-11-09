package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.PeriodUnit

/**
 * 자산(현금, 국민은행, 신한카드 ...).
 *
 * 카테고리처럼 상위 아래에 하위를 둘 수 있다(체크카드 > A카드/B카드). 2뎁스까지만 쓴다.
 * 카드마다 실적·한도가 따로라 카드를 한 장씩 자산으로 만들어야 하는데, 그러면 "체크카드로 얼마 썼지"를
 * 손으로 더해야 한다. 상위 아래에 모으면 합계로 보인다.
 */
@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            // 상위가 사라지면 하위는 상위로 올라온다. 지워지는 것보다 낫다(카테고리와 같다).
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parent_id")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    /** 앱 사용 시작 시점의 잔액(원). 현재 잔액은 거래를 누적해 계산한다. */
    @ColumnInfo(name = "initial_balance")
    val initialBalance: Long = 0L,
    /**
     * 선택 화면에서 쓰는 색상 (ARGB).
     *
     * 종류(현금/은행/카드)가 아니라 자산 하나하나에 붙는다. 종류로 색을 정하면
     * 은행 계좌를 둘 만들었을 때 둘이 같은 색이 되어 구분이 안 된다.
     */
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    /** 같은 부모 아래에서의 순서. 상위끼리, 같은 상위의 하위끼리 따로 매긴다(카테고리와 같다). */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    /**
     * 사용 한도(원). 복지포인트처럼 주기마다 쓸 수 있는 금액이 정해진 결제수단에만 둔다.
     *
     * 잔액으로 대신하지 않는 이유: 잔액은 해가 바뀌어도 저절로 채워지지 않아 매년 손으로
     * 맞춰야 한다. 한도는 주기만 정해 두면 사용액이 달력 기준으로 알아서 0부터 다시 센다.
     */
    @ColumnInfo(name = "limit_amount")
    val limitAmount: Long? = null,
    /** 한도가 다시 차는 주기. 달력 기준(매월 1일 / 매년 1월 1일)이다. [limitAmount] 와 함께 있거나 함께 없다. */
    @ColumnInfo(name = "limit_period")
    val limitPeriod: PeriodUnit? = null,
    /** 상위 자산이면 null, 하위 자산이면 부모 자산 id. */
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
    /**
     * 실적 목표(원). 카드 혜택을 받으려고 주기마다 채워야 하는 사용액이다.
     *
     * 한도와 방향이 반대라(넘기지 말 것 / 채울 것) 칸을 따로 둔다. 한 카드에 둘 다 있을 수 있다.
     */
    @ColumnInfo(name = "performance_target")
    val performanceTarget: Long? = null,
    /** 실적을 세는 주기. 달력 기준이다. [performanceTarget] 와 함께 있거나 함께 없다. */
    @ColumnInfo(name = "performance_period")
    val performancePeriod: PeriodUnit? = null,
    /**
     * 1단계([performanceTarget]) 위의 실적 단계. "700000,1000000"처럼 작은 것부터 쉼표로 잇는다. 없으면 null.
     *
     * 카드 혜택은 "30만원 이상 5%, 70만원 이상 10%"처럼 단계로 나뉘는 일이 많다.
     * 1단계는 원래 칸에 그대로 두어, 단계를 안 쓰는 카드와 실적 셈 쿼리는 바뀌지 않는다.
     * 목록 변환기를 두지 않은 이유: Room 은 List<Long> 변환기가 있으면 `IN (:ids)` 인자까지 그걸로 바꾼다.
     */
    @ColumnInfo(name = "performance_steps")
    val performanceSteps: String? = null,
    /**
     * 매월 실적을 세기 시작하는 날(1~31). null 이면 1일, 곧 달력 기준이다.
     * "매월 15일 ~ 다음 달 14일"처럼 세는 카드용. 그 달에 그 날이 없으면 그 달 마지막 날부터 센다.
     */
    @ColumnInfo(name = "performance_start_day")
    val performanceStartDay: Int? = null,
) {
    val isTopLevel: Boolean get() = parentId == null
}
